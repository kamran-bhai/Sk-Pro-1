package com.bdpro.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(val token: String, val email: String)

object ApiClient {
    fun login(email: String, password: String): Result<LoginResult> = runCatching {
        val connection = (URL(ApiConfig.BASE_URL + ApiConfig.LOGIN_PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
        if (connection.responseCode !in 200..299) {
            val message = json.optString("message").ifBlank { "Login failed (HTTP " + connection.responseCode + ")" }
            error(message)
        }
        val token = json.optString("token").ifBlank { json.optString("accessToken") }
        if (token.isBlank()) error("Backend returned no access token")
        LoginResult(token, json.optString("email", email.trim()))
    }
}
