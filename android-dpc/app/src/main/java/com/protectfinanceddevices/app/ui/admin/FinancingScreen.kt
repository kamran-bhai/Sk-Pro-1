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
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancingScreen(
    agreements: List<AgreementEntity>,
    installments: List<InstallmentEntity>,
    onRecordPayment: (installmentId: String) -> Unit,
    onBack: () -> Unit
) {
    var selectedAgreementId by remember { mutableStateOf(agreements.firstOrNull()?.id ?: "") }
    val currentAgreement = agreements.find { it.id == selectedAgreementId } ?: agreements.firstOrNull()
    val agreementInstallments = installments.filter { it.agreementId == currentAgreement?.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financing & Installments", fontWeight = FontWeight.Bold) },
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

            // Contract Summary Card
            if (currentAgreement != null) {
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
                                    Text("AGREEMENT CODE", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                    Text(currentAgreement.id, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate100)
                                }
                                StatusBadge(status = currentAgreement.status)
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Slate800)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Total Financed", color = Slate400, fontSize = 11.sp)
                                    Text("$${currentAgreement.totalFinancedAmount}", color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Remaining", color = Slate400, fontSize = 11.sp)
                                    Text("$${currentAgreement.remainingAmount}", color = CrimsonRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Next Due Date", color = Slate400, fontSize = 11.sp)
                                    Text(currentAgreement.nextDueDate, color = AmberWarning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = {
                                    if (currentAgreement.numberOfInstallments > 0)
                                        currentAgreement.paidInstallments.toFloat() / currentAgreement.numberOfInstallments
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = EmeraldGreen,
                                trackColor = Slate800,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Paid: ${currentAgreement.paidInstallments} of ${currentAgreement.numberOfInstallments} installments",
                                color = Slate400,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Installment Breakdown
            item {
                Text(
                    text = "INSTALLMENT SCHEDULE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontWeight = FontWeight.Bold
                )
            }

            items(agreementInstallments) { ins ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Installment #${ins.installmentNumber}",
                                fontWeight = FontWeight.SemiBold,
                                color = Slate100,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Due: ${ins.dueDate}",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${ins.amount}",
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = ins.status,
                                    color = when (ins.status) {
                                        "PAID" -> EmeraldGreen
                                        "OVERDUE" -> CrimsonRed
                                        else -> AmberWarning
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (ins.status != "PAID") {
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { onRecordPayment(ins.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Pay", fontSize = 12.sp)
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
