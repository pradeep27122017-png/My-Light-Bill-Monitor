package com.example.data.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface DeviceLocationResult {
    data class Success(val latitude: Double, val longitude: Double, val locationLabel: String) : DeviceLocationResult
    data object PermissionDenied : DeviceLocationResult
    data object GpsDisabled : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}

suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { ex -> cont.resumeWithException(ex) }
    addOnCanceledListener { cont.cancel() }
}

class LocationService(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    fun isLocationServiceEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): DeviceLocationResult {
        if (!hasLocationPermission()) {
            return DeviceLocationResult.PermissionDenied
        }
        if (!isLocationServiceEnabled()) {
            return DeviceLocationResult.GpsDisabled
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val location: Location? = withTimeoutOrNull(7000L) {
            try {
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).awaitTask()
            } catch (_: Exception) {
                null
            }
        } ?: withTimeoutOrNull(3000L) {
            try {
                fusedClient.lastLocation.awaitTask()
            } catch (_: Exception) {
                null
            }
        } ?: getFallbackLastLocation()

        return if (location != null) {
            val label = resolveAreaLabel(location.latitude, location.longitude)
            DeviceLocationResult.Success(location.latitude, location.longitude, label)
        } else {
            DeviceLocationResult.Unavailable
        }
    }

    @SuppressLint("MissingPermission")
    private fun getFallbackLastLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = manager.getProviders(true)
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveAreaLabel(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext formatCoords(lat, lon)
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val first = addresses?.firstOrNull()
            val locality = first?.locality ?: first?.subAdminArea ?: first?.adminArea
            val country = first?.countryCode
            when {
                !locality.isNullOrBlank() && !country.isNullOrBlank() -> "$locality, $country"
                !locality.isNullOrBlank() -> locality
                else -> formatCoords(lat, lon)
            }
        } catch (_: Exception) {
            formatCoords(lat, lon)
        }
    }

    private fun formatCoords(lat: Double, lon: Double): String =
        "${"%.2f".format(lat)}, ${"%.2f".format(lon)}"
}
