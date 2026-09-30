package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity
import com.protectfinanceddevices.app.ui.theme.AmberWarning
import com.protectfinanceddevices.app.ui.theme.CrimsonRed
import com.protectfinanceddevices.app.ui.theme.EmeraldGreen
import com.protectfinanceddevices.app.ui.theme.PrimaryBlue
import com.protectfinanceddevices.app.ui.theme.Slate100
import com.protectfinanceddevices.app.ui.theme.Slate400
import com.protectfinanceddevices.app.ui.theme.Slate800
import com.protectfinanceddevices.app.ui.theme.Slate900
import com.protectfinanceddevices.app.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancingScreen(
    agreements: List<AgreementEntity>,
    installments: List<InstallmentEntity>,
    onCreateAgreement: () -> Unit,
    onRecordPayment: (installmentId: String) -> Unit,
    onBack: () -> Unit
) {
    var selectedAgreementId by remember {
        mutableStateOf(
            agreements.firstOrNull()?.id ?: ""
        )
    }

    if (
        selectedAgreementId.isEmpty() ||
        agreements.none { it.id == selectedAgreementId }
    ) {
        selectedAgreementId =
            agreements.firstOrNull()?.id ?: ""
    }

    val currentAgreement =
        agreements.find {
            it.id == selectedAgreementId
        }

    val agreementInstallments =
        installments.filter {
            it.agreementId == currentAgreement?.id
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Financing & Installments",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCreateAgreement
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create Agreement"
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Spacer(
                    modifier = Modifier.height(4.dp)
                )
            }

            item {
                Button(
                    onClick = onCreateAgreement,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(
                        Icons.Default.AttachMoney,
                        contentDescription = null
                    )

                    Text(
                        text = "Create New Agreement",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (agreements.isEmpty()) {

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Slate900
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No financing agreements yet.",
                                color = Slate100,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = "Tap Create New Agreement to add the first financing contract.",
                                color = Slate400
                            )
                        }
                    }
                }

            } else {

                item {
                    Text(
                        text = "AGREEMENTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(
                    items = agreements,
                    key = { it.id }
                ) { agreement ->

                    val selected =
                        agreement.id == selectedAgreementId

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedAgreementId =
                                agreement.id
                        },
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (selected) {
                                    Slate800
                                } else {
                                    Slate900
                                }
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Agreement",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )

                                    Text(
                                        text = agreement.id,
                                        color = Slate100,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                StatusBadge(
                                    status = agreement.status
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "Total",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )

                                    Text(
                                        "%.2f".format(
                                            agreement.totalFinancedAmount
                                        ),
                                        color = Slate100,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column {
                                    Text(
                                        "Remaining",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )

                                    Text(
                                        "%.2f".format(
                                            agreement.remainingAmount
                                        ),
                                        color = CrimsonRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column {
                                    Text(
                                        "Next Due",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )

                                    Text(
                                        agreement.nextDueDate,
                                        color = AmberWarning,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "SELECTED AGREEMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (currentAgreement != null) {

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Slate900
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "AGREEMENT CODE",
                                            fontSize = 11.sp,
                                            color = Slate400,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            currentAgreement.id,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate100
                                        )
                                    }

                                    StatusBadge(
                                        status =
                                            currentAgreement.status
                                    )
                                }

                                Divider(
                                    modifier = Modifier.padding(
                                        vertical = 12.dp
                                    ),
                                    color = Slate800
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            "Total",
                                            color = Slate400,
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            "%.2f".format(
                                                currentAgreement
                                                    .totalFinancedAmount
                                            ),
                                            color = Slate100,
                                            fontSize = 14.sp,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                    }

                                    Column {
                                        Text(
                                            "Down Payment",
                                            color = Slate400,
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            "%.2f".format(
                                                currentAgreement
                                                    .downPayment
                                            ),
                                            color = Slate100,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Column {
                                        Text(
                                            "Remaining",
                                            color = Slate400,
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            "%.2f".format(
                                                currentAgreement
                                                    .remainingAmount
                                            ),
                                            color = CrimsonRed,
                                            fontSize = 14.sp,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                val progress =
                                    if (
                                        currentAgreement
                                            .numberOfInstallments > 0
                                    ) {
                                        (
                                            currentAgreement
                                                .paidInstallments
                                                .toFloat()
                                                /
                                            currentAgreement
                                                .numberOfInstallments
                                                .toFloat()
                                        ).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }

                                LinearProgressIndicator(
                                    progress = {
                                        progress
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = EmeraldGreen,
                                    trackColor = Slate800
                                )

                                Spacer(
                                    modifier = Modifier.height(4.dp)
                                )

                                Text(
                                    text =
                                        "Paid: ${currentAgreement.paidInstallments} of ${currentAgreement.numberOfInstallments}",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "INSTALLMENT SCHEDULE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (agreementInstallments.isEmpty()) {

                    item {
                        Text(
                            text = "No installments found for this agreement.",
                            color = Slate400
                        )
                    }

                } else {

                    items(
                        items = agreementInstallments,
                        key = { it.id }
                    ) { installment ->

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Slate900
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Installment #${installment.installmentNumber}",
                                        fontWeight =
                                            FontWeight.SemiBold,
                                        color = Slate100,
                                        fontSize = 14.sp
                                    )

                                    Text(
                                        "Due: ${installment.dueDate}",
                                        color = Slate400,
                                        fontSize = 12.sp
                                    )

                                    if (
                                        installment.penaltyFee > 0
                                    ) {
                                        Text(
                                            "Penalty: %.2f".format(
                                                installment.penaltyFee
                                            ),
                                            color = CrimsonRed,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment =
                                        Alignment.End
                                ) {
                                    Text(
                                        "%.2f".format(
                                            installment.amount
                                        ),
                                        fontWeight =
                                            FontWeight.Bold,
                                        color = Slate100,
                                        fontSize = 14.sp
                                    )

                                    Text(
                                        installment.status,
                                        color =
                                            when (
                                                installment.status
                                            ) {
                                                "PAID" ->
                                                    EmeraldGreen

                                                "OVERDUE" ->
                                                    CrimsonRed

                                                "WAIVED" ->
                                                    Slate400

                                                else ->
                                                    AmberWarning
                                            },
                                        fontSize = 11.sp,
                                        fontWeight =
                                            FontWeight.Bold
                                    )

                                    if (
                                        installment.status !=
                                        "PAID" &&
                                        installment.status !=
                                        "WAIVED"
                                    ) {
                                        Spacer(
                                            modifier =
                                                Modifier.height(6.dp)
                                        )

                                        Button(
                                            onClick = {
                                                onRecordPayment(
                                                    installment.id
                                                )
                                            },
                                            colors =
                                                ButtonDefaults
                                                    .buttonColors(
                                                        containerColor =
                                                            PrimaryBlue
                                                    ),
                                            shape =
                                                RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "Pay",
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }
        }
    }
}
