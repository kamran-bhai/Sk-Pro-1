package com.protectfinanceddevices.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.protectfinanceddevices.app.core.network.DeviceEnrollmentService
import com.protectfinanceddevices.app.core.network.EnrollmentResult
import com.protectfinanceddevices.app.core.dpc.DeviceLockManager
import com.protectfinanceddevices.app.core.storage.entities.AgreementEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceCommandEntity
import com.protectfinanceddevices.app.core.storage.entities.InstallmentEntity
import com.protectfinanceddevices.app.ui.admin.*
import com.protectfinanceddevices.app.ui.customer.CustomerHomeScreen
import com.protectfinanceddevices.app.ui.customer.CustomerEnrollmentScreen
import com.protectfinanceddevices.app.ui.customer.CustomerDeviceStatusScreen
import com.protectfinanceddevices.app.core.heartbeat.HeartbeatScheduler
import com.protectfinanceddevices.app.ui.lock.DeviceRestrictedScreen
import com.protectfinanceddevices.app.ui.navigation.NavRoutes
import com.protectfinanceddevices.app.ui.theme.ProtectFinancedDevicesTheme
import com.protectfinanceddevices.app.ui.theme.Slate900
import com.protectfinanceddevices.app.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val applicationInstance by lazy {
        application as FinancedDeviceApplication
    }

    private val database by lazy {
        applicationInstance.database
    }

    private val keyStoreManager by lazy {
        applicationInstance.keyStoreManager
    }

    private val lockManager by lazy {
        DeviceLockManager(this)
    }

    private val enrollmentService by lazy {
        DeviceEnrollmentService(
            keyStoreManager = keyStoreManager,
            database = database
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            HeartbeatScheduler.schedulePeriodicHeartbeat(this)
        } catch (e: Exception) {
            android.util.Log.e(
                "MainActivity",
                "Failed to schedule WorkManager heartbeat: ${e.message}"
            )
        }

        setContent {
            ProtectFinancedDevicesTheme {

                val navController = rememberNavController()

                val devices by database.deviceDao()
                    .getAllDevices()
                    .collectAsState(initial = emptyList())

                val alerts by database.alertDao()
                    .getAllAlerts()
                    .collectAsState(initial = emptyList())

                val customers by database.customerDao()
                    .getAllCustomers()
                    .collectAsState(initial = emptyList())

                val agreements by database.agreementDao()
                    .getAllAgreements()
                    .collectAsState(initial = emptyList())

                val installments by database.installmentDao()
                    .getAllInstallments()
                    .collectAsState(initial = emptyList())

                val activeEnrollment by database.deviceEnrollmentDao()
                    .getActiveEnrollmentFlow()
                    .collectAsState(initial = null)

                val currentRoute =
                    navController
                        .currentBackStackEntryAsState()
                        .value
                        ?.destination
                        ?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Slate950,
                    bottomBar = {

                        if (
                            currentRoute in listOf(
                                NavRoutes.Dashboard.route,
                                NavRoutes.DeviceList.route,
                                NavRoutes.Customers.route,
                                NavRoutes.Financing.route,
                                NavRoutes.Settings.route
                            )
                        ) {

                            NavigationBar(
                                containerColor = Slate900
                            ) {

                                NavigationBarItem(
                                    selected =
                                        currentRoute ==
                                            NavRoutes.Dashboard.route,
                                    onClick = {
                                        navController.navigate(
                                            NavRoutes.Dashboard.route
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Dashboard,
                                            contentDescription =
                                                "Dashboard"
                                        )
                                    },
                                    label = {
                                        Text("Dashboard")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        currentRoute ==
                                            NavRoutes.DeviceList.route,
                                    onClick = {
                                        navController.navigate(
                                            NavRoutes.DeviceList.route
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Smartphone,
                                            contentDescription =
                                                "Devices"
                                        )
                                    },
                                    label = {
                                        Text("Devices")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        currentRoute ==
                                            NavRoutes.Customers.route,
                                    onClick = {
                                        navController.navigate(
                                            NavRoutes.Customers.route
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.People,
                                            contentDescription =
                                                "Customers"
                                        )
                                    },
                                    label = {
                                        Text("Customers")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        currentRoute ==
                                            NavRoutes.Financing.route,
                                    onClick = {
                                        navController.navigate(
                                            NavRoutes.Financing.route
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.AttachMoney,
                                            contentDescription =
                                                "Financing"
                                        )
                                    },
                                    label = {
                                        Text("Financing")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        currentRoute ==
                                            NavRoutes.Settings.route,
                                    onClick = {
                                        navController.navigate(
                                            NavRoutes.Settings.route
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription =
                                                "Security"
                                        )
                                    },
                                    label = {
                                        Text("Security")
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->

                    NavHost(
                        navController = navController,
                        startDestination =
                            NavRoutes.Dashboard.route,
                        modifier =
                            Modifier.padding(innerPadding)
                    ) {

                        // 1. Dashboard
                        composable(
                            NavRoutes.Dashboard.route
                        ) {

                            AdminDashboardScreen(
                                devices = devices,
                                alerts = alerts,

                                onNavigateToDeviceDetails = { deviceId ->
                                    navController.navigate(
                                        NavRoutes.DeviceDetails
                                            .createRoute(deviceId)
                                    )
                                },

                                onNavigateToDevices = {
                                    navController.navigate(
                                        NavRoutes.DeviceList.route
                                    )
                                },

                                onNavigateToAlerts = {
                                    navController.navigate(
                                        NavRoutes.Alerts.route
                                    )
                                },

                                onNavigateToFinancing = {
                                    navController.navigate(
                                        NavRoutes.Financing.route
                                    )
                                }
                            )
                        }

                        // 2. Devices List
                        composable(
                            NavRoutes.DeviceList.route
                        ) {

                            DeviceListScreen(
                                devices = devices,

                                onDeviceClick = { deviceId ->
                                    navController.navigate(
                                        NavRoutes.DeviceDetails
                                            .createRoute(deviceId)
                                    )
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 3. Device Details
                        composable(
                            route =
                                NavRoutes.DeviceDetails.route,
                            arguments = listOf(
                                navArgument("deviceId") {
                                    type =
                                        NavType.StringType
                                }
                            )
                        ) { backStackEntry ->

                            val deviceId =
                                backStackEntry.arguments
                                    ?.getString("deviceId")
                                    ?: ""

                            val currentDevice =
                                devices.find {
                                    it.id == deviceId
                                }

                            val customer =
                                customers.find {
                                    it.id ==
                                        currentDevice?.customerId
                                }

                            val agreement =
                                agreements.find {
                                    it.deviceId == deviceId
                                }

                            val commands by database
                                .deviceCommandDao()
                                .getCommandsForDevice(deviceId)
                                .collectAsState(
                                    initial = emptyList()
                                )

                            DeviceDetailsScreen(
                                device = currentDevice,
                                customer = customer,
                                agreement = agreement,
                                commands = commands,

                                onLockDevice = { reason ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database.deviceDao()
                                            .updateDeviceStatus(
                                                deviceId,
                                                "LOCKED"
                                            )

                                        val cmd =
                                            DeviceCommandEntity(
                                                commandId =
                                                    UUID.randomUUID()
                                                        .toString(),
                                                deviceId = deviceId,
                                                commandType =
                                                    "LOCK_DEVICE",
                                                status =
                                                    "ACKNOWLEDGED",
                                                nonce =
                                                    UUID.randomUUID()
                                                        .toString()
                                                        .replace(
                                                            "-",
                                                            ""
                                                        ),
                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",
                                                issuedAt =
                                                    System.currentTimeMillis(),
                                                expiresAt =
                                                    System.currentTimeMillis() +
                                                        86400000,
                                                executionLog =
                                                    "Lock enforced: $reason"
                                            )

                                        database.deviceCommandDao()
                                            .insertCommand(cmd)

                                        lockManager
                                            .enforceLockState(
                                                this@MainActivity
                                            )
                                    }
                                },

                                onUnlockDevice = {

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database.deviceDao()
                                            .updateDeviceStatus(
                                                deviceId,
                                                "ACTIVE"
                                            )

                                        val cmd =
                                            DeviceCommandEntity(
                                                commandId =
                                                    UUID.randomUUID()
                                                        .toString(),
                                                deviceId = deviceId,
                                                commandType =
                                                    "UNLOCK_DEVICE",
                                                status =
                                                    "ACKNOWLEDGED",
                                                nonce =
                                                    UUID.randomUUID()
                                                        .toString()
                                                        .replace(
                                                            "-",
                                                            ""
                                                        ),
                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",
                                                issuedAt =
                                                    System.currentTimeMillis(),
                                                expiresAt =
                                                    System.currentTimeMillis() +
                                                        86400000,
                                                executionLog =
                                                    "Unlocked upon payment / manual override"
                                            )

                                        database.deviceCommandDao()
                                            .insertCommand(cmd)

                                        lockManager
                                            .releaseLockState(
                                                this@MainActivity
                                            )
                                    }
                                },

                                onRequestStatus = {

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        val cmd =
                                            DeviceCommandEntity(
                                                commandId =
                                                    UUID.randomUUID()
                                                        .toString(),
                                                deviceId = deviceId,
                                                commandType =
                                                    "STATUS_REQUEST",
                                                status =
                                                    "ACKNOWLEDGED",
                                                nonce =
                                                    UUID.randomUUID()
                                                        .toString()
                                                        .replace(
                                                            "-",
                                                            ""
                                                        ),
                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",
                                                issuedAt =
                                                    System.currentTimeMillis(),
                                                expiresAt =
                                                    System.currentTimeMillis() +
                                                        86400000,
                                                executionLog =
                                                    "Diagnostic ping returned healthy"
                                            )

                                        database.deviceCommandDao()
                                            .insertCommand(cmd)
                                    }
                                },

                                onRequestLocation = {

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        val cmd =
                                            DeviceCommandEntity(
                                                commandId =
                                                    UUID.randomUUID()
                                                        .toString(),
                                                deviceId = deviceId,
                                                commandType =
                                                    "LOCATION_REQUEST",
                                                status =
                                                    "ACKNOWLEDGED",
                                                nonce =
                                                    UUID.randomUUID()
                                                        .toString()
                                                        .replace(
                                                            "-",
                                                            ""
                                                        ),
                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",
                                                issuedAt =
                                                    System.currentTimeMillis(),
                                                expiresAt =
                                                    System.currentTimeMillis() +
                                                        86400000,
                                                executionLog =
                                                    "Location telemetry requested with user consent."
                                            )

                                        database.deviceCommandDao()
                                            .insertCommand(cmd)
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 4. Customers
                        composable(
                            NavRoutes.Customers.route
                        ) {

                            CustomerScreen(
                                customers = customers,

                                onCustomerClick = { customer ->

                                    val linkedDevice =
                                        devices.find {
                                            it.customerId ==
                                                customer.id
                                        }

                                    if (linkedDevice != null) {
                                        navController.navigate(
                                            NavRoutes.DeviceDetails
                                                .createRoute(
                                                    linkedDevice.id
                                                )
                                        )
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 5. Financing
                        composable(
                            NavRoutes.Financing.route
                        ) {

                            FinancingScreen(
                                agreements = agreements,
                                installments = installments,

                                onCreateAgreement = {
                                    navController.navigate(
                                        "create_agreement"
                                    )
                                },

                                onRecordPayment = { installmentId ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        val installment =
                                            installments.find {
                                                it.id ==
                                                    installmentId
                                            }

                                        if (installment != null) {

                                            val agreement =
                                                agreements.find {
                                                    it.id ==
                                                        installment
                                                            .agreementId
                                                }

                                            database
                                                .installmentDao()
                                                .markInstallmentPaid(
                                                    installmentId,
                                                    todayString()
                                                )

                                            if (agreement != null) {

                                                val newPaid =
                                                    (
                                                        agreement
                                                            .paidInstallments
                                                        + 1
                                                    ).coerceAtMost(
                                                        agreement
                                                            .numberOfInstallments
                                                    )

                                                val newRemaining =
                                                    (
                                                        agreement
                                                            .remainingAmount
                                                        - installment.amount
                                                    ).coerceAtLeast(
                                                        0.0
                                                    )

                                                val newRemainingCount =
                                                    (
                                                        agreement
                                                            .numberOfInstallments
                                                        - newPaid
                                                    ).coerceAtLeast(
                                                        0
                                                    )

                                                val newStatus =
                                                    if (
                                                        newRemainingCount ==
                                                        0
                                                    ) {
                                                        "COMPLETED"
                                                    } else {
                                                        "ACTIVE"
                                                    }

                                                val updatedAgreement =
                                                    agreement.copy(
                                                        paidInstallments =
                                                            newPaid,
                                                        remainingAmount =
                                                            newRemaining,
                                                        remainingInstallments =
                                                            newRemainingCount,
                                                        status =
                                                            newStatus
                                                    )

                                                database
                                                    .agreementDao()
                                                    .updateAgreement(
                                                        updatedAgreement
                                                    )
                                            }
                                        }
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 5b. Create Financing Agreement
                        composable(
                            "create_agreement"
                        ) {

                            CreateAgreementScreen(
                                customers = customers,
                                devices = devices,

                                onSaveAgreement = {
                                        agreement,
                                        newInstallments ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        val existingAgreement =
                                            database
                                                .agreementDao()
                                                .getAgreementByDeviceId(
                                                    agreement.deviceId
                                                )

                                        if (
                                            existingAgreement == null
                                        ) {

                                            database
                                                .agreementDao()
                                                .insertAgreement(
                                                    agreement
                                                )

                                            database
                                                .installmentDao()
                                                .insertInstallments(
                                                    newInstallments
                                                )

                                            runOnUiThread {
                                                navController
                                                    .popBackStack()
                                            }

                                        } else {

                                            android.util.Log.w(
                                                "Financing",
                                                "Device already has an agreement: ${agreement.deviceId}"
                                            )
                                        }
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 6. Alerts
                        composable(
                            NavRoutes.Alerts.route
                        ) {

                            AlertsScreen(
                                alerts = alerts,

                                onAcknowledgeAlert = { alertId ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {
                                        database.alertDao()
                                            .acknowledgeAlert(
                                                alertId
                                            )
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 7. Settings
                        composable(
                            NavRoutes.Settings.route
                        ) {

                            val isOwner =
                                lockManager.isDeviceOwner()

                            val isAdmin =
                                lockManager.isDeviceAdminActive()

                            val isHardware =
                                keyStoreManager
                                    .isHardwareBacked()

                            val pubKeySnippet =
                                try {
                                    keyStoreManager
                                        .getOrCreateEnrollmentKeyPair()
                                        .take(45) + "..."
                                } catch (
                                    e: Exception
                                ) {
                                    "Generated in Android Keystore"
                                }

                            SettingsScreen(
                                isDeviceOwner = isOwner,
                                isDeviceAdminActive = isAdmin,
                                isKeystoreHardwareBacked =
                                    isHardware,
                                publicKeySnippet =
                                    pubKeySnippet,

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 8. Customer Portal
                        composable(
                            NavRoutes.CustomerPortal.route
                        ) {

                            val currentDevice =
                                devices.firstOrNull()

                            val currentAgreement =
                                agreements.firstOrNull()

                            CustomerHomeScreen(
                                device = currentDevice,
                                agreement =
                                    currentAgreement,

                                onSwitchToAdmin = {
                                    navController.navigate(
                                        NavRoutes.Dashboard.route
                                    )
                                },

                                onSimulateLock = {
                                    navController.navigate(
                                        NavRoutes.LockScreenPreview.route
                                    )
                                },

                                onViewDeviceStatus = {
                                    navController.navigate(
                                        NavRoutes.CustomerDeviceStatus.route
                                    )
                                }
                            )
                        }

                        // 8b. Customer Device Status
                        composable(
                            NavRoutes.CustomerDeviceStatus.route
                        ) {

                            val currentDevice =
                                devices.firstOrNull()

                            val currentAgreement =
                                agreements.firstOrNull()

                            CustomerDeviceStatusScreen(
                                device = currentDevice,
                                agreement =
                                    currentAgreement,
                                enrollment =
                                    activeEnrollment,

                                onNavigateBack = {
                                    navController.popBackStack()
                                },

                                onManualSync = {
                                    HeartbeatScheduler
                                        .enqueueImmediateHeartbeat(
                                            this@MainActivity
                                        )
                                }
                            )
                        }

                        // 9. Lock Preview
                        composable(
                            NavRoutes.LockScreenPreview.route
                        ) {

                            DeviceRestrictedScreen(
                                onDismissPreview = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 10. Customer Enrollment
                        composable(
                            NavRoutes.CustomerEnrollment.route
                        ) {

                            val isHardware =
                                keyStoreManager
                                    .isHardwareBacked()

                            CustomerEnrollmentScreen(
                                isHardwareBacked =
                                    isHardware,

                                onGenerateKeyAndEnroll = {
                                        code,
                                        onProgress,
                                        onDone ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        try {

                                            onProgress(
                                                "Connecting to backend authority..."
                                            )

                                            val disclosureResult =
                                                enrollmentService
                                                    .fetchEnrollmentDisclosure(
                                                        code
                                                    )

                                            val enrollmentId =
                                                if (
                                                    disclosureResult
                                                        .isSuccess
                                                ) {
                                                    disclosureResult
                                                        .getOrNull()
                                                        ?.enrollmentId
                                                        ?: code
                                                } else {
                                                    code
                                                }

                                            when (
                                                val res =
                                                    enrollmentService
                                                        .executeEnrollmentVerification(
                                                            enrollmentId,
                                                            onProgress
                                                        )
                                            ) {

                                                is EnrollmentResult.Success -> {

                                                    HeartbeatScheduler
                                                        .enqueueImmediateHeartbeat(
                                                            this@MainActivity
                                                        )

                                                    onDone(
                                                        true,
                                                        res.message
                                                    )
                                                }

                                                is EnrollmentResult.Failure -> {

                                                    onDone(
                                                        false,
                                                        res.error
                                                    )
                                                }
                                            }

                                        } catch (
                                            e: Exception
                                        ) {

                                            onDone(
                                                false,
                                                e.message
                                                    ?: "Enrollment failed"
                                            )
                                        }
                                    }
                                },

                                onEnrollmentComplete = {
                                    navController.navigate(
                                        NavRoutes.CustomerDeviceStatus.route
                                    )
                                },

                                onCancel = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun todayString(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())
    }
}
