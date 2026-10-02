package com.bdpro.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(val token: String, val email: String)
data class DeviceDto(val id: String, val deviceId: String, val imei: String, val model: String, val customerName: String, val customerPhone: String, val status: String)
data class DeviceEnrollment(val device: DeviceDto, val controlKey: String)
data class CommandDto(val id: String, val deviceId: String, val command: String, val status: String, val createdAt: String, val result: String? = null)

object ApiClient {
    private fun request(method: String, path: String, token: String?, body: String? = null): String {
        val c = (URL(ApiConfig.BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 30000; readTimeout = 30000; doOutput = body != null
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        body?.let { value -> c.outputStream.use { it.write(value.toByteArray(Charsets.UTF_8)) } }
        val code = c.responseCode
        val response = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
            error(json.optString("message").ifBlank { "Request failed (HTTP $code)" })
        }
        return response
    }

    fun login(email: String, password: String): Result<LoginResult> = runCatching {
        val j = JSONObject(request("POST", ApiConfig.LOGIN_PATH, null, JSONObject().put("email", email.trim()).put("password", password).toString()))
        LoginResult(j.getString("token"), j.optString("email", email.trim()))
    }

    fun listDevices(token: String): Result<List<DeviceDto>> = runCatching {
        val a = JSONObject(request("GET", "/api/v1/devices", token)).getJSONArray("devices")
        (0 until a.length()).map {
            val j = a.getJSONObject(it)
            DeviceDto(j.getString("id"), j.getString("deviceId"), j.getString("imei"), j.optString("model"), j.optString("customerName"), j.optString("customerPhone"), j.optString("status"))
        }
    }

    fun addDevice(token: String, deviceId: String, imei: String, model: String, customerName: String, customerPhone: String): Result<DeviceEnrollment> = runCatching {
        val body = JSONObject().put("deviceId", deviceId).put("imei", imei).put("model", model).put("customerName", customerName).put("customerPhone", customerPhone).toString()
        val root = JSONObject(request("POST", "/api/v1/devices", token, body))
        val j = root.getJSONObject("device")
        val device = DeviceDto(j.getString("id"), j.getString("deviceId"), j.getString("imei"), j.optString("model"), j.optString("customerName"), j.optString("customerPhone"), j.optString("status"))
        DeviceEnrollment(device, root.getJSONObject("enrollment").getString("controlKey"))
    }

    fun sendCommand(token: String, deviceId: String, command: String): Result<CommandDto> = runCatching {
        val j = JSONObject(request("POST", "/api/v1/devices/$deviceId/commands", token, JSONObject().put("command", command).toString())).getJSONObject("command")
        parseCommand(j)
    }

    fun commandStatus(token: String, commandId: String): Result<CommandDto> = runCatching {
        parseCommand(JSONObject(request("GET", "/api/v1/commands/$commandId", token)).getJSONObject("command"))
    }

    private fun parseCommand(j: JSONObject) = CommandDto(j.getString("id"), j.getString("deviceId"), j.getString("command"), j.getString("status"), j.getString("createdAt"), if (j.isNull("result")) null else j.optString("result"))
}