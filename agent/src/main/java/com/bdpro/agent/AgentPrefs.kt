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
}
