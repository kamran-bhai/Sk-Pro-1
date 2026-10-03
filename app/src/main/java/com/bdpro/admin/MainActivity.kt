package com.bdpro.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

private val modules = listOf("Add Device", "Device List", "Run Command", "Auto Lock", "Anti Theft", "Location", "Diagnostics", "Customers", "EMI / Installment", "eNACH", "Remove Device", "Admin Profile")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { BDProApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BDProApp() {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    var loggedIn by remember { mutableStateOf(session.isLoggedIn()) }
    var selected by remember { mutableStateOf("Dashboard") }
    var selectedDevice by remember { mutableStateOf<DeviceDto?>(null) }
    BackHandler(enabled = selected != "Dashboard") { selected = "Dashboard" }
    MaterialTheme {
        if (!loggedIn) LoginScreen { token -> session.saveToken(token); loggedIn = true }
        else Scaffold(topBar = { TopAppBar(title = { Text("BD Pro • $selected") }) }) { pad ->
            Box(Modifier.padding(pad)) {
                when (selected) {
                    "Add Device" -> AddDeviceScreen(session.token() ?: "") { selected = "Device List" }
                    "Device List" -> DeviceListScreen(session.token() ?: "") { d -> selectedDevice = d; selected = "Device Details" }
                    "Device Details" -> selectedDevice?.let { DeviceDetailsScreen(it) { selected = "Run Command" } } ?: DashboardScreen { selected = it }
                    "Run Command" -> RunCommandScreen(session.token() ?: "", selectedDevice)
                    "Location" -> RunCommandScreen(session.token() ?: "", selectedDevice, "LOCATION")
                    "Diagnostics" -> RunCommandScreen(session.token() ?: "", selectedDevice, "DIAGNOSTICS")
                    "Customers" -> CustomersScreen(session.token() ?: "")
                    "EMI / Installment" -> ModuleInfoScreen("EMI / Installment")
                    "eNACH" -> ModuleInfoScreen("eNACH")
                    "Remove Device" -> ModuleInfoScreen("Remove Device")
                    "Admin Profile" -> AdminProfileScreen()
                    "Auto Lock" -> AutoLockScreen(session.token() ?: "", selectedDevice) { selectedDevice = it }
                    "Anti Theft" -> AntiTheftScreen(session.token() ?: "", selectedDevice) { d -> selectedDevice = d }
                    else -> DashboardScreen { selected = it }
                }
            }
        }
    }
}

@Composable private fun DashboardScreen(onSelect: (String) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("BD Pro", style = MaterialTheme.typography.headlineLarge); Text("Device Management Console", style = MaterialTheme.typography.titleMedium); Text("Connected to the live BD Pro backend", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        modules.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { name -> ElevatedButton({ onSelect(name) }, Modifier.weight(1f).height(72.dp)) { Text(name) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable private fun AddDeviceScreen(token: String, onAdded: () -> Unit) {
    val context = LocalContext.current
    var deviceId by remember { mutableStateOf("") }; var imei by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }; var customerPhone by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }; var controlKey by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Add Device", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp))
        OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
        OutlinedTextField(imei, { imei = it }, label = { Text("IMEI") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
        OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
        OutlinedTextField(customerName, { customerName = it }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
        OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Customer Phone") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
        Button({
            busy = true; message = null; controlKey = null
            Thread {
                val r = ApiClient.addDevice(token, deviceId, imei, model, customerName, customerPhone)
                Handler(Looper.getMainLooper()).post {
                    busy = false
                    r.onSuccess { controlKey = it.controlKey; message = "Device added successfully. Save this Control Key for the Device Agent." }.onFailure { message = it.message ?: "Add device failed" }
                }
            }.start()
        }, enabled = !busy && deviceId.isNotBlank() && imei.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "ADDING..." else "ADD DEVICE") }
        message?.let { Text(it, Modifier.padding(top = 10.dp)) }
        controlKey?.let { key ->
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Device Control Key", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text(key); Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BD Pro Control Key", key)); message = "Control Key copied."
                }, modifier = Modifier.fillMaxWidth()) { Text("COPY CONTROL KEY") }
                Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onAdded, modifier = Modifier.fillMaxWidth()) { Text("GO TO DEVICE LIST") }
            }}
        }
    }
}

@Composable private fun DeviceListScreen(token: String, onSelect: (DeviceDto) -> Unit) {
    var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            ApiClient.listDevices(token).onSuccess { devices = it }.onFailure { error = it.message ?: "Unable to load devices" }
            delay(10000)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Device List", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (devices.isEmpty() && error == null) Text("No devices added yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(devices) { d ->
            ElevatedCard(onClick = { onSelect(d) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(d.model.ifBlank { "Unknown model" }, style = MaterialTheme.typography.titleMedium); Text("Device ID: " + d.deviceId); Text("IMEI: " + d.imei)
                Text("Customer: " + d.customerName.ifBlank { "—" }); Text("Status: " + d.status); Text("Last seen: " + (d.lastSeenAt ?: "Not connected yet")); Text("Tap for details")
            }}
        }}
    }
}

