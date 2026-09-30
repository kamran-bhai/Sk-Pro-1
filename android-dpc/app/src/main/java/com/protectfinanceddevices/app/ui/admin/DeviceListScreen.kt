package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    devices: List<DeviceEntity>,
    onDeviceClick: (String) -> Unit,
    onAddDevice: () -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filterOptions = listOf("ALL", "ACTIVE", "OVERDUE", "LOCKED", "OFFLINE")

    val filteredDevices = devices.filter { device ->
        val matchesSearch = device.model.contains(searchQuery, ignoreCase = true) ||
                device.manufacturer.contains(searchQuery, ignoreCase = true) ||
                device.id.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "ALL" -> true
            "ACTIVE" -> device.enrollmentStatus == "ACTIVE"
            "OVERDUE" -> device.enrollmentStatus == "OVERDUE"
            "LOCKED" -> device.enrollmentStatus == "LOCKED"
            "OFFLINE" -> !device.isOnline
            else -> true
        }

        matchesSearch && matchesFilter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financed Devices (${filteredDevices.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddDevice) {
                        Icon(
                            Icons.Default.AddToQueue,
                            contentDescription = "Add Device"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100,
                    navigationIconContentColor = Slate100,
                    actionIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate950
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAddDevice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AddToQueue,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Device")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by device ID, model, or brand...", color = Slate400) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Slate700,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Slate100,
                            containerColor = Slate800,
                            labelColor = Slate400
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No financed devices matching criteria.",
                            color = Slate400
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(onClick = onAddDevice) {
                            Icon(
                                Icons.Default.AddToQueue,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Device")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDevices) { device ->
                        DeviceCard(device = device, onClick = { onDeviceClick(device.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: DeviceEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${device.manufacturer} ${device.model}",
                        fontWeight = FontWeight.Bold,
                        color = Slate100,
                        fontSize = 15.sp
                    )
                }
                StatusBadge(status = device.enrollmentStatus)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ID: ${device.id}", color = Slate400, fontSize = 12.sp)
                    Text("Mode: ${device.managementMode}", color = Slate400, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (device.isOnline) "● Online" else "○ Offline",
                        color = if (device.isOnline) EmeraldGreen else Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Battery: ${device.batteryPercent}%",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
