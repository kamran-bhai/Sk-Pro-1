package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AttachMoney
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.CustomerEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgreementScreen(
    customers: List<CustomerEntity>,
    devices: List<DeviceEntity>,
    onSaveAgreement: (
        AgreementEntity,
        List<com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity>
    ) -> Unit,
    onBack: () -> Unit
) {
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedDevice by remember { mutableStateOf<DeviceEntity?>(null) }

    var customerMenuExpanded by remember { mutableStateOf(false) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }

    var totalAmount by remember { mutableStateOf("") }
    var downPayment by remember { mutableStateOf("") }
    var numberOfInstallments by remember { mutableStateOf("12") }
    var startDate by remember {
        mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var firstDueDate by remember {
        mutableStateOf(
            LocalDate.now()
                .plusMonths(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
    }
    var gracePeriodDays by remember { mutableStateOf("3") }

    val total = totalAmount.toDoubleOrNull() ?: 0.0
    val down = downPayment.toDoubleOrNull() ?: 0.0
    val installmentCount = numberOfInstallments.toIntOrNull() ?: 0

    val financedAmount = (total - down).coerceAtLeast(0.0)

    val calculatedInstallment =
        if (installmentCount > 0) {
            financedAmount / installmentCount
        } else {
            0.0
        }

    val canSave =
        selectedCustomer != null &&
        selectedDevice != null &&
        total > 0.0 &&
        down >= 0.0 &&
        down < total &&
        installmentCount > 0 &&
        startDate.trim().isNotEmpty() &&
        firstDueDate.trim().isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Financing Agreement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
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
                            Icons.Default.AttachMoney,
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        Text(
                            text = "Financing Agreement",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Create a financing agreement and automatically generate its installment schedule.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Customer",
                style = MaterialTheme.typography.titleSmall
            )

            androidx.compose.foundation.layout.Box(
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
                    if (customers.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text("No customers available")
                            },
                            onClick = {
                                customerMenuExpanded = false
                            }
                        )
                    } else {
                        customers.forEach { customer ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(customer.fullName)
                                        Text(
                                            customer.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCustomer = customer
                                    selectedDevice = null
                                    customerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Device",
                style = MaterialTheme.typography.titleSmall
            )

            val customerDevices =
                devices.filter {
                    selectedCustomer?.id == it.customerId
                }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        deviceMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedCustomer != null
                ) {
                    Text(
                        selectedDevice?.let {
                            "${it.manufacturer} ${it.model}"
                        } ?: "Select Device"
                    )
                }

                DropdownMenu(
                    expanded = deviceMenuExpanded,
                    onDismissRequest = {
                        deviceMenuExpanded = false
                    }
                ) {
                    if (customerDevices.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text("No devices for this customer")
                            },
                            onClick = {
                                deviceMenuExpanded = false
                            }
                        )
                    } else {
                        customerDevices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "${device.manufacturer} ${device.model}"
                                        )
                                        Text(
                                            device.enrollmentStatus,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    selectedDevice = device
                                    deviceMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = totalAmount,
                onValueChange = {
                    totalAmount = it.filter { char ->
                        char.isDigit() || char == '.'
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Total Financed Amount *")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = downPayment,
                onValueChange = {
                    downPayment = it.filter { char ->
                        char.isDigit() || char == '.'
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Down Payment")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = numberOfInstallments,
                onValueChange = {
                    numberOfInstallments =
                        it.filter { char -> char.isDigit() }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Number of Installments *")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = startDate,
                onValueChange = {
                    startDate = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Start Date")
                },
                supportingText = {
                    Text("Format: YYYY-MM-DD")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = firstDueDate,
                onValueChange = {
                    firstDueDate = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("First Due Date")
                },
                supportingText = {
                    Text("Format: YYYY-MM-DD")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = gracePeriodDays,
                onValueChange = {
                    gracePeriodDays =
                        it.filter { char -> char.isDigit() }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Grace Period (Days)")
                },
                singleLine = true
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Agreement Summary",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Financed Amount: %.2f".format(financedAmount)
                    )

                    Text(
                        "Estimated Installment: %.2f".format(
                            calculatedInstallment
                        )
                    )

                    Text(
                        "Installments: $installmentCount"
                    )

                    Text(
                        "Grace Period: ${gracePeriodDays.toIntOrNull() ?: 0} days"
                    )
                }
            }

            Button(
                onClick = {
                    val customer = selectedCustomer ?: return@Button
                    val device = selectedDevice ?: return@Button

                    val count = numberOfInstallments
                        .toIntOrNull()
                        ?.coerceAtLeast(1)
                        ?: return@Button

                    val totalValue =
                        totalAmount.toDoubleOrNull()
                            ?: return@Button

                    val downValue =
                        downPayment.toDoubleOrNull()
                            ?: 0.0

                    val financed =
                        (totalValue - downValue)
                            .coerceAtLeast(0.0)

                    val standardInstallment =
                        financed / count

                    val agreementId =
                        UUID.randomUUID().toString()

                    val agreement = AgreementEntity(
                        id = agreementId,
                        customerId = customer.id,
                        deviceId = device.id,
                        totalFinancedAmount = totalValue,
                        downPayment = downValue,
                        remainingAmount = financed,
                        installmentAmount = standardInstallment,
                        numberOfInstallments = count,
                        paidInstallments = 0,
                        remainingInstallments = count,
                        startDate = startDate.trim(),
                        nextDueDate = firstDueDate.trim(),
                        gracePeriodDays =
                            gracePeriodDays.toIntOrNull()
                                ?.coerceAtLeast(0)
                                ?: 0,
                        status = "ACTIVE"
                    )

                    val installments =
                        buildInstallments(
                            agreementId = agreementId,
                            firstDueDate = firstDueDate.trim(),
                            amount = financed,
                            count = count
                        )

                    onSaveAgreement(
                        agreement,
                        installments
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Text("Create Agreement")
            }

            if (!canSave) {
                Text(
                    text = "Customer, Device, valid amount and installment details are required.",
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

private fun buildInstallments(
    agreementId: String,
    firstDueDate: String,
    amount: Double,
    count: Int
): List<com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity> {

    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    val firstDate = try {
        LocalDate.parse(
            firstDueDate,
            formatter
        )
    } catch (_: Exception) {
        LocalDate.now().plusMonths(1)
    }

    val totalCents =
        kotlin.math.round(amount * 100.0).toLong()

    val baseCents =
        if (count > 0) {
            totalCents / count
        } else {
            0L
        }

    val remainderCents =
        if (count > 0) {
            totalCents % count
        } else {
            0L
        }

    return (1..count).map { number ->

        val cents =
            if (number == count) {
                baseCents + remainderCents
            } else {
                baseCents
            }

        val installmentAmount =
            cents.toDouble() / 100.0

        com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity(
            id = UUID.randomUUID().toString(),
            agreementId = agreementId,
            installmentNumber = number,
            dueDate = firstDate
                .plusMonths((number - 1).toLong())
                .format(formatter),
            amount = installmentAmount,
            penaltyFee = 0.0,
            status = "PENDING",
            paidDate = null
        )
    }
}
