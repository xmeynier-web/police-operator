package com.example.geotracker.xavier.policeoperator.service

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
import com.xavier.policeoperator.model.GpsSample
import com.xavier.policeoperator.model.PatrolStatus

class LocationService : Service() {

    companion object {
        const val CHANNEL_ID = "police_op_foreground_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "com.xavier.policeoperator.action.START"
        const val ACTION_STOP = "com.xavier.policeoperator.action.STOP"
        const val EXTRA_UNIT_ID = "extra_unit_id"
        const val EXTRA_STATUS_CODE = "extra_status_code"
    }

    private val uploader = FirestoreLocationUploader()
    private lateinit var tracker: ActiveLocationTracker
    private var currentUnitId = "ALPHA 1"
    private var currentStatus = PatrolStatus.PATROUILLE
    private var lastSample: GpsSample? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tracker = ActiveLocationTracker(this) { sample ->
            lastSample = sample
            uploader.upsertUnitState(
                unitId = currentUnitId,
                status = currentStatus,
                isTracking = true,
                sample = sample,
                recordHistory = true
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                uploader.upsertUnitState(
                    unitId = currentUnitId,
                    status = currentStatus,
                    isTracking = false,
                    sample = lastSample,
                    recordHistory = false
                )
                tracker.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                currentUnitId = intent?.getStringExtra(EXTRA_UNIT_ID)?.trim().orEmpty().ifEmpty { "ALPHA 1" }
                currentStatus = PatrolStatus.fromCode(intent?.getStringExtra(EXTRA_STATUS_CODE))
                startForeground(NOTIFICATION_ID, buildNotification())
                tracker.start()
                uploader.upsertUnitState(
                    unitId = currentUnitId,
                    status = currentStatus,
                    isTracking = true,
                    sample = lastSample,
                    recordHistory = false
                )
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_police_op_logo)
            .setContentTitle("Police OP · $currentUnitId")
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
