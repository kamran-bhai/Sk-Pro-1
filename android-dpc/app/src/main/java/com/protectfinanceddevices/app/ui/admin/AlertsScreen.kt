package com.protectfinanceddevices.app.ui.admin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.storage.entities.AlertEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    alerts: List<AlertEntity>,
    onAcknowledgeAlert: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Financing Alerts (${alerts.size})", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (alerts.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No active alerts. All devices in compliance.", color = Slate400)
                    }
                }
            } else {
                items(alerts) { alert ->
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
                                    Icon(
                                        when (alert.severity) {
                                            "CRITICAL" -> Icons.Default.Error
                                            "WARNING" -> Icons.Default.Warning
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when (alert.severity) {
                                            "CRITICAL" -> CrimsonRed
                                            "WARNING" -> AmberWarning
                                            else -> PrimaryBlue
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = alert.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate100,
                                        fontSize = 14.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = alert.severity,
                                        color = when (alert.severity) {
                                            "CRITICAL" -> CrimsonRed
                                            "WARNING" -> AmberWarning
                                            else -> PrimaryBlue
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = alert.details,
                                color = Slate400,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            if (alert.deviceId != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Target Device: ${alert.deviceId}",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (!alert.isAcknowledged) {
                                    Button(
                                        onClick = { onAcknowledgeAlert(alert.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Acknowledge", fontSize = 11.sp, color = Slate100)
                                    }
                                } else {
                                    Text("Acknowledged", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
