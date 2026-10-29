package com.xavier.policeoperator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val db = FirebaseFirestore.getInstance()
    private val unitId = "Unite_01"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#0f172a"))
        }

        val title = TextView(this).apply {
            text = "Police Operator"
            textSize = 22f
            setTextColor(Color.parseColor("#06b6d4"))
            setPadding(0, 0, 0, 16)
        }

        val statusText = TextView(this).apply {
            text = "Statut : Prêt au déploiement"
            textSize = 14f
            setTextColor(Color.parseColor("#94a3b8"))
            setPadding(0, 0, 0, 32)
        }

        val btnStart = Button(this).apply {
            text = "Démarrer le Tracking GPS"
            setBackgroundColor(Color.parseColor("#06b6d4"))
            setTextColor(Color.parseColor("#0f172a"))
            setOnClickListener {
                if (checkLocationPermissions()) {
                    startTrackingService()
                    statusText.text = "Statut : Actif sur le terrain"
                } else {
                    requestLocationPermissions()
                }
            }
        }

        val btnSos = Button(this).apply {
            text = "🚨 ALERTE SOS / URGENCE"
            setBackgroundColor(Color.parseColor("#ef4444"))
            setTextColor(Color.parseColor("#ffffff"))
            setOnClickListener {
                triggerSos()
                statusText.text = "STATUT : ALERTE SOS EMPISE !"
            }
        }
        btnSos.setPadding(0, 24, 0, 0)

        val footer = TextView(this).apply {
            text = "Conception & Architecture : Xavier Meynier-Laloi"
            textSize = 11f
            setTextColor(Color.parseColor("#64748b"))
            setPadding(0, 64, 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(btnStart)
        layout.addView(btnSos)
        layout.addView(footer)

        setContentView(layout)
        if (!checkLocationPermissions()) requestLocationPermissions()
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun startTrackingService() {
        val intent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun triggerSos() {
        db.collection("units").document(unitId).update("status", "SOS")
    }
}
