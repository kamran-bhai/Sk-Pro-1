package com.bdpro.agent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
class AgentService : Service() {
 private var running=false
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { if(!running){running=true;Thread{loop()}.start()};return START_STICKY }
 private fun loop(){val p=AgentPrefs(this);while(running&&p.deviceId.isNotBlank()&&p.controlKey.isNotBlank()){try{poll(p)}catch(_:Exception){};Thread.sleep(10000)};stopSelf()}
 private fun poll(p:AgentPrefs){val c=(URL(p.backendUrl+"/api/v1/agent/devices/"+p.deviceId+"/commands").openConnection() as HttpURLConnection).apply{requestMethod="GET";connectTimeout=15000;readTimeout=15000;setRequestProperty("X-Device-Key",p.controlKey)};val body=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty();if(c.responseCode !in 200..299)return;val a=JSONObject(body).optJSONArray("commands")?:return;for(i in 0 until a.length())execute(p,a.getJSONObject(i))}
 private fun execute(p:AgentPrefs,cmd:JSONObject){val name=cmd.optString("command");val dpm=getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager;var status="SUCCESS";var result:String?=null;try{when(name){"LOCK"->{dpm.lockNow();result="Device locked"};"UNLOCK"->{result="Unlock requires local user action"};"DIAGNOSTICS"->{result="Agent online; device-admin="+dpm.isAdminActive(component())};"LOCATION"->{result="Location collection not enabled in this agent build"};"AUTOLOCK_ON","AUTOLOCK_OFF","ANTI_THEFT_ON","ANTI_THEFT_OFF"->{result=name+" policy acknowledged"};else->{status="FAILED";result="Unsupported command"}}}catch(e:Exception){status="FAILED";result=e.message?:"Execution failed"};ack(p,cmd.optString("id"),status,result)}
 private fun ack(p:AgentPrefs,id:String,status:String,result:String?){val body=JSONObject().put("status",status).put("result",result).toString();val c=(URL(p.backendUrl+"/api/v1/agent/commands/"+id+"/ack").openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=15000;readTimeout=15000;doOutput=true;setRequestProperty("Content-Type","application/json");setRequestProperty("X-Device-Key",p.controlKey)};c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))};c.inputStream.close()}
 private fun component()=android.content.ComponentName(this,DeviceAdminReceiver::class.java)
 override fun onBind(intent:Intent?):IBinder?=null
}