package com.xavier.policeoperator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val db = FirebaseFirestore.getInstance()
    
    private var unitId = ""
    private var currentStatusCode = "EN_PATROUILLE"
    private var currentStatusLabel = "En Patrouille"
    private var currentStatusColor = "#22bb33"

    // Dictionnaire du matériel embarqué
    private val equipmentState = mutableMapOf(
        "Stop-Stick" to false, "Bouclier" to false, "GAIL" to false, "LBD" to false,
        "PIE" to false, "BTD" to false, "PSA" to false, "Casque" to false,
        "Trousse PS" to false, "Ethylo" to false, "Kit Stup" to false, "DSA" to false
    )

    private val statusList = listOf(
        mapOf("label" to "En Patrouille", "code" to "EN_PATROUILLE", "hex" to "#22bb33"),
        mapOf("label" to "En Intervention", "code" to "EN_INTERVENTION", "hex" to "#ff8800"),
        mapOf("label" to "Contrôle PR", "code" to "CONTROLE_PR", "hex" to "#f1c40f"),
        mapOf("label" to "Transport Escorte", "code" to "TRANSPORT_ESCORTE", "hex" to "#00a8ff"),
        mapOf("label" to "Attente Fourrière", "code" to "ATTENTE_FOURRIERE", "hex" to "#009688"),
        mapOf("label" to "Escale Technique", "code" to "ESCALE_TECHNIQUE", "hex" to "#e91e63"),
        mapOf("label" to "Retour au poste", "code" to "RETOUR_POSTE", "hex" to "#9c27b0"),
        mapOf("label" to "Indisponible", "code" to "INDISPONIBLE", "hex" to "#777777")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLoginDialog()
    }

    private fun showLoginDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#121319"))
        }

        val title = TextView(this).apply {
            text = "REZO+ // Connexion Équipement"
            textSize = 18f
            setTextColor(Color.parseColor("#00a8ff"))
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }

        val input = EditText(this).apply {
            hint = "Ex: ALPHA, Patrouille-01..."
            setTextColor(Color.white)
            setHintTextColor(Color.parseColor("#8e95a5"))
            setBackgroundColor(Color.parseColor("#1e2028"))
            setPadding(24, 24, 24, 24)
        }

        val btnConnect = Button(this).apply {
            text = "Démarrer la prise de service"
            setBackgroundColor(Color.parseColor("#00a8ff"))
            setTextColor(Color.white)
            setPadding(0, 24, 0, 24)
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    unitId = text
                    initDashboardUI()
                } else {
                    Toast.makeText(this@MainActivity, "Veuillez entrer un indicatif valide.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(input)
        layout.addView(Space(this).apply { minimumHeight = 24 })
        layout.addView(btnConnect)

        setContentView(layout)
        if (!checkLocationPermissions()) requestLocationPermissions()
    }

    private fun initDashboardUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#121319"))
        }

        val scrollView = ScrollView(this).apply {
            addView(rootLayout)
        }

        // 1. Bouton SOS Principal
        val btnSos = Button(this).apply {
            text = "🚨 DÉCLENCHER UN SOS"
            setBackgroundColor(Color.parseColor("#ff2222"))
            setTextColor(Color.white)
            setOnClickListener {
                if (currentStatusCode == "SOS_ACTIF") {
                    updateStatus("EN_PATROUILLE", "En Patrouille", "#22bb33")
                } else {
                    updateStatus("SOS_ACTIF", "SOS ACTIF", "#ff2222")
                }
            }
        }
        rootLayout.addView(btnSos)
        rootLayout.addView(Space(this).apply { minimumHeight = 16 })

        // 2. Bannière de Statut Actuel
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#22bb33"))
        }
        val statusTitle = TextView(this).apply {
            text = "STATUT OPÉRATIONNEL ACTUEL"
            textSize = 11f
            setTextColor(Color.parseColor("#ffffff"))
            gravity = Gravity.CENTER
        }
        val statusValue = TextView(this).apply {
            text = "EN PATROUILLE"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.white)
            gravity = Gravity.CENTER
        }
        statusCard.addView(statusTitle)
        statusCard.addView(statusValue)
        rootLayout.addView(statusCard)
        rootLayout.addView(Space(this).apply { minimumHeight = 16 })

        // 3. Grille des Statuts
        val gridLayout = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        
        statusList.forEach { s ->
            val btn = Button(this).apply {
                text = s["label"]
                setBackgroundColor(Color.parseColor(s["hex"]))
                setTextColor(Color.white)
                setOnClickListener {
                    updateStatus(s["code"]!, s["label"]!, s["hex"]!)
                    statusCard.setBackgroundColor(Color.parseColor(s["hex"]))
                    statusValue.text = s["label"]!!.uppercase()
                }
            }
            gridLayout.addView(btn)
        }
        rootLayout.addView(gridLayout)

        setContentView(scrollView)
        startTrackingService()
        listenToMissions()
    }

    private fun updateStatus(code: String, label: String, hex: String) {
        currentStatusCode = code
        currentStatusLabel = label
        currentStatusColor = hex
        
        val data = mapOf(
            "status" to code,
            "statusLabel" to label,
            "statusColor" to hex,
            "equipment" to equipmentState,
            "sos" to (code == "SOS_ACTIF")
        )
        db.collection("units").document(unitId).set(data, SetOptions.merge())
        Toast.makeText(this, "Statut : $label", Toast.LENGTH_SHORT).show()
    }

    private fun listenToMissions() {
        db.collection("units").document(unitId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val mission = snapshot.get("active_mission") as? Map<*, *>
                if (mission != null) {
                    // Mission CSU reçue en direct
                    val title = mission["title"] ?: mission["titre"] ?: "Intervention"
                    Toast.makeText(this, "Nouvelle mission CSU : $title", slangDuration()).show()
                }
            }
        }
    }

    private fun slangDuration() = Toast.LENGTH_LONG

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
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            putExtra("UNIT_ID", unitId)
            putExtra("CURRENT_STATUS", currentStatusCode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
