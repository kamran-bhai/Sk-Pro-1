package com.bdpro.agent

import android.Manifest
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.telephony.SubscriptionManager
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
    private var worker: Thread? = null
    private var consecutiveFailures = 0
    private val defaultAutoLockTimeoutMs = 5 * 60 * 1000L
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        if (!running) {
            running = true
            worker = Thread { loop() }.also { it.start() }
        }
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
            try {
                enforcePolicies(p)
                poll(p)
                consecutiveFailures = 0
                updateNotification("Connected • checking every 10s")
            } catch (_: Exception) {
                consecutiveFailures++
                updateNotification("Connection retry • attempt $consecutiveFailures")
            }
            try { Thread.sleep(if (consecutiveFailures == 0) 10000L else minOf(30000L, 5000L * consecutiveFailures)) } catch (_: InterruptedException) { break }
        }
        stopSelf()
    }
    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(this, "bdpro_agent")
            .setContentTitle("BD Pro Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        nm.notify(1001, n)
    }

    private fun poll(p: AgentPrefs) {
        val c = (URL(p.backendUrl + "/api/v1/agent/devices/" + p.deviceId + "/commands").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000; setRequestProperty("X-Device-Key", p.controlKey); setRequestProperty("X-Agent-Version", BuildConfig.VERSION_NAME); setRequestProperty("X-Agent-Status", "RUNNING")
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("Agent poll failed (HTTP $code)")
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
                "AUTOLOCK_ON" -> {
                    if (!dpm.isAdminActive(component())) {
                        status = "FAILED"; result = "Device Admin is not enabled"
                    } else {
                        val requested = cmd.optJSONObject("payload")?.optInt("timeoutMinutes", p.autoLockTimeoutMinutes)
                            ?: p.autoLockTimeoutMinutes
                        val minutes = requested.coerceIn(1, 1440)
                        p.autoLockTimeoutMinutes = minutes
                        p.autoLockEnabled = true
                        dpm.setMaximumTimeToLock(component(), minutes * 60_000L)
                        result = "Auto Lock enabled: $minutes minute(s)"
                    }
                }
                "AUTOLOCK_OFF" -> {
                    if (!dpm.isAdminActive(component())) {
                        status = "FAILED"; result = "Device Admin is not enabled"
                    } else {
                        p.autoLockEnabled = false
                        dpm.setMaximumTimeToLock(component(), 0L)
                        result = "Auto Lock disabled"
                    }
                }
                "ANTI_THEFT_ON" -> {
                    if (!hasPhoneStatePermission()) {
                        status = "FAILED"
                        result = "READ_PHONE_STATE permission is required"
                    } else {
                        val fingerprint = readSimFingerprint()
                        if (fingerprint.isBlank()) {
                            status = "FAILED"
                            result = "No active SIM subscription detected"
                        } else {
                            p.simFingerprint = fingerprint
                            p.antiTheftEnabled = true
                            result = "ANTI_THEFT_ON policy enabled and SIM baseline saved"
                        }
                    }
                }
                "ANTI_THEFT_OFF" -> {
                    p.antiTheftEnabled = false
                    p.simFingerprint = ""
                    result = "ANTI_THEFT_OFF policy disabled"
                }
                else -> { status = "FAILED"; result = "Unsupported command" }
            }
        } catch (e: Exception) { status = "FAILED"; result = e.message ?: "Execution failed" }
        ack(p, cmd.optString("id"), status, result)
    }
    private fun enforcePolicies(p: AgentPrefs) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isAdminActive(component())) return
        if (p.autoLockEnabled) {
            try { dpm.setMaximumTimeToLock(component(), p.autoLockTimeoutMinutes * 60_000L) } catch (_: Exception) {}
        }
        if (p.antiTheftEnabled && hasPhoneStatePermission()) {
            val current = readSimFingerprint()
            val saved = p.simFingerprint
            if (saved.isNotBlank() && current.isNotBlank() && current != saved) {
                try {
                    dpm.lockNow()
                    p.antiTheftEnabled = false
                    p.simFingerprint = ""
                } catch (_: Exception) {}
            }
        }
    }
    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun readSimFingerprint(): String {
        if (!hasPhoneStatePermission()) return ""
        return try {
            val sm = getSystemService(SubscriptionManager::class.java)
            val subscriptions = sm.activeSubscriptionInfoList.orEmpty().sortedBy { it.subscriptionId }
            subscriptions.joinToString(";") { info ->
                listOf(info.subscriptionId.toString(), info.mccString.orEmpty(), info.mncString.orEmpty(), info.countryIso.orEmpty()).joinToString("|")
            }
        } catch (_: SecurityException) { "" } catch (_: Exception) { "" }
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
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            stream?.close()
        } finally {
            c.disconnect()
        }
    }
    private fun component() = android.content.ComponentName(this, DeviceAdminReceiver::class.java)
    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}