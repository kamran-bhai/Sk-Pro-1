package com.protectfinanceddevices.app.core.network

import android.os.Build
import android.util.Log
import com.protectfinanceddevices.app.core.crypto.AndroidKeyStoreManager
import com.protectfinanceddevices.app.core.storage.AppDatabase
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEnrollmentEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import org.json.JSONObject

data class EnrollmentDisclosure(
    val enrollmentId: String,
    val deviceId: String,
    val customerName: String,
    val deviceModel: String,
    val totalAmount: Double,
    val installmentAmount: Double,
    val nextDueDate: String,
    val agreementCode: String,
    val disclosureText: String
)

sealed class EnrollmentResult {
    data class Success(val enrollmentId: String, val message: String) : EnrollmentResult()
    data class Failure(val error: String) : EnrollmentResult()
}

/**
 * Service managing client enrollment lifecycle with the backend server.
 * Executes the 3-step cryptographically secure enrollment flow:
 * 1. Fetch disclosure details using one-time pairing code
 * 2. Request unique challenge nonce from server
 * 3. Sign challenge nonce using Android Keystore EC P-256 private key and complete verification
 */
class DeviceEnrollmentService(
    private val apiClient: ApiClient = ApiClient(),
    private val keyStoreManager: AndroidKeyStoreManager,
    private val database: AppDatabase
) {

    /**
     * Step 1: Resolves one-time pairing code or QR code ticket to view financing disclosure.
     */
    suspend fun fetchEnrollmentDisclosure(enrollmentCode: String): Result<EnrollmentDisclosure> {
        val path = "${ApiConfig.ENDPOINT_ENROLLMENT_DISCLOSURE}/$enrollmentCode"
        val response = apiClient.get(path)

        if (!response.isSuccess || response.data == null) {
            return Result.failure(Exception(response.errorMessage ?: "Failed to resolve pairing code"))
        }

        return try {
            val root = response.data
            val data = root.getJSONObject("data")
            val enrollment = data.getJSONObject("enrollment")
            val device = data.optJSONObject("device")
            val customer = data.optJSONObject("customer")
            val agreement = data.optJSONObject("agreement")

            val disclosure = EnrollmentDisclosure(
                enrollmentId = enrollment.getString("id"),
                deviceId = enrollment.getString("deviceId"),
                customerName = customer?.optString("fullName", "Customer") ?: "Customer",
                deviceModel = "${device?.optString("brand", "")} ${device?.optString("model", "")}".trim(),
                totalAmount = agreement?.optDouble("totalAmount", 0.0) ?: 0.0,
                installmentAmount = agreement?.optDouble("installmentAmount", 0.0) ?: 0.0,
                nextDueDate = agreement?.optString("nextDueDate", "") ?: "",
                agreementCode = agreement?.optString("agreementCode", "") ?: "",
                disclosureText = data.optString("disclosureText", "Financing protection agreement")
            )
            Result.success(disclosure)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing enrollment disclosure: ${e.message}", e)
            Result.failure(Exception("Malformed disclosure response from server"))
        }
    }

    /**
     * Step 2 & 3: Requests challenge nonce, signs with Keystore EC P-256 key, and verifies on server.
     */
    suspend fun executeEnrollmentVerification(
        enrollmentId: String,
        onProgress: (String) -> Unit
    ): EnrollmentResult {
        try {
            onProgress("1/4 Requesting attestation challenge nonce from server...")
            val challengePath = "${ApiConfig.ENDPOINT_ENROLLMENT_CHALLENGE}/$enrollmentId/challenge"
            val challengeResp = apiClient.post(challengePath, JSONObject())

            if (!challengeResp.isSuccess || challengeResp.data == null) {
                return EnrollmentResult.Failure(challengeResp.errorMessage ?: "Failed to obtain challenge nonce")
            }

            val challengeData = challengeResp.data.getJSONObject("data")
            // Backend names the challenge field challengeNonce; keep this contract exact.
            val nonce = challengeData.getString("challengeNonce")

            onProgress("2/4 Accessing hardware-backed EC P-256 keypair in Android Keystore...")
            // Ensure keypair exists inside StrongBox / TEE
            val publicKeyPem = keyStoreManager.getOrCreateEnrollmentKeyPair()

            onProgress("3/4 Signing attestation challenge with device private key...")
            val signature = keyStoreManager.signData(nonce.toByteArray(Charsets.UTF_8))

            onProgress("4/4 Submitting attestation proof to backend authority...")
            val verifyPath = "${ApiConfig.ENDPOINT_ENROLLMENT_VERIFY}/$enrollmentId/verify"
            val verifyPayload = JSONObject().apply {
                // Match EnrollmentController.verifyEnrollmentKey() exactly.
                put("challengeNonce", nonce)
                put("signedChallenge", signature)
                put("devicePublicKeyPem", publicKeyPem)
                put("androidVersion", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                put("appVersion", ApiConfig.CLIENT_APP_VERSION)
                // Do not claim Device Owner unless Android actually grants it.
                put("managementMode", "UNMANAGED")
            }

            val verifyResp = apiClient.post(verifyPath, verifyPayload)
            if (!verifyResp.isSuccess || verifyResp.data == null) {
                return EnrollmentResult.Failure(verifyResp.errorMessage ?: "Cryptographic verification failed on server")
            }

            // Verification only registers the key. Complete the enrollment explicitly.
            onProgress("5/5 Activating enrollment on server...")
            val completePath = "${ApiConfig.ENDPOINT_ENROLLMENT_DISCLOSURE}/$enrollmentId/complete"
            val completeResp = apiClient.post(completePath, JSONObject())
            if (!completeResp.isSuccess || completeResp.data == null) {
                return EnrollmentResult.Failure(
                    completeResp.errorMessage ?: "Cryptographic verification succeeded, but enrollment activation failed"
                )
            }

            // Save successful enrollment into local Room DB
            val verifyData = completeResp.data.getJSONObject("data")
            val enrObj = verifyData.optJSONObject("enrollment")

            val deviceId = enrObj?.optString("deviceId", "dev-hw-local") ?: "dev-hw-local"
            val customerId = enrObj?.optString("customerId", "cust-local") ?: "cust-local"
            val agreementId = enrObj?.optString("agreementId", "agr-local") ?: "agr-local"

            val enrollmentEntity = DeviceEnrollmentEntity(
                enrollmentId = enrollmentId,
                deviceId = deviceId,
                customerId = customerId,
                agreementId = agreementId,
                enrollmentStatus = "ACTIVE",
                managementMode = "UNMANAGED",
                serverUrl = ApiConfig.DEFAULT_BASE_URL,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            database.deviceEnrollmentDao().saveEnrollment(enrollmentEntity)

            // Also update local device entity if present
            val localDev = database.deviceDao().getDeviceById(deviceId)
            if (localDev != null) {
                database.deviceDao().updateDevice(localDev.copy(enrollmentStatus = "ACTIVE", isOnline = true))
            } else {
                database.deviceDao().insertDevice(
                    DeviceEntity(
                        id = deviceId,
                        customerId = customerId,
                        model = Build.MODEL,
                        manufacturer = Build.MANUFACTURER,
                        androidVersion = "Android ${Build.VERSION.RELEASE}",
                        enrollmentStatus = "ACTIVE",
                        managementMode = "UNMANAGED",
                        enrollmentPublicKey = publicKeyPem,
                        lastSeenTimestamp = System.currentTimeMillis(),
                        batteryPercent = 100,
                        isOnline = true,
                        simCarrier = null,
                        usbDebuggingActive = false
                    )
                )
            }

            return EnrollmentResult.Success(enrollmentId, "Device enrollment cryptographically verified and activated.")
        } catch (e: Exception) {
            Log.e(TAG, "Enrollment verification error: ${e.message}", e)
            return EnrollmentResult.Failure(e.message ?: "Enrollment error")
        }
    }

    /**
     * Checks server-side enrollment and compliance status for an active device.
     */
    suspend fun checkDeviceComplianceStatus(enrollmentId: String): Result<JSONObject> {
        val path = "${ApiConfig.ENDPOINT_ENROLLMENT_DISCLOSURE}/$enrollmentId"
        val response = apiClient.get(path)
        return if (response.isSuccess && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.errorMessage ?: "Failed to fetch device compliance status"))
        }
    }

    /**
     * Fetches current server-evaluated device online/offline and lock state.
     */
    suspend fun fetchDeviceStatus(deviceId: String): Result<JSONObject> {
        val path = "${ApiConfig.ENDPOINT_DEVICE_STATUS}/$deviceId/status"
        val response = apiClient.get(path)
        return if (response.isSuccess && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.errorMessage ?: "Failed to fetch device online status"))
        }
    }

    companion object {
        private const val TAG = "DeviceEnrollmentService"
    }
}
