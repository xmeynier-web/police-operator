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
import com.example.geotracker.location.ActiveLocationTracker
import com.example.geotracker.model.LocationRepository
import com.example.geotracker.ui.MainActivity

class LocationService : Service() {

    private lateinit var locationTracker: ActiveLocationTracker

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        locationTracker = ActiveLocationTracker(this) { sample ->
            LocationRepository.updateLocation(
                latitude = sample.latitude,
                longitude = sample.longitude,
                accuracyMeters = sample.accuracyMeters,
                speedMetersPerSec = sample.speedMetersPerSec,
                bearingDegrees = sample.bearingDegrees,
                altitudeMeters = sample.altitudeMeters
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        LocationRepository.setTrackingStarted(hasBackground)
        locationTracker.start()

        return START_STICKY
    }

    private fun stopTrackingAndService() {
        locationTracker.stop()
        LocationRepository.setTrackingStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationTracker.stop()
        LocationRepository.setTrackingStopped()
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

    private fun buildNotification(): Notification {
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
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Géolocalisation active")
            .setContentText("Suivi GPS en temps réel en cours...")
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

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
