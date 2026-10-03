package com.bdpro.agent

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private lateinit var prefs: AgentPrefs
    private val locationRequestCode = 2001
    private val phoneStateRequestCode = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AgentPrefs(this)

        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        fun f(v: String, h: String) = EditText(this).apply {
            setText(v)
            hint = h
        }

        val backend = f(prefs.backendUrl, "Backend URL")
        val device = f(prefs.deviceId, "Device ID")
        val key = f(prefs.controlKey, "Control Key")

        l.addView(TextView(this).apply {
            text = "BD Pro Device Agent"
            textSize = 24f
        })
        l.addView(backend)
        l.addView(device)
        l.addView(key)

        l.addView(Button(this).apply {
            text = "ENABLE DEVICE ADMIN"
            setOnClickListener {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(this@MainActivity, DeviceAdminReceiver::class.java)
                        )
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "BD Pro requires device administration to execute remote lock commands."
                        )
                )
            }
        })

        l.addView(Button(this).apply {
            text = "ALLOW LOCATION"
            setOnClickListener { requestLocationPermission() }
        })

        l.addView(Button(this).apply {
            text = "ALLOW PHONE STATE (ANTI-THEFT)"
            setOnClickListener { requestPhoneStatePermission() }
        })

        l.addView(Button(this).apply {
            text = "TEST CONNECTION"
            setOnClickListener {
                val base = backend.text.toString().trimEnd('/')
                val did = device.text.toString().trim()
                val ck = key.text.toString().trim()
                if (!validateInputs(base, did, ck)) return@setOnClickListener

                Thread {
                    val result = enrollDevice(base, did, ck)
                    runOnUiThread {
                        val message = if (result.first in 200..299) {
                            "Connection OK • Device enrolled"
                        } else {
                            "Connection failed • " + result.second
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        })

        l.addView(Button(this).apply {
            text = "SAVE & START AGENT"
            setOnClickListener {
                val base = backend.text.toString().trimEnd('/')
                val did = device.text.toString().trim()
                val ck = key.text.toString().trim()

                if (!validateInputs(base, did, ck)) return@setOnClickListener

                Toast.makeText(
                    this@MainActivity,
                    "Verifying device enrollment…",
                    Toast.LENGTH_SHORT
                ).show()

                Thread {
                    val result = enrollDevice(base, did, ck)
                    runOnUiThread {
                        if (result.first !in 200..299) {
                            Toast.makeText(
                                this@MainActivity,
                                "Agent not started • " + result.second,
                                Toast.LENGTH_LONG
                            ).show()
                            return@runOnUiThread
                        }

                        prefs.backendUrl = base
                        prefs.deviceId = did
                        prefs.controlKey = ck

                        when {
                            !hasLocationPermission() -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Device verified. Allow location to continue.",
                                    Toast.LENGTH_LONG
                                ).show()
                                requestLocationPermission()
                            }
                            !hasPhoneStatePermission() -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Device verified. Allow Phone State for Anti-Theft.",
                                    Toast.LENGTH_LONG
                                ).show()
                                requestPhoneStatePermission()
                            }
                            else -> startAgent()
                        }
                    }
                }.start()
            }
        })

        setContentView(l)
    }

    private fun validateInputs(base: String, did: String, ck: String): Boolean {
        if (base.isBlank() || did.isBlank() || ck.isBlank()) {
            Toast.makeText(
                this,
                "Enter Backend URL, Device ID and Control Key",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    private fun enrollDevice(base: String, deviceId: String, controlKey: String): Pair<Int, String> {
        return try {
            val c = (URL(base + "/api/v1/agent/enroll").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Device-Key", controlKey)
                doOutput = true
            }

            c.outputStream.use {
                it.write(JSONObject().put("deviceId", deviceId).toString().toByteArray(Charsets.UTF_8))
            }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()

            val detail = try {
                JSONObject(body).optString("error").ifBlank {
                    JSONObject(body).optString("message")
                }
            } catch (_: Exception) {
                ""
            }

            code to when {
                code == 404 -> "Device ID is not registered"
                code == 401 -> "Invalid Device ID or Control Key"
                code in 200..299 -> "Device enrolled"
                detail.isNotBlank() -> detail
                else -> "HTTP $code"
            }
        } catch (e: Exception) {
            -1 to (e.message ?: "network error")
        }
    }

    private fun startAgent() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, AgentService::class.java)
        )
        Toast.makeText(
            this,
            "BD Pro Agent started",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationRequestCode
            )
        }
    }

    private fun requestPhoneStatePermission() {
        if (!hasPhoneStatePermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                phoneStateRequestCode
            )
        }
    }
}
