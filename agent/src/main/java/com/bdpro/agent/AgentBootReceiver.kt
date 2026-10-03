package com.bdpro.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AgentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = AgentPrefs(context)
        if (prefs.deviceId.isBlank() || prefs.controlKey.isBlank()) return

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AgentService::class.java)
            )
        } catch (_: Exception) {
            // Android may restrict foreground-service starts from some boot/update states.
        }
    }
}
