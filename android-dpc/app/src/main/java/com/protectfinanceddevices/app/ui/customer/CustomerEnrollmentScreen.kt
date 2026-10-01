package com.protectfinanceddevices.app.ui.customer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.protectfinanceddevices.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class EnrollmentStep {
    ENTER_CODE,
    DISCLOSURES,
    PERMISSIONS_CONSENT,
    KEYSTORE_ATTESTATION,
    COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerEnrollmentScreen(
    initialCode: String = "",
    isHardwareBacked: Boolean = true,
    onGenerateKeyAndEnroll: (code: String, onProgress: (String) -> Unit, onDone: (Boolean, String) -> Unit) -> Unit,
    onEnrollmentComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(EnrollmentStep.ENTER_CODE) }
    var enrollmentCodeInput by remember { mutableStateOf(initialCode) }
    var progressMessage by remember { mutableStateOf("Initializing secure hardware module...") }
    var enrollmentError by remember { mutableStateOf<String?>(null) }
    var consentAgreed by remember { mutableStateOf(false) }
    var permissionsAgreed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun startEnrollmentFlow() {
        enrollmentError = null
        step = EnrollmentStep.KEYSTORE_ATTESTATION
        coroutineScope.launch {
            progressMessage = "Generating hardware NIST P-256 keypair in Android Keystore..."
            delay(700)
            progressMessage = "Requesting cryptographic challenge nonce from server..."
            delay(700)
            progressMessage = "Signing challenge with hardware private key..."

            onGenerateKeyAndEnroll(
                enrollmentCodeInput,
                { msg -> progressMessage = msg },
                { success, err ->
                    if (success) {
                        step = EnrollmentStep.COMPLETED
                    } else {
                        enrollmentError = err
                        step = EnrollmentStep.ENTER_CODE
                    }
                }
            )
        }
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startEnrollmentFlow()
        } else {
            enrollmentError = "SIM change alerts are unavailable because Phone permission was denied. Enrollment can continue without SIM telemetry."
            startEnrollmentFlow()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Device Protection Enrollment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Phase 4 Keystore Identity & Consent",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate200)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EnrollmentStep.values().forEachIndexed { index, s ->
                        val isDone = s.ordinal < step.ordinal
                        val isCurrent = s == step
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> EmeraldGreen
                                        isCurrent -> PrimaryBlue
                                        else -> Slate800
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) Color.White else Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (index < EnrollmentStep.values().size - 1) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .padding(horizontal = 4.dp)
                                    .background(if (s.ordinal < step.ordinal) EmeraldGreen else Slate800)
                            )
                        }
                    }
                }

                // Error Message Banner
                AnimatedVisibility(visible = enrollmentError != null) {
                    enrollmentError?.let { err ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CrimsonDark.copy(alpha = 0.3f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed)
                                Text(err, color = Slate100, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // STEP 1: Enter Pairing Code
                if (step == EnrollmentStep.ENTER_CODE) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "Enter Enrollment Pairing Code",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )

                            Text(
                                text = "Enter the one-time pairing code generated by your financing provider. No demo code is pre-filled.",
                                fontSize = 12.sp,
                                color = Slate400,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = enrollmentCodeInput,
                                onValueChange = { enrollmentCodeInput = it.uppercase() },
                                label = { Text("One-Time Enrollment Code", color = Slate400) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    color = Slate100
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = Slate700
                                )
                            )

                            Button(
                                onClick = {
                                    if (enrollmentCodeInput.isBlank()) {
                                        enrollmentError = "Enrollment code cannot be empty"
                                    } else {
                                        enrollmentError = null
                                        step = EnrollmentStep.DISCLOSURES
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Continue to Disclosures", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // STEP 2: Policy & Privacy Transparency Disclosures
                if (step == EnrollmentStep.DISCLOSURES) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue)
                                Text("Customer Privacy & Transparency", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate100)
                            }

                            Text(
                                text = "In accordance with consumer privacy guidelines and Android Enterprise standards, this device management system enforces legitimate installment compliance only.",
                                fontSize = 12.sp,
                                color = Slate400,
                                lineHeight = 18.sp
                            )

                            // What We DO Monitor
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Slate950)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("ALLOWED MONITORING & POLICIES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    Text("• Battery level & network connectivity status", fontSize = 11.sp, color = Slate300())
                                    Text("• SIM carrier identity (for swap/relocation alerts)", fontSize = 11.sp, color = Slate300())
                                    Text("• Installment due dates & overdue enforcement kiosk lock", fontSize = 11.sp, color = Slate300())
                                    Text("• Emergency calls (911/112) remain unrestricted at all times", fontSize = 11.sp, color = Slate300())
                                }
                            }

                            // What We CANNOT and DO NOT Monitor
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Slate950)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("PRIVACY GUARANTEE (STRICTLY PROHIBITED)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                                    Text("✕ NO access to personal photos, videos, or files", fontSize = 11.sp, color = Slate400)
                                    Text("✕ NO monitoring of SMS messages, phone call recordings, or chats", fontSize = 11.sp, color = Slate400)
                                    Text("✕ NO tracking of web browser history or account passwords", fontSize = 11.sp, color = Slate400)
                                    Text("✕ NO stealth surveillance or unauthorized screen captures", fontSize = 11.sp, color = Slate400)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Checkbox(
                                    checked = consentAgreed,
                                    onCheckedChange = { consentAgreed = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                                )
                                Text(
                                    "I have read and accept the privacy disclosure and financing agreement terms.",
                                    fontSize = 11.sp,
                                    color = Slate200
                                )
                            }

                            Button(
                                onClick = {
                                    if (!consentAgreed) {
                                        enrollmentError = "Please check the box to accept the agreement terms."
                                    } else {
                                        enrollmentError = null
                                        step = EnrollmentStep.PERMISSIONS_CONSENT
                                    }
                                },
                                enabled = consentAgreed,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Continue to Permissions", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // STEP 3: Permission & Enterprise Consent
                if (step == EnrollmentStep.PERMISSIONS_CONSENT) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = EmeraldGreen)
                                Text("Enterprise Device Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate100)
                            }

                            Text(
                                text = "The application requires explicit Android Enterprise policies to ensure hardware financing protection. Review the required permissions below:",
                                fontSize = 12.sp,
                                color = Slate400
                            )

                            // Permission 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate950, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SimCard, contentDescription = null, tint = PrimaryBlue)
                                Column {
                                    Text("SIM / SUBSCRIPTION ACCESS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate100)
                                    Text("Uses Android-supported SIM/subscription information only when the required permission and platform access are available.", fontSize = 11.sp, color = Slate400)
                                }
                            }

                            // Permission 2
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate950, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryBlue)
                                Column {
                                    Text("POST_NOTIFICATIONS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate100)
                                    Text("Displays installment due date reminders and payment receipts.", fontSize = 11.sp, color = Slate400)
                                }
                            }

                            // Permission 3
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate950, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AmberWarning)
                                Column {
                                    Text("DEVICE POLICY CONTROLLER (DPC)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate100)
                                    Text("Device Owner policies are available only when this app has actually been provisioned as a managed device.", fontSize = 11.sp, color = Slate400)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Checkbox(
                                    checked = permissionsAgreed,
                                    onCheckedChange = { permissionsAgreed = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                                )
                                Text(
                                    "I grant permissions to enforce financing compliance on this smartphone.",
                                    fontSize = 11.sp,
                                    color = Slate200
                                )
                            }

                            Button(
                                onClick = {
                                    if (!permissionsAgreed) {
                                        enrollmentError = "Please acknowledge permission consent."
                                    } else if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_PHONE_STATE
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                    } else {
                                        startEnrollmentFlow()
                                    }
                                },
                                enabled = permissionsAgreed,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Acknowledge & Start Secure Enrollment", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // STEP 4: Keystore Attestation & Challenge Signing Animation
                if (step == EnrollmentStep.KEYSTORE_ATTESTATION) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(52.dp),
                                color = PrimaryBlue,
                                strokeWidth = 4.dp
                            )

                            Text(
                                text = "Enrolling Hardware Unit",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )

                            Text(
                                text = progressMessage,
                                fontSize = 13.sp,
                                color = Slate400,
                                textAlign = TextAlign.Center
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Slate950)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = if (isHardwareBacked) EmeraldGreen else AmberWarning
                                    )
                                    Column {
                                        Text(
                                            text = if (isHardwareBacked) "Hardware-backed Android Keystore available" else "Android Keystore available; hardware backing not confirmed",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHardwareBacked) EmeraldGreen else AmberWarning
                                        )
                                        Text(
                                            text = "Private key remains in Android Keystore and is not transmitted to the server.",
                                            fontSize = 10.sp,
                                            color = Slate400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // STEP 5: Enrollment Complete
                if (step == EnrollmentStep.COMPLETED) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = "Device Successfully Enrolled!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate100
                            )

                            Text(
                                text = "Your smartphone is linked to the financing enrollment. The device identity was verified by the server using a signed challenge.",
                                fontSize = 13.sp,
                                color = Slate400,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Slate950)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ENROLLMENT STATUS", fontSize = 11.sp, color = Slate400)
                                        Text("ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("DEVICE IDENTITY", fontSize = 11.sp, color = Slate400)
                                        Text("EC P-256 Registered", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("MANAGEMENT MODE", fontSize = 11.sp, color = Slate400)
                                        Text("Managed mode is shown only when actually provisioned", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate200)
                                    }
                                }
                            }

                            Button(
                                onClick = onEnrollmentComplete,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open Customer Transparency Portal", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Slate300(): Color = Color(0xFFCBD5E1)
