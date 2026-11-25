package com.xavier.policeoperator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xavier.policeoperator.R
import com.xavier.policeoperator.data.FirestoreLocationUploader
import com.xavier.policeoperator.location.ActiveLocationTracker
import com.xavier.policeoperator.model.CsuDispatchMessage
import com.xavier.policeoperator.model.DEFAULT_EQUIPMENT_LIST
import com.xavier.policeoperator.model.GpsSample
import com.xavier.policeoperator.model.PatrolStatus
import com.xavier.policeoperator.service.LocationService

class MainActivity : ComponentActivity() {

    private val uploader = FirestoreLocationUploader()
    private var tracker: ActiveLocationTracker? = null
    private var onSampleCallback: ((GpsSample) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineOk = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineOk) {
            tracker?.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tracker = ActiveLocationTracker(this) { sample ->
            onSampleCallback?.invoke(sample)
        }

        setContent {
            PoliceOpScreen(
                uploader = uploader,
                onRequestStart = { unitId, crewCount, status, equipment ->
                    startTrackingService(unitId, crewCount, status, equipment)
                },
                onRequestStop = { unitId, crewCount, status, equipment, lastSample ->
                    stopTrackingService(
                        unitId = unitId,
                        crewCount = crewCount,
                        status = status,
                        equipment = equipment,
                        lastSample = lastSample,
                        updateFirestore = true
                    )
                },
                onEndOfService = { unitId, crewCount, equipment, lastSample ->
                    stopTrackingService(
                        unitId = unitId,
                        crewCount = crewCount,
                        status = PatrolStatus.FIN_DE_SERVICE,
                        equipment = equipment,
                        lastSample = lastSample,
                        updateFirestore = true
                    )
                },
                onStateChanged = { unitId, crewCount, status, isTracking, equipment, lastSample ->
                    uploader.upsertUnitState(
                        unitId = unitId,
                        crewCount = crewCount,
                        status = status,
                        isTracking = isTracking,
                        sample = lastSample,
                        equipment = equipment,
                        recordHistory = false
                    )
                    if (isTracking) {
                        startTrackingService(unitId, crewCount, status, equipment)
                    }
                },
                bindSampleListener = { cb ->
                    onSampleCallback = cb
                }
            )
        }
    }

