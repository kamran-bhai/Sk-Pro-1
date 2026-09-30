package com.protectfinanceddevices.app.core.dpc

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * Manages device policy enforcement, lock states, and financing protection.
 *
 * Capability levels:
 * 1. Device Owner / fully managed: enterprise device policies and lock-task.
 * 2. Device Admin: legacy lockNow() only; no factory-reset prevention.
 * 3. Normal app: informational only.
 *
 * Factory-reset protection is reported as unsupported when the Android
 * version/device does not expose the supported enterprise API. This class
 * never pretends that a normal APK can block recovery/firmware wipes.
 */
class DeviceLockManager(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        FinancedDeviceAdminReceiver.getComponentName(context)

    fun isDeviceAdminActive(): Boolean = dpm.isAdminActive(adminComponent)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun enforceLockState(activity: Activity? = null) {
        if (isDeviceOwner()) {
            Log.i(TAG, "Enforcing enterprise lock as Device Owner")
            dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                )
            }

            activity?.startLockTask()
        } else if (isDeviceAdminActive()) {
            Log.i(TAG, "Enforcing standard lockNow as active Device Admin")
            dpm.lockNow()
        } else {
            Log.w(TAG, "Cannot force system lock: no Device Admin or Device Owner rights")
        }
    }

    fun releaseLockState(activity: Activity? = null) {
        if (isDeviceOwner()) {
            Log.i(TAG, "Releasing lock task mode as Device Owner")
            activity?.stopLockTask()
            dpm.setLockTaskPackages(adminComponent, emptyArray())
        }
    }

    /**
     * Applies enterprise protection while a financed agreement is active.
     *
     * DISALLOW_FACTORY_RESET covers the supported Android settings reset path.
     * FactoryResetProtectionPolicy (API 30+) adds the Android enterprise FRP
     * layer on devices that support it.
     *
     * Neither mechanism guarantees blocking a recovery/firmware wipe on every
     * Android device. Persistent post-reset enforcement requires supported
     * enterprise provisioning such as zero-touch/OEM infrastructure.
     */
    fun applyProtectionRestrictions(enable: Boolean) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "applyProtectionRestrictions ignored: not Device Owner")
            return
        }

        val restrictions = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_ADD_USER
        )

        for (restriction in restrictions) {
            if (enable) {
                dpm.addUserRestriction(adminComponent, restriction)
            } else {
                dpm.clearUserRestriction(adminComponent, restriction)
            }
        }

        dpm.setUninstallBlocked(adminComponent, context.packageName, enable)

        val frpResult = setFactoryResetProtectionEnabled(enable)

        Log.i(
            TAG,
            "Protection restrictions applied: enabled=$enable, frpResult=$frpResult"
        )
    }

    fun isRestrictionEnabled(restriction: String): Boolean {
        if (!isDeviceOwner()) return false
        return dpm.getUserRestrictions(adminComponent).getBoolean(restriction, false)
    }

    fun setFactoryResetBlocked(enabled: Boolean) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setFactoryResetBlocked ignored: not Device Owner")
            return
        }

        if (enabled) {
            dpm.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_FACTORY_RESET
            )
        } else {
            dpm.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_FACTORY_RESET
            )
        }

        setFactoryResetProtectionEnabled(enabled)
    }

    /**
     * Applies the Android enterprise FRP policy on API 30+.
     * Returns false when the device/management mode does not support it.
     */
    fun setFactoryResetProtectionEnabled(enabled: Boolean): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "FRP policy ignored: not Device Owner")
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(TAG, "FRP policy API unavailable before Android 11")
            return false
        }

        return try {
            val policy =
                DevicePolicyManager.FactoryResetProtectionPolicy
                    .Builder()
                    .setFactoryResetProtectionEnabled(enabled)
                    .build()

            dpm.setFactoryResetProtectionPolicy(adminComponent, policy)

            Log.i(TAG, "Factory Reset Protection policy applied: enabled=$enabled")
            true
        } catch (e: UnsupportedOperationException) {
            Log.w(
                TAG,
                "This device does not support enterprise FRP policy: ${e.message}"
            )
            false
        } catch (e: SecurityException) {
            Log.w(
                TAG,
                "Enterprise FRP policy not permitted for this management mode: ${e.message}"
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply enterprise FRP policy", e)
            false
        }
    }

    /**
     * Capability detection only. This does not claim that recovery firmware
     * can be made impossible to wipe.
     */
    fun isFactoryResetProtectionSupported(): Boolean {
        if (!isDeviceOwner()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        return try {
            dpm.getFactoryResetProtectionPolicy(adminComponent)
            true
        } catch (e: UnsupportedOperationException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Synchronizes supported enterprise protection with the financing state.
     * ACTIVE/OVERDUE financing keeps protection enabled; COMPLETED/CANCELLED
     * releases the supported policies. Non-Device-Owner devices are reported
     * as unsupported rather than pretending that protection was applied.
     */
    fun syncFinancingProtection(agreementStatus: String): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Financing protection sync ignored: not Device Owner")
            return false
        }

        val normalizedStatus = agreementStatus.uppercase()
        val shouldProtect = normalizedStatus == "ACTIVE" || normalizedStatus == "OVERDUE"
        val shouldRelease = normalizedStatus == "COMPLETED" || normalizedStatus == "PAID" || normalizedStatus == "CANCELLED"

        if (!shouldProtect && !shouldRelease) {
            Log.w(TAG, "Unknown agreement status; protection state unchanged: $agreementStatus")
            return false
        }

        applyProtectionRestrictions(shouldProtect)
        return true
    }

    fun setDebuggingBlocked(enabled: Boolean) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setDebuggingBlocked ignored: not Device Owner")
            return
        }

        if (enabled) {
            dpm.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_DEBUGGING_FEATURES
            )
        } else {
            dpm.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_DEBUGGING_FEATURES
            )
        }
    }

    fun isUninstallBlocked(): Boolean {
        return isDeviceOwner() &&
            dpm.isUninstallBlocked(adminComponent, context.packageName)
    }

    fun setUninstallBlocked(enabled: Boolean) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "setUninstallBlocked ignored: not Device Owner")
            return
        }

        dpm.setUninstallBlocked(adminComponent, context.packageName, enabled)
    }

    companion object {
        private const val TAG = "DeviceLockManager"
    }
}
