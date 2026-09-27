package com.protectfinanceddevices.app.core.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.protectfinanceddevices.app.core.crypto.AndroidKeyStoreManager
import com.protectfinanceddevices.app.core.network.ApiClient
import com.protectfinanceddevices.app.core.network.ApiConfig
import com.protectfinanceddevices.app.core.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Background WorkManager worker responsible for dispatching periodic cryptographically authenticated
 * device heartbeats to the financing backend authority.
 *
 * Adheres strictly to Android WorkManager standards:
 * - Minimum 15-minute periodic interval
 * - Network constraint validation
 * - Hardware Keystore EC P-256 signing (${enrollmentId}|${nonce}|${timestamp})
 * - Replay prevention with unique nonces
 * - Exponential backoff retry on transient network or server errors
 */
class DeviceHeartbeatWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val keyStoreManager = AndroidKeyStoreManager()
    private val database = AppDatabase.getDatabase(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing scheduled secure device heartbeat worker...")

        // 1. Verify network connectivity
        if (!isNetworkConnected()) {
            Log.w(TAG, "No network connectivity detected. Backing off with WorkManager retry.")
            return@withContext Result.retry()
        }

        try {
            // 2. Fetch local active enrollment from Room DB
            val enrollmentDao = database.deviceEnrollmentDao()
            val enrollment = enrollmentDao.getActiveEnrollment()
            val enrollmentId = enrollment?.enrollmentId ?: run {
                Log.w(TAG, "No active enrollment record located in local storage. Skipping heartbeat.")
                return@withContext Result.success()
            }

            // If enrollment is locally marked revoked, do not waste battery
            if (enrollment.enrollmentStatus == "REVOKED") {
                Log.i(TAG, "Device enrollment is revoked. Skipping periodic heartbeat.")
                return@withContext Result.success()
            }

            // 3. Assemble hardware telemetry metrics
            val batteryPercent = getBatteryPercentage()
            val networkState = getNetworkTypeString()
            val timestamp = System.currentTimeMillis()
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val appVersion = ApiConfig.CLIENT_APP_VERSION
            val managementStatus = enrollment.managementMode

            // 4. Hardware Keystore cryptographic signature
            // Canonical format: `${enrollmentId}|${nonce}|${timestamp}`
            val canonicalData = "$enrollmentId|$nonce|$timestamp"
            val signature = keyStoreManager.signData(canonicalData.toByteArray(Charsets.UTF_8))

            // 5. Construct payload for backend ingestion
            val payload = JSONObject().apply {
                put("enrollmentId", enrollmentId)
                put("nonce", nonce)
                put("timestamp", timestamp)
                put("signature", signature)
                put("batteryPercent", batteryPercent)
                put("networkType", networkState)
                put("appVersion", appVersion)
                put("managementStatus", managementStatus)
            }

            val serverUrl = enrollment.serverUrl.ifEmpty { ApiConfig.DEFAULT_BASE_URL }
            val client = ApiClient(serverUrl)
            val response = client.post(ApiConfig.ENDPOINT_HEARTBEAT, payload)

            when (response.statusCode) {
                in 200..299 -> {
                    Log.i(TAG, "Device heartbeat acknowledged by server (HTTP ${response.statusCode}). Updating local sync timestamp.")
                    enrollmentDao.updateLastSync(enrollmentId, timestamp)
                    enrollmentDao.updateEnrollmentStatus(enrollmentId, "ACTIVE")
                    Result.success()
                }
                401 -> {
                    Log.e(TAG, "Server rejected signature (HTTP 401 INVALID_SIGNATURE).")
                    Result.failure()
                }
                403 -> {
                    Log.e(TAG, "Server rejected heartbeat: Device enrollment suspended or revoked (HTTP 403).")
                    enrollmentDao.updateEnrollmentStatus(enrollmentId, "REVOKED")
                    Result.failure()
                }
                400 -> {
                    Log.w(TAG, "Heartbeat bad request: ${response.errorMessage}. Will retry on next window.")
                    Result.retry()
                }
                in 500..599 -> {
                    Log.w(TAG, "Server transient error during heartbeat sync (HTTP ${response.statusCode}). Retrying with backoff.")
                    Result.retry()
                }
                else -> {
                    Log.w(TAG, "Unexpected HTTP response ${response.statusCode}: ${response.errorMessage}. Retrying.")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during secure heartbeat dispatch: ${e.message}", e)
            Result.retry()
        }
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                ((level / scale.toFloat()) * 100).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    private fun getNetworkTypeString(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "UNKNOWN"
            val activeNetwork = connectivityManager.activeNetwork ?: return "DISCONNECTED"
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "UNKNOWN"
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "OTHER"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    companion object {
        private const val TAG = "DeviceHeartbeatWorker"
    }
}
