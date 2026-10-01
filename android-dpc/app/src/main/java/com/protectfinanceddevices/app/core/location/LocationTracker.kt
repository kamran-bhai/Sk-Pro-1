package com.protectfinanceddevices.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.google.android.gms.location.*

data class StoredLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: Long,
    val provider: String
)

class LocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun readLast(): StoredLocation? {
        if (!prefs.contains(KEY_LAT)) return null
        return StoredLocation(
            prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null,
            prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null,
            prefs.getFloat(KEY_ACCURACY, -1f),
            prefs.getLong(KEY_TIME, 0L),
            prefs.getString(KEY_PROVIDER, "fused") ?: "fused"
        )
    }

    @SuppressLint("MissingPermission")
    fun start(onError: (Exception) -> Unit = {}) {
        if (!hasLocationPermission()) {
            onError(SecurityException("Location permission is not granted."))
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMinUpdateDistanceMeters(50f)
            .setWaitForAccurateLocation(false)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener(onError)
    }

    fun stop() = client.removeLocationUpdates(callback)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::persist)
        }
    }

    private fun persist(location: Location) {
        prefs.edit()
            .putString(KEY_LAT, location.latitude.toString())
            .putString(KEY_LON, location.longitude.toString())
            .putFloat(KEY_ACCURACY, location.accuracy)
            .putLong(KEY_TIME, location.time)
            .putString(KEY_PROVIDER, location.provider ?: "fused")
            .apply()
    }

    companion object {
        private const val PREFS = "location_state"
        private const val KEY_LAT = "latitude"
        private const val KEY_LON = "longitude"
        private const val KEY_ACCURACY = "accuracy"
        private const val KEY_TIME = "captured_at"
        private const val KEY_PROVIDER = "provider"
    }
}
