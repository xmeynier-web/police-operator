package com.example.geotracker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.geotracker.data.FirestoreLocationUploader
import com.example.geotracker.location.ActiveLocationTracker
import com.example.geotracker.location.GpsSample
import com.example.geotracker.model.LocationRepository
import com.example.geotracker.model.OperationalStatus
import com.example.geotracker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uploader = FirestoreLocationUploader()
    private lateinit var locationTracker: ActiveLocationTracker
    private var stateObserverJob: Job? = null

    private var currentUnitId: String = "UNITE-01"
    private var currentUnitName: String = "Unité 01"
    private var currentStatus: OperationalStatus = OperationalStatus.PATROUILLE
    private var currentEquipment: List<String> = emptyList()
    private var currentNote: String = ""
    private var isServiceTracking: Boolean = false
    private var hasInitializedObserver: Boolean = false

    private data class UnitStateSnapshot(
        val unitId: String,
        val unitName: String,
        val status: OperationalStatus,
        val equipment: List<String>,
        val note: String
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        locationTracker = ActiveLocationTracker(this) { sample ->
            handleNewSample(sample)
        }

        observeUnitStateChanges()
    }

    private fun observeUnitStateChanges() {
        stateObserverJob = serviceScope.launch {
            LocationRepository.telemetry
                .map { state ->
                    UnitStateSnapshot(
                        unitId = state.unitId,
                        unitName = state.unitName,
                        status = state.operationalStatus,
                        equipment = state.selectedEquipment.toList().sorted(),
                        note = state.missionNote
                    )
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    currentUnitId = snapshot.unitId
                    currentUnitName = snapshot.unitName
                    currentStatus = snapshot.status
                    currentEquipment = snapshot.equipment
                    currentNote = snapshot.note

                    if (!hasInitializedObserver) {
                        hasInitializedObserver = true
                        return@collect
                    }
                    if (isServiceTracking) {
                        updateNotification(currentUnitId, currentUnitName, currentStatus)
                        val result = uploader.updateUnitStateOnly(
                            unitId = currentUnitId,
                            unitName = currentUnitName,
                            operationalStatus = currentStatus,
                            equipment = currentEquipment,
                            missionNote = currentNote,
                            isTracking = true
                        )
                        result.fold(
                            onSuccess = { LocationRepository.recordSyncSuccess() },
                            onFailure = { err -> LocationRepository.recordSyncError(err.localizedMessage) }
                        )
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedId = intent?.getStringExtra(EXTRA_UNIT_ID)
        val requestedName = intent?.getStringExtra(EXTRA_UNIT_NAME)
        if (!requestedId.isNullOrBlank() || !requestedName.isNullOrBlank()) {
            val resolvedId = requestedId?.takeIf { it.isNotBlank() } ?: currentUnitId
            val resolvedName = requestedName?.takeIf { it.isNotBlank() } ?: resolvedId
            currentUnitId = resolvedId
            currentUnitName = resolvedName
            LocationRepository.setUnitIdentity(resolvedId, resolvedName)
        }

        intent?.getStringExtra(EXTRA_OPERATIONAL_STATUS)?.let { rawStatus ->
            val parsed = OperationalStatus.fromCode(rawStatus)
            currentStatus = parsed
            LocationRepository.updateOperationalStatus(parsed)
        }

        intent?.getStringArrayListExtra(EXTRA_EQUIPMENT)?.let { list ->
            currentEquipment = list.toList().sorted()
            LocationRepository.setEquipmentList(list.toSet())
        }

        intent?.getStringExtra(EXTRA_MISSION_NOTE)?.let { rawNote ->
            currentNote = rawNote.trim()
            LocationRepository.updateMissionNote(currentNote)
        }

        if (intent?.action == ACTION_STOP) {
            stopTrackingAndService()
            return START_NOT_STICKY
        }

        val hasBackground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notification = buildNotification(currentUnitId, currentUnitName, currentStatus)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isServiceTracking = true
        LocationRepository.setTrackingStarted(hasBackground)
        locationTracker.start()

        return START_STICKY
    }

    private fun handleNewSample(sample: GpsSample) {
        LocationRepository.updateLocation(
            latitude = sample.latitude,
            longitude = sample.longitude,
            accuracyMeters = sample.accuracyMeters,
            speedMetersPerSec = sample.speedMetersPerSec
        )

        val unitIdSnapshot = currentUnitId
        val unitNameSnapshot = currentUnitName
        val statusSnapshot = currentStatus
        val equipmentSnapshot = currentEquipment
        val noteSnapshot = currentNote

        serviceScope.launch {
            val result = uploader.uploadSample(
                unitId = unitIdSnapshot,
                unitName = unitNameSnapshot,
                operationalStatus = statusSnapshot,
                equipment = equipmentSnapshot,
                missionNote = noteSnapshot,
                sample = sample,
                isTracking = true
            )
            result.fold(
                onSuccess = { LocationRepository.recordSyncSuccess() },
                onFailure = { err -> LocationRepository.recordSyncError(err.localizedMessage) }
            )
        }
    }

    private fun stopTrackingAndService() {
        isServiceTracking = false
        locationTracker.stop()
        val unitIdSnapshot = currentUnitId
        val unitNameSnapshot = currentUnitName
        val statusSnapshot = currentStatus
        val equipmentSnapshot = currentEquipment
        val noteSnapshot = currentNote
        LocationRepository.setTrackingStopped()

        serviceScope.launch {
            uploader.markTrackingStopped(
                unitId = unitIdSnapshot,
                unitName = unitNameSnapshot,
                operationalStatus = statusSnapshot,
                equipment = equipmentSnapshot,
                missionNote = noteSnapshot
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        isServiceTracking = false
        stateObserverJob?.cancel()
        locationTracker.stop()
        LocationRepository.setTrackingStopped()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Géolocalisation active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification persistante pendant le suivi GPS actif"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(
        unitId: String,
        unitName: String,
        status: OperationalStatus
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(unitId, unitName, status))
    }

    private fun buildNotification(
        unitId: String,
        unitName: String,
        status: OperationalStatus
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_UNIT_ID, unitId)
            putExtra(EXTRA_UNIT_NAME, unitName)
            putExtra(EXTRA_OPERATIONAL_STATUS, status.code)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$unitName ($unitId)")
            .setContentText("Statut : ${status.label}")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "geotracker_active_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.geotracker.action.STOP"
        const val EXTRA_UNIT_ID = "com.example.geotracker.extra.UNIT_ID"
        const val EXTRA_UNIT_NAME = "com.example.geotracker.extra.UNIT_NAME"
        const val EXTRA_OPERATIONAL_STATUS = "com.example.geotracker.extra.OPERATIONAL_STATUS"
        const val EXTRA_EQUIPMENT = "com.example.geotracker.extra.EQUIPMENT"
        const val EXTRA_MISSION_NOTE = "com.example.geotracker.extra.MISSION_NOTE"

        fun start(
            context: Context,
            unitId: String,
            unitName: String,
            status: OperationalStatus,
            equipment: List<String>,
            missionNote: String
        ) {
            val intent = Intent(context, LocationService::class.java).apply {
                putExtra(EXTRA_UNIT_ID, unitId)
                putExtra(EXTRA_UNIT_NAME, unitName)
                putExtra(EXTRA_OPERATIONAL_STATUS, status.code)
                putStringArrayListExtra(EXTRA_EQUIPMENT, ArrayList(equipment))
                putExtra(EXTRA_MISSION_NOTE, missionNote)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(
            context: Context,
            unitId: String,
            unitName: String,
            status: OperationalStatus,
            equipment: List<String>,
            missionNote: String
        ) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_UNIT_ID, unitId)
                putExtra(EXTRA_UNIT_NAME, unitName)
                putExtra(EXTRA_OPERATIONAL_STATUS, status.code)
                putStringArrayListExtra(EXTRA_EQUIPMENT, ArrayList(equipment))
                putExtra(EXTRA_MISSION_NOTE, missionNote)
            }
            context.startService(intent)
        }
    }
}
