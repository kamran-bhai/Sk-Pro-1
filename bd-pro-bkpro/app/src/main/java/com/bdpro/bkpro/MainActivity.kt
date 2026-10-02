package com.bdpro.bkpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy=Color(0xFF0D1529)
private val Deep=Color(0xFF00030B)
private val Blue=Color(0xFF2E67F0)
private val TextMain=Color(0xFFF2F5FA)
private val TextMuted=Color(0xFF9BA9C1)
private val CardNavy=Color(0xFF111A30)

private data class Device(val id:String,val name:String,val customer:String,val status:String)
private val devices=listOf(
 Device("BK-1001","Samsung Galaxy A15","Customer 001","ACTIVE"),
 Device("BK-1002","Google Pixel 9 Pro","Customer 002","ACTIVE"),
 Device("BK-1003","Redmi Note 13","Customer 003","LOCKED")
)
private val commands=listOf("LOCK","UNLOCK","LOCATION","DIAGNOSTICS","AUTOLOCK_ON","AUTOLOCK_OFF","ANTI_THEFT_ON","ANTI_THEFT_OFF","REMOVE_DEVICE")

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{BDProApp()}}
}

@Composable
private fun BDProApp(){
 var loggedIn by remember{mutableStateOf(false)}
 var page by remember{mutableStateOf("dashboard")}
 MaterialTheme(colorScheme=darkColorScheme(primary=Blue,background=Deep,surface=CardNavy,onBackground=TextMain,onSurface=TextMain)){
  Surface(Modifier.fillMaxSize(),color=Deep){
   if(!loggedIn) AdminLogin{loggedIn=true} else when(page){
    "dashboard"->Dashboard{page=it}
    "devices"->DeviceList{page="dashboard"}
    "commands"->CommandCenter{page="dashboard"}
    "policies"->PolicyCenter{page="dashboard"}
    "customers"->SimplePage("Customer List","Customer Name / Mobile Number"){page="dashboard"}
    "emi"->SimplePage("EMI / Installment","Payment and installment status"){page="dashboard"}
    "enach"->SimplePage("ENACH","E-MANDATE management"){page="dashboard"}
    "profile"->SimplePage("Profile","Administrator profile"){page="dashboard"}
    else->Dashboard{page=it}
   }
  }
 }
}

@Composable private fun TopBar(title:String,onBack:(()->Unit)?=null){
 Row(Modifier.fillMaxWidth().background(Navy).padding(24.dp),verticalAlignment=Alignment.CenterVertically){
  if(onBack!=null){Icon(Icons.Default.ArrowBack,null,tint=TextMain,modifier=Modifier.size(34.dp).clickable{onBack()});Spacer(Modifier.width(20.dp))}
  Text(title,color=TextMain,fontSize=28.sp,fontWeight=FontWeight.Bold)
  Spacer(Modifier.weight(1f));Icon(Icons.Default.Refresh,null,tint=TextMuted,modifier=Modifier.size(30.dp))
 }
}

