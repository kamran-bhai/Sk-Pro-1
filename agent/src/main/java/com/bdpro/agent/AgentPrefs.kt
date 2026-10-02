package com.bdpro.agent
import android.content.Context
class AgentPrefs(context: Context) { private val p=context.getSharedPreferences("bd_pro_agent",Context.MODE_PRIVATE)
 var deviceId:String get()=p.getString("device_id","").orEmpty() set(v)=p.edit().putString("device_id",v).apply()
 var controlKey:String get()=p.getString("control_key","").orEmpty() set(v)=p.edit().putString("control_key",v).apply()
 var backendUrl:String get()=p.getString("backend_url","https://bd-pro-backend.onrender.com").orEmpty() set(v)=p.edit().putString("backend_url",v.trimEnd('/')).apply() }