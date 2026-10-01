package com.protectfinanceddevices.app.core.dpc

import android.content.Context

/**
 * Executes only Android device-management operations that the current device
 * is actually authorized to perform.
 */
class RemoteCommandExecutor(context: Context) {

    private val lockManager = DeviceLockManager(context)

    data class ExecutionResult(
        val success: Boolean,
        val reason: String
    )

    fun execute(commandType: String): ExecutionResult {
        return when (commandType) {
            "LOCK_DEVICE" -> executeLock()
            "UNLOCK_DEVICE" -> ExecutionResult(
                success = false,
                reason = "REMOTE_UNLOCK_UNSUPPORTED: Android requires user authentication to unlock the system lock screen."
            )
            else -> ExecutionResult(
                success = false,
                reason = "UNSUPPORTED_COMMAND: $commandType is not handled by the remote lock executor."
            )
        }
    }

    private fun executeLock(): ExecutionResult {
        if (!lockManager.isDeviceOwner() && !lockManager.isDeviceAdminActive()) {
            return ExecutionResult(
                success = false,
                reason = "DEVICE_MANAGEMENT_REQUIRED: Remote system lock requires active Device Owner or Device Admin authority."
            )
        }

        return try {
            lockManager.lockDeviceNow()
            ExecutionResult(
                success = true,
                reason = "DEVICE_LOCKED: DevicePolicyManager.lockNow() executed successfully."
            )
        } catch (e: SecurityException) {
            ExecutionResult(
                success = false,
                reason = "LOCK_NOT_AUTHORIZED: ${e.message ?: "Device policy authorization was rejected."}"
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                reason = "LOCK_FAILED: ${e.message ?: "Android rejected the lock request."}"
            )
        }
    }
}
