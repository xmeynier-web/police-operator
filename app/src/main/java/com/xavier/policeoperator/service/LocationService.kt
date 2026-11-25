package com.xavier.policeoperator.service

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
import com.xavier.policeoperator.data.FirestoreLocationUploader
import com.xavier.policeoperator.location.ActiveLocationTracker
import com.xavier.policeoperator.location.GpsSample
import com.xavier.policeoperator.model.LocationRepository
import com.xavier.policeoperator.model.OperationalStatus
import com.xavier.policeoperator.ui.MainActivity
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
    private var currentStatus: OperationalStatus = OperationalStatus.PATROUILLE
    private var currentEquipment: List<String> = emptyList()
    private var isServiceTracking: Boolean = false
    private var hasInitializedObserver: Boolean = false

    private data class UnitStateSnapshot(
        val unitId: String,
        val status: OperationalStatus,
        val equipment: List<String>
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
                        status = state.operationalStatus,
                        equipment = state.selectedEquipment.toList().sorted()
                    )
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    currentUnitId = snapshot.unitId
                    currentStatus = snapshot.status
                    currentEquipment = snapshot.equipment

                    if (!hasInitializedObserver) {
                        hasInitializedObserver = true
                        return@collect
                    }
                    if (isServiceTracking) {
                        updateNotification(currentUnitId, currentStatus)
                        val latest = LocationRepository.telemetry.value
                        val result = uploader.pushUnitState(
                            unitId = currentUnitId,
                            operationalStatus = currentStatus,
                            equipment = currentEquipment,
                            isTracking = true,
                            latitude = latest.latitude,
                            longitude = latest.longitude
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
        intent?.getStringExtra(EXTRA_UNIT_ID)?.takeIf { it.isNotBlank() }?.let { rawId ->
            currentUnitId = rawId.trim().uppercase()
            LocationRepository.setUnitId(currentUnitId)
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

        val notification = buildNotification(currentUnitId, currentStatus)

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

        serviceScope.launch {
            val latest = LocationRepository.telemetry.value
            val result = uploader.pushUnitState(
                unitId = currentUnitId,
                operationalStatus = currentStatus,
                equipment = currentEquipment,
                isTracking = true,
                latitude = latest.latitude,
                longitude = latest.longitude
            )
            result.fold(
                onSuccess = { LocationRepository.recordSyncSuccess() },
                onFailure = { err -> LocationRepository.recordSyncError(err.localizedMessage) }
            )
        }

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
        val statusSnapshot = currentStatus
        val equipmentSnapshot = currentEquipment

        serviceScope.launch {
            val result = uploader.uploadSample(
                unitId = unitIdSnapshot,
                operationalStatus = statusSnapshot,
                equipment = equipmentSnapshot,
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
        val statusSnapshot = currentStatus
        val equipmentSnapshot = currentEquipment
        LocationRepository.setTrackingStopped()

        serviceScope.launch {
            uploader.pushUnitState(
                unitId = unitIdSnapshot,
                operationalStatus = statusSnapshot,
                equipment = equipmentSnapshot,
                isTracking = false
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
                "Police Operator — GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification persistante pendant le suivi GPS actif"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(unitId: String, status: OperationalStatus) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(unitId, status))
    }

    private fun buildNotification(unitId: String, status: OperationalStatus): Notification {
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
            putExtra(EXTRA_OPERATIONAL_STATUS, status.code)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Police Operator · $unitId")
            .setContentText("Statut : ${status.label}")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "police_operator_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.xavier.policeoperator.action.STOP"
        const val EXTRA_UNIT_ID = "com.xavier.policeoperator.extra.UNIT_ID"
        const val EXTRA_OPERATIONAL_STATUS = "com.xavier.policeoperator.extra.OPERATIONAL_STATUS"
        const val EXTRA_EQUIPMENT = "com.xavier.policeoperator.extra.EQUIPMENT"

        fun start(
            context: Context,
            unitId: String,
            status: OperationalStatus,
            equipment: List<String>
        ) {
            val intent = Intent(context, LocationService::class.java).apply {
                putExtra(EXTRA_UNIT_ID, unitId)
                putExtra(EXTRA_OPERATIONAL_STATUS, status.code)
                putStringArrayListExtra(EXTRA_EQUIPMENT, ArrayList(equipment))
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
            status: OperationalStatus,
            equipment: List<String>
        ) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_UNIT_ID, unitId)
                putExtra(EXTRA_OPERATIONAL_STATUS, status.code)
                putStringArrayListExtra(EXTRA_EQUIPMENT, ArrayList(equipment))
            }
            context.startService(intent)
        }
    }
}
