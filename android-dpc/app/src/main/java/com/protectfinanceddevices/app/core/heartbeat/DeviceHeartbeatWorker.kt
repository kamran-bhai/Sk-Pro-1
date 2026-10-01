package com.protectfinanceddevices.app.core.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.protectfinanceddevices.app.core.crypto.AndroidKeyStoreManager
import com.protectfinanceddevices.app.core.dpc.RemoteCommandExecutor
import com.protectfinanceddevices.app.core.storage.entities.DeviceCommandEntity
import com.protectfinanceddevices.app.core.storage.dao.DeviceCommandDao
import org.json.JSONArray
import java.time.Instant
import com.protectfinanceddevices.app.core.network.ApiClient
import com.protectfinanceddevices.app.core.network.ApiConfig
import com.protectfinanceddevices.app.core.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.security.MessageDigest

class DeviceHeartbeatWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val keyStoreManager = AndroidKeyStoreManager()
    private val database = AppDatabase.getDatabase(context)
    private val commandExecutor = RemoteCommandExecutor(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing scheduled secure device heartbeat worker...")

        if (!isNetworkConnected()) {
            Log.w(TAG, "No network connectivity detected. Backing off with WorkManager retry.")
            return@withContext Result.retry()
        }

        try {
            val enrollmentDao = database.deviceEnrollmentDao()
            val deviceDao = database.deviceDao()
            val enrollment = enrollmentDao.getActiveEnrollment()
            val enrollmentId = enrollment?.enrollmentId ?: run {
                Log.w(TAG, "No active enrollment record located in local storage. Skipping heartbeat.")
                return@withContext Result.success()
            }

            if (enrollment.enrollmentStatus == "REVOKED") {
                Log.i(TAG, "Device enrollment is revoked. Skipping periodic heartbeat.")
                return@withContext Result.success()
            }

            val batteryPercent = getBatteryPercentage()
            val networkState = getNetworkTypeString()
            val timestamp = System.currentTimeMillis()
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val appVersion = ApiConfig.CLIENT_APP_VERSION
            val managementStatus = enrollment.managementMode
            val simSnapshot = readSimSnapshot()

            val canonicalData = "$enrollmentId|$nonce|$timestamp"
            val signature = keyStoreManager.signData(canonicalData.toByteArray(Charsets.UTF_8))

            val payload = JSONObject().apply {
                put("enrollmentId", enrollmentId)
                put("nonce", nonce)
                put("timestamp", timestamp)
                put("signature", signature)
                put("batteryPercent", batteryPercent)
                put("networkType", networkState)
                put("androidVersion", Build.VERSION.RELEASE)
                put("appVersion", appVersion)
                put("managementStatus", managementStatus)
                simSnapshot.carrier?.let { put("simCarrier", it) }
                simSnapshot.fingerprint?.let { put("simFingerprint", it) }
                simSnapshot.subscriptionCount?.let { put("simSubscriptionCount", it) }
            }

            val serverUrl = enrollment.serverUrl.ifEmpty { ApiConfig.DEFAULT_BASE_URL }
            val client = ApiClient(serverUrl)
            val response = client.post(ApiConfig.ENDPOINT_HEARTBEAT, payload)

            when (response.statusCode) {
                in 200..299 -> {
                    Log.i(TAG, "Device heartbeat acknowledged by server. Updating verified local telemetry.")
                    enrollmentDao.updateLastSync(enrollmentId, timestamp)

                    val heartbeatData = response.data?.optJSONObject("data")
                    val authoritativeStatus =
                        heartbeatData?.optString("enrollmentStatus")
                            ?.takeIf { it.isNotBlank() }
                            ?: "ACTIVE"
                    enrollmentDao.updateEnrollmentStatus(
                        enrollmentId,
                        authoritativeStatus
                    )

                    val localDevice = deviceDao.getDeviceById(enrollment.deviceId)
                    if (localDevice != null) {
                        deviceDao.markHeartbeatAcknowledged(
                            deviceId = localDevice.id,
                            lastSeenTimestamp = timestamp,
                            batteryPercent = batteryPercent
                        )
                    }

                    val pendingCommands = heartbeatData?.optJSONArray("pendingCommands") ?: JSONArray()
                    processPendingCommands(
                        enrollmentId = enrollmentId,
                        deviceId = enrollment.deviceId,
                        serverUrl = serverUrl,
                        commands = pendingCommands,
                        commandDao = enrollmentDao.let { database.deviceCommandDao() }
                    )

                    Result.success()
                }
                401 -> {
                    Log.e(TAG, "Server rejected signature (HTTP 401 INVALID_SIGNATURE).")
                    Result.failure()
                }
                403 -> {
                    Log.e(TAG, "Server rejected heartbeat: Device enrollment suspended or revoked (HTTP 403).")
                    enrollmentDao.updateEnrollmentStatus(enrollmentId, "REVOKED")
                    deviceDao.markOffline(enrollment.deviceId)
                    Result.failure()
                }
                400 -> {
                    Log.w(TAG, "Heartbeat bad request: " + response.errorMessage + ". Will retry on next window.")
                    Result.retry()
                }
                in 500..599 -> {
                    Log.w(TAG, "Server transient error during heartbeat sync (HTTP " + response.statusCode + "). Retrying with backoff.")
                    Result.retry()
                }
                else -> {
                    Log.w(TAG, "Unexpected HTTP response " + response.statusCode + ": " + response.errorMessage + ". Retrying.")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during secure heartbeat dispatch: " + e.message, e)
            Result.retry()
        }
    }

    private suspend fun processPendingCommands(
        enrollmentId: String,
        deviceId: String,
        serverUrl: String,
        commands: JSONArray,
        commandDao: DeviceCommandDao
    ) {
        for (index in 0 until commands.length()) {
            val command = commands.optJSONObject(index) ?: continue
            val commandId = command.optString("commandId").takeIf { it.isNotBlank() } ?: continue
            val commandType = command.optString("commandType").takeIf { it.isNotBlank() } ?: continue
            val nonce = command.optString("nonce").takeIf { it.isNotBlank() } ?: continue
            val serverSignature = command.optString("serverSignature")
            val expiresAtString = command.optString("expiresAt").takeIf { it.isNotBlank() } ?: continue

            val expiresAt = try {
                Instant.parse(expiresAtString).toEpochMilli()
            } catch (_: Exception) {
                Log.w(TAG, "Ignoring command " + commandId + ": invalid expiry timestamp.")
                continue
            }

            val existing = commandDao.getCommandById(commandId)
            if (existing?.status == "ACKNOWLEDGED") continue

            val local = existing ?: DeviceCommandEntity(
                commandId = commandId,
                deviceId = deviceId,
                commandType = commandType,
                status = "PENDING",
                nonce = nonce,
                serverSignature = serverSignature,
                issuedAt = System.currentTimeMillis(),
                expiresAt = expiresAt
            )

            if (existing == null) {
                commandDao.insertCommand(local)
            }

            if (System.currentTimeMillis() >= expiresAt) {
                commandDao.updateCommandStatus(
                    commandId,
                    "EXPIRED",
                    "Command expired before execution."
                )
                continue
            }

            val executionResult =
                if (local.status == "SENT") {
                    RemoteCommandExecutor.ExecutionResult(
                        success = local.executionLog?.startsWith("DEVICE_LOCKED:") == true,
                        reason = local.executionLog ?: "Previously executed; retrying acknowledgment."
                    )
                } else {
                    commandExecutor.execute(commandType)
                }

            if (local.status != "SENT") {
                commandDao.updateCommandStatus(
                    commandId,
                    "SENT",
                    executionResult.reason
                )
            }

            val executionStatus = if (executionResult.success) "SUCCESS" else "FAILED"
            val ackCanonical =
                commandId + "|" + enrollmentId + "|" + executionStatus + "|" + nonce
            val deviceSignature = keyStoreManager.signData(
                ackCanonical.toByteArray(Charsets.UTF_8)
            )

            val ackPayload = JSONObject().apply {
                put("commandId", commandId)
                put("enrollmentId", enrollmentId)
                put("executionStatus", executionStatus)
                put("deviceSignature", deviceSignature)
                if (!executionResult.success) {
                    put("failureReason", executionResult.reason)
                }
            }

            val ackResponse = ApiClient(serverUrl).post(
                ApiConfig.ENDPOINT_COMMAND_ACK,
                ackPayload
            )

            if (ackResponse.isSuccess) {
                commandDao.updateCommandStatus(
                    commandId,
                    if (executionResult.success) "ACKNOWLEDGED" else "FAILED",
                    executionResult.reason
                )
            } else {
                Log.w(
                    TAG,
                    "Command " + commandId + " executed locally but ACK was not accepted: " +
                        (ackResponse.errorMessage ?: "unknown server error")
                )
            }
        }
    }

    private data class SimSnapshot(
        val carrier: String?,
        val fingerprint: String?,
        val subscriptionCount: Int?
    )

    private fun readSimSnapshot(): SimSnapshot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return SimSnapshot(null, null, null)
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "READ_PHONE_STATE not granted; SIM change telemetry is unavailable.")
            return SimSnapshot(null, null, null)
        }

        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
                ?: return SimSnapshot(null, null, null)
            val subscriptions = manager.activeSubscriptionInfoList.orEmpty()
            if (subscriptions.isEmpty()) {
                return SimSnapshot("NO_ACTIVE_SUBSCRIPTION", "NO_ACTIVE_SUBSCRIPTION", 0)
            }

            val normalized = subscriptions.map { info ->
                val carrierId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.carrierId.toString()
                } else {
                    info.carrierName?.toString().orEmpty()
                }
                info.subscriptionId.toString() + "|" + info.simSlotIndex + "|" + carrierId
            }.sorted()

            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.joinToString(";").toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }

            val carrier = subscriptions
                .mapNotNull { it.carrierName?.toString()?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted()
                .joinToString(", ")
                .ifBlank { "UNKNOWN" }

            SimSnapshot(carrier, digest, subscriptions.size)
        } catch (security: SecurityException) {
            Log.w(TAG, "SIM telemetry unavailable because Android denied subscription access.", security)
            SimSnapshot(null, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Android subscription telemetry.", e)
            SimSnapshot(null, null, null)
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
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
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
