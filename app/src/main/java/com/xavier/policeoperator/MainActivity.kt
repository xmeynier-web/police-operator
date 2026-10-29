package com.xavier.policeoperator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val db = FirebaseFirestore.getInstance()
    
    private var unitId = ""
    private var currentStatusCode = "EN_PATROUILLE"
    private var currentStatusLabel = "En Patrouille"
    private var currentStatusColor = "#22bb33"

    private val equipmentState = mutableMapOf(
        "Stop-Stick" to false, "Bouclier" to false, "GAIL" to false, "LBD" to false,
        "PIE" to false, "BTD" to false, "PSA" to false, "Casque" to false,
        "Trousse PS" to false, "Ethylo" to false, "Kit Stup" to false, "DSA" to false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLoginDialog()
    }

    private fun showLoginDialog() {
        val input = EditText(this).apply {
            hint = "Ex: ALPHA, Patrouille-01..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8e95a5"))
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("REZO+ // Connexion Équipement")
            .setMessage("Entrez votre indicatif radio :")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Démarrer") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    unitId = text
                    initDashboard()
                } else {
                    showLoginDialog()
                }
            }
            .show()

        if (!checkLocationPermissions()) requestLocationPermissions()
    }

    private fun initDashboard() {
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.tvUnitTitle).text = "Unité : $unitId"

        // Bouton SOS
        findViewById<Button>(R.id.btnSos).setOnClickListener {
            if (currentStatusCode == "SOS_ACTIF") {
                updateStatus("EN_PATROUILLE", "En Patrouille", "#22bb33")
            } else {
                updateStatus("SOS_ACTIF", "SOS ACTIF", "#dc2626")
            }
        }

        // Boutons de Statut
        findViewById<Button>(R.id.btnPatrouille).setOnClickListener { updateStatus("EN_PATROUILLE", "En Patrouille", "#22bb33") }
        findViewById<Button>(R.id.btnIntervention).setOnClickListener { updateStatus("EN_INTERVENTION", "En Intervention", "#ff8800") }
        findViewById<Button>(R.id.btnControlePr).setOnClickListener { updateStatus("CONTROLE_PR", "Contrôle PR", "#009688") }
        findViewById<Button>(R.id.btnTransport).setOnClickListener { updateStatus("TRANSPORT_ESCORTE", "Transport", "#00a8ff") }
        findViewById<Button>(R.id.btnEscale).setOnClickListener { updateStatus("ESCALE_TECHNIQUE", "Escale", "#e91e63") }
        findViewById<Button>(R.id.btnRetourPoste).setOnClickListener { updateStatus("RETOUR_POSTE", "Retour au poste", "#9c27b0") }
        findViewById<Button>(R.id.btnIndisponible).setOnClickListener { updateStatus("INDISPONIBLE", "Indisponible", "#555555") }

        // Déconnexion
        findViewById<ImageView>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Fin de service")
                .setMessage("Voulez-vous vraiment quitter le service ?")
                .setPositiveButton("Confirmer") { _, _ ->
                    db.collection("units").document(unitId).delete()
                    recreate()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        // Liaison du matériel embarqué (Checkboxes)
        setupEquipmentCheckbox(R.id.cbStopStick, "Stop-Stick")
        setupEquipmentCheckbox(R.id.cbBouclier, "Bouclier")
        setupEquipmentCheckbox(R.id.cbGail, "GAIL")
        setupEquipmentCheckbox(R.id.cbLbd, "LBD")
        setupEquipmentCheckbox(R.id.cbPie, "PIE")
        setupEquipmentCheckbox(R.id.cbBtd, "BTD")
        setupEquipmentCheckbox(R.id.cbPsa, "PSA")
        setupEquipmentCheckbox(R.id.cbCasque, "Casque")
        setupEquipmentCheckbox(R.id.cbTroussePs, "Trousse PS")
        setupEquipmentCheckbox(R.id.cbEthylo, "Ethylo")
        setupEquipmentCheckbox(R.id.cbKitStup, "Kit Stup")
        setupEquipmentCheckbox(R.id.cbDsa, "DSA")

        startTrackingService()
        listenToMissions()
        updateUIState()
    }

    private fun setupEquipmentCheckbox(checkboxId: Int, key: String) {
        findViewById<CheckBox>(checkboxId)?.setOnCheckedChangeListener { _, isChecked ->
            equipmentState[key] = isChecked
            syncToFirestore()
        }
    }

    private fun updateStatus(code: String, label: String, hex: String) {
        currentStatusCode = code
        currentStatusLabel = label
        currentStatusColor = hex
        updateUIState()
        syncToFirestore()
        Toast.makeText(this, "Statut : $label", Toast.LENGTH_SHORT).show()
    }

    private fun updateUIState() {
        val card = findViewById<LinearLayout>(R.id.cardCurrentStatus)
        val valueTv = findViewById<TextView>(R.id.tvStatusValue)
        
        card?.setBackgroundColor(Color.parseColor(currentStatusColor))
        valueTv?.text = currentStatusLabel.uppercase()
    }

    private fun syncToFirestore() {
        if (unitId.isEmpty()) return
        val data = mapOf(
            "status" to currentStatusCode,
            "statusLabel" to currentStatusLabel,
            "statusColor" to currentStatusColor,
            "equipment" to equipmentState,
            "sos" to (currentStatusCode == "SOS_ACTIF"),
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("units").document(unitId).set(data, SetOptions.merge())
    }

    private fun listenToMissions() {
        db.collection("units").document(unitId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val mission = snapshot.get("active_mission") as? Map<*, *>
                if (mission != null) {
                    val title = mission["title"] ?: mission["titre"] ?: "Intervention"
                    Toast.makeText(this, "Mission CSU reçue : $title", Toast.LENGTH_LONG).show()
                }
            }
        }
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
