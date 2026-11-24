package com.example.geotracker.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocationTelemetry(
    val unitId: String = "UNITE-01",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMetersPerSec: Float = 0f,
    val bearingDegrees: Float? = null,
    val altitudeMeters: Double? = null,
    val lastUpdateFormatted: String = "--:--:--",
    val lastSyncFormatted: String = "--:--:--",
    val lastSyncError: String? = null,
    val fixCount: Int = 0,
    val isTracking: Boolean = false,
    val hasBackgroundPermission: Boolean = false,
    val statusMessage: String = "En attente de démarrage"
) {
    val speedKmh: Float
        get() = if (speedMetersPerSec > 0.15f) speedMetersPerSec * 3.6f else 0f
}

object LocationRepository {
    private val _telemetry = MutableStateFlow(LocationTelemetry())
    val telemetry: StateFlow<LocationTelemetry> = _telemetry.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    fun setUnitId(unitId: String) {
        val cleaned = unitId.trim().ifEmpty { "UNITE-01" }
        _telemetry.update { it.copy(unitId = cleaned) }
    }

    fun setBackgroundPermissionState(granted: Boolean) {
        _telemetry.update { it.copy(hasBackgroundPermission = granted) }
    }

    fun setTrackingStarted(hasBackgroundPermission: Boolean) {
        _telemetry.update {
            it.copy(
                isTracking = true,
                hasBackgroundPermission = hasBackgroundPermission,
                lastSyncError = null,
                statusMessage = if (hasBackgroundPermission) {
                    "Suivi actif (premier plan & arrière-plan)"
                } else {
                    "Suivi actif (premier plan uniquement)"
                }
            )
        }
    }

    fun setTrackingStopped() {
        _telemetry.update {
            it.copy(
                isTracking = false,
                speedMetersPerSec = 0f,
                statusMessage = "Suivi arrêté"
            )
        }
    }

    fun updateLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMetersPerSec: Float,
        bearingDegrees: Float?,
        altitudeMeters: Double?
    ) {
        val now = timeFormatter.format(Date())
        _telemetry.update { current ->
            current.copy(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                speedMetersPerSec = speedMetersPerSec,
                bearingDegrees = bearingDegrees,
                altitudeMeters = altitudeMeters,
                lastUpdateFormatted = now,
                fixCount = current.fixCount + 1,
                isTracking = true,
                statusMessage = "Position reçue à $now"
            )
        }
    }

    fun recordSyncSuccess() {
        val now = timeFormatter.format(Date())
        _telemetry.update {
            it.copy(
                lastSyncFormatted = now,
                lastSyncError = null
            )
        }
    }

    fun recordSyncError(message: String?) {
        _telemetry.update {
            it.copy(lastSyncError = message ?: "Erreur de synchronisation")
        }
    }
}
