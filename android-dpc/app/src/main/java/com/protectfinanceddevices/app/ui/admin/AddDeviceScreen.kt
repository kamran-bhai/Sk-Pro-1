package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protectfinanceddevices.app.core.storage.entities.CustomerEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import java.util.UUID

@Composable
fun AddDeviceScreen(
    customers: List<CustomerEntity>,
    onSaveDevice: (DeviceEntity) -> Unit,
    onCreateCustomer: () -> Unit,
    onBack: () -> Unit
) {

    var selectedCustomer by remember {
        mutableStateOf<CustomerEntity?>(null)
    }

    var customerMenuExpanded by remember {
        mutableStateOf(false)
    }

    var deviceId by remember {
        mutableStateOf(
            "DEV-${UUID.randomUUID().toString().take(8).uppercase()}"
        )
    }

    var manufacturer by remember {
        mutableStateOf("")
    }

    var model by remember {
        mutableStateOf("")
    }

    var androidVersion by remember {
        mutableStateOf("Unknown")
    }

    var enrollmentPublicKey by remember {
        mutableStateOf("")
    }

    var batteryText by remember {
        mutableStateOf("0")
    }

    var simCarrier by remember {
        mutableStateOf("")
    }

    var isOnline by remember {
        mutableStateOf(false)
    }

    var usbDebuggingActive by remember {
        mutableStateOf(false)
    }

    var managementMode by remember {
        mutableStateOf("UNMANAGED")
    }

    var managementMenuExpanded by remember {
        mutableStateOf(false)
    }

    var enrollmentStatus by remember {
        mutableStateOf("OFFLINE")
    }

    var statusMenuExpanded by remember {
        mutableStateOf(false)
    }

    val canSave =
        selectedCustomer != null &&
        deviceId.trim().isNotEmpty() &&
        manufacturer.trim().isNotEmpty() &&
        model.trim().isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Add Device")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        Text(
                            text = "Device Information",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "This creates a local device record. Actual Android Device Owner provisioning is handled separately.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (customers.isEmpty()) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text(
                            text = "No customers found",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        Text(
                            text = "Create a customer before adding a device."
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Button(
                            onClick = onCreateCustomer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create Customer")
                        }
                    }
                }

            } else {

                Text(
                    text = "Customer *",
                    style = MaterialTheme.typography.labelLarge
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedButton(
                        onClick = {
                            customerMenuExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedCustomer?.fullName
                                ?: "Select Customer"
                        )
                    }

                    DropdownMenu(
                        expanded = customerMenuExpanded,
                        onDismissRequest = {
                            customerMenuExpanded = false
                        }
                    ) {

                        customers.forEach { customer ->

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = customer.fullName
                                        )

                                        Text(
                                            text = customer.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCustomer = customer
                                    customerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = deviceId,
                onValueChange = {
                    deviceId = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Device ID *")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = manufacturer,
                onValueChange = {
                    manufacturer = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Manufacturer *")
                },
                placeholder = {
                    Text("Example: Samsung")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Model *")
                },
                placeholder = {
                    Text("Example: Galaxy A15")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = androidVersion,
                onValueChange = {
                    androidVersion = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Android Version")
                },
                placeholder = {
                    Text("Example: Android 14")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = enrollmentPublicKey,
                onValueChange = {
                    enrollmentPublicKey = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Enrollment Public Key")
                },
                supportingText = {
                    Text("Optional")
                },
                minLines = 2,
                maxLines = 4
            )

            OutlinedTextField(
                value = batteryText,
                onValueChange = { value ->
                    if (
                        value.isEmpty() ||
                        value.all { it.isDigit() }
                    ) {
                        batteryText = value
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Battery Percent")
                },
                supportingText = {
                    Text("0 - 100")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = simCarrier,
                onValueChange = {
                    simCarrier = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("SIM Carrier")
                },
                placeholder = {
                    Text("Example: GP / Robi / Banglalink")
                },
                singleLine = true
            )

            Text(
                text = "Management Mode",
                style = MaterialTheme.typography.labelLarge
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick = {
                        managementMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(managementMode)
                }

                DropdownMenu(
                    expanded = managementMenuExpanded,
                    onDismissRequest = {
                        managementMenuExpanded = false
                    }
                ) {

                    listOf(
                        "UNMANAGED",
                        "DEVICE_ADMIN",
                        "DEVICE_OWNER"
                    ).forEach { mode ->

                        DropdownMenuItem(
                            text = {
                                Text(mode)
                            },
                            onClick = {
                                managementMode = mode
                                managementMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Enrollment Status",
                style = MaterialTheme.typography.labelLarge
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick = {
                        statusMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(enrollmentStatus)
                }

                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = {
                        statusMenuExpanded = false
                    }
                ) {

                    listOf(
                        "OFFLINE",
                        "ACTIVE",
                        "LOCKED",
                        "OVERDUE",
                        "SUSPENDED",
                        "COMPLETED"
                    ).forEach { status ->

                        DropdownMenuItem(
                            text = {
                                Text(status)
                            },
                            onClick = {
                                enrollmentStatus = status
                                statusMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Device State",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text = "Online",
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = isOnline,
                            onCheckedChange = {
                                isOnline = it
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text = "USB Debugging Active",
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = usbDebuggingActive,
                            onCheckedChange = {
                                usbDebuggingActive = it
                            }
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Button(
                onClick = {

                    val battery = batteryText
                        .toIntOrNull()
                        ?.coerceIn(0, 100)
                        ?: 0

                    val device = DeviceEntity(
                        id = deviceId.trim(),
                        customerId = selectedCustomer!!.id,
                        model = model.trim(),
                        manufacturer = manufacturer.trim(),
                        androidVersion = androidVersion.trim()
                            .ifBlank { "Unknown" },
                        enrollmentStatus = enrollmentStatus,
                        managementMode = managementMode,
                        enrollmentPublicKey =
                            enrollmentPublicKey.trim(),
                        lastSeenTimestamp =
                            if (isOnline) {
                                System.currentTimeMillis()
                            } else {
                                0L
                            },
                        batteryPercent = battery,
                        isOnline = isOnline,
                        simCarrier = simCarrier.trim()
                            .ifBlank { null },
                        usbDebuggingActive =
                            usbDebuggingActive
                    )

                    onSaveDevice(device)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {

                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Text("Save Device")
            }

            if (!canSave) {

                Text(
                    text = "Customer, Device ID, Manufacturer and Model are required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )
        }
    }
}
