package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.CustomerEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceCommandEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    device: DeviceEntity?,
    customer: CustomerEntity?,
    agreement: AgreementEntity?,
    commands: List<DeviceCommandEntity>,
    onLockDevice: (reason: String) -> Unit,
    onUnlockDevice: () -> Unit,
    onRequestStatus: () -> Unit,
    statusRefreshing: Boolean = false,
    statusError: String? = null,
    onRequestLocation: () -> Unit,
    onGenerateEnrollment: () -> Unit,
    onBack: () -> Unit
) {
    if (device == null) {
        Box(modifier = Modifier.fillMaxSize().background(Slate950), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    var showLockDialog by remember { mutableStateOf(false) }
    var lockReason by remember { mutableStateOf("Payment overdue for > 15 days as per agreement terms") }

    LaunchedEffect(device.id) {
        onRequestStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${device.manufacturer} ${device.model}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRequestStatus) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100,
                    navigationIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate950
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (statusRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryBlue
                                )
                            }

                            Column {
                                Text("DEVICE STATUS", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                Text(
                                    text = device.enrollmentStatus,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (device.enrollmentStatus) {
                                        "ACTIVE" -> EmeraldGreen
                                        "LOCKED" -> CrimsonRed
                                        "OVERDUE" -> AmberWarning
                                        else -> Slate200
                                    }
                                )
                            }
                            StatusBadge(status = device.enrollmentStatus)
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Slate800)

                        statusError?.let { message ->
                            Text(
                                text = message,
                                color = AmberWarning,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Connectivity", color = Slate400, fontSize = 11.sp)
                                Text(if (device.isOnline) "Connected" else "Offline", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Battery", color = Slate400, fontSize = 11.sp)
                                Text("${device.batteryPercent}%", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Carrier SIM", color = Slate400, fontSize = 11.sp)
                                Text(device.simCarrier ?: "Unknown", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Verified Status Details
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "VERIFIED DEVICE DETAILS",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailRow("Device ID", device.id)
                        DetailRow("Manufacturer", device.manufacturer)
                        DetailRow("Model", device.model)
                        DetailRow("Android Version", device.androidVersion.ifBlank { "Not reported" })
                        DetailRow("Management", device.managementMode)
                        DetailRow(
                            "Last Seen",
                            if (device.lastSeenTimestamp > 0L) {
                                java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(device.lastSeenTimestamp))
                            } else "Never confirmed"
                        )
                        DetailRow("SIM", device.simCarrier ?: "Not reported")
                        DetailRow(
                            "USB Debugging",
                            if (device.usbDebuggingActive) "Enabled" else "Disabled / not reported"
                        )
                        DetailRow(
                            "Telemetry",
                            if (device.isOnline) "Verified online" else "Last-known offline"
                        )
                    }
                }
            }

            // Command Controller Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DEVICE COMMAND CONTROLLER",
                            fontSize = 12.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showLockDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                shape = RoundedCornerShape(8.dp),
                                enabled = device.enrollmentStatus != "LOCKED"
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Device")
                            }

                            Button(
                                onClick = onUnlockDevice,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp),
                                enabled = device.enrollmentStatus == "LOCKED"
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unlock")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRequestLocation,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get Location")
                            }

                            OutlinedButton(
                                onClick = onRequestStatus,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ping Sync")
                            }
                        }
                    }
                }
            }

            // Enrollment Ticket
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "DEVICE ENROLLMENT",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (agreement != null) {
                                "Generate a one-time enrollment code after the financing agreement is ready."
                            } else {
                                "Create the financing agreement first. An enrollment ticket is bound to customer, device, and agreement."
                            },
                            color = Slate400,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onGenerateEnrollment,
                            enabled = agreement != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate Enrollment Code")
                        }
                    }
                }
            }

            // Architecture Transparency Card: Technical Boundaries
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PLATFORM BOUNDARY & INTEGRITY MATRIX",
                                fontSize = 12.sp,
                                color = Slate100,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Normal App Permissions: Network telemetry, battery state, explicit user-consented location.\n" +
                                   "2. Requires Device Owner: Hardware lockTask Kiosk, uninstall prevention, USB debug restrictions.\n" +
                                   "3. Requires OEM / Zero-Touch: Bootloader persistence and pre-activation Knox locks.\n" +
                                   "4. Technically Impossible & Prohibited: IMEI altering/spoofing, stealth malware surveillance, hidden network packet interception.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Assigned Customer & Agreement
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("FINANCING & CUSTOMER DETAILS", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(customer?.fullName ?: "Unknown Customer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Slate100)
                        Text("Phone: ${customer?.phoneNumber ?: "N/A"}", fontSize = 12.sp, color = Slate400)
                        Text("Agreement: ${agreement?.id ?: "N/A"}", fontSize = 12.sp, color = Slate400)
                        Text("Outstanding Balance: $${agreement?.remainingAmount ?: 0.0}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }

            // Command Audit History
            item {
                Text("DISPATCHED COMMAND HISTORY", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
            }

            if (commands.isEmpty()) {
                item {
                    Text("No commands issued yet for this unit.", color = Slate400, fontSize = 12.sp)
                }
            } else {
                items(commands) { cmd ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(cmd.commandType, color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Nonce: ${cmd.nonce.take(12)}...", color = Slate400, fontSize = 11.sp)
                            }
                            Text(cmd.status, color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

    if (showLockDialog) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            title = { Text("Confirm Device Restriction Lock") },
            text = {
                Column {
                    Text(
                        "Are you sure you want to enforce lock on " + device.manufacturer + " " + device.model + "?",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This will restrict the device according to supported Android device-management policy. Reason:",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = lockReason,
                        onValueChange = { lockReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLockDialog = false
                        onLockDevice(lockReason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Enforce Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Slate900,
            titleContentColor = Slate100,
            textContentColor = Slate200
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Slate400, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            color = Slate100,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
