package com.protectfinanceddevices.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.protectfinanceddevices.app.core.storage.AppDatabase
import com.protectfinanceddevices.app.core.storage.entities.CustomerEntity
import com.protectfinanceddevices.app.core.storage.entities.DeviceEntity
import com.protectfinanceddevices.app.ui.admin.AddDeviceScreen
import com.protectfinanceddevices.app.ui.admin.AdminDashboardScreen
import com.protectfinanceddevices.app.ui.admin.AlertsScreen
import com.protectfinanceddevices.app.ui.admin.CustomerScreen
import com.protectfinanceddevices.app.ui.admin.DeviceDetailsScreen
import com.protectfinanceddevices.app.ui.admin.DeviceListScreen
import com.protectfinanceddevices.app.ui.admin.FinancingScreen
import com.protectfinanceddevices.app.ui.admin.NewCustomerScreen
import com.protectfinanceddevices.app.ui.admin.SettingsScreen
import com.protectfinanceddevices.app.ui.customer.CustomerDeviceStatusScreen
import com.protectfinanceddevices.app.ui.customer.CustomerEnrollmentScreen
import com.protectfinanceddevices.app.ui.customer.CustomerHomeScreen
import com.protectfinanceddevices.app.ui.navigation.NavRoutes
import com.protectfinanceddevices.app.ui.theme.ProtectFinancedDevicesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "protect_financed_devices.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        setContent {

            ProtectFinancedDevicesTheme {

                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val navController = rememberNavController()

                    val devices by database.deviceDao()
                        .getAllDevices()
                        .collectAsState(initial = emptyList())

                    val customers by database.customerDao()
                        .getAllCustomers()
                        .collectAsState(initial = emptyList())

                    val totalDevices by database.deviceDao()
                        .getTotalDevicesCount()
                        .collectAsState(initial = 0)

                    val activeDevices by database.deviceDao()
                        .getActiveDevicesCount()
                        .collectAsState(initial = 0)

                    val overdueDevices by database.deviceDao()
                        .getOverdueDevicesCount()
                        .collectAsState(initial = 0)

                    val lockedDevices by database.deviceDao()
                        .getLockedDevicesCount()
                        .collectAsState(initial = 0)

                    val offlineDevices by database.deviceDao()
                        .getOfflineDevicesCount()
                        .collectAsState(initial = 0)

                    val customerCount by database.customerDao()
                        .getCustomerCount()
                        .collectAsState(initial = 0)

                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.Dashboard.route
                    ) {

                        // -------------------------------------------------
                        // DASHBOARD
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.Dashboard.route
                        ) {

                            AdminDashboardScreen(
                                devices = devices,
                                alerts = emptyList(),
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
                                onNavigateToNewCustomer = {
                                    navController.navigate(
                                        NavRoutes.NewCustomer.route
                                    )
                                },
                                onNavigateToCustomerList = {
                                    navController.navigate(
                                        NavRoutes.Customers.route
                                    )
                                },
                                onNavigateToAddDevice = {
                                    navController.navigate(
                                        NavRoutes.AddDevice.route
                                    )
                                },
                                onNavigateToKeyManagement = {
                                    navController.navigate(
                                        NavRoutes.Settings.route
                                    )
                                },
                                onNavigateToHistory = {
                                    navController.navigate(
                                        NavRoutes.Financing.route
                                    )
                                },
                                onNavigateToSupport = {
                                    navController.navigate(
                                        NavRoutes.Alerts.route
                                    )
                                },
                                onNavigateToProfile = {
                                    navController.navigate(
                                        NavRoutes.Settings.route
                                    )
                                },
                                onNavigateToTransferPoint = {
                                    navController.navigate(
                                        NavRoutes.Customers.route
                                    )
                                },
                                onNavigateToRetailerList = {
                                    navController.navigate(
                                        NavRoutes.Customers.route
                                    )
                                }
                            )
                        }

                        // -------------------------------------------------
                        // NEW CUSTOMER - PHASE 2
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.NewCustomer.route
                        ) {

                            NewCustomerScreen(

                                onSaveCustomer = { customer ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database.customerDao()
                                            .insertCustomer(customer)
                                    }

                                    navController.popBackStack()
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // ADD DEVICE - PHASE 2
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.AddDevice.route
                        ) {

                            AddDeviceScreen(

                                customers = customers,

                                onSaveDevice = { device ->

                                    lifecycleScope.launch(
                                        Dispatchers.IO
                                    ) {

                                        database.deviceDao()
                                            .insertDevice(device)
                                    }

                                    navController.popBackStack()
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

                        // -------------------------------------------------
                        // DEVICE LIST
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.DeviceList.route
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

                        // -------------------------------------------------
                        // DEVICE DETAILS
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.DeviceDetails.route
                        ) { backStackEntry ->

                            val deviceId =
                                backStackEntry.arguments
                                    ?.getString("deviceId")
                                    ?: return@composable

                            DeviceDetailsScreen(
                                deviceId = deviceId,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // CUSTOMERS
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.Customers.route
                        ) {

                            CustomerScreen(
                                customers = customers,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // FINANCING
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.Financing.route
                        ) {

                            FinancingScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // ALERTS
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.Alerts.route
                        ) {

                            AlertsScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // SETTINGS
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.Settings.route
                        ) {

                            SettingsScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // CUSTOMER PORTAL
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.CustomerPortal.route
                        ) {

                            CustomerHomeScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // CUSTOMER DEVICE STATUS
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.CustomerDeviceStatus.route
                        ) {

                            CustomerDeviceStatusScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // CUSTOMER ENROLLMENT
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.CustomerEnrollment.route
                        ) {

                            CustomerEnrollmentScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // -------------------------------------------------
                        // LOCK SCREEN PREVIEW
                        // -------------------------------------------------

                        composable(
                            route = NavRoutes.LockScreenPreview.route
                        ) {

                            CustomerDeviceStatusScreen(
                                onBack = {
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
