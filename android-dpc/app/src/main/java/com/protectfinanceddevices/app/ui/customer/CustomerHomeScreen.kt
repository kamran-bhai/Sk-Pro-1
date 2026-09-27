package com.protectfinanceddevices.app.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHomeScreen(
    device: DeviceEntity?,
    agreement: AgreementEntity?,
    onSwitchToAdmin: () -> Unit,
    onSimulateLock: () -> Unit,
    onViewDeviceStatus: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Financed Device Portal", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Customer Transparency & Agreement Management",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSwitchToAdmin) {
                        Text("Admin Portal", color = PrimaryBlue, fontSize = 12.sp)
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

            // Device Status & Agreement Card
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
                            Column {
                                Text("FINANCING STATUS", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                Text(
                                    text = agreement?.status ?: "ACTIVE",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (agreement?.status == "OVERDUE") AmberWarning else EmeraldGreen
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("In Good Standing", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Slate800)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Next Due Date", color = Slate400, fontSize = 12.sp)
                                Text(agreement?.nextDueDate ?: "2026-10-15", color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Monthly Installment", color = Slate400, fontSize = 12.sp)
                                Text("$${agreement?.installmentAmount ?: 91.66}", color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Remaining", color = Slate400, fontSize = 12.sp)
                                Text("$${agreement?.remainingAmount ?: 733.33}", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = onViewDeviceStatus,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Device Status & Background Sync")
                        }
                    }
                }
            }

            // Customer Transparency: What this app does
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("YOUR PRIVACY & DEVICE MANAGEMENT RIGHTS", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "• Purpose: This device is enrolled in an installment financing plan. The management app enforces payment compliance as agreed in your financing contract.\n" +
                                   "• What we collect: Device model, battery level, online status, SIM carrier name, and diagnostic events.\n" +
                                   "• What we NEVER do: We do not access your personal photos, messages, calls, browsing history, or keystrokes.\n" +
                                   "• Remote Lock: If an installment default occurs beyond the grace period, non-essential device functions may be restricted until payment is confirmed.",
                            color = Slate400,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Support & Emergency Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("NEED ASSISTANCE OR PAYMENT HELP?", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

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
                            Text("Call Financing Support: 1-800-555-FINANCE")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onSimulateLock,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preview Device Restricted Screen")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