    private fun ensureLocationPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            tracker?.start()
        }
    }

    private fun startTrackingService(
        unitId: String,
        crewCount: Int,
        status: PatrolStatus,
        equipment: List<String>
    ) {
        ensureLocationPermissions()
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra(LocationService.EXTRA_UNIT_ID, unitId)
            putExtra(LocationService.EXTRA_CREW_COUNT, crewCount)
            putExtra(LocationService.EXTRA_STATUS_CODE, status.code)
            putStringArrayListExtra(LocationService.EXTRA_EQUIPMENT, ArrayList(equipment))
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTrackingService(
        unitId: String,
        crewCount: Int,
        status: PatrolStatus,
        equipment: List<String>,
        lastSample: GpsSample?,
        updateFirestore: Boolean
    ) {
        tracker?.stop()
        if (updateFirestore) {
            uploader.upsertUnitState(
                unitId = unitId,
                crewCount = crewCount,
                status = status,
                isTracking = false,
                sample = lastSample,
                equipment = equipment,
                recordHistory = false
            )
        }
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
            putExtra(LocationService.EXTRA_UNIT_ID, unitId)
            putExtra(LocationService.EXTRA_CREW_COUNT, crewCount)
            putExtra(LocationService.EXTRA_STATUS_CODE, status.code)
            putStringArrayListExtra(LocationService.EXTRA_EQUIPMENT, ArrayList(equipment))
        }
        startService(intent)
    }

    override fun onDestroy() {
        tracker?.stop()
        onSampleCallback = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoliceOpScreen(
    uploader: FirestoreLocationUploader,
    onRequestStart: (String, Int, PatrolStatus, List<String>) -> Unit,
    onRequestStop: (String, Int, PatrolStatus, List<String>, GpsSample?) -> Unit,
    onEndOfService: (String, Int, List<String>, GpsSample?) -> Unit,
    onStateChanged: (String, Int, PatrolStatus, Boolean, List<String>, GpsSample?) -> Unit,
    bindSampleListener: (((GpsSample) -> Unit)?) -> Unit
) {
    var unitId by remember { mutableStateOf("ALPHA 1") }
    var crewCountText by remember { mutableStateOf("2") }
    val crewCount = crewCountText.toIntOrNull()?.coerceAtLeast(1) ?: 1

    var currentStatus by remember { mutableStateOf(PatrolStatus.PATROUILLE) }
    var isTracking by remember { mutableStateOf(false) }
    var lastSample by remember { mutableStateOf<GpsSample?>(null) }

    val selectedEquipment = remember {
        mutableStateListOf<String>().apply { addAll(DEFAULT_EQUIPMENT_LIST) }
    }
    val messages = remember { mutableStateListOf<CsuDispatchMessage>() }
    val localAckIds = remember { mutableStateListOf<String>() }

    val selectableStatuses = remember {
        PatrolStatus.entries.filter { it != PatrolStatus.FIN_DE_SERVICE }
    }

    DisposableEffect(Unit) {
        bindSampleListener { sample ->
            lastSample = sample
        }
        onDispose {
            bindSampleListener(null)
        }
    }

    DisposableEffect(unitId) {
        val reg = uploader.observeCsuMessages(
            unitId = unitId,
            onUpdate = { list ->
                messages.clear()
                messages.addAll(list)
            }
        )
        onDispose { reg.remove() }
    }

    LaunchedEffect(Unit) {
        uploader.upsertUnitState(
            unitId = unitId,
            crewCount = crewCount,
            status = currentStatus,
            isTracking = false,
            sample = null,
            equipment = selectedEquipment.toList(),
            recordHistory = false
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 580.dp)
            ) {
                // 1. En-tête : logo, Police OP, statut courant, bouton Actif / Stop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF162032), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .border(2.dp, Color(currentStatus.colorHex), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_police_op_logo),
                            contentDescription = "Icône Police OP",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Police OP",
                            color = Color(0xFFF8FAFC),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Statut : ${currentStatus.label}",
                            color = Color(currentStatus.colorHex),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val nextTracking = !isTracking
                            isTracking = nextTracking
                            if (nextTracking) {
                                if (currentStatus == PatrolStatus.FIN_DE_SERVICE) {
                                    currentStatus = PatrolStatus.PATROUILLE
                                }
                                onRequestStart(
                                    unitId,
                                    crewCount,
                                    currentStatus,
                                    selectedEquipment.toList()
                                )
                            } else {
                                onRequestStop(
                                    unitId,
                                    crewCount,
                                    currentStatus,
                                    selectedEquipment.toList(),
                                    lastSample
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTracking) Color(0xFFDC2626) else Color(0xFF16A34A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isTracking) "Stop" else "Actif",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Bandeau géolocalisation en haut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = null,
                        tint = if (isTracking) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val gpsSummary = lastSample?.let { s ->
                        String.format(
                            "%.5f, %.5f · %.1f km/h (±%.0fm)",
                            s.latitude,
                            s.longitude,
                            s.speedKmh,
                            s.accuracyMeters
                        )
                    } ?: "GPS en attente de position"
                    Text(
                        text = gpsSummary,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Indicatif équipage + case effectif à bord (nombre d'agents)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = unitId,
                        onValueChange = { unitId = it.uppercase() },
                        singleLine = true,
                        label = { Text("Indicatif", fontSize = 11.sp) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = crewCountText,
                        onValueChange = { raw ->
                            crewCountText = raw.filter { it.isDigit() }.take(2)
                        },
                        singleLine = true,
                        label = { Text("Effectif", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(126.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Boutons de statuts élargis
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectableStatuses.forEach { status ->
                        val selected = currentStatus == status
                        val badgeColor = Color(status.colorHex)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                currentStatus = status
                                onStateChanged(
                                    unitId,
                                    crewCount,
                                    status,
                                    isTracking,
                                    selectedEquipment.toList(),
                                    lastSample
                                )
                            },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                Text(
                                    text = status.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF162032),
                                labelColor = Color(0xFFE2E8F0),
                                selectedContainerColor = badgeColor,
                                selectedLabelColor = if (status == PatrolStatus.CONTROLE_ROUTE) {
                                    Color(0xFF082F49)
                                } else {
                                    Color.White
                                }
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Bouton dédié Fin de service (fait aussi office de statut)
                val isEndOfService = currentStatus == PatrolStatus.FIN_DE_SERVICE && !isTracking
                OutlinedButton(
                    onClick = {
                        currentStatus = PatrolStatus.FIN_DE_SERVICE
                        isTracking = false
                        onEndOfService(
                            unitId,
                            crewCount,
                            selectedEquipment.toList(),
                            lastSample
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isEndOfService) Color(0xFF475569) else Color(0xFF162032),
                        contentColor = Color(0xFFF8FAFC)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isEndOfService) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEndOfService) "Statut : Fin de service" else "Fin de service",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 6. Matériel embarqué par l'équipage
                Text(
                    text = "Matériel embarqué",
                    color = Color(0xFF38BDF8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DEFAULT_EQUIPMENT_LIST.forEach { item ->
                        val isSelected = selectedEquipment.contains(item)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedEquipment.remove(item)
                                } else {
                                    selectedEquipment.add(item)
                                }
                                onStateChanged(
                                    unitId,
                                    crewCount,
                                    currentStatus,
                                    isTracking,
                                    selectedEquipment.toList(),
                                    lastSample
                                )
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(9.dp),
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = item,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF162032),
                                labelColor = Color(0xFFCBD5E1),
                                selectedContainerColor = Color(0xFF1D4ED8),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF334155))

                // 7. Messages CSU
                Text(
                    text = "Messages CSU",
                    color = Color(0xFF38BDF8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isAck = localAckIds.contains(msg.id) || !msg.acknowledgedBy.isNullOrBlank()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.5.dp,
                                    color = if (isAck) Color(0xFF16A34A) else Color(0xFF38BDF8),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.title,
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (msg.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.notes,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            if (!isAck) {
                                                localAckIds.add(msg.id)
                                                uploader.acknowledgeMessage(unitId, msg)
                                            }
                                        },
                                        enabled = !isAck,
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAck) Color(0xFF1E293B) else Color(0xFF16A34A),
                                            disabledContainerColor = Color(0xFF1E293B),
                                            contentColor = Color.White,
                                            disabledContentColor = Color(0xFF4ADE80)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        if (isAck) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (isAck) "Validé" else "Valider",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
