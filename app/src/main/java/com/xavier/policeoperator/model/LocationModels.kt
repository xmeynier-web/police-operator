package com.xavier.policeoperator.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OperationalStatus(
    val code: String,
    val label: String
) {
    PATROUILLE("PATROUILLE", "En patrouille"),
    INTERVENTION("INTERVENTION", "En intervention"),
    DISPONIBLE("DISPONIBLE", "Disponible"),
    PAUSE("PAUSE", "En pause");

    companion object {
        fun fromCode(code: String?): OperationalStatus {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: PATROUILLE
        }
    }
}

object OnboardEquipmentCatalog {
    val defaultItems: List<String> = listOf(
        "Terminal / Radio",
        "Caméra piéton",
        "Éclairage / Signalétique",
        "Trousse premiers secours",
        "Équipement protection"
    )
}

data class LocationTelemetry(
    val unitId: String = "UNITE-01",
    val unitName: String = "Unité 01",
    val operationalStatus: OperationalStatus = OperationalStatus.PATROUILLE,
    val selectedEquipment: Set<String> = setOf("Terminal / Radio", "Caméra piéton"),
    val missionNote: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val speedKmh: Float = 0f,
    val lastUpdateFormatted: String = "--:--:--",
    val lastSyncFormatted: String = "--:--:--",
    val lastSyncError: String? = null,
    val isTracking: Boolean = false,
    val hasBackgroundPermission: Boolean = false,
    val statusMessage: String = "En attente de démarrage"
)

object LocationRepository {
    private val _telemetry = MutableStateFlow(LocationTelemetry())
    val telemetry: StateFlow<LocationTelemetry> = _telemetry.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    fun setUnitIdentity(unitId: String, unitName: String) {
        val cleanedId = unitId.trim().uppercase(Locale.ROOT).ifEmpty { "UNITE-01" }
        val cleanedName = unitName.trim().ifEmpty { cleanedId }
        _telemetry.update {
            it.copy(
                unitId = cleanedId,
                unitName = cleanedName
            )
        }
    }

    fun updateOperationalStatus(status: OperationalStatus) {
        _telemetry.update { it.copy(operationalStatus = status) }
    }

    fun toggleEquipment(item: String) {
        _telemetry.update { current ->
            val updated = current.selectedEquipment.toMutableSet().apply {
                if (!add(item)) remove(item)
            }
            current.copy(selectedEquipment = updated)
        }
    }

    fun setEquipmentList(items: Set<String>) {
        _telemetry.update { it.copy(selectedEquipment = items) }
    }

    fun updateMissionNote(note: String) {
        _telemetry.update { it.copy(missionNote = note.trim()) }
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
                    "Actif · Arrière-plan OK"
                } else {
                    "Actif · Premier plan"
                }
            )
        }
    }

    fun setTrackingStopped() {
        _telemetry.update {
            it.copy(
                isTracking = false,
                speedKmh = 0f,
                statusMessage = "Suivi arrêté"
            )
        }
    }

    fun updateLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMetersPerSec: Float
    ) {
        val now = timeFormatter.format(Date())
        val computedSpeedKmh = if (speedMetersPerSec > 0.15f) speedMetersPerSec * 3.6f else 0f
        _telemetry.update { current ->
            current.copy(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                speedKmh = computedSpeedKmh,
                lastUpdateFormatted = now,
                isTracking = true,
                statusMessage = "MAJ GPS à $now"
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
            it.copy(lastSyncError = message ?: "Erreur réseau")
        }
    }
}
