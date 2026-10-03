package com.bdpro.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val RefNavy = Color(0xFF071827)
private val RefBlue = Color(0xFF0E7490)

class ReferenceMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ReferenceApp() } }
}

@Composable
private fun ReferenceApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionManager(context) }
    var loggedIn by remember { mutableStateOf(session.isLoggedIn()) }
    var tab by remember { mutableStateOf("Home") }
    var page by remember { mutableStateOf<String?>(null) }
    var selectedDevice by remember { mutableStateOf<DeviceDto?>(null) }
    MaterialTheme {
        if (!loggedIn) RefLogin { session.saveToken(it); loggedIn = true } else {
            BackHandler(enabled = page != null) { page = null }
            Scaffold(bottomBar = { if (page == null) RefNav(tab) { tab = it } }) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    if (page != null) when (page) {
                        "customer" -> CustomerWizard(session.token() ?: "") { page = null; tab = "Customers" }
                        "add" -> RefAddDevice(session.token() ?: "") { page = null; tab = "Devices" }
                        "control" -> RefControl(session.token() ?: "", selectedDevice)
                        else -> RefInfo(page ?: "More")
                    } else when (tab) {
                        "Customers" -> RefCustomers(session.token() ?: "") { page = "customer" }
                        "Devices" -> RefDevices(session.token() ?: "") { selectedDevice = it; page = "control" }
                        "Payments" -> RefInfo("Payments")
                        "More" -> RefMore { page = it }
                        else -> RefHome(session.token() ?: "") { a -> when(a) { "add" -> page="add"; "customers" -> tab="Customers"; "devices" -> tab="Devices"; "payments" -> tab="Payments" } }
                    }
                }
            }
        }
    }
}

@Composable private fun RefNav(tab:String,onTab:(String)->Unit){ NavigationBar { listOf("Home" to "⌂","Customers" to "♙","Devices" to "▣","Payments" to "৳","More" to "⋮").forEach{(n,i)->NavigationBarItem(tab==n,{onTab(n)},{Text(i)},label={Text(n)})} } }

@Composable private fun RefHome(token:String,on:(String)->Unit){
 var devices by remember{mutableStateOf<List<DeviceDto>>(emptyList())};var customers by remember{mutableStateOf<List<CustomerDto>>(emptyList())};var agreements by remember{mutableStateOf<List<AgreementDto>>(emptyList())}
 LaunchedEffect(Unit){ApiClient.listDevices(token).onSuccess{devices=it};ApiClient.listCustomers(token).onSuccess{customers=it};ApiClient.listAgreements(token).onSuccess{agreements=it}}
 val online=devices.count{it.status.equals("ONLINE",true)};val offline=devices.count{it.status.equals("OFFLINE",true)};val outstanding=agreements.sumOf{it.remainingAmount}
 LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
  item{Column(Modifier.fillMaxWidth().background(RefNavy,RoundedCornerShape(bottomStart=30.dp,bottomEnd=30.dp)).padding(20.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("BD PRO",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Device & Finance",color=Color.White.copy(.7f))};Pill("EN");Spacer(Modifier.width(6.dp));Pill("🔔");Spacer(Modifier.width(6.dp));Pill("●")};Spacer(Modifier.height(20.dp));Text("Control centre",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Devices",devices.size.toString(),Modifier.weight(1f));Stat("Online",online.toString(),Modifier.weight(1f));Stat("Offline",offline.toString(),Modifier.weight(1f))}}}
  item{Title("Installation & Account");Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){CardInfo("Installation","0","keys available",Modifier.weight(1f));CardInfo("Customers",customers.size.toString(),"registered",Modifier.weight(1f))}}
  item{Title("Quick Actions");Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Action("Add Device","＋",Modifier.weight(1f)){on("add")};Action("Customers","♙",Modifier.weight(1f)){on("customers")}}}
  item{Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Action("Device Control","▣",Modifier.weight(1f)){on("devices")};Action("Payments","৳",Modifier.weight(1f)){on("payments")}}}
  item{ElevatedCard(Modifier.padding(horizontal=16.dp).fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text("Finance Overview",fontWeight=FontWeight.Bold);Text("Outstanding balance");Text("${"%.2f".format(outstanding)}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Button({on("payments")},Modifier.fillMaxWidth()){Text("VIEW INSTALLMENTS")}}}}
 }
}
@Composable private fun Pill(t:String){Surface(color=Color.White.copy(.12f),shape=RoundedCornerShape(50)){Text(t,color=Color.White,Modifier.padding(horizontal=9.dp,vertical=6.dp))}}
@Composable private fun Stat(l:String,v:String,m:Modifier){Surface(color=Color.White.copy(.09f),shape=RoundedCornerShape(12.dp),modifier=m){Column(Modifier.padding(9.dp)){Text(v,color=Color.White,fontWeight=FontWeight.Bold);Text(l,color=Color.White.copy(.7f),style=MaterialTheme.typography.labelSmall)}}}
@Composable private fun Title(t:String){Text(t,Modifier.padding(horizontal=16.dp),fontWeight=FontWeight.Bold)}
@Composable private fun CardInfo(t:String,v:String,s:String,m:Modifier){ElevatedCard(m,shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(14.dp)){Text(t,style=MaterialTheme.typography.labelMedium);Text(v,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(s,style=MaterialTheme.typography.bodySmall)}}}
@Composable private fun Action(t:String,i:String,m:Modifier,on:()->Unit){ElevatedCard(onClick=on,modifier=m.height(100.dp),shape=RoundedCornerShape(16.dp)){Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween){Text(i,color=RefBlue,style=MaterialTheme.typography.headlineSmall);Text(t,fontWeight=FontWeight.SemiBold)}}}

