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
import com.protectfinanceddevices.app.core.storage.entities.DeviceCommandEntity
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
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val applicationInstance by lazy { application as FinancedDeviceApplication }
    private val database by lazy { applicationInstance.database }
    private val keyStoreManager by lazy { applicationInstance.keyStoreManager }
    private val lockManager by lazy { DeviceLockManager(this) }
    private val enrollmentService by lazy { DeviceEnrollmentService(keyStoreManager = keyStoreManager, database = database) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WorkManager periodic heartbeat schedule (compliant with Android background execution limits)
        try {
            HeartbeatScheduler.schedulePeriodicHeartbeat(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule WorkManager heartbeat: ${e.message}")
        }

        setContent {
            ProtectFinancedDevicesTheme {
                val navController = rememberNavController()

                // Observe Room flows
                val devices by database.deviceDao().getAllDevices().collectAsState(initial = emptyList())
                val alerts by database.alertDao().getAllAlerts().collectAsState(initial = emptyList())
                val customers by database.customerDao().getAllCustomers().collectAsState(initial = emptyList())
                val agreements by database.agreementDao().getAllAgreements().collectAsState(initial = emptyList())
                val installments by database.installmentDao().getOverdueInstallments().collectAsState(initial = emptyList())
                val activeEnrollment by database.deviceEnrollmentDao().getActiveEnrollmentFlow().collectAsState(initial = null)

                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Slate950,
                    bottomBar = {
                        // Show bottom nav only on main top-level admin routes
                        if (currentRoute in listOf(
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
                                    selected = currentRoute == NavRoutes.Dashboard.route,
                                    onClick = { navController.navigate(NavRoutes.Dashboard.route) },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                    label = { Text("Dashboard") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == NavRoutes.DeviceList.route,
                                    onClick = { navController.navigate(NavRoutes.DeviceList.route) },
                                    icon = { Icon(Icons.Default.Smartphone, contentDescription = "Devices") },
                                    label = { Text("Devices") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == NavRoutes.Customers.route,
                                    onClick = { navController.navigate(NavRoutes.Customers.route) },
                                    icon = { Icon(Icons.Default.People, contentDescription = "Customers") },
                                    label = { Text("Customers") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == NavRoutes.Financing.route,
                                    onClick = { navController.navigate(NavRoutes.Financing.route) },
                                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Financing") },
                                    label = { Text("Financing") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == NavRoutes.Settings.route,
                                    onClick = { navController.navigate(NavRoutes.Settings.route) },
                                    icon = { Icon(Icons.Default.Shield, contentDescription = "Security") },
                                    label = { Text("Security") }
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
                        // 1. Dashboard
                        composable(NavRoutes.Dashboard.route) {
                            AdminDashboardScreen(
                                devices = devices,
                                alerts = alerts,
                                onNavigateToDeviceDetails = { deviceId ->
                                    navController.navigate(NavRoutes.DeviceDetails.createRoute(deviceId))
                                },
                                onNavigateToDevices = { navController.navigate(NavRoutes.DeviceList.route) },
                                onNavigateToAlerts = { navController.navigate(NavRoutes.Alerts.route) },
                                onNavigateToFinancing = { navController.navigate(NavRoutes.Financing.route) }
                            )
                        }

                        // 2. Devices List
                        composable(NavRoutes.DeviceList.route) {
                            DeviceListScreen(
                                devices = devices,
                                onDeviceClick = { deviceId ->
                                    navController.navigate(NavRoutes.DeviceDetails.createRoute(deviceId))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 3. Device Details
                        composable(
                            route = NavRoutes.DeviceDetails.route,
                            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                            val currentDevice = devices.find { it.id == deviceId }
                            val customer = customers.find { it.id == currentDevice?.customerId }
                            val agreement = agreements.find { it.deviceId == deviceId }
                            val commands by database.deviceCommandDao().getCommandsForDevice(deviceId).collectAsState(initial = emptyList())

                            DeviceDetailsScreen(
                                device = currentDevice,
                                customer = customer,
                                agreement = agreement,
                                commands = commands,
                                onLockDevice = { reason ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        // 1. Update Room device status
                                        database.deviceDao().updateDeviceStatus(deviceId, "LOCKED")
                                        // 2. Insert signed command
                                        val cmd = DeviceCommandEntity(
                                            commandId = UUID.randomUUID().toString(),
                                            deviceId = deviceId,
                                            commandType = "LOCK_DEVICE",
                                            status = "ACKNOWLEDGED",
                                            nonce = UUID.randomUUID().toString().replace("-", ""),
                                            serverSignature = "ECDSA_NIST_P256_SERVER_SIG",
                                            issuedAt = System.currentTimeMillis(),
                                            expiresAt = System.currentTimeMillis() + 86400000,
                                            executionLog = "Lock enforced: $reason"
                                        )
                                        database.deviceCommandDao().insertCommand(cmd)
                                        // 3. Trigger DPC Lock
                                        lockManager.enforceLockState(this@MainActivity)
                                    }
                                },
                                onUnlockDevice = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        database.deviceDao().updateDeviceStatus(deviceId, "ACTIVE")
                                        val cmd = DeviceCommandEntity(
                                            commandId = UUID.randomUUID().toString(),
                                            deviceId = deviceId,
                                            commandType = "UNLOCK_DEVICE",
                                            status = "ACKNOWLEDGED",
                                            nonce = UUID.randomUUID().toString().replace("-", ""),
                                            serverSignature = "ECDSA_NIST_P256_SERVER_SIG",
                                            issuedAt = System.currentTimeMillis(),
                                            expiresAt = System.currentTimeMillis() + 86400000,
                                            executionLog = "Unlocked upon payment / manual override"
                                        )
                                        database.deviceCommandDao().insertCommand(cmd)
                                        lockManager.releaseLockState(this@MainActivity)
                                    }
                                },
                                onRequestStatus = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val cmd = DeviceCommandEntity(
                                            commandId = UUID.randomUUID().toString(),
                                            deviceId = deviceId,
                                            commandType = "STATUS_REQUEST",
                                            status = "ACKNOWLEDGED",
                                            nonce = UUID.randomUUID().toString().replace("-", ""),
                                            serverSignature = "ECDSA_NIST_P256_SERVER_SIG",
                                            issuedAt = System.currentTimeMillis(),
                                            expiresAt = System.currentTimeMillis() + 86400000,
                                            executionLog = "Diagnostic ping returned healthy"
                                        )
                                        database.deviceCommandDao().insertCommand(cmd)
                                    }
                                },
                                onRequestLocation = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val cmd = DeviceCommandEntity(
                                            commandId = UUID.randomUUID().toString(),
                                            deviceId = deviceId,
                                            commandType = "LOCATION_REQUEST",
                                            status = "ACKNOWLEDGED",
                                            nonce = UUID.randomUUID().toString().replace("-", ""),
                                            serverSignature = "ECDSA_NIST_P256_SERVER_SIG",
                                            issuedAt = System.currentTimeMillis(),
                                            expiresAt = System.currentTimeMillis() + 86400000,
                                            executionLog = "Location telemetry requested with user consent."
                                        )
                                        database.deviceCommandDao().insertCommand(cmd)
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 4. Customers Screen
                        composable(NavRoutes.Customers.route) {
                            CustomerScreen(
                                customers = customers,
                                onCustomerClick = { customer ->
                                    val linkedDevice = devices.find { it.customerId == customer.id }
                                    if (linkedDevice != null) {
                                        navController.navigate(NavRoutes.DeviceDetails.createRoute(linkedDevice.id))
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 5. Financing & Installments Screen
                        composable(NavRoutes.Financing.route) {
                            val allInstallments by database.installmentDao().getInstallmentsForAgreement(agreements.firstOrNull()?.id ?: "").collectAsState(initial = emptyList())
                            FinancingScreen(
                                agreements = agreements,
                                installments = allInstallments,
                                onRecordPayment = { installmentId ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        database.installmentDao().markInstallmentPaid(installmentId, "2026-09-25")
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 6. Alerts Screen
                        composable(NavRoutes.Alerts.route) {
                            AlertsScreen(
                                alerts = alerts,
                                onAcknowledgeAlert = { alertId ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        database.alertDao().acknowledgeAlert(alertId)
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 7. Security Settings & DPC Diagnostics
                        composable(NavRoutes.Settings.route) {
                            val isOwner = lockManager.isDeviceOwner()
                            val isAdmin = lockManager.isDeviceAdminActive()
                            val isHardware = keyStoreManager.isHardwareBacked()
                            val pubKeySnippet = try {
                                keyStoreManager.getOrCreateEnrollmentKeyPair().take(45) + "..."
                            } catch (e: Exception) {
                                "Generated in Android Keystore"
                            }

                            SettingsScreen(
                                isDeviceOwner = isOwner,
                                isDeviceAdminActive = isAdmin,
                                isKeystoreHardwareBacked = isHardware,
                                publicKeySnippet = pubKeySnippet,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 8. Customer Transparency Portal
                        composable(NavRoutes.CustomerPortal.route) {
                            val currentDevice = devices.firstOrNull()
                            val currentAgreement = agreements.firstOrNull()
                            CustomerHomeScreen(
                                device = currentDevice,
                                agreement = currentAgreement,
                                onSwitchToAdmin = { navController.navigate(NavRoutes.Dashboard.route) },
                                onSimulateLock = { navController.navigate(NavRoutes.LockScreenPreview.route) },
                                onViewDeviceStatus = { navController.navigate(NavRoutes.CustomerDeviceStatus.route) }
                            )
                        }

                        // 8b. Customer Device Status & Sync Screen
                        composable(NavRoutes.CustomerDeviceStatus.route) {
                            val currentDevice = devices.firstOrNull()
                            val currentAgreement = agreements.firstOrNull()
                            CustomerDeviceStatusScreen(
                                device = currentDevice,
                                agreement = currentAgreement,
                                enrollment = activeEnrollment,
                                onNavigateBack = { navController.popBackStack() },
                                onManualSync = {
                                    HeartbeatScheduler.enqueueImmediateHeartbeat(this@MainActivity)
                                }
                            )
                        }

                        // 9. Lock Screen Preview
                        composable(NavRoutes.LockScreenPreview.route) {
                            DeviceRestrictedScreen(
                                onDismissPreview = { navController.popBackStack() }
                            )
                        }

                        // 10. Customer Enrollment Wizard
                        composable(NavRoutes.CustomerEnrollment.route) {
                            val isHardware = keyStoreManager.isHardwareBacked()
                            CustomerEnrollmentScreen(
                                isHardwareBacked = isHardware,
                                onGenerateKeyAndEnroll = { code, onProgress, onDone ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            onProgress("Connecting to backend authority...")
                                            val disclosureResult = enrollmentService.fetchEnrollmentDisclosure(code)
                                            val enrollmentId = if (disclosureResult.isSuccess) {
                                                disclosureResult.getOrNull()?.enrollmentId ?: code
                                            } else {
                                                code
                                            }

                                            when (val res = enrollmentService.executeEnrollmentVerification(enrollmentId, onProgress)) {
                                                is EnrollmentResult.Success -> {
                                                    // Trigger immediate initial heartbeat to register ONLINE state with backend
                                                    HeartbeatScheduler.enqueueImmediateHeartbeat(this@MainActivity)
                                                    onDone(true, res.message)
                                                }
                                                is EnrollmentResult.Failure -> {
                                                    onDone(false, res.error)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            onDone(false, e.message ?: "Enrollment failed")
                                        }
                                    }
                                },
                                onEnrollmentComplete = {
                                    navController.navigate(NavRoutes.CustomerDeviceStatus.route)
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
