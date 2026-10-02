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
            text = "SAVE & START AGENT"
            setOnClickListener {
                prefs.backendUrl = backend.text.toString()
                prefs.deviceId = device.text.toString()
                prefs.controlKey = key.text.toString()

                when {
                    !hasLocationPermission() -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Allow location first",
                            Toast.LENGTH_LONG
                        ).show()
                        requestLocationPermission()
                    }
                    !hasPhoneStatePermission() -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Allow Phone State for Anti-Theft",
                            Toast.LENGTH_LONG
                        ).show()
                        requestPhoneStatePermission()
                    }
                    else -> {
                        ContextCompat.startForegroundService(
                            this@MainActivity,
                            Intent(this@MainActivity, AgentService::class.java)
                        )
                        Toast.makeText(
                            this@MainActivity,
                            "BD Pro Agent started",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        setContentView(l)
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