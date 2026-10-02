package com.bdpro.agent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
class MainActivity:ComponentActivity(){
 private lateinit var prefs:AgentPrefs
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);prefs=AgentPrefs(this)
  val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32)}
  fun f(v:String,h:String)=EditText(this).apply{setText(v);hint=h}
  val backend=f(prefs.backendUrl,"Backend URL");val device=f(prefs.deviceId,"Device ID");val key=f(prefs.controlKey,"Control Key")
  l.addView(TextView(this).apply{text="BD Pro Device Agent";textSize=24f});l.addView(backend);l.addView(device);l.addView(key)
  l.addView(Button(this).apply{text="ENABLE DEVICE ADMIN";setOnClickListener{startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,ComponentName(this@MainActivity,DeviceAdminReceiver::class.java)).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"BD Pro requires device administration to execute remote lock commands."))}})
  l.addView(Button(this).apply{text="SAVE & START AGENT";setOnClickListener{prefs.backendUrl=backend.text.toString();prefs.deviceId=device.text.toString();prefs.controlKey=key.text.toString();startForegroundService(Intent(this@MainActivity,AgentService::class.java))}})
  setContentView(l)
 }
}