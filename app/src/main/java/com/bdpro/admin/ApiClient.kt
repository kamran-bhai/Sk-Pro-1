package com.bdpro.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(val token: String, val email: String)
data class DeviceDto(val id: String, val deviceId: String, val imei: String, val model: String, val customerName: String, val customerPhone: String, val status: String)

object ApiClient {
    private fun request(method: String, path: String, token: String?, body: String? = null): String {
        val connection = (URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 30000; readTimeout = 30000; doOutput = body != null
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        body?.let { value -> connection.outputStream.use { it.write(value.toByteArray(Charsets.UTF_8)) } }
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
            error(json.optString("message").ifBlank { "Request failed (HTTP $code)" })
        }
        return response
    }

    fun login(email: String, password: String): Result<LoginResult> = runCatching {
        val json = JSONObject(request("POST", ApiConfig.LOGIN_PATH, null, JSONObject().put("email", email.trim()).put("password", password).toString()))
        LoginResult(json.getString("token"), json.optString("email", email.trim()))
    }

    fun listDevices(token: String): Result<List<DeviceDto>> = runCatching {
        val array = JSONObject(request("GET", "/api/v1/devices", token)).getJSONArray("devices")
        (0 until array.length()).map {
            val json = array.getJSONObject(it)
            DeviceDto(json.getString("id"), json.getString("deviceId"), json.getString("imei"), json.optString("model"), json.optString("customerName"), json.optString("customerPhone"), json.optString("status"))
        }
    }

    fun addDevice(token: String, deviceId: String, imei: String, model: String, customerName: String, customerPhone: String): Result<DeviceDto> = runCatching {
        val body = JSONObject().put("deviceId", deviceId).put("imei", imei).put("model", model).put("customerName", customerName).put("customerPhone", customerPhone).toString()
        val json = JSONObject(request("POST", "/api/v1/devices", token, body)).getJSONObject("device")
        DeviceDto(json.getString("id"), json.getString("deviceId"), json.getString("imei"), json.optString("model"), json.optString("customerName"), json.optString("customerPhone"), json.optString("status"))
    }
}