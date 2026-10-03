package com.bdpro.admin

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LoginResult(val token: String, val email: String)
data class DeviceDto(val id: String, val deviceId: String, val imei: String, val model: String, val customerName: String, val customerPhone: String, val status: String, val lastSeenAt: String? = null)
data class DeviceEnrollment(val device: DeviceDto, val controlKey: String)
data class CustomerDto(val id:String,val name:String,val phone:String,val address:String,val createdAt:String)
data class AgreementDto(val id:String,val customerId:String,val deviceId:String,val totalAmount:Double,val downPayment:Double,val installmentAmount:Double,val numberOfInstallments:Int,val paidInstallments:Int,val remainingAmount:Double,val nextDueDate:String)
data class EnachDto(val id:String,val customerId:String,val agreementId:String,val mandateRef:String,val status:String)
data class CommandDto(
 val id:String,val deviceId:String,val command:String,val status:String,val createdAt:String,val result:String?=null,
 val latitude:Double?=null,val longitude:Double?=null,val accuracyMeters:Double?=null,val locationTimestamp:Long?=null,
 val batteryPercent:Int?=null,val charging:Boolean?=null,val deviceAdmin:Boolean?=null,val uptimeSeconds:Long?=null)

object ApiClient {
 private fun request(method:String,path:String,token:String?,body:String?=null):String {
  val c=(URL(ApiConfig.BASE_URL+path).openConnection() as HttpURLConnection).apply {
   requestMethod=method;connectTimeout=30000;readTimeout=30000;doOutput=body!=null
   setRequestProperty("Accept","application/json");if(body!=null)setRequestProperty("Content-Type","application/json")
   if(!token.isNullOrBlank())setRequestProperty("Authorization","Bearer $token")
  }
  body?.let{c.outputStream.use{out->out.write(it.toByteArray(Charsets.UTF_8))}}
  val code=c.responseCode
  val response=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
  c.disconnect()
  if(code !in 200..299){val j=runCatching{JSONObject(response)}.getOrElse{JSONObject()};error(j.optString("message").ifBlank{"Request failed (HTTP $code)"})}
  return response
 }
 fun login(email:String,password:String)=runCatching{val j=JSONObject(request("POST",ApiConfig.LOGIN_PATH,null,JSONObject().put("email",email.trim()).put("password",password).toString()));LoginResult(j.getString("token"),j.optString("email",email.trim()))}
 fun listDevices(token:String)=runCatching{val a=JSONObject(request("GET","/api/v1/devices",token)).getJSONArray("devices");(0 until a.length()).map{val j=a.getJSONObject(it);DeviceDto(j.getString("id"),j.getString("deviceId"),j.getString("imei"),j.optString("model"),j.optString("customerName"),j.optString("customerPhone"),j.optString("status"),j.optString("lastSeenAt").takeIf{!j.isNull("lastSeenAt")})}}
 fun addDevice(token:String,deviceId:String,imei:String,model:String,customerName:String,customerPhone:String)=runCatching{val root=JSONObject(request("POST","/api/v1/devices",token,JSONObject().put("deviceId",deviceId).put("imei",imei).put("model",model).put("customerName",customerName).put("customerPhone",customerPhone).toString()));val j=root.getJSONObject("device");DeviceEnrollment(DeviceDto(j.getString("id"),j.getString("deviceId"),j.getString("imei"),j.optString("model"),j.optString("customerName"),j.optString("customerPhone"),j.optString("status")),root.getJSONObject("enrollment").getString("controlKey"))}
 fun deleteDevice(token:String,id:String)=runCatching{request("DELETE","/api/v1/devices/$id",token);true}
 fun listCustomers(token:String)=runCatching{val a=JSONObject(request("GET","/api/v1/customers",token)).getJSONArray("customers");(0 until a.length()).map{val j=a.getJSONObject(it);CustomerDto(j.getString("id"),j.getString("name"),j.getString("phone"),j.optString("address"),j.optString("createdAt"))}}
 fun addCustomer(token:String,name:String,phone:String,address:String)=runCatching{val j=JSONObject(request("POST","/api/v1/customers",token,JSONObject().put("name",name).put("phone",phone).put("address",address).toString())).getJSONObject("customer");CustomerDto(j.getString("id"),j.getString("name"),j.getString("phone"),j.optString("address"),j.optString("createdAt"))}
 fun listAgreements(token:String)=runCatching{val a=JSONObject(request("GET","/api/v1/agreements",token)).getJSONArray("agreements");(0 until a.length()).map{parseAgreement(a.getJSONObject(it))}}
 fun addAgreement(token:String,customerId:String,deviceId:String,total:Double,down:Double,installment:Double,count:Int,nextDue:String)=runCatching{val j=JSONObject(request("POST","/api/v1/agreements",token,JSONObject().put("customerId",customerId).put("deviceId",deviceId).put("totalAmount",total).put("downPayment",down).put("installmentAmount",installment).put("numberOfInstallments",count).put("nextDueDate",nextDue).toString())).getJSONObject("agreement");parseAgreement(j)}
 fun payInstallment(token:String,id:String)=runCatching{parseAgreement(JSONObject(request("POST","/api/v1/agreements/$id/pay",token,"{}")).getJSONObject("agreement"))}
 fun listEnach(token:String)=runCatching{val a=JSONObject(request("GET","/api/v1/enach",token)).getJSONArray("enach");(0 until a.length()).map{val j=a.getJSONObject(it);EnachDto(j.getString("id"),j.getString("customerId"),j.getString("agreementId"),j.getString("mandateRef"),j.getString("status"))}}
 fun addEnach(token:String,customerId:String,agreementId:String,ref:String)=runCatching{val j=JSONObject(request("POST","/api/v1/enach",token,JSONObject().put("customerId",customerId).put("agreementId",agreementId).put("mandateRef",ref).toString())).getJSONObject("enach");EnachDto(j.getString("id"),j.getString("customerId"),j.getString("agreementId"),j.getString("mandateRef"),j.getString("status"))}
 fun updateEnach(token:String,id:String,status:String)=runCatching{val j=JSONObject(request("POST","/api/v1/enach/$id/status",token,JSONObject().put("status",status).toString())).getJSONObject("enach");EnachDto(j.getString("id"),j.getString("customerId"),j.getString("agreementId"),j.getString("mandateRef"),j.getString("status"))}
 private fun parseAgreement(j:JSONObject)=AgreementDto(j.getString("id"),j.getString("customerId"),j.getString("deviceId"),j.getDouble("totalAmount"),j.getDouble("downPayment"),j.getDouble("installmentAmount"),j.getInt("numberOfInstallments"),j.getInt("paidInstallments"),j.getDouble("remainingAmount"),j.optString("nextDueDate"))
 fun sendCommand(token:String,deviceId:String,command:String,payload:JSONObject?=null)=runCatching{val body=JSONObject().put("command",command).apply{if(payload!=null)put("payload",payload)}.toString();parseCommand(JSONObject(request("POST","/api/v1/devices/$deviceId/commands",token,body)).getJSONObject("command"))}
 fun commandStatus(token:String,id:String)=runCatching{parseCommand(JSONObject(request("GET","/api/v1/commands/$id",token)).getJSONObject("command"))}
 fun listCommands(token:String,deviceId:String?=null)=runCatching{
  val path="/api/v1/commands"+(deviceId?.let{"?deviceId="+java.net.URLEncoder.encode(it,"UTF-8")}&limit=50} ?: "?limit=50")
  val a=JSONObject(request("GET",path,token)).getJSONArray("commands")
  (0 until a.length()).map{parseCommand(a.getJSONObject(it))}
 }
 private fun parseCommand(j:JSONObject):CommandDto{val raw=j.optString("result").takeIf{!j.isNull("result")};val p=raw?.let{runCatching{JSONObject(it)}.getOrNull()};return CommandDto(j.getString("id"),j.getString("deviceId"),j.getString("command"),j.getString("status"),j.getString("createdAt"),raw,p?.takeIf{it.optString("type")=="location"}?.optDouble("latitude")?.takeUnless{it.isNaN()},p?.takeIf{it.optString("type")=="location"}?.optDouble("longitude")?.takeUnless{it.isNaN()},p?.takeIf{it.optString("type")=="location"}?.optDouble("accuracyMeters")?.takeUnless{it.isNaN()},p?.takeIf{it.optString("type")=="location"}?.optLong("timestamp")?.takeUnless{it==0L},p?.takeIf{it.optString("type")=="diagnostics"}?.optInt("batteryPercent")?.takeUnless{it==0},p?.takeIf{it.optString("type")=="diagnostics"}?.optBoolean("charging"),p?.takeIf{it.optString("type")=="diagnostics"}?.optBoolean("deviceAdmin"),p?.takeIf{it.optString("type")=="diagnostics"}?.optLong("uptimeSeconds")?.takeUnless{it==0L})}
}