package com.xavier.policeoperator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xavier.policeoperator.data.FirestoreLocationUploader
import com.xavier.policeoperator.location.ActiveLocationTracker
import com.xavier.policeoperator.model.DEFAULT_EQUIPMENT_LIST
import com.xavier.policeoperator.model.GpsSample
import com.xavier.policeoperator.model.PatrolStatus
import com.xavier.policeoperator.ui.MainActivity

class LocationService : Service() {

    private val uploader = FirestoreLocationUploader()
    private var tracker: ActiveLocationTracker? = null

    private var unitId: String = "ALPHA 1"
    private var crewCount: Int = 2
    private var status: PatrolStatus = PatrolStatus.PATROUILLE
    private var equipment: List<String> = DEFAULT_EQUIPMENT_LIST
    private var lastSample: GpsSample? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tracker = ActiveLocationTracker(this) { sample ->
            lastSample = sample
            uploader.upsertUnitState(
                unitId = unitId,
                crewCount = crewCount,
                status = status,
                isTracking = true,
                sample = sample,
                equipment = equipment,
                recordHistory = true
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                unitId = intent.getStringExtra(EXTRA_UNIT_ID)?.trim()?.ifEmpty { "ALPHA 1" } ?: "ALPHA 1"
                crewCount = intent.getIntExtra(EXTRA_CREW_COUNT, 2).coerceAtLeast(1)
                status = PatrolStatus.fromCode(intent.getStringExtra(EXTRA_STATUS_CODE))
                equipment = intent.getStringArrayListExtra(EXTRA_EQUIPMENT)?.toList() ?: DEFAULT_EQUIPMENT_LIST

                startForeground(NOTIFICATION_ID, buildNotification())
                tracker?.start()

                uploader.upsertUnitState(
                    unitId = unitId,
                    crewCount = crewCount,
                    status = status,
                    isTracking = true,
                    sample = lastSample,
                    equipment = equipment,
                    recordHistory = false
                )
            }

            ACTION_STOP -> {
                tracker?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tracker?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Géolocalisation Police OP",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Suivi de position en service actif"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Police OP · $unitId")
            .setContentText("Statut : ${status.label} (Suivi actif)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "police_op_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.xavier.policeoperator.action.START"
        const val ACTION_STOP = "com.xavier.policeoperator.action.STOP"

        const val EXTRA_UNIT_ID = "extra_unit_id"
        const val EXTRA_CREW_COUNT = "extra_crew_count"
        const val EXTRA_STATUS_CODE = "extra_status_code"
        const val EXTRA_EQUIPMENT = "extra_equipment"
    }
}
