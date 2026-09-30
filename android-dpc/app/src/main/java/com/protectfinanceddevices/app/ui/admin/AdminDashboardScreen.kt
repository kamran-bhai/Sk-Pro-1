package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.storage.entities.AlertEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    devices: List<DeviceEntity>,
    alerts: List<AlertEntity>,
    onNavigateToDeviceDetails: (String) -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToFinancing: () -> Unit,

    // Phase 1 menu actions
    onNewCustomer: () -> Unit,
    onCustomerList: () -> Unit,
    onAddDevice: () -> Unit,
    onKeyManagement: () -> Unit,
    onHistory: () -> Unit,
    onSupport: () -> Unit,
    onProfile: () -> Unit,
    onTransferPoint: () -> Unit,
    onRetailerList: () -> Unit
) {
    val totalDevices = devices.size
    val activeDevices = devices.count { it.enrollmentStatus == "ACTIVE" }
    val overdueDevices = devices.count { it.enrollmentStatus == "OVERDUE" }
    val lockedDevices = devices.count { it.enrollmentStatus == "LOCKED" }
    val unackAlerts = alerts.count { !it.isAcknowledged }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SK PRO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Device & Finance Management",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAlerts) {
                        BadgedBox(
                            badge = {
                                if (unackAlerts > 0) {
                                    Badge(containerColor = CrimsonRed) {
                                        Text("$unackAlerts")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Alerts"
                            )
                        }
                    }

                    IconButton(onClick = onProfile) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Profile"
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ---------------------------------------------------------
            // MAIN MENU
            // ---------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "MAIN MENU",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuCard(
                            title = "New Customer",
                            icon = Icons.Default.PersonAdd,
                            modifier = Modifier.weight(1f),
                            onClick = onNewCustomer
                        )

                        MenuCard(
                            title = "Customer List",
                            icon = Icons.Default.People,
                            modifier = Modifier.weight(1f),
                            onClick = onCustomerList
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuCard(
                            title = "Add Device",
                            icon = Icons.Default.AddToQueue,
                            modifier = Modifier.weight(1f),
                            onClick = onAddDevice
                        )

                        MenuCard(
                            title = "Key Management",
                            icon = Icons.Default.Key,
                            modifier = Modifier.weight(1f),
                            onClick = onKeyManagement
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuCard(
                            title = "History",
                            icon = Icons.Default.History,
                            modifier = Modifier.weight(1f),
                            onClick = onHistory
                        )

                        MenuCard(
                            title = "Support",
                            icon = Icons.Default.SupportAgent,
                            modifier = Modifier.weight(1f),
                            onClick = onSupport
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MenuCard(
                            title = "Transfer Point",
                            icon = Icons.Default.SwapHoriz,
                            modifier = Modifier.weight(1f),
                            onClick = onTransferPoint
                        )

                        MenuCard(
                            title = "Retailer List",
                            icon = Icons.Default.Store,
                            modifier = Modifier.weight(1f),
                            onClick = onRetailerList
                        )
                    }
                }
            }

            // ---------------------------------------------------------
            // PORTFOLIO OVERVIEW
            // ---------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "PORTFOLIO OVERVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "TOTAL DEVICES",
                        value = "$totalDevices",
                        icon = Icons.Default.PhoneAndroid,
                        accentColor = PrimaryBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToDevices
                    )

                    MetricCard(
                        title = "ACTIVE",
                        value = "$activeDevices",
                        icon = Icons.Default.CheckCircle,
                        accentColor = EmeraldGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToDevices
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "OVERDUE",
                        value = "$overdueDevices",
                        icon = Icons.Default.Warning,
                        accentColor = AmberWarning,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToFinancing
                    )

                    MetricCard(
                        title = "LOCKED",
                        value = "$lockedDevices",
                        icon = Icons.Default.Lock,
                        accentColor = CrimsonRed,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToDevices
                    )
                }
            }

            // ---------------------------------------------------------
            // ALERT
            // ---------------------------------------------------------
            if (unackAlerts > 0) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAlerts() },
                        colors = CardDefaults.cardColors(
                            containerColor = CrimsonDark.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = CrimsonRed
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$unackAlerts Critical Alert(s) Require Action",
                                    color = Slate100,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )

                                Text(
                                    text = "Overdue and device events detected",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )
                            }

                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Slate400
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // RECENT DEVICES
            // ---------------------------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT FINANCED UNITS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = onNavigateToDevices) {
                        Text(
                            text = "View All ($totalDevices)",
                            color = PrimaryBlue,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(devices.take(4)) { device ->
                DeviceRowItem(
                    device = device,
                    onClick = {
                        onNavigateToDeviceDetails(device.id)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------
// MAIN MENU CARD
// ---------------------------------------------------------------------

@Composable
fun MenuCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(82.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Slate900
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = PrimaryBlue,
                modifier = Modifier.size(25.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = Slate100,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------
// METRIC CARD
// ---------------------------------------------------------------------

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Slate900
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
        }
    }
}

// ---------------------------------------------------------------------
// DEVICE ROW
// ---------------------------------------------------------------------

@Composable
fun DeviceRowItem(
    device: DeviceEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Slate900
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate800),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = PrimaryBlue
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${device.manufacturer} ${device.model}",
                    color = Slate100,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Text(
                    text = "ID: ${device.id} • ${device.managementMode}",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }

            StatusBadge(
                status = device.enrollmentStatus
            )
        }
    }
}

// ---------------------------------------------------------------------
// STATUS BADGE
// ---------------------------------------------------------------------

@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "ACTIVE" -> EmeraldGreen.copy(alpha = 0.2f) to EmeraldGreen
        "OVERDUE" -> AmberWarning.copy(alpha = 0.2f) to AmberWarning
        "LOCKED" -> CrimsonRed.copy(alpha = 0.2f) to CrimsonRed
        else -> Slate700 to Slate200
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp
            )
    ) {
        Text(
            text = status,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
