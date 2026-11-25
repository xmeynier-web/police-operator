package com.xavier.policeoperator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xavier.policeoperator.R
import com.xavier.policeoperator.data.FirestoreLocationUploader
import com.xavier.policeoperator.location.ActiveLocationTracker
import com.xavier.policeoperator.model.DEFAULT_EQUIPMENT_LIST
import com.xavier.policeoperator.model.GpsSample
import com.xavier.policeoperator.model.PatrolStatus

class LocationService : Service() {

    companion object {
        const val CHANNEL_ID = "police_op_foreground_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "com.xavier.policeoperator.action.START"
        const val ACTION_STOP = "com.xavier.policeoperator.action.STOP"
        const val EXTRA_UNIT_ID = "extra_unit_id"
        const val EXTRA_CREW_COUNT = "extra_crew_count"
        const val EXTRA_STATUS_CODE = "extra_status_code"
        const val EXTRA_EQUIPMENT = "extra_equipment"
    }

    private val uploader = FirestoreLocationUploader()
    private lateinit var tracker: ActiveLocationTracker
    private var currentUnitId = "ALPHA 1"
    private var currentCrewCount = 2
    private var currentStatus = PatrolStatus.PATROUILLE
    private var currentEquipment: List<String> = DEFAULT_EQUIPMENT_LIST
    private var lastSample: GpsSample? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tracker = ActiveLocationTracker(this) { sample ->
            lastSample = sample
            uploader.upsertUnitState(
                unitId = currentUnitId,
                crewCount = currentCrewCount,
                status = currentStatus,
                isTracking = true,
                sample = sample,
                equipment = currentEquipment,
                recordHistory = true
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                currentUnitId = intent.getStringExtra(EXTRA_UNIT_ID)?.trim().orEmpty()
                    .ifEmpty { currentUnitId }
                currentCrewCount = intent.getIntExtra(EXTRA_CREW_COUNT, currentCrewCount)
                    .coerceAtLeast(1)
                currentStatus = PatrolStatus.fromCode(
                    intent.getStringExtra(EXTRA_STATUS_CODE) ?: currentStatus.code
                )
                val extrasEquip = intent.getStringArrayListExtra(EXTRA_EQUIPMENT)
                if (extrasEquip != null) {
                    currentEquipment = extrasEquip
                }
                uploader.upsertUnitState(
                    unitId = currentUnitId,
                    crewCount = currentCrewCount,
                    status = currentStatus,
                    isTracking = false,
                    sample = lastSample,
                    equipment = currentEquipment,
                    recordHistory = false
                )
                tracker.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                currentUnitId = intent?.getStringExtra(EXTRA_UNIT_ID)?.trim().orEmpty()
                    .ifEmpty { "ALPHA 1" }
                currentCrewCount = intent?.getIntExtra(EXTRA_CREW_COUNT, currentCrewCount)
                    ?.coerceAtLeast(1) ?: 2
                currentStatus = PatrolStatus.fromCode(intent?.getStringExtra(EXTRA_STATUS_CODE))
                val extrasEquip = intent?.getStringArrayListExtra(EXTRA_EQUIPMENT)
                if (extrasEquip != null) {
                    currentEquipment = extrasEquip
                }
                startForeground(NOTIFICATION_ID, buildNotification())
                tracker.start()
                uploader.upsertUnitState(
                    unitId = currentUnitId,
                    crewCount = currentCrewCount,
                    status = currentStatus,
                    isTracking = true,
                    sample = lastSample,
                    equipment = currentEquipment,
                    recordHistory = false
                )
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_police_op_logo)
            .setContentTitle("Police OP · $currentUnitId ($currentCrewCount agents)")
            .setContentText("Statut : ${currentStatus.label}")
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Police OP",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tracker.stop()
        super.onDestroy()
    }
}
