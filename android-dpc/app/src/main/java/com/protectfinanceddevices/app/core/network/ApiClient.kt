package com.protectfinanceddevices.app.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ApiResponse<T>(
    val isSuccess: Boolean,
    val statusCode: Int,
    val data: T? = null,
    val errorMessage: String? = null
)

/**
 * Robust TLS HTTP Network Client for communicating with the Protect Financed Devices backend.
 * Uses standard HttpURLConnection with strict timeouts, proper header handling, and JSON parsing.
 */
class ApiClient(private val baseUrl: String = ApiConfig.DEFAULT_BASE_URL) {

    private val sanitizedBaseUrl: String = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

    suspend fun get(path: String, queryParams: Map<String, String> = emptyMap(), accessToken: String? = null): ApiResponse<JSONObject> =
        withContext(Dispatchers.IO) {
            var urlString = "$sanitizedBaseUrl$path"
            if (queryParams.isNotEmpty()) {
                val queryString = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
                urlString += "?$queryString"
            }

            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }

                val json = if (body.isNotEmpty()) JSONObject(body) else JSONObject()
                if (responseCode in 200..299) {
                    ApiResponse(isSuccess = true, statusCode = responseCode, data = json)
                } else {
                    val msg = json.optString("message", json.optString("error", "HTTP error $responseCode"))
                    ApiResponse(isSuccess = false, statusCode = responseCode, errorMessage = msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "GET request failed to $urlString: ${e.message}")
                ApiResponse(isSuccess = false, statusCode = -1, errorMessage = e.message ?: "Network error")
            } finally {
                conn?.disconnect()
            }
        }

    suspend fun post(path: String, payload: JSONObject, accessToken: String? = null): ApiResponse<JSONObject> =
        withContext(Dispatchers.IO) {
            val urlString = "$sanitizedBaseUrl$path"
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                    connectTimeout = 30000
                    readTimeout = 30000
                    doOutput = true
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }

                val json = if (body.isNotEmpty()) JSONObject(body) else JSONObject()
                if (responseCode in 200..299) {
                    ApiResponse(isSuccess = true, statusCode = responseCode, data = json)
                } else {
                    val msg = json.optString("message", json.optString("error", "HTTP error $responseCode"))
                    ApiResponse(isSuccess = false, statusCode = responseCode, errorMessage = msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "POST request failed to $urlString: ${e.message}")
                ApiResponse(isSuccess = false, statusCode = -1, errorMessage = e.message ?: "Network error")
            } finally {
                conn?.disconnect()
            }
        }

    companion object {
        private const val TAG = "ApiClient"
    }
}
