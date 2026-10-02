package com.bdpro.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(val token: String, val email: String)
data class DeviceDto(
    val id: String, val deviceId: String, val imei: String, val model: String,
    val customerName: String, val customerPhone: String, val status: String
)

object ApiClient {
    private fun request(method:String,path:String,token:String?,body:String?=null):String{
        val c=(URL(ApiConfig.BASE_URL+path).openConnection() as HttpURLConnection).apply{
            requestMethod=method;connectTimeout=30000;readTimeout=30000;doOutput=body!=null
            setRequestProperty("Accept","application/json")
            if(body!=null)setRequestProperty("Content-Type","application/json")
            if(!token.isNullOrBlank())setRequestProperty("Authorization","Bearer $token")
        }
        body?.let{c.outputStream.use{out->out.write(it.toByteArray(Charsets.UTF_8))}}
        val r=c.responseCode
        val s=(if(r in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        if(r !in 200..299){val j=runCatching{JSONObject(s)}.getOrElse{JSONObject()};error(j.optString("message").ifBlank{"Request failed (HTTP $r)"})}
        return s
    }
    fun login(email:String,password:String):Result<LoginResult>=runCatching{
        val j=JSONObject(request("POST",ApiConfig.LOGIN_PATH,null,JSONObject().put("email",email.trim()).put("password",password).toString()))
        LoginResult(j.getString("token"),j.optString("email",email.trim()))
    }
    fun listDevices(token:String):Result<List<DeviceDto>> = runCatching {
        val a=JSONObject(request("GET","/api/v1/devices",token)).getJSONArray("devices")
        (0 until a.length()).map{val j=a.getJSONObject(it);DeviceDto(j.getString("id"),j.getString("deviceId"),j.getString("imei"),j.optString("model"),j.optString("customerName"),j.optString("customerPhone"),j.optString("status"))}
    }
    fun addDevice(token:String,deviceId:String,imei:String,model:String,customerName:String,customerPhone:String):Result<DeviceDto>=runCatching{
        val j=JSONObject(request("POST","/api/v1/devices",token,JSONObject().put("deviceId",deviceId).put("imei",imei).put("model",model).put("customerName",customerName).put("customerPhone",customerPhone).toString())).getJSONObject("device")
        DeviceDto(j.getString("id"),j.getString("deviceId"),j.getString("imei"),j.optString("model"),j.optString("customerName"),j.optString("customerPhone"),j.optString("status"))
    }
}