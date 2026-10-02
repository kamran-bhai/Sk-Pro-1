package com.bdpro.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val modules = listOf("Add Device", "Device List", "Run Command", "Auto Lock", "Anti Theft", "Location", "Diagnostics", "Customers", "EMI / Installment", "eNACH", "Remove Device", "Admin Profile")
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { BDProApp() } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BDProApp() {
    val context = LocalContext.current; val session = remember { SessionManager(context) }
    var loggedIn by remember { mutableStateOf(session.isLoggedIn()) }; var selected by remember { mutableStateOf("Dashboard") }
    var selectedDevice by remember { mutableStateOf<DeviceDto?>(null) }
    MaterialTheme {
        if (!loggedIn) LoginScreen { token -> session.saveToken(token); loggedIn = true } else
        Scaffold(topBar = { TopAppBar(title = { Text("BD Pro • $selected") }) }) { pad ->
            when (selected) {
                "Add Device" -> AddDeviceScreen(session.token() ?: "") { selected = "Device List" }
                "Device List" -> DeviceListScreen(session.token() ?: "") { d -> selectedDevice = d; selected = "Device Details" }
                "Device Details" -> selectedDevice?.let { DeviceDetailsScreen(it) { selected = "Run Command" } } ?: DashboardScreen { selected = it }
                "Run Command" -> RunCommandScreen(session.token() ?: "", selectedDevice)
                else -> DashboardScreen { selected = it }
            }
        }
    }
}

@Composable private fun DashboardScreen(onSelect: (String) -> Unit) {
    Column(Modifier.padding(16.dp)) { Text("Admin Dashboard", style = MaterialTheme.typography.headlineSmall); Text("Admin-controlled Android device management"); Spacer(Modifier.height(16.dp))
        modules.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { name -> ElevatedButton({ onSelect(name) }, Modifier.weight(1f).height(72.dp)) { Text(name) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) }; Spacer(Modifier.height(10.dp)) }
    }
}

@Composable private fun AddDeviceScreen(token: String, onAdded: () -> Unit) {
    val context = LocalContext.current
    var deviceId by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var controlKey by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Add Device", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(imei, { imei = it }, label = { Text("IMEI") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(customerName, { customerName = it }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Customer Phone") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        Button(
            {
                busy = true
                message = null
                controlKey = null
                Thread {
                    val r = ApiClient.addDevice(token, deviceId, imei, model, customerName, customerPhone)
                    Handler(Looper.getMainLooper()).post {
                        busy = false
                        r.onSuccess {
                            controlKey = it.controlKey
                            message = "Device added successfully. Save this Control Key for the Device Agent."
                        }.onFailure { message = it.message ?: "Add device failed" }
                    }
                }.start()
            },
            enabled = !busy && deviceId.isNotBlank() && imei.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "ADDING..." else "ADD DEVICE") }

        message?.let { Text(it, Modifier.padding(top = 10.dp)) }

        controlKey?.let { key ->
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Device Control Key", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(key, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("BD Pro Control Key", key))
                            message = "Control Key copied."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("COPY CONTROL KEY") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onAdded, modifier = Modifier.fillMaxWidth()) {
                        Text("GO TO DEVICE LIST")
                    }
                }
            }
        }
    }
}

@Composable private fun DeviceListScreen(token: String, onSelect: (DeviceDto) -> Unit) {
    var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { ApiClient.listDevices(token).onSuccess { devices = it }.onFailure { error = it.message ?: "Unable to load devices" } }
    Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Device List", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (devices.isEmpty() && error == null) Text("No devices added yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(devices) { d -> ElevatedCard(onClick = { onSelect(d) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(d.model.ifBlank { "Unknown model" }, style = MaterialTheme.typography.titleMedium); Text("Device ID: " + d.deviceId); Text("IMEI: " + d.imei); Text("Customer: " + d.customerName.ifBlank { "—" }); Text("Status: " + d.status); Text("Tap for details") } } } }
    }
}

@Composable private fun DeviceDetailsScreen(device: DeviceDto, onCommand: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Device Details", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp)); Text("Device ID: " + device.deviceId); Text("IMEI: " + device.imei); Text("Model: " + device.model.ifBlank { "Unknown" }); Text("Customer: " + device.customerName.ifBlank { "—" }); Text("Phone: " + device.customerPhone.ifBlank { "—" }); Text("Status: " + device.status); Spacer(Modifier.height(20.dp)); Button(onClick = onCommand, modifier = Modifier.fillMaxWidth()) { Text("RUN COMMAND") } }
}

@Composable private fun RunCommandScreen(token: String, device: DeviceDto?) {
    var message by remember { mutableStateOf<String?>(null) }
    var activeCommand by remember { mutableStateOf<CommandDto?>(null) }
    val commands = listOf("LOCK", "UNLOCK", "LOCATION", "DIAGNOSTICS", "AUTOLOCK_ON", "AUTOLOCK_OFF", "ANTI_THEFT_ON", "ANTI_THEFT_OFF")

    LaunchedEffect(activeCommand?.id) {
        val id = activeCommand?.id ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val result = ApiClient.commandStatus(token, id)
            var finished = false
            result.onSuccess { updated ->
                activeCommand = updated
                message = updated.command + " • " + updated.status + (updated.result?.let { " • $it" } ?: "")
                finished = updated.status == "SUCCESS" || updated.status == "FAILED"
            }
            if (finished) break
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Run Command", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Target: " + (device?.deviceId ?: "Select a device from Device List"))
        Spacer(Modifier.height(16.dp))
        if (device != null) {
            commands.forEach { command ->
                Button(
                    onClick = {
                        message = "Sending $command..."
                        Thread {
                            val result = ApiClient.sendCommand(token, device.id, command)
                            Handler(Looper.getMainLooper()).post {
                                result.onSuccess {
                                    activeCommand = it
                                    message = "$command • QUEUED"
                                }.onFailure { message = it.message ?: "Command failed" }
                            }
                        }.start()
                    },
                    enabled = activeCommand?.status != "QUEUED" && activeCommand?.status != "SENT",
                    modifier = Modifier.fillMaxWidth()
                ) { Text(command.replace("_", " ")) }
                Spacer(Modifier.height(8.dp))
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable private fun LoginScreen(onLogin: (String) -> Unit) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text("BD Pro", style = MaterialTheme.typography.headlineLarge); Text("Admin Control Panel"); Spacer(Modifier.height(24.dp)); OutlinedTextField(email, { email = it }, label = { Text("Admin email") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp))
        Button({ loading = true; error = null; Thread { val r = ApiClient.login(email, password); Handler(Looper.getMainLooper()).post { loading = false; r.onSuccess { onLogin(it.token) }.onFailure { error = it.message ?: "Login failed" } } }.start() }, modifier = Modifier.fillMaxWidth(), enabled = !loading && email.isNotBlank() && password.isNotBlank()) { Text(if (loading) "LOGGING IN..." else "LOGIN") }; error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}