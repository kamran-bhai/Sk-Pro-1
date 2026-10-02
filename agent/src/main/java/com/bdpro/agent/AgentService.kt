package com.bdpro.agent

import android.Manifest
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AgentService : Service() {
    private var running = false
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        if (!running) { running = true; Thread { loop() }.start() }
        return START_STICKY
    }
    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bdpro_agent", "BD Pro Agent", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, "bdpro_agent").setContentTitle("BD Pro Agent").setContentText("Device management agent is running").setSmallIcon(android.R.drawable.ic_lock_lock).build()
        startForeground(1001, n)
    }
    private fun loop() {
        val p = AgentPrefs(this)
        while (running && p.deviceId.isNotBlank() && p.controlKey.isNotBlank()) {
            try { poll(p) } catch (_: Exception) {}
            Thread.sleep(10000)
        }
        stopSelf()
    }
    private fun poll(p: AgentPrefs) {
        val c = (URL(p.backendUrl + "/api/v1/agent/devices/" + p.deviceId + "/commands").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000; setRequestProperty("X-Device-Key", p.controlKey)
        }
        val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (c.responseCode !in 200..299) return
        val a = JSONObject(body).optJSONArray("commands") ?: return
        for (i in 0 until a.length()) execute(p, a.getJSONObject(i))
    }
    private fun execute(p: AgentPrefs, cmd: JSONObject) {
        val name = cmd.optString("command")
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        var status = "SUCCESS"; var result: String? = null
        try {
            when (name) {
                "LOCK" -> if (!dpm.isAdminActive(component())) { status = "FAILED"; result = "Device Admin is not enabled" } else { dpm.lockNow(); result = "Device locked" }
                "UNLOCK" -> result = "Unlock requires local user action"
                "DIAGNOSTICS" -> result = collectDiagnostics(dpm)
                "LOCATION" -> result = collectLocation()
                "AUTOLOCK_ON", "AUTOLOCK_OFF", "ANTI_THEFT_ON", "ANTI_THEFT_OFF" -> result = name + " policy acknowledged"
                else -> { status = "FAILED"; result = "Unsupported command" }
            }
        } catch (e: Exception) { status = "FAILED"; result = e.message ?: "Execution failed" }
        ack(p, cmd.optString("id"), status, result)
    }
    private fun collectDiagnostics(dpm: DevicePolicyManager): String {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return JSONObject().put("type", "diagnostics").put("deviceAdmin", dpm.isAdminActive(component()))
            .put("batteryPercent", battery).put("charging", charging)
            .put("uptimeSeconds", SystemClock.elapsedRealtime() / 1000L).toString()
    }
    private fun collectLocation(): String {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return JSONObject().put("type", "location_error").put("message", "Location permission not granted").toString()
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: android.location.Location? = null
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) try {
            val location = lm.getLastKnownLocation(provider) ?: continue
            if (best == null || location.time > best!!.time) best = location
        } catch (_: SecurityException) {}
        return if (best != null) JSONObject().put("type", "location").put("latitude", best.latitude).put("longitude", best.longitude).put("accuracyMeters", best.accuracy).put("timestamp", best.time).toString()
        else JSONObject().put("type", "location_error").put("message", "Location unavailable").toString()
    }
    private fun ack(p: AgentPrefs, id: String, status: String, result: String?) {
        val body = JSONObject().put("status", status).put("result", result).toString()
        val c = (URL(p.backendUrl + "/api/v1/agent/commands/" + id + "/ack").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true; setRequestProperty("Content-Type", "application/json"); setRequestProperty("X-Device-Key", p.controlKey)
        }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }; c.inputStream.close()
    }
    private fun component() = android.content.ComponentName(this, DeviceAdminReceiver::class.java)
    override fun onBind(intent: Intent?): IBinder? = null
}