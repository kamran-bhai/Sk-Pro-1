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
import java.time.Instant
import java.time.Duration
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
    var tab by remember { mutableStateOf("Home") }
    BackHandler(enabled = selected != "Dashboard") { selected = "Dashboard" }
    MaterialTheme {
        if (!loggedIn) LoginScreen { token -> session.saveToken(token); loggedIn = true }
        else {
            val primary = selected == "Dashboard"
            Scaffold(
                topBar = { if (!primary) TopAppBar(title = { Text(selected) }, navigationIcon = { TextButton(onClick = { selected = "Dashboard" }) { Text("‹ Back") } }) },
                bottomBar = {
                    if (primary) NavigationBar {
                        listOf("Home" to "⌂", "Customers" to "♙", "Devices" to "▣", "Payments" to "৳", "More" to "⋮").forEach { (name, icon) ->
                            NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = { Text(icon, style = MaterialTheme.typography.titleLarge) }, label = { Text(name) })
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (selected) {
                        "Add Device" -> AddDeviceScreen(session.token() ?: "") { selected = "Dashboard"; tab = "Devices" }
                        "Device List" -> DeviceListScreen(session.token() ?: "") { d -> selectedDevice = d; selected = "Device Details" }
                        "Device Details" -> selectedDevice?.let { DeviceDetailsScreen(session.token() ?: "", it) { selected = "Run Command" } }
                        "Run Command" -> RunCommandScreen(session.token() ?: "", selectedDevice)
                        "Location" -> RunCommandScreen(session.token() ?: "", selectedDevice, "LOCATION")
                        "Diagnostics" -> RunCommandScreen(session.token() ?: "", selectedDevice, "DIAGNOSTICS")
                        "Auto Lock" -> AutoLockScreen(session.token() ?: "", selectedDevice) { selectedDevice = it }
                        "Anti Theft" -> AntiTheftScreen(session.token() ?: "", selectedDevice) { d -> selectedDevice = d }
                        "Customers" -> CustomersScreen(session.token() ?: "")
                        "EMI / Installment" -> EmiScreen(session.token() ?: "")
                        "eNACH" -> EnachScreen(session.token() ?: "")
                        "Remove Device" -> RemoveDeviceScreen(session.token() ?: "")
                        "Admin Profile" -> AdminProfileScreen()
                        else -> when (tab) {
                            "Customers" -> CustomersScreen(session.token() ?: "")
                            "Devices" -> DeviceListScreen(session.token() ?: "") { d -> selectedDevice = d; selected = "Device Details" }
                            "Payments" -> EmiScreen(session.token() ?: "")
                            "More" -> MoreScreen { selected = it }
                            else -> DashboardScreen(session.token() ?: "") { action ->
                                when (action) {
                                    "ADD_DEVICE" -> selected = "Add Device"
                                    "DEVICES" -> tab = "Devices"
                                    "CUSTOMERS" -> tab = "Customers"
                                    "PAYMENTS" -> tab = "Payments"
                                    else -> selected = action
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(token: String, onSelect: (String) -> Unit) {
    var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }
    var customers by remember { mutableStateOf<List<CustomerDto>>(emptyList()) }
    var agreements by remember { mutableStateOf<List<AgreementDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        ApiClient.listDevices(token).onSuccess { devices = it }
        ApiClient.listCustomers(token).onSuccess { customers = it }
        ApiClient.listAgreements(token).onSuccess { agreements = it }
    }
    val online = devices.count { it.status.equals("ONLINE", true) }
    val offline = devices.count { it.status.equals("OFFLINE", true) }
    val outstanding = agreements.sumOf { it.remainingAmount }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
    ) {
        item {
            Text("BD Pro", style = MaterialTheme.typography.headlineLarge)
            Text("Device & Finance Management", style = MaterialTheme.typography.titleMedium)
            Text("Live control centre", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Overview", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Devices", devices.size.toString(), Modifier.weight(1f))
                        DashboardStat("Online", online.toString(), Modifier.weight(1f))
                        DashboardStat("Offline", offline.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Customers", customers.size.toString(), Modifier.weight(1f))
                        DashboardStat("Agreements", agreements.size.toString(), Modifier.weight(1f))
                    }
                }
            }
        }
        item { Text("Quick Actions", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("Add Device", "＋", Modifier.weight(1f)) { onSelect("ADD_DEVICE") }
                QuickActionCard("Customers", "♙", Modifier.weight(1f)) { onSelect("CUSTOMERS") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("Device Control", "▣", Modifier.weight(1f)) { onSelect("DEVICES") }
                QuickActionCard("Payments", "৳", Modifier.weight(1f)) { onSelect("PAYMENTS") }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Finance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Outstanding balance: ${"%.2f".format(outstanding)}")
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { onSelect("PAYMENTS") }, Modifier.fillMaxWidth()) { Text("OPEN EMI / INSTALLMENTS") }
                }
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickActionCard(title: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier.height(104.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun MoreScreen(onSelect: (String) -> Unit) {
    val items = listOf(
        "Auto Lock" to "Automatic device lock policy",
        "Anti Theft" to "SIM change protection",
        "Location" to "Request latest location",
        "Diagnostics" to "Battery and device health",
        "eNACH" to "Manage mandates",
        "Remove Device" to "Permanently remove a device",
        "Admin Profile" to "Admin account and backend"
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("More", style = MaterialTheme.typography.headlineSmall) }
        items(items) { (title, subtitle) ->
            ElevatedCard(onClick = { onSelect(title) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
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
                Text("Agent setup", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp))
                Text("1. Install the BD Pro Device Agent on the customer's phone.")
                Text("2. Enter this Device ID: " + deviceId)
                Text("3. Enter this Control Key exactly as shown above.")
                Text("4. Keep Backend URL as https://bd-pro-backend.onrender.com")
                Text("5. Tap TEST CONNECTION, then SAVE & START AGENT.")
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BD Pro Control Key", key)); message = "Control Key copied."
                }, modifier = Modifier.fillMaxWidth()) { Text("COPY CONTROL KEY") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BD Pro Device ID", deviceId)); message = "Device ID copied."
                }, modifier = Modifier.fillMaxWidth()) { Text("COPY DEVICE ID") }
                Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onAdded, modifier = Modifier.fillMaxWidth()) { Text("GO TO DEVICE LIST") }
            }}
        }
    }
}

private fun connectionLabel(device: DeviceDto): String {
    return when (device.status.uppercase()) {
        "ONLINE" -> "ONLINE"
        "OFFLINE" -> "OFFLINE"
        else -> device.status.ifBlank { "UNKNOWN" }.uppercase()
    }
}

private fun lastSeenLabel(value: String?): String {
    if (value.isNullOrBlank()) return "Not connected yet"
    val parsed = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    val seconds = Duration.between(parsed, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 10 -> "Just now"
        seconds < 60 -> seconds.toString() + " sec ago"
        seconds < 3600 -> (seconds / 60).toString() + " min ago"
        seconds < 86400 -> (seconds / 3600).toString() + " hr ago"
        else -> (seconds / 86400).toString() + " day ago"
    }
}

@Composable private fun ConnectionBadge(device: DeviceDto) {
    val online = connectionLabel(device) == "ONLINE"
    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
        Text(
            if (online) "● ONLINE" else "● " + connectionLabel(device),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable private fun DeviceListScreen(token: String, onSelect: (DeviceDto) -> Unit) {
    var devices by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var customerFilter by remember { mutableStateOf("") }

    LaunchedEffect(search, statusFilter, customerFilter) {
        while (true) {
            refreshing = true
            ApiClient.listDevices(token, search, if (statusFilter == "ALL") "" else statusFilter, customerFilter)
                .onSuccess { devices = it; error = null }
                .onFailure { error = it.message ?: "Unable to load devices" }
            refreshing = false
            delay(10000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Device List", style = MaterialTheme.typography.headlineSmall)
            Text(if (refreshing) "Updating..." else "Live • 10s", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(search, { search = it }, label = { Text("Search device / IMEI / model / customer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL","ONLINE","OFFLINE").forEach { s -> OutlinedButton(onClick = { statusFilter = s }, modifier = Modifier.weight(1f)) { Text(if (statusFilter == s) "✓ $s" else s) } }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(customerFilter, { customerFilter = it }, label = { Text("Customer filter (name / phone)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("ONLINE means the Device Agent checked in within the last 30 seconds.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (devices.isEmpty() && error == null) Text("No devices added yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(devices) { d ->
                ElevatedCard(onClick = { onSelect(d) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.model.ifBlank { "Unknown model" }, style = MaterialTheme.typography.titleMedium)
                            ConnectionBadge(d)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Device ID: " + d.deviceId)
                        Text("IMEI: " + d.imei)
                        Text("Customer: " + d.customerName.ifBlank { "—" })
                        Text("Agent: " + d.agentStatus.ifBlank { "UNKNOWN" } + " • v" + d.agentVersion.ifBlank { "—" })
                        Text("Last heartbeat: " + lastSeenLabel(d.lastSeenAt))
                        Spacer(Modifier.height(4.dp))
                        Text("Tap for details", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable private fun DeviceDetailsScreen(token: String, initialDevice: DeviceDto, onCommand: () -> Unit) {
    var device by remember(initialDevice.id) { mutableStateOf(initialDevice) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(initialDevice.id) {
        while (true) {
            refreshing = true
            ApiClient.listDevices(token).onSuccess { list ->
                list.firstOrNull { it.id == initialDevice.id }?.let { device = it }
            }
            refreshing = false
            delay(10000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Device Details", style = MaterialTheme.typography.headlineSmall)
            Text(if (refreshing) "Updating..." else "Live • 10s", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        ConnectionBadge(device)
        Spacer(Modifier.height(12.dp))
        Text("Device ID: " + device.deviceId)
        Text("IMEI: " + device.imei)
        Text("Model: " + device.model.ifBlank { "Unknown" })
        Text("Customer: " + device.customerName.ifBlank { "—" })
        Text("Phone: " + device.customerPhone.ifBlank { "—" })
        Spacer(Modifier.height(10.dp))
        Text("Connection: " + connectionLabel(device))
        Text("Last heartbeat: " + lastSeenLabel(device.lastSeenAt))
        Text("Agent version: " + device.agentVersion.ifBlank { "Unknown" })
        Text("Agent health: " + device.agentStatus.ifBlank { "UNKNOWN" })
        Text(
            when {
                connectionLabel(device) == "ONLINE" && device.agentStatus.uppercase() == "RUNNING" ->
                    "✓ Agent connected and reporting normally."
                connectionLabel(device) == "ONLINE" ->
                    "⚠ Agent is connected, but its reported state is " + device.agentStatus + "."
                device.lastSeenAt.isNullOrBlank() ->
                    "⚠ Agent has never reported. Complete Agent setup and TEST CONNECTION on the device."
                else ->
                    "✕ Agent heartbeat is stale. Check that the Agent is running, Device Admin is enabled, and the phone has internet."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(20.dp))
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
    val commands = listOf(
        "LOCK" to "Lock device",
        "UNLOCK" to "Unlock request",
        "LOCATION" to "Fetch location",
        "DIAGNOSTICS" to "Device health",
        "AUTOLOCK_ON" to "Auto lock",
        "AUTOLOCK_OFF" to "Disable auto lock",
        "ANTI_THEFT_ON" to "Anti theft",
        "ANTI_THEFT_OFF" to "Disable anti theft"
    )
    if (device != null) {
            val visibleCommands = focusCommand?.let { commands.filter { pair -> pair.first == it } } ?: commands
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(visibleCommands.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (command, label) ->
                            ElevatedCard(
                                onClick = {
                                    message = "Sending $command..."
                                    Thread {
                                        val result = ApiClient.sendCommand(token, device.id, command)
                                        Handler(Looper.getMainLooper()).post {
                                            result.onSuccess { activeCommand = it; message = "$command • QUEUED" }
                                                .onFailure { message = it.message ?: "Command failed" }
                                        }
                                    }.start()
                                },
                                modifier = Modifier.weight(1f).height(96.dp)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(command.replace("_", " "), style = MaterialTheme.typography.titleSmall)
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Text("Select a device from Device List to control it.", style = MaterialTheme.typography.bodyMedium)
        }
        message?.let { Text(it, color = if (activeCommand?.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        Text("Command History", style = MaterialTheme.typography.titleMedium)
        var history by remember { mutableStateOf<List<CommandDto>>(emptyList()) }
        LaunchedEffect(device?.id, activeCommand?.status) {
            val did = device?.id ?: return@LaunchedEffect
            ApiClient.listCommands(token, did).onSuccess { history = it }
        }
        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history) { item ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(item.command.replace("_", " "), style = MaterialTheme.typography.titleSmall)
                        Text("Status: " + item.status)
                        Text("Created: " + lastSeenLabel(item.createdAt))
                        item.result?.takeIf { it.isNotBlank() }?.let { Text("Result: " + it) }
                        if (item.status == "FAILED") {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    message = "Retrying " + item.command + "..."
                                    Thread {
                                        val result = ApiClient.retryCommand(token, item.id)
                                        Handler(Looper.getMainLooper()).post {
                                            result.onSuccess {
                                                activeCommand = it
                                                message = it.command + " • RETRY QUEUED"
                                            }.onFailure {
                                                message = it.message ?: "Retry failed"
                                            }
                                        }
                                    }.start()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("RETRY COMMAND") }
                        }
                    }
                }
            }
        }
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

@Composable private fun CustomersScreen(token: String) {
 var customers by remember{mutableStateOf<List<CustomerDto>>(emptyList())};var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var address by remember{mutableStateOf("")};var msg by remember{mutableStateOf<String?>(null)}
 fun load(){Thread{val r=ApiClient.listCustomers(token);Handler(Looper.getMainLooper()).post{r.onSuccess{customers=it}.onFailure{msg=it.message}}}.start()}
 LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize().padding(16.dp)){Text("Customers",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(10.dp))
  OutlinedTextField(name,{name=it},label={Text("Customer Name")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(6.dp))
  OutlinedTextField(phone,{phone=it},label={Text("Phone")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(6.dp))
  OutlinedTextField(address,{address=it},label={Text("Address")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(8.dp))
  Button(enabled=name.isNotBlank()&&phone.isNotBlank(),onClick={Thread{val r=ApiClient.addCustomer(token,name,phone,address);Handler(Looper.getMainLooper()).post{r.onSuccess{msg="Customer saved";name="";phone="";address="";load()}.onFailure{msg=it.message}}}.start()},modifier=Modifier.fillMaxWidth()){Text("ADD CUSTOMER")};msg?.let{Text(it,Modifier.padding(vertical=8.dp))}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(customers){x->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(x.name,style=MaterialTheme.typography.titleMedium);Text("Phone: "+x.phone);Text("Address: "+x.address.ifBlank{"—"})}}}}
 }
}
@Composable private fun EmiScreen(token:String){
 var customers by remember{mutableStateOf<List<CustomerDto>>(emptyList())};var devices by remember{mutableStateOf<List<DeviceDto>>(emptyList())};var agreements by remember{mutableStateOf<List<AgreementDto>>(emptyList())};var customerId by remember{mutableStateOf("")};var deviceId by remember{mutableStateOf("")};var total by remember{mutableStateOf("")};var down by remember{mutableStateOf("0")};var installment by remember{mutableStateOf("")};var count by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var msg by remember{mutableStateOf<String?>(null)}
 fun load(){Thread{val a=ApiClient.listCustomers(token);val d=ApiClient.listDevices(token);val g=ApiClient.listAgreements(token);Handler(Looper.getMainLooper()).post{a.onSuccess{customers=it};d.onSuccess{devices=it};g.onSuccess{agreements=it}}}.start()}
 LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize().padding(16.dp)){Text("EMI / Installment",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(8.dp))
  Text("Customer ID: $customerId");LazyColumn(Modifier.heightIn(max=110.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){items(customers){x->OutlinedButton({customerId=x.id},Modifier.fillMaxWidth()){Text(x.name+" • "+x.phone+if(customerId==x.id)" ✓" else "")}}}
  Text("Device ID: $deviceId");LazyColumn(Modifier.heightIn(max=110.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){items(devices){x->OutlinedButton({deviceId=x.id},Modifier.fillMaxWidth()){Text(x.deviceId+" • "+x.model+if(deviceId==x.id)" ✓" else "")}}}
  OutlinedTextField(total,{total=it},label={Text("Total Amount")},modifier=Modifier.fillMaxWidth());OutlinedTextField(down,{down=it},label={Text("Down Payment")},modifier=Modifier.fillMaxWidth());OutlinedTextField(installment,{installment=it},label={Text("Installment Amount")},modifier=Modifier.fillMaxWidth());OutlinedTextField(count,{count=it},label={Text("Number of Installments")},modifier=Modifier.fillMaxWidth());OutlinedTextField(due,{due=it},label={Text("Next Due Date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())
  Button(enabled=customerId.isNotBlank()&&deviceId.isNotBlank()&&total.toDoubleOrNull()!=null&&installment.toDoubleOrNull()!=null&&count.toIntOrNull()!=null,onClick={Thread{val r=ApiClient.addAgreement(token,customerId,deviceId,total.toDouble(),down.toDoubleOrNull()?:0.0,installment.toDouble(),count.toInt(),due);Handler(Looper.getMainLooper()).post{r.onSuccess{msg="Agreement created";load()}.onFailure{msg=it.message}}}.start()},modifier=Modifier.fillMaxWidth()){Text("CREATE AGREEMENT")};msg?.let{Text(it,Modifier.padding(8.dp))}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(agreements){a->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("Agreement "+a.id);Text("Remaining: "+a.remainingAmount);Text("Paid: "+a.paidInstallments+" / "+a.numberOfInstallments);Text("Next due: "+a.nextDueDate);Button(enabled=a.remainingAmount>0,onClick={Thread{val r=ApiClient.payInstallment(token,a.id);Handler(Looper.getMainLooper()).post{r.onSuccess{msg="Installment marked paid";load()}.onFailure{msg=it.message}}}.start()}){Text("MARK INSTALLMENT PAID")}}}}}
 }
}
@Composable private fun EnachScreen(token:String){
 var customers by remember{mutableStateOf<List<CustomerDto>>(emptyList())};var agreements by remember{mutableStateOf<List<AgreementDto>>(emptyList())};var records by remember{mutableStateOf<List<EnachDto>>(emptyList())};var cid by remember{mutableStateOf("")};var aid by remember{mutableStateOf("")};var ref by remember{mutableStateOf("")};var msg by remember{mutableStateOf<String?>(null)}
 fun load(){Thread{val c=ApiClient.listCustomers(token);val a=ApiClient.listAgreements(token);val e=ApiClient.listEnach(token);Handler(Looper.getMainLooper()).post{c.onSuccess{customers=it};a.onSuccess{agreements=it};e.onSuccess{records=it}}}.start()};LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize().padding(16.dp)){Text("eNACH",style=MaterialTheme.typography.headlineSmall);Text("Customer: $cid");LazyColumn(Modifier.heightIn(max=100.dp)){items(customers){x->OutlinedButton({cid=x.id},Modifier.fillMaxWidth()){Text(x.name+if(cid==x.id)" ✓" else "")}}};Text("Agreement: $aid");LazyColumn(Modifier.heightIn(max=100.dp)){items(agreements){x->OutlinedButton({aid=x.id},Modifier.fillMaxWidth()){Text(x.id+" • "+x.remainingAmount)}}};OutlinedTextField(ref,{ref=it},label={Text("Mandate Reference")},modifier=Modifier.fillMaxWidth());Button(enabled=cid.isNotBlank()&&aid.isNotBlank()&&ref.isNotBlank(),onClick={Thread{val r=ApiClient.addEnach(token,cid,aid,ref);Handler(Looper.getMainLooper()).post{r.onSuccess{msg="eNACH created";load()}.onFailure{msg=it.message}}}.start()},modifier=Modifier.fillMaxWidth()){Text("CREATE eNACH")};msg?.let{Text(it,Modifier.padding(8.dp))}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(records){e->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("Mandate: "+e.mandateRef);Text("Status: "+e.status);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("ACTIVE","CANCELLED","FAILED").forEach{s->OutlinedButton({Thread{val r=ApiClient.updateEnach(token,e.id,s);Handler(Looper.getMainLooper()).post{r.onSuccess{msg="Status updated";load()}.onFailure{msg=it.message}}}.start()}){Text(s)}}}}}}}
 }
}
@Composable private fun RemoveDeviceScreen(token:String){
 var devices by remember{mutableStateOf<List<DeviceDto>>(emptyList())};var msg by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var pending by remember{mutableStateOf<DeviceDto?>(null)}
 fun load(){Thread{val r=ApiClient.listDevices(token);Handler(Looper.getMainLooper()).post{r.onSuccess{devices=it}.onFailure{msg=it.message}}}.start()};LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize().padding(16.dp)){Text("Remove Device",style=MaterialTheme.typography.headlineSmall);Text("Removing a device permanently deletes its record and queued commands.",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(10.dp));msg?.let{Text(it)}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(devices){d->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(d.deviceId,style=MaterialTheme.typography.titleMedium);Text(d.model.ifBlank{"Unknown model"});Text("Status: "+d.status);Button(enabled=!busy,onClick={pending=d},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error),modifier=Modifier.fillMaxWidth()){Text("REMOVE DEVICE")}}}}}
 }
 pending?.let{d->AlertDialog(onDismissRequest={if(!busy)pending=null},title={Text("Remove device?")},text={Text("Device ${d.deviceId} will be permanently removed. This action cannot be undone.")},confirmButton={Button(enabled=!busy,onClick={busy=true;Thread{val r=ApiClient.deleteDevice(token,d.id);Handler(Looper.getMainLooper()).post{busy=false;r.onSuccess{msg="Device removed";pending=null;load()}.onFailure{msg=it.message}}}.start()}){Text(if(busy)"REMOVING..." else "REMOVE")}},dismissButton={TextButton(enabled=!busy,onClick={pending=null}){Text("CANCEL")}})}
}
@Composable private fun AdminProfileScreen() { Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Admin Profile", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp)); Text("Role: ADMIN"); Text("Backend: https://bd-pro-backend.onrender.com"); Text("Live server authentication enabled") } }