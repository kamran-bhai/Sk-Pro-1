package com.bdpro.admin

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("bd_pro_session", Context.MODE_PRIVATE)
    fun saveToken(token: String) { prefs.edit().putString("access_token", token).apply() }
    fun token(): String? = prefs.getString("access_token", null)
    fun clear() { prefs.edit().clear().apply() }
    fun isLoggedIn(): Boolean = !token().isNullOrBlank()
}