@Composable private fun RefCustomers(token:String,onAdd:()->Unit){var cs by remember{mutableStateOf<List<CustomerDto>>(emptyList())};LaunchedEffect(Unit){ApiClient.listCustomers(token).onSuccess{cs=it}};LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Customers",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Customer onboarding")};Button(onAdd){Text("ADD")}}};item{WizardBar(0)};if(cs.isEmpty())item{Text("No customers yet.")};items(cs){c->ElevatedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(14.dp)){Text(c.name,fontWeight=FontWeight.Bold);Text(c.phone);Text(c.address.ifBlank{"Address not added"},style=MaterialTheme.typography.bodySmall)}}}}}
@Composable private fun WizardBar(active:Int){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Customer","Device","Documents","Agreement").forEachIndexed{i,s->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Surface(color=if(i<=active)RefBlue else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(50),modifier=Modifier.size(28.dp)){Box(contentAlignment=Alignment.Center){Text("${i+1}",color=if(i<=active)Color.White else MaterialTheme.colorScheme.onSurfaceVariant)}};Text(s,style=MaterialTheme.typography.labelSmall)}}}}

@Composable private fun CustomerWizard(token:String,onDone:()->Unit){var step by remember{mutableStateOf(0)};var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var alt by remember{mutableStateOf("")};var address by remember{mutableStateOf("")};var message by remember{mutableStateOf<String?>(null)};var saving by remember{mutableStateOf(false)}
 LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=24.dp)){item{Text("New Customer",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Customer → Device → Documents → Agreement");WizardBar(step)};when(step){0->item{Field("Customer Name",name){name=it};Field("Mobile Number",phone){phone=it};Field("Email",email){email=it};Field("Alternate Mobile",alt){alt=it};Field("Complete Address",address){address=it}};1->item{Text("Device",fontWeight=FontWeight.Bold);Text("Select the financed device in the next step of the workflow.")};2->item{Text("Documents",fontWeight=FontWeight.Bold);listOf("Aadhaar / ID proof","PAN","Bank / passbook","Customer selfie","Signature").forEach{OutlinedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(13.dp)){Text(it,Modifier.weight(1f));Text("Pending")}}}};3->item{Text("Agreement",fontWeight=FontWeight.Bold);Text("Agreement creation remains available from Payments after the customer is saved.")}};item{message?.let{Text(it,color=MaterialTheme.colorScheme.primary)};Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){if(step>0)OutlinedButton({step--},Modifier.weight(1f)){Text("BACK")};if(step<3)Button(enabled=step!=0||name.isNotBlank()&&phone.isNotBlank(),onClick={step++},modifier=Modifier.weight(1f)){Text("CONTINUE")};else Button(enabled=!saving&&name.isNotBlank()&&phone.isNotBlank(),onClick={saving=true;Thread{val r=ApiClient.addCustomer(token,name,phone,address);android.os.Handler(android.os.Looper.getMainLooper()).post{saving=false;r.onSuccess{message="Customer saved";onDone()}.onFailure{message=it.message?:"Save failed"}}}.start()},modifier=Modifier.weight(1f)){Text(if(saving)"SAVING…" else "SAVE")}}}}
}
@Composable private fun Field(l:String,v:String,on:(String)->Unit){OutlinedTextField(v,on,label={Text(l)},modifier=Modifier.fillMaxWidth())}

@Composable private fun RefDevices(token:String,onSelect:(DeviceDto)->Unit){var ds by remember{mutableStateOf<List<DeviceDto>>(emptyList())};var search by remember{mutableStateOf("")};var status by remember{mutableStateOf("ALL")};LaunchedEffect(search,status){while(true){ApiClient.listDevices(token,search,if(status=="ALL")"" else status).onSuccess{ds=it};delay(10000)}};LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Text("Devices",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Field("Search device / IMEI / customer",search){search=it}};item{Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("ALL","ONLINE","OFFLINE").forEach{s->OutlinedButton({status=s},Modifier.weight(1f)){Text(if(status==s)"✓ $s" else s)}}}};items(ds){d->ElevatedCard({onSelect(d)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(d.model.ifBlank{"Device"},fontWeight=FontWeight.Bold);Text(d.status)};Text(d.deviceId);Text("IMEI: ${d.imei}",style=MaterialTheme.typography.bodySmall);Text("Customer: ${d.customerName.ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall)}}}}
}

@Composable private fun RefControl(token:String,device:DeviceDto?){val cmds=listOf("LOCK" to "Ultimate Screen Lock","UNLOCK" to "Unlock request","LOCATION" to "Fetch Location","DIAGNOSTICS" to "Diagnostics","AUTOLOCK_ON" to "Auto Lock","AUTOLOCK_OFF" to "Auto Lock Off","ANTI_THEFT_ON" to "Anti Theft","ANTI_THEFT_OFF" to "Anti Theft Off");var msg by remember{mutableStateOf<String?>(null)};LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Text("Device Control",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(device?.deviceId?:"No device selected")};items(cmds.chunked(2)){row->Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){row.forEach{(c,l)->ElevatedCard(onClick={val d=device?:return@ElevatedCard;Thread{val r=ApiClient.sendCommand(token,d.id,c);android.os.Handler(android.os.Looper.getMainLooper()).post{r.onSuccess{msg="$c • QUEUED"}.onFailure{msg=it.message?:"Command failed"}}}.start()},modifier=Modifier.weight(1f).height(98.dp),shape=RoundedCornerShape(15.dp)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.SpaceBetween){Text(l,fontWeight=FontWeight.Bold);Text(c,style=MaterialTheme.typography.labelSmall)}}};if(row.size==1)Spacer(Modifier.weight(1f))}};item{msg?.let{Text(it,color=MaterialTheme.colorScheme.primary)}};item{Text("Reference catalog also contains App Lock, PIN Lock, Camera, Wallpaper, Call List and EMI Alert. These are not shown as working controls until their BD Pro backend/agent commands are implemented.",style=MaterialTheme.typography.bodySmall)}}}

@Composable private fun RefAddDevice(token:String,onDone:()->Unit){var id by remember{mutableStateOf("")};var imei by remember{mutableStateOf("")};var model by remember{mutableStateOf("")};var customer by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var msg by remember{mutableStateOf<String?>(null)};LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("Add Device",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Configurator / IMEI setup")};item{Field("Device ID",id){id=it}};item{Field("IMEI",imei){imei=it}};item{Field("Device Model",model){model=it}};item{Field("Customer Name",customer){customer=it}};item{Field("Customer Phone",phone){phone=it}};item{OutlinedButton({msg="QR scanner placeholder: scanner dependency will be connected next."},Modifier.fillMaxWidth()){Text("SCAN IMEI QR CODE")}};item{Button(enabled=id.isNotBlank()&&imei.isNotBlank(),onClick={Thread{val r=ApiClient.addDevice(token,id,imei,model,customer,phone);android.os.Handler(android.os.Looper.getMainLooper()).post{r.onSuccess{msg="Device added • Control Key generated";onDone()}.onFailure{msg=it.message?:"Add device failed"}}}.start()},Modifier.fillMaxWidth()){Text("ADD DEVICE")}};item{msg?.let{Text(it)}}}}

@Composable private fun RefMore(on:(String)->Unit){LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Text("More",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)};items(listOf("eNACH","Auto Lock","Anti Theft","Location","Diagnostics","Admin Profile")){t->ElevatedCard({on(t)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp)){Text(t,Modifier.padding(16.dp),fontWeight=FontWeight.SemiBold)}}}}
@Composable private fun RefInfo(title:String){Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text("This section is reserved in the BD Pro navigation and will be connected to its production workflow without using the reference app's backend.")}}
@Composable private fun RefLogin(onLogin:(String)->Unit){var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var err by remember{mutableStateOf<String?>(null)};Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){Text("BD PRO",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("Retailer device & finance management");Spacer(Modifier.height(20.dp));Field("Email",email){email=it};Field("Password",pass){pass=it};Spacer(Modifier.height(10.dp));Button({Thread{val r=ApiClient.login(email,pass);android.os.Handler(android.os.Looper.getMainLooper()).post{r.onSuccess{onLogin(it.token)}.onFailure{err=it.message?:"Login failed"}}}.start()},Modifier.fillMaxWidth()){Text("LOGIN")};err?.let{Text(it,color=MaterialTheme.colorScheme.error)}}}
