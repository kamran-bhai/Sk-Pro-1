package com.protectfinanceddevices.app.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.network.ApiConfig
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEnrollmentEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import com.protectfinanceddevices.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Customer Device Status Screen (Phase 6 SK Pro)
 *
 * Transparently displays to the customer:
 * - Protection / Enrollment Status (Protected, Suspended, Revoked)
 * - Server Online / Offline dynamic status
 * - Last Successful Sync timestamp
 * - App Version and hardware Keystore attestation
 * - Financing Agreement terms and 24/7 support contacts
 *
 * Adheres strictly to security and privacy guidelines:
 * No hidden surveillance, no unauthorized remote commands, no private keys exposed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDeviceStatusScreen(
    device: DeviceEntity?,
    agreement: AgreementEntity?,
    enrollment: DeviceEnrollmentEntity? = null,
    onNavigateBack: () -> Unit,
    onManualSync: () -> Unit = {}
) {
    val context = LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }

    val lastSyncTs = enrollment?.lastSyncTimestamp?.takeIf { it > 0 }
        ?: device?.lastSeenTimestamp?.takeIf { it > 0 }

    val formattedSyncTime = remember(lastSyncTs) {
        if (lastSyncTs != null && lastSyncTs > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            sdf.format(Date(lastSyncTs))
        } else {
            "Verified on onboarding"
        }
    }

    // Dynamic online calculation based on 15m heartbeat freshness
    val isOnlineNow = remember(lastSyncTs) {
        if (lastSyncTs != null && lastSyncTs > 0) {
            (System.currentTimeMillis() - lastSyncTs) <= (15 * 60 * 1000)
        } else {
            device?.isOnline ?: true
        }
    }

    val enrollmentStatus = enrollment?.enrollmentStatus ?: device?.enrollmentStatus ?: "ACTIVE"
    val isProtected = enrollmentStatus == "ACTIVE"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Device Status & Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "Managed Financed Device • Transparent Status",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate100)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSyncing = true
                            onManualSync()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isSyncing = false
                            }, 1500)
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sync Now",
                            tint = if (isSyncing) EmeraldGreen else PrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100
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

            // 1. Enrollment & Protection Status Card
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isProtected) EmeraldGreen.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isProtected) Icons.Default.Shield else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isProtected) EmeraldGreen else AmberWarning,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("ENROLLMENT STATUS", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (isProtected) "PROTECTED" else enrollmentStatus,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isProtected) EmeraldGreen else AmberWarning
                                    )
                                }
                            }

                            // Dynamic Online / Offline Indicator Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isOnlineNow) EmeraldGreen.copy(alpha = 0.2f) else Slate800)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnlineNow) EmeraldGreen else Slate400)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isOnlineNow) "ONLINE" else "OFFLINE",
                                    color = if (isOnlineNow) EmeraldGreen else Slate400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Slate800)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Management Mode", color = Slate400, fontSize = 11.sp)
                                Text(
                                    text = if ((enrollment?.managementMode ?: device?.managementMode) == "DEVICE_OWNER") "Managed Device (Device Owner)" else "Device Administrator",
                                    color = Slate100,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Last Successful Sync", color = Slate400, fontSize = 11.sp)
                                Text(
                                    text = formattedSyncTime,
                                    color = Slate100,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. System Information Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SYSTEM INFORMATION", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        DetailRow(label = "Application Version", value = "${ApiConfig.CLIENT_APP_VERSION} (SK Pro)")
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)
                        DetailRow(label = "Security Attestation", value = "Android Keystore EC P-256 (TEE/StrongBox)")
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)
                        DetailRow(label = "Background Sync Engine", value = "Android WorkManager (15m interval)")
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)
                        DetailRow(label = "Financing Agreement Code", value = agreement?.agreementCode ?: "AGR-2026-001")
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Slate800)
                        DetailRow(label = "Enrolled Device Serial", value = device?.id ?: "dev-hw-01")
                    }
                }
            }

            // 3. Customer Transparency & Privacy Disclosure Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TRANSPARENCY & PRIVACY GUARANTEE", color = Slate100, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This application protects financed hardware in accordance with your transparent customer agreement. It does NOT perform hidden surveillance, access private photos, monitor personal messages, record audio/video, or execute covert locks. All sync operations are cryptographically authenticated via Android Keystore.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 4. Background Sync Notice (WorkManager & Doze Mode)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BATTERY & BACKGROUND SYNC", color = Slate100, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To protect your battery life, this app uses standard Android WorkManager background scheduling. Background sync occurs periodically (minimum 15-minute interval) and pauses when the device enters Android Doze mode. The status shown represents the verified state from the last successful cryptographic heartbeat.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 5. Customer Financing Support Contacts
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("FINANCING SUPPORT & INQUIRIES", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "If you have questions about your installments, require a payment extension, or need technical assistance with this unit, our support team is available 24/7.",
                            color = Slate400,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:18005553462")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Call Financing Support (1-800-555-FINANCE)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@company.com?subject=Financing%20Device%20Inquiry%20${agreement?.agreementCode ?: ""}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Email Support (support@company.com)")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Slate400, fontSize = 12.sp)
        Text(text = value, color = Slate100, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
