package com.example.geotracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSec: Float,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?
)

class ActiveLocationTracker(
    context: Context,
    private val intervalMillis: Long = 3000L,
    private val minUpdateIntervalMillis: Long = 1500L,
    private val minDistanceMeters: Float = 1f,
    private val onSampleReceived: (GpsSample) -> Unit
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private var isRunning = false

    private val locationRequest: LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).apply {
            setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            setMinUpdateDistanceMeters(minDistanceMeters)
            setGranularity(Granularity.GRANULARITY_FINE)
            setWaitForAccurateLocation(false)
        }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            onSampleReceived(
                GpsSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    speedMetersPerSec = if (location.hasSpeed()) location.speed else 0f,
                    bearingDegrees = if (location.hasBearing()) location.bearing else null,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