@Composable private fun AdminLogin(onSuccess:()->Unit){
 var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().background(Deep).padding(horizontal=48.dp),horizontalAlignment=Alignment.CenterHorizontally){
  TopBar("Admin Login");Spacer(Modifier.height(220.dp));Icon(Icons.Default.Lock,null,tint=Blue,modifier=Modifier.size(82.dp))
  Spacer(Modifier.height(32.dp));Text("BD PRO BKPRO",color=TextMain,fontSize=30.sp,fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(12.dp));Text("Admin authentication is required for control.",color=TextMuted,fontSize=18.sp)
  Spacer(Modifier.height(34.dp));OutlinedTextField(email,{email=it},label={Text("Admin email")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Spacer(Modifier.height(18.dp));OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  if(error.isNotBlank()){Spacer(Modifier.height(12.dp));Text(error,color=Color.Red)}
  Spacer(Modifier.height(28.dp));Button(onClick={if(email.isNotBlank()&&password.isNotBlank())onSuccess()else error="Enter admin email and password."},modifier=Modifier.fillMaxWidth().height(58.dp),colors=ButtonDefaults.buttonColors(containerColor=Blue)){Text("Sign in",fontSize=18.sp,fontWeight=FontWeight.Bold)}
 }
}

@Composable private fun Dashboard(onNavigate:(String)->Unit){
 val cards=listOf(
  Triple("Add Device","Pair a financed device","add"),Triple("Device List","View paired devices","devices"),
  Triple("Run Command","Send device commands","commands"),Triple("Auto Lock","Automatic lock policy","policies"),
  Triple("Anti Theft","Protection and alerts","policies"),Triple("Location","Device location","commands"),
  Triple("Diagnostics","Device diagnostics","commands"),Triple("Customer List","Customer Name / Mobile Number","customers"),
  Triple("EMI / Installment","Payment and installment status","emi"),Triple("ENACH","E-MANDATE management","enach"),
  Triple("Remove Device","Remove from paired devices","commands"),Triple("Profile","Administrator profile","profile"))
 LazyColumn(Modifier.fillMaxSize().background(Deep),contentPadding=PaddingValues(bottom=32.dp)){
  item{TopBar("BD Pro BKPro")}
  item{Column(Modifier.padding(24.dp)){Text("Admin Control",color=TextMain,fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Device management and retailer operations",color=TextMuted);Spacer(Modifier.height(20.dp))}}
  items(cards){item->
   Card(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=7.dp).clickable{onNavigate(item.third)},colors=CardDefaults.cardColors(containerColor=CardNavy),shape=RoundedCornerShape(16.dp)){
    Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){
     Icon(Icons.Default.Tune,null,tint=Blue,modifier=Modifier.size(32.dp));Spacer(Modifier.width(18.dp))
     Column(Modifier.weight(1f)){Text(item.first,color=TextMain,fontSize=19.sp,fontWeight=FontWeight.SemiBold);Text(item.second,color=TextMuted,fontSize=14.sp)}
     Icon(Icons.Default.ChevronRight,null,tint=TextMuted)
    }
   }
  }
 }
}

@Composable private fun DeviceList(onBack:()->Unit){
 Column(Modifier.fillMaxSize().background(Deep)){TopBar("Device List",onBack);LazyColumn(contentPadding=PaddingValues(20.dp)){
  items(devices){d->Card(Modifier.fillMaxWidth().padding(vertical=7.dp),colors=CardDefaults.cardColors(containerColor=CardNavy)){
   Column(Modifier.padding(18.dp)){Row(verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Default.Devices,null,tint=Blue);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(d.name,color=TextMain,fontSize=19.sp,fontWeight=FontWeight.Bold);Text(d.customer,color=TextMuted)}
    Text(d.status,color=if(d.status=="LOCKED")Color.Red else Color(0xFF58D68D),fontWeight=FontWeight.Bold)}
    Spacer(Modifier.height(8.dp));Text("Device ID: "+d.id,color=TextMuted)
   }
  }}
 }}
}

@Composable private fun CommandCenter(onBack:()->Unit){
 var selected by remember{mutableStateOf(devices.first())};var message by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().background(Deep)){TopBar("Run Command",onBack);Column(Modifier.padding(20.dp)){
  Text("Selected device",color=TextMuted);Spacer(Modifier.height(8.dp))
  OutlinedButton(onClick={selected=devices[(devices.indexOf(selected)+1)%devices.size]},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Devices,null);Spacer(Modifier.width(10.dp));Text(selected.name+" • "+selected.id)}
  Spacer(Modifier.height(18.dp));LazyColumn{items(commands){command->
   Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{message=command+" queued for "+selected.id+" (prototype)"},colors=CardDefaults.cardColors(containerColor=CardNavy)){
    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Build,null,tint=Blue);Spacer(Modifier.width(16.dp));Text(command,color=TextMain,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
   }
  }}
  if(message.isNotBlank()){Spacer(Modifier.height(12.dp));Text(message,color=Color(0xFF58D68D))}
 }}
}

@Composable private fun PolicyCenter(onBack:()->Unit){
 var autoLock by remember{mutableStateOf(true)};var antiTheft by remember{mutableStateOf(true)};var simLock by remember{mutableStateOf(true)};var usbLock by remember{mutableStateOf(true)}
 Column(Modifier.fillMaxSize().background(Deep)){TopBar("Auto Lock / Anti Theft",onBack);Column(Modifier.padding(20.dp)){
  Text("PROTECTION POLICIES",color=TextMain,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Admin-controlled policy configuration",color=TextMuted);Spacer(Modifier.height(18.dp))
  PolicyRow("Auto-Lock on overdue",autoLock){autoLock=it};PolicyRow("Anti-Theft protection",antiTheft){antiTheft=it};PolicyRow("Lock on SIM change",simLock){simLock=it};PolicyRow("Lock on USB debugging",usbLock){usbLock=it}
 }}
}
@Composable private fun PolicyRow(title:String,checked:Boolean,onChange:(Boolean)->Unit){
 Card(Modifier.fillMaxWidth().padding(vertical=6.dp),colors=CardDefaults.cardColors(containerColor=CardNavy)){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f)){Text(title,color=TextMain,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text(if(checked)"Enabled" else "Disabled",color=TextMuted)};Switch(checked,onChange)
 }}
}
@Composable private fun SimplePage(title:String,subtitle:String,onBack:()->Unit){
 Column(Modifier.fillMaxSize().background(Deep)){TopBar(title,onBack);Column(Modifier.padding(24.dp)){
  Text(subtitle,color=TextMuted,fontSize=18.sp);Spacer(Modifier.height(24.dp))
  Card(colors=CardDefaults.cardColors(containerColor=CardNavy),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(22.dp)){
   Text("BD Pro BKPro",color=TextMain,fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
   Text("This admin module is included in the first prototype. Backend integration will be connected separately.",color=TextMuted)
  }}
 }}
}
