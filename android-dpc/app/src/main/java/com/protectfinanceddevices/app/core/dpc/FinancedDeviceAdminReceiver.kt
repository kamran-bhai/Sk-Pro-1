package com.protectfinanceddevices.app.core.dpc

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Handles Device Administration and Device Owner lifecycle events.
 */
class FinancedDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for financed device management.")
        Toast.makeText(context, "Financed Device Policy Manager Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "WARNING: Device Admin was disabled by user/system!")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i(TAG, "LockTaskMode (Kiosk) initiated for package: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i(TAG, "LockTaskMode (Kiosk) exited.")
    }

    companion object {
        private const val TAG = "FinancedAdminReceiver"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, FinancedDeviceAdminReceiver::class.java)
        }
    }
}
