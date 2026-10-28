package com.xavier.policeoperator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Structure visuelle ergonomique similaire à l'interface originale
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        val title = TextView(this).apply {
            text = "Police Operator"
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor("#00bcd4"))
            setPadding(0, 0, 0, 24)
        }

        val statusText = TextView(this).apply {
            text = "Statut : En attente des autorisations GPS..."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#aaaaaa"))
            setPadding(0, 0, 0, 48)
        }

        val btnToggle = Button(this).apply {
            text = "Démarrer le Service GPS"
            setBackgroundColor(android.graphics.Color.parseColor("#00bcd4"))
            setTextColor(android.graphics.Color.parseColor("#121212"))
            setOnClickListener {
                if (checkLocationPermissions()) {
                    startTrackingService()
                    statusText.text = "Statut : Transmission GPS active (Unite_01)"
                    text = "Service Actif"
                } else {
                    requestLocationPermissions()
                }
            }
        }

        val footer = TextView(this).apply {
            text = "Conception & Architecture : Xavier Meynier-Laloi"
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            setPadding(0, 96, 0, 0)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(btnToggle)
        layout.addView(footer)

        setContentView(layout)

        // Vérification automatique au lancement
        if (checkLocationPermissions()) {
            statusText.text = "Statut : Prêt à démarrer"
        } else {
            requestLocationPermissions()
        }
    }

    private fun checkLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineLocation
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
