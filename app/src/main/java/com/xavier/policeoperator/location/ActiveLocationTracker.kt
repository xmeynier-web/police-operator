package com.example.geotracker.xavier.policeoperator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.example.geotracker.xavier.policeoperator.model.GpsSample

class ActiveLocationTracker(
    context: Context,
    private val onSample: (GpsSample) -> Unit
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var active = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onSample(location.toGpsSample())
        }
    }

    @SuppressLint("MissingPermission")
    fun start(minTimeMs: Long = 3000L, minDistanceMeters: Float = 5f) {
        if (active) return
        active = true

        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            },
            LocationManager.NETWORK_PROVIDER.takeIf {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        )

        providers.firstOrNull()?.let { provider ->
            locationManager.getLastKnownLocation(provider)?.let { last ->
                onSample(last.toGpsSample())
            }
        }

        for (provider in providers) {
            locationManager.requestLocationUpdates(
                provider,
                minTimeMs,
                minDistanceMeters,
                listener
            )
        }
    }

    fun stop() {
        active = false
        locationManager.removeUpdates(listener)
    }

    private fun Location.toGpsSample(): GpsSample {
        val speedKmh = if (hasSpeed() && speed > 0f) (speed * 3.6f).toDouble() else 0.0
        return GpsSample(
            latitude = latitude,
            longitude = longitude,
            speedKmh = speedKmh,
            accuracyMeters = accuracy,
            timestampMillis = time
        )
    }
}
