package com.protectfinanceddevices.app.core.dpc

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * Manages device policy enforcement, lock states, and restrictions.
 * Differentiates strictly between:
 * 1. Device Owner (full enterprise lockTask and system policy authority)
 * 2. Device Admin (lockNow and password compliance only)
 * 3. Normal App (informational only)
 */
class DeviceLockManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent: ComponentName = FinancedDeviceAdminReceiver.getComponentName(context)

    fun isDeviceAdminActive(): Boolean {
        return dpm.isAdminActive(adminComponent)
    }

    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Puts device in restricted lock mode.
     * If Device Owner: whitelists our package for LockTaskMode and locks immediately.
     * If Device Admin: executes standard lockNow().
     */
    fun enforceLockState(activity: Activity? = null) {
        if (isDeviceOwner()) {
            Log.i(TAG, "Enforcing enterprise lock as Device Owner")
            // Whitelist this app for lock task (kiosk) mode
            dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                )
            }
            
            activity?.startLockTask()
        } else if (isDeviceAdminActive()) {
            Log.i(TAG, "Enforcing standard lockNow as active Device Admin")
            dpm.lockNow()
        } else {
            Log.w(TAG, "Cannot force system lock: App does not have Device Admin or Device Owner rights")
        }
    }

    /**
     * Releases device from restricted lock mode.
     */
    fun releaseLockState(activity: Activity? = null) {
        if (isDeviceOwner()) {
            Log.i(TAG, "Releasing lock task mode as Device Owner")
            activity?.stopLockTask()
            dpm.setLockTaskPackages(adminComponent, emptyArray())
        }
    }

    /**
     * Applies enterprise device protection restrictions (Factory reset protection, safe boot disable, debug disable).
     * Only works when app is Device Owner.
     */
    fun applyProtectionRestrictions(enable: Boolean) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "applyProtectionRestrictions ignored: App is not Device Owner")
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

        // Prevent uninstallation while financed
        dpm.setUninstallBlocked(adminComponent, context.packageName, enable)
        Log.i(TAG, "Protection restrictions applied: enabled=$enable")
    }

    companion object {
        private const val TAG = "DeviceLockManager"
    }
}
