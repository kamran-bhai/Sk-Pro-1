package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDeviceOwner: Boolean,
    isDeviceAdminActive: Boolean,
    isKeystoreHardwareBacked: Boolean,
    publicKeySnippet: String,
    onBack: () -> Unit
) {
    var usbDebuggingBlocked by remember { mutableStateOf(true) }
    var factoryResetBlocked by remember { mutableStateOf(true) }
    var uninstallBlocked by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Policies & DPC Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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

            // Device Administration Mode
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DEVICE MANAGEMENT PRIVILEGE", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Device Owner Authority", color = Slate100, fontSize = 14.sp)
                            Text(
                                text = if (isDeviceOwner) "ACTIVE" else "NOT PROVISIONED",
                                color = if (isDeviceOwner) EmeraldGreen else AmberWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Device Admin Receiver", color = Slate100, fontSize = 14.sp)
                            Text(
                                text = if (isDeviceAdminActive) "ACTIVE" else "INACTIVE",
                                color = if (isDeviceAdminActive) EmeraldGreen else CrimsonRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "To enable full Device Owner mode on a clean unit: 'dpm set-device-owner com.protectfinanceddevices.app/.core.dpc.FinancedDeviceAdminReceiver' via ADB or Zero-Touch QR.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Cryptographic Hardware Attestation
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("HARDWARE KEYSTORE SECURITY", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Hardware-Backed Storage (TEE/StrongBox)", color = Slate100, fontSize = 13.sp)
                            Text(
                                text = if (isKeystoreHardwareBacked) "YES (TEE)" else "SOFTWARE / EMULATOR",
                                color = if (isKeystoreHardwareBacked) EmeraldGreen else AmberWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Curve: NIST P-256 (secp256r1) ECDSA", color = Slate400, fontSize = 12.sp)
                        Text("Public Key: $publicKeySnippet", color = Slate400, fontSize = 11.sp)
                    }
                }
            }

            // Enterprise Restrictions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("MANAGED RESTRICTIONS (Requires Device Owner)", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block USB File Transfer & Debug", color = Slate100, fontSize = 13.sp)
                                Text("Prevents unauthorized ADB extraction", color = Slate400, fontSize = 11.sp)
                            }
                            Switch(
                                checked = usbDebuggingBlocked,
                                onCheckedChange = { usbDebuggingBlocked = it },
                                enabled = isDeviceOwner
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block Factory Reset via Settings", color = Slate100, fontSize = 13.sp)
                                Text("Disallows reset option in system menu", color = Slate400, fontSize = 11.sp)
                            }
                            Switch(
                                checked = factoryResetBlocked,
                                onCheckedChange = { factoryResetBlocked = it },
                                enabled = isDeviceOwner
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block Application Uninstallation", color = Slate100, fontSize = 13.sp)
                                Text("Enforces package persistence during agreement", color = Slate400, fontSize = 11.sp)
                            }
                            Switch(
                                checked = uninstallBlocked,
                                onCheckedChange = { uninstallBlocked = it },
                                enabled = isDeviceOwner
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
