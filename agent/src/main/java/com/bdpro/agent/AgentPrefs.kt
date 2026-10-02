package com.bdpro.agent

import android.content.Context

class AgentPrefs(context: Context) {
    private val p = context.getSharedPreferences("bd_pro_agent", Context.MODE_PRIVATE)

    var deviceId: String
        get() = p.getString("device_id", "").orEmpty()
        set(value) { p.edit().putString("device_id", value).apply() }

    var controlKey: String
        get() = p.getString("control_key", "").orEmpty()
        set(value) { p.edit().putString("control_key", value).apply() }

    var backendUrl: String
        get() = p.getString("backend_url", "https://bd-pro-backend.onrender.com").orEmpty()
        set(value) { p.edit().putString("backend_url", value.trimEnd('/')).apply() }

    var autoLockEnabled: Boolean
        get() = p.getBoolean("auto_lock_enabled", false)
        set(value) { p.edit().putBoolean("auto_lock_enabled", value).apply() }

    var autoLockTimeoutMinutes: Int
        get() = p.getInt("auto_lock_timeout_minutes", 5).coerceIn(1, 1440)
        set(value) { p.edit().putInt("auto_lock_timeout_minutes", value.coerceIn(1, 1440)).apply() }

    var antiTheftEnabled: Boolean
        get() = p.getBoolean("anti_theft_enabled", false)
        set(value) { p.edit().putBoolean("anti_theft_enabled", value).apply() }

    var simFingerprint: String
        get() = p.getString("sim_fingerprint", "").orEmpty()
        set(value) { p.edit().putString("sim_fingerprint", value).apply() }
}