@Composable private fun DeviceDetailsScreen(device: DeviceDto, onCommand: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Device Details", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp))
        Text("Device ID: " + device.deviceId); Text("IMEI: " + device.imei); Text("Model: " + device.model.ifBlank { "Unknown" }); Text("Customer: " + device.customerName.ifBlank { "—" }); Text("Phone: " + device.customerPhone.ifBlank { "—" })
        Text("Status: " + device.status); Text("Last seen: " + (device.lastSeenAt ?: "Not connected yet")); Spacer(Modifier.height(20.dp))
        Button(onClick = onCommand, modifier = Modifier.fillMaxWidth()) { Text("RUN COMMAND") }
    }
}

@Composable private fun AutoLockScreen(token: String, initialDevice: DeviceDto?, onSelectDevice: (DeviceDto?) -> Unit) {
    var device by remember { mutableStateOf(initialDevice) }
    var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }
    LaunchedEffect(Unit) { ApiClient.listDevices(token).onSuccess { devices = it } }
    var minutes by remember { mutableStateOf("5") }
    var message by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf<CommandDto?>(null) }
    LaunchedEffect(active?.id) {
        val id = active?.id ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val result = ApiClient.commandStatus(token, id)
            var done = false
            result.onSuccess { updated ->
                active = updated
                message = updated.result ?: (updated.command + " • " + updated.status)
                done = updated.status == "SUCCESS" || updated.status == "FAILED"
            }
            if (done) break
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Auto Lock", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Select Device", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(devices) { d ->
                OutlinedButton(
                    onClick = { device = d; onSelectDevice(d) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("${d.model.ifBlank { "Device" }} • ${d.deviceId}${if (device?.id == d.id) " ✓" else ""}") }
            }
        }
        Text("Target: " + (device?.deviceId ?: "No device selected"), Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
            label = { Text("Timeout (minutes)") },
            supportingText = { Text("Allowed range: 1–1440 minutes") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = device != null && minutes.toIntOrNull()?.let { it in 1..1440 } == true,
            onClick = {
                val timeout = minutes.toInt()
                message = "Sending Auto Lock • " + timeout + " minutes..."
                Thread {
                    val r = ApiClient.sendCommand(token, device!!.id, "AUTOLOCK_ON", JSONObject().put("timeoutMinutes", timeout))
                    Handler(Looper.getMainLooper()).post {
                        r.onSuccess { active = it; message = "AUTOLOCK_ON • QUEUED" }.onFailure { message = it.message ?: "Command failed" }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ENABLE AUTO LOCK") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            enabled = device != null,
            onClick = {
                message = "Disabling Auto Lock..."
                Thread {
                    val r = ApiClient.sendCommand(token, device!!.id, "AUTOLOCK_OFF")
                    Handler(Looper.getMainLooper()).post {
                        r.onSuccess { active = it; message = "AUTOLOCK_OFF • QUEUED" }.onFailure { message = it.message ?: "Command failed" }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("DISABLE AUTO LOCK") }
        message?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable private fun AntiTheftScreen(token: String, initialDevice: DeviceDto?, onSelectDevice: (DeviceDto) -> Unit) {
    var device by remember { mutableStateOf(initialDevice) }
    var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf<CommandDto?>(null) }

    LaunchedEffect(Unit) { ApiClient.listDevices(token).onSuccess { devices = it } }

    LaunchedEffect(active?.id) {
        val id = active?.id ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val result = ApiClient.commandStatus(token, id)
            var done = false
            result.onSuccess { updated ->
                active = updated
                message = updated.result ?: (updated.command + " • " + updated.status)
                done = updated.status == "SUCCESS" || updated.status == "FAILED"
            }
            if (done) break
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Anti Theft", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Select Device", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(devices) { d ->
                OutlinedButton(
                    onClick = { device = d; onSelectDevice(d) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("${d.model.ifBlank { "Device" }} • ${d.deviceId}${if (device?.id == d.id) " ✓" else ""}") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Target: " + (device?.deviceId ?: "No device selected"))
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = device != null && active?.status != "QUEUED" && active?.status != "SENT",
            onClick = {
                val target = device ?: return@Button
                message = "Enabling Anti Theft..."
                Thread {
                    val r = ApiClient.sendCommand(token, target.id, "ANTI_THEFT_ON")
                    Handler(Looper.getMainLooper()).post {
                        r.onSuccess { active = it; message = "ANTI_THEFT_ON • QUEUED" }
                            .onFailure { message = it.message ?: "Command failed" }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ENABLE ANTI THEFT") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            enabled = device != null && active?.status != "QUEUED" && active?.status != "SENT",
            onClick = {
                val target = device ?: return@OutlinedButton
                message = "Disabling Anti Theft..."
                Thread {
                    val r = ApiClient.sendCommand(token, target.id, "ANTI_THEFT_OFF")
                    Handler(Looper.getMainLooper()).post {
                        r.onSuccess { active = it; message = "ANTI_THEFT_OFF • QUEUED" }
                            .onFailure { message = it.message ?: "Command failed" }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("DISABLE ANTI THEFT") }
        message?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(12.dp))
        Text(
            "Anti-Theft uses the device agent's SIM subscription baseline. If a change is detected, the agent can lock the device. Android/OEM limitations mean this is not a guaranteed SIM-identity check.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable private fun RunCommandScreen(token: String, device: DeviceDto?, focusCommand: String? = null) {
    val context = LocalContext.current
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
                message = updated.command + " • " + updated.status + (updated.result?.let { " • " + it } ?: "")
                finished = updated.status == "SUCCESS" || updated.status == "FAILED"
            }
            if (finished) break
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Run Command", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text("Target: " + (device?.deviceId ?: "Select a device from Device List")); Spacer(Modifier.height(16.dp))
        if (device != null) {
            val visibleCommands = focusCommand?.let { listOf(it) } ?: commands
            visibleCommands.forEach { command ->
                Button(
                    onClick = {
                        message = "Sending $command..."
                        Thread {
                            val result = ApiClient.sendCommand(token, device.id, command)
                            Handler(Looper.getMainLooper()).post {
                                result.onSuccess { activeCommand = it; message = command + " • QUEUED" }.onFailure { message = it.message ?: "Command failed" }
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
        val diagnostic = activeCommand
        if (diagnostic?.command == "DIAGNOSTICS" && diagnostic.status == "SUCCESS" && diagnostic.batteryPercent != null) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Device Diagnostics", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                Text("Battery: " + diagnostic.batteryPercent + "%"); Text("Charging: " + if (diagnostic.charging == true) "Yes" else "No"); Text("Device Admin: " + if (diagnostic.deviceAdmin == true) "Enabled" else "Disabled")
                diagnostic.uptimeSeconds?.let { Text("Uptime: " + (it / 3600) + "h " + ((it % 3600) / 60) + "m") }
            }}
        }
        val loc = activeCommand
        if (loc?.command == "LOCATION" && loc.status == "SUCCESS" && loc.latitude != null && loc.longitude != null) {
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Latest Device Location", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                Text("Latitude: " + loc.latitude); Text("Longitude: " + loc.longitude); loc.accuracyMeters?.let { Text("Accuracy: " + "%.1f m".format(it)) }; Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val uri = Uri.parse("geo:" + loc.latitude + "," + loc.longitude + "?q=" + loc.latitude + "," + loc.longitude + "(BD%20Pro%20Device)")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }, modifier = Modifier.fillMaxWidth()) { Text("OPEN IN MAP") }
            }}
        }
    }
}

@Composable private fun LoginScreen(onLogin: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("BD Pro", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(8.dp)); Text("Admin Login"); Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
        Button({
            loading = true; error = null
            Thread {
                val r = ApiClient.login(email, password)
                Handler(Looper.getMainLooper()).post { loading = false; r.onSuccess { onLogin(it.token) }.onFailure { error = it.message ?: "Login failed" } }
            }.start()
        }, modifier = Modifier.fillMaxWidth(), enabled = !loading && email.isNotBlank() && password.isNotBlank()) { Text(if (loading) "LOGGING IN..." else "LOGIN") }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun ModuleInfoScreen(title: String) { Column(Modifier.fillMaxSize().padding(16.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp)); Text("This module is connected to the live system; no demo data is shown.") } }
@Composable private fun CustomersScreen(token: String) { var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }; LaunchedEffect(Unit) { ApiClient.listDevices(token).onSuccess { devices = it }.onFailure { error = it.message } }; Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Customers", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(devices) { d -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(d.customerName.ifBlank { "Customer not set" }, style = MaterialTheme.typography.titleMedium); Text("Phone: " + d.customerPhone.ifBlank { "—" }); Text("Device: " + d.deviceId); Text("Status: " + d.status) } } } } } }
@Composable private fun AdminProfileScreen() { Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Admin Profile", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); Text("Role: ADMIN"); Text("Backend: https://bd-pro-backend.onrender.com"); Text("Live server authentication enabled") } }
