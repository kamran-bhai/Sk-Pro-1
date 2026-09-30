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
import androidx.room.withTransaction
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.protectfinanceddevices.app.core.heartbeat.HeartbeatScheduler
import com.protectfinanceddevices.app.core.network.DeviceEnrollmentService
import com.protectfinanceddevices.app.core.network.EnrollmentResult
import com.protectfinanceddevices.app.core.dpc.DeviceLockManager
import com.protectfinanceddevices.app.core.storage.entities.DeviceCommandEntity
import com.protectfinanceddevices.app.ui.admin.*
import com.protectfinanceddevices.app.ui.customer.CustomerDeviceStatusScreen
import com.protectfinanceddevices.app.ui.customer.CustomerEnrollmentScreen
import com.protectfinanceddevices.app.ui.customer.CustomerHomeScreen
import com.protectfinanceddevices.app.ui.lock.DeviceRestrictedScreen
import com.protectfinanceddevices.app.ui.navigation.NavRoutes
import com.protectfinanceddevices.app.ui.theme.ProtectFinancedDevicesTheme
import com.protectfinanceddevices.app.ui.theme.Slate900
import com.protectfinanceddevices.app.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        /*
         * Start compliant WorkManager heartbeat scheduling.
         */
        try {
            HeartbeatScheduler.schedulePeriodicHeartbeat(this)
        } catch (e: Exception) {
            android.util.Log.e(
                "MainActivity",
                "Failed to schedule heartbeat: ${e.message}"
            )
        }

        setContent {

            ProtectFinancedDevicesTheme {

                val navController = rememberNavController()

                /*
                 * Room database observers
                 */
                val devices by database
                    .deviceDao()
                    .getAllDevices()
                    .collectAsState(initial = emptyList())

                val alerts by database
                    .alertDao()
                    .getAllAlerts()
                    .collectAsState(initial = emptyList())

                val customers by database
                    .customerDao()
                    .getAllCustomers()
                    .collectAsState(initial = emptyList())

                val agreements by database
                    .agreementDao()
                    .getAllAgreements()
                    .collectAsState(initial = emptyList())

                val activeEnrollment by database
                    .deviceEnrollmentDao()
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
                                            contentDescription = "Dashboard"
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
                                            contentDescription = "Devices"
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
                                            contentDescription = "Customers"
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
                                            contentDescription = "Financing"
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
                                            contentDescription = "Security"
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
                        startDestination = NavRoutes.Dashboard.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {

                        /*
                         * =====================================================
                         * 1. ADMIN DASHBOARD
                         * =====================================================
                         */

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
                                },

                                /*
                                 * NEW DASHBOARD MENU CALLBACKS
                                 */

                                onNewCustomer = {

                                    navController.navigate(
                                        NavRoutes.NewCustomer.route
                                    )
                                },

                                onCustomerList = {

                                    navController.navigate(
                                        NavRoutes.Customers.route
                                    )
                                },

                                onAddDevice = {

                                    navController.navigate(
                                        NavRoutes.AddDevice.route
                                    )
                                },

                                onKeyManagement = {

                                    navController.navigate(
                                        NavRoutes.Settings.route
                                    )
                                },

                                onHistory = {

                                    navController.navigate(
                                        NavRoutes.Alerts.route
                                    )
                                },

                                onSupport = {

                                    navController.navigate(
                                        NavRoutes.Settings.route
                                    )
                                },

                                onProfile = {

                                    navController.navigate(
                                        NavRoutes.Settings.route
                                    )
                                },

                                onTransferPoint = {

                                    navController.navigate(
                                        NavRoutes.Settings.route
                                    )
                                },

                                onRetailerList = {

                                    navController.navigate(
                                        NavRoutes.Customers.route
                                    )
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 2. DEVICE LIST
                         * =====================================================
                         */

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

                                onAddDevice = {
                                    navController.navigate(
                                        NavRoutes.AddDevice.route
                                    )
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 3. DEVICE DETAILS
                         * =====================================================
                         */

                        composable(
                            route = NavRoutes.DeviceDetails.route,

                            arguments = listOf(
                                navArgument("deviceId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->

                            val deviceId =
                                backStackEntry
                                    .arguments
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

                                        database
                                            .deviceDao()
                                            .updateDeviceStatus(
                                                deviceId,
                                                "LOCKED"
                                            )

                                        val now =
                                            System.currentTimeMillis()

                                        val command =
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
                                                        .replace("-", ""),

                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",

                                                issuedAt = now,

                                                expiresAt =
                                                    now + 86400000,

                                                executionLog =
                                                    "Lock enforced: $reason"
                                            )

                                        database
                                            .deviceCommandDao()
                                            .insertCommand(command)

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

                                        database
                                            .deviceDao()
                                            .updateDeviceStatus(
                                                deviceId,
                                                "ACTIVE"
                                            )

                                        val now =
                                            System.currentTimeMillis()

                                        val command =
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
                                                        .replace("-", ""),

                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",

                                                issuedAt = now,

                                                expiresAt =
                                                    now + 86400000,

                                                executionLog =
                                                    "Unlocked upon payment / manual override"
                                            )

                                        database
                                            .deviceCommandDao()
                                            .insertCommand(command)

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

                                        val now =
                                            System.currentTimeMillis()

                                        val command =
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
                                                        .replace("-", ""),

                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",

                                                issuedAt = now,

                                                expiresAt =
                                                    now + 86400000,

                                                executionLog =
                                                    "Diagnostic status requested"
                                            )

                                        database
                                            .deviceCommandDao()
                                            .insertCommand(command)
                                    }
                                },

                                onRequestLocation = {

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        val now =
                                            System.currentTimeMillis()

                                        val command =
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
                                                        .replace("-", ""),

                                                serverSignature =
                                                    "ECDSA_NIST_P256_SERVER_SIG",

                                                issuedAt = now,

                                                expiresAt =
                                                    now + 86400000,

                                                executionLog =
                                                    "Location requested with user consent"
                                            )

                                        database
                                            .deviceCommandDao()
                                            .insertCommand(command)
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 4. CUSTOMERS
                         * =====================================================
                         */

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

                        /*
                         * =====================================================
                         * 5. NEW CUSTOMER
                         * =====================================================
                         */

                        composable(
                            NavRoutes.NewCustomer.route
                        ) {

                            NewCustomerScreen(

                                onSaveCustomer = { customer ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database
                                            .customerDao()
                                            .insertCustomer(customer)

                                        launch(
                                            Dispatchers.Main
                                        ) {
                                            navController.popBackStack()
                                        }
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 6. ADD DEVICE
                         * =====================================================
                         */

                        composable(
                            NavRoutes.AddDevice.route
                        ) {

                            AddDeviceScreen(

                                customers = customers,

                                onSaveDevice = { device ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database
                                            .deviceDao()
                                            .insertDevice(device)

                                        launch(
                                            Dispatchers.Main
                                        ) {
                                            navController.popBackStack()
                                        }
                                    }
                                },

                                onCreateCustomer = {

                                    navController.navigate(
                                        NavRoutes.NewCustomer.route
                                    )
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 7. CREATE FINANCING AGREEMENT
                         * =====================================================
                         */

                        composable(
                            NavRoutes.CreateAgreement.route
                        ) {

                            CreateAgreementScreen(

                                customers = customers,

                                devices = devices,

                                onSaveAgreement = { agreement, installments ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database
                                            .agreementDao()
                                            .insertAgreement(agreement)

                                        database
                                            .installmentDao()
                                            .insertInstallments(installments)

                                        // Newly created financing starts protected when the
                                        // device is actually managed as Device Owner.
                                        lockManager.syncFinancingProtection(agreement.status)

                                        launch(Dispatchers.Main) {
                                            navController.popBackStack()
                                        }
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 7. FINANCING
                         * =====================================================
                         */

                        composable(
                            NavRoutes.Financing.route
                        ) {

                            val selectedAgreement =
                                agreements.firstOrNull()

                            val allInstallments by database
                                .installmentDao()
                                .getInstallmentsForAgreement(
                                    selectedAgreement?.id ?: ""
                                )
                                .collectAsState(
                                    initial = emptyList()
                                )

                            FinancingScreen(

                                agreements = agreements,

                                installments = allInstallments,

                                onCreateAgreement = {
                                    navController.navigate(NavRoutes.CreateAgreement.route)
                                },

                                onRecordPayment = { installmentId ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {
                                        database.withTransaction {
                                            val installment =
                                                database
                                                    .installmentDao()
                                                    .getInstallmentById(installmentId)
                                                    ?: return@withTransaction

                                            if (installment.status == "PAID") {
                                                return@withTransaction
                                            }

                                            val agreement =
                                                database
                                                    .agreementDao()
                                                    .getAgreementById(
                                                        installment.agreementId
                                                    )
                                                    ?: return@withTransaction

                                            val paidDate =
                                                java.text.SimpleDateFormat(
                                                    "yyyy-MM-dd",
                                                    java.util.Locale.US
                                                ).format(
                                                    java.util.Date()
                                                )

                                            val updatedRows =
                                                database
                                                    .installmentDao()
                                                    .markInstallmentPaid(
                                                        installmentId,
                                                        paidDate
                                                    )

                                            if (updatedRows == 0) {
                                                return@withTransaction
                                            }

                                            val payment =
                                                com.protectfinanceddevices.app
                                                    .core.storage.entities.PaymentEntity(
                                                        id = UUID.randomUUID().toString(),
                                                        agreementId = agreement.id,
                                                        installmentId = installment.id,
                                                        amount = installment.amount +
                                                            installment.penaltyFee,
                                                        paidAt = paidDate
                                                    )

                                            database
                                                .paymentDao()
                                                .insertPayment(payment)

                                            database
                                                .auditLogDao()
                                                .insert(
                                                    com.protectfinanceddevices.app
                                                        .core.storage.entities.AuditLogEntity(
                                                            id = UUID.randomUUID().toString(),
                                                            actorType = "ADMIN",
                                                            action = "PAYMENT_RECORDED",
                                                            entityType = "INSTALLMENT",
                                                            entityId = installment.id,
                                                            details = "Payment recorded for agreement ${agreement.id}; amount=${payment.amount}"
                                                        )
                                                )

                                            val allInstallments =
                                                database
                                                    .installmentDao()
                                                    .getInstallmentsForAgreementOnce(
                                                        agreement.id
                                                    )

                                            val unpaidInstallments =
                                                allInstallments.filter {
                                                    it.status != "PAID" &&
                                                        it.status != "WAIVED"
                                                }

                                            val paidInstallments =
                                                allInstallments.count {
                                                    it.status == "PAID"
                                                }

                                            val remainingAmount =
                                                unpaidInstallments.sumOf {
                                                    it.amount + it.penaltyFee
                                                }

                                            val nextDueDate =
                                                unpaidInstallments
                                                    .minByOrNull { it.dueDate }
                                                    ?.dueDate
                                                    ?: agreement.nextDueDate

                                            val updatedStatus =
                                                when {
                                                    unpaidInstallments.isEmpty() ->
                                                        "COMPLETED"
                                                    allInstallments.any {
                                                        it.status == "OVERDUE"
                                                    } ->
                                                        "OVERDUE"
                                                    else -> "ACTIVE"
                                                }

                                            val updatedAgreement = agreement.copy(
                                                remainingAmount = remainingAmount,
                                                paidInstallments = paidInstallments,
                                                remainingInstallments = unpaidInstallments.size,
                                                nextDueDate = nextDueDate,
                                                status = updatedStatus
                                            )

                                            database
                                                .agreementDao()
                                                .updateAgreement(updatedAgreement)

                                            // Protection is released only when the agreement
                                            // is fully completed; paying one installment is
                                            // not enough to remove device-management policy.
                                            lockManager.syncFinancingProtection(updatedAgreement.status)
                                        }
                                    }
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 8. ALERTS
                         * =====================================================
                         */

                        composable(
                            NavRoutes.Alerts.route
                        ) {

                            AlertsScreen(

                                alerts = alerts,

                                onAcknowledgeAlert = { alertId ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database
                                            .alertDao()
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

                        /*
                         * =====================================================
                         * 9. SECURITY SETTINGS
                         * =====================================================
                         */

                        composable(
                            NavRoutes.Settings.route
                        ) {

                            val isOwner =
                                lockManager.isDeviceOwner()

                            val isAdmin =
                                lockManager.isDeviceAdminActive()

                            val isHardware =
                                keyStoreManager.isHardwareBacked()

                            val publicKeySnippet =
                                try {

                                    keyStoreManager
                                        .getOrCreateEnrollmentKeyPair()
                                        .take(45) + "..."

                                } catch (e: Exception) {

                                    "Generated in Android Keystore"
                                }

                            SettingsScreen(

                                isDeviceOwner = isOwner,

                                isDeviceAdminActive = isAdmin,

                                isKeystoreHardwareBacked =
                                    isHardware,

                                publicKeySnippet =
                                    publicKeySnippet,

                                usbDebuggingBlocked =
                                    lockManager.isRestrictionEnabled(
                                        android.os.UserManager.DISALLOW_DEBUGGING_FEATURES
                                    ),

                                factoryResetBlocked =
                                    lockManager.isRestrictionEnabled(
                                        android.os.UserManager.DISALLOW_FACTORY_RESET
                                    ),

                                uninstallBlocked =
                                    lockManager.isUninstallBlocked(),

                                onUsbDebuggingBlockedChange = { enabled ->
                                    lockManager.setDebuggingBlocked(enabled)
                                },

                                onFactoryResetBlockedChange = { enabled ->
                                    lockManager.setFactoryResetBlocked(enabled)
                                },

                                onUninstallBlockedChange = { enabled ->
                                    lockManager.setUninstallBlocked(enabled)
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 10. CUSTOMER PORTAL
                         * =====================================================
                         */

                        composable(
                            NavRoutes.CustomerPortal.route
                        ) {

                            val currentDevice =
                                devices.firstOrNull()

                            val currentAgreement =
                                agreements.firstOrNull()

                            CustomerHomeScreen(

                                device = currentDevice,

                                agreement = currentAgreement,

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

                        /*
                         * =====================================================
                         * 11. CUSTOMER DEVICE STATUS
                         * =====================================================
                         */

                        composable(
                            NavRoutes.CustomerDeviceStatus.route
                        ) {

                            val currentDevice =
                                devices.firstOrNull()

                            val currentAgreement =
                                agreements.firstOrNull()

                            CustomerDeviceStatusScreen(

                                device = currentDevice,

                                agreement = currentAgreement,

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

                        /*
                         * =====================================================
                         * 12. LOCK SCREEN PREVIEW
                         * =====================================================
                         */

                        composable(
                            NavRoutes.LockScreenPreview.route
                        ) {

                            DeviceRestrictedScreen(

                                onDismissPreview = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        /*
                         * =====================================================
                         * 13. CUSTOMER ENROLLMENT
                         * =====================================================
                         */

                        composable(
                            NavRoutes.CustomerEnrollment.route
                        ) {

                            val isHardware =
                                keyStoreManager.isHardwareBacked()

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
                                                val result =
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
                                                        result.message
                                                    )
                                                }

                                                is EnrollmentResult.Failure -> {

                                                    onDone(
                                                        false,
                                                        result.error
                                                    )
                                                }
                                            }

                                        } catch (e: Exception) {

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
}
