package com.protectfinanceddevices.app.ui.navigation

sealed class NavRoutes(val route: String) {

    object Dashboard : NavRoutes("admin_dashboard")

    object DeviceList : NavRoutes("device_list")

    object DeviceDetails : NavRoutes("device_details/{deviceId}") {
        fun createRoute(deviceId: String) = "device_details/$deviceId"
    }

    object Customers : NavRoutes("customers")

    object Financing : NavRoutes("financing")

    object Alerts : NavRoutes("alerts")

    object Settings : NavRoutes("settings")

    object CustomerPortal : NavRoutes("customer_portal")

    object CustomerDeviceStatus : NavRoutes("customer_device_status")

    object CustomerEnrollment : NavRoutes("customer_enrollment")

    object LockScreenPreview : NavRoutes("lock_screen_preview")

    // Phase 2
    object NewCustomer : NavRoutes("new_customer")

    object AddDevice : NavRoutes("add_device")
}
