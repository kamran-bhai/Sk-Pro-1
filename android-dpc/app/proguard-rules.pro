# Proguard configuration for Protect Your Financed Devices
-keep class com.protectfinanceddevices.app.core.storage.entities.** { *; }
-keep class com.protectfinanceddevices.app.core.storage.dao.** { *; }
-keep class com.protectfinanceddevices.app.core.dpc.** { *; }
-dontwarn androidx.security.crypto.**
