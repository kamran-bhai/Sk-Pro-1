package com.protectfinanceddevices.app.ui.lock

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectfinanceddevices.app.ui.theme.*

@Composable
fun DeviceRestrictedScreen(
    agreementId: String = "AGR-2026-002",
    businessName: String = "Financed Mobile Solutions Inc.",
    outstandingBalance: Double = 949.00,
    supportPhoneNumber: String = "1-800-555-0199",
    onDismissPreview: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Lock Indicator Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(CrimsonRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = CrimsonRed,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DEVICE TEMPORARILY RESTRICTED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = businessName,
                fontSize = 13.sp,
                color = Slate400,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notice Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Agreement ID:", color = Slate400, fontSize = 12.sp)
                        Text(agreementId, color = Slate100, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Outstanding Balance:", color = Slate400, fontSize = 12.sp)
                        Text("$$outstandingBalance", color = CrimsonRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Policy Reason:", color = Slate400, fontSize = 12.sp)
                        Text("Overdue Installment Default", color = AmberWarning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Slate800)

                    Text(
                        text = "This device has been restricted pursuant to your financing contract. Once your installment payment is recorded or authorized by customer support, this unit will be automatically unlocked remotely.",
                        color = Slate400,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact & Emergency Actions
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$supportPhoneNumber")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Contact Support ($supportPhoneNumber)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:911")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Emergency, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Emergency Call (911 / 112)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onDismissPreview) {
                Text("Exit Restriction Preview (Dev Mode)", color = Slate400, fontSize = 11.sp)
            }
        }
    }
}
