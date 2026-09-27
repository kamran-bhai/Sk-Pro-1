package com.protectfinanceddevices.app.ui.admin

import androidx.compose.foundation.clickable
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
import com.protectfinanceddevices.app.core.storage.entities.CustomerEntity
import com.protectfinanceddevices.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    customers: List<CustomerEntity>,
    onCustomerClick: (CustomerEntity) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financing Customers (${customers.size})", fontWeight = FontWeight.Bold) },
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
            item { Spacer(modifier = Modifier.height(6.dp)) }

            items(customers) { customer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCustomerClick(customer) },
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = customer.fullName,
                                fontWeight = FontWeight.Bold,
                                color = Slate100,
                                fontSize = 16.sp
                            )
                            Text(
                                text = customer.id,
                                color = PrimaryBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Slate400, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = customer.phoneNumber, color = Slate400, fontSize = 13.sp)
                        }

                        if (!customer.email.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = Slate400, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = customer.email, color = Slate400, fontSize = 13.sp)
                            }
                        }

                        if (!customer.nationalIdMasked.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Identity: ${customer.nationalIdMasked}", color = Slate400, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
