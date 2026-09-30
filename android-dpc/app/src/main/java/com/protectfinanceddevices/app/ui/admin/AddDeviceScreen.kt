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
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
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

    var manufacturer by remember {
        mutableStateOf("")
    }

    var model by remember {
        mutableStateOf("")
    }

    var androidVersion by remember {
        mutableStateOf("")
    }

    var enrollmentPublicKey by remember {
        mutableStateOf("")
    }

    var batteryPercent by remember {
        mutableStateOf("0")
    }

    var simCarrier by remember {
        mutableStateOf("")
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

    var isOnline by remember {
        mutableStateOf(false)
    }

    var usbDebuggingActive by remember {
        mutableStateOf(false)
    }

    val canSave =
        selectedCustomer != null &&
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
                        text = "Create a device record for an existing customer.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Customer",
                style = MaterialTheme.typography.titleSmall
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
                        text = selectedCustomer?.fullName
                            ?: "Select Customer"
                    )
                }

                DropdownMenu(
                    expanded = customerMenuExpanded,
                    onDismissRequest = {
                        customerMenuExpanded = false
                    }
                ) {

                    if (customers.isEmpty()) {

                        DropdownMenuItem(
                            text = {
                                Text("No customers found")
                            },
                            onClick = {
                                customerMenuExpanded = false
                                onCreateCustomer()
                            }
                        )

                    } else {

                        customers.forEach { customer ->

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(customer.fullName)

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

            if (customers.isEmpty()) {

                Button(
                    onClick = onCreateCustomer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Customer First")
                }
            }

            OutlinedTextField(
                value = manufacturer,
                onValueChange = {
                    manufacturer = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Manufacturer *")
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
                    Text("Device Model *")
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
                singleLine = true
            )

            OutlinedTextField(
                value = batteryPercent,
                onValueChange = {
                    batteryPercent = it.filter { char ->
                        char.isDigit()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Battery Percentage")
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
                singleLine = true
            )

            Text(
                text = "Management Mode",
                style = MaterialTheme.typography.titleSmall
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

                    val modes = listOf(
                        "UNMANAGED",
                        "DEVICE_ADMIN",
                        "DEVICE_OWNER"
                    )

                    modes.forEach { mode ->

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
                style = MaterialTheme.typography.titleSmall
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

                    val statuses = listOf(
                        "OFFLINE",
                        "ACTIVE",
                        "LOCKED",
                        "OVERDUE",
                        "SUSPENDED",
                        "COMPLETED"
                    )

                    statuses.forEach { status ->

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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        text = "Device State",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        Column {
                            Text("Online")

                            Text(
                                text = if (isOnline) {
                                    "Device is online"
                                } else {
                                    "Device is offline"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Switch(
                            checked = isOnline,
                            onCheckedChange = {
                                isOnline = it
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        Column {
                            Text("USB Debugging")

                            Text(
                                text = if (usbDebuggingActive) {
                                    "Enabled"
                                } else {
                                    "Disabled"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

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

                    val parsedBattery =
                        batteryPercent
                            .toIntOrNull()
                            ?.coerceIn(0, 100)
                            ?: 0

                    val device = DeviceEntity(
                        id = UUID.randomUUID().toString(),
                        customerId = selectedCustomer!!.id,
                        model = model.trim(),
                        manufacturer = manufacturer.trim(),
                        androidVersion = androidVersion.trim(),
                        enrollmentStatus = enrollmentStatus,
                        managementMode = managementMode,
                        enrollmentPublicKey = enrollmentPublicKey.trim(),
                        lastSeenTimestamp = System.currentTimeMillis(),
                        batteryPercent = parsedBattery,
                        isOnline = isOnline,
                        simCarrier = simCarrier.trim().ifBlank {
                            null
                        },
                        usbDebuggingActive = usbDebuggingActive
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
                    text = "Customer, Manufacturer and Device Model are required.",
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
