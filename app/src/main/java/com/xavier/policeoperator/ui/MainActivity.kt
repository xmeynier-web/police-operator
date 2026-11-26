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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
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
        if (isFinishing) {
            tracker?.stop()
        }
        onSampleCallback = null
        super.onDestroy()
    }
}

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
    var syncError by remember { mutableStateOf<String?>(null) }

    val selectedEquipment = remember { mutableStateListOf<String>() }
    val messages = remember { mutableStateListOf<CsuDispatchMessage>() }
    val localAckIds = remember { mutableStateListOf<String>() }

    val selectableStatuses = remember {
        PatrolStatus.entries.filter { it != PatrolStatus.FIN_DE_SERVICE }
    }
    val statusRows = remember(selectableStatuses) {
        selectableStatuses.chunked(2)
    }
    val equipmentRows = remember {
        DEFAULT_EQUIPMENT_LIST.chunked(2)
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
        localAckIds.clear()
        val reg = uploader.observeCsuMessages(
            unitId = unitId,
            onUpdate = { list ->
                syncError = null
                messages.clear()
                messages.addAll(list)
            },
            onError = { err ->
                syncError = err.localizedMessage ?: "Erreur de synchronisation CSU"
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
            recordHistory = false,
            onError = { err ->
                syncError = err.localizedMessage ?: "Erreur d'authentification ou Firestore"
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 580.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = 64.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "header") {
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
                }

                item(key = "gps_and_error") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

                        syncError?.let { errText ->
                            Text(
                                text = errText,
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item(key = "unit_and_crew") {
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
                }

                item(key = "statuses") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusRows.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { status ->
                                    val selected = currentStatus == status
                                    val badgeColor = Color(status.colorHex)
                                    val textColor = if (selected) {
                                        if (status == PatrolStatus.CONTROLE_ROUTE) Color(0xFF082F49) else Color.White
                                    } else {
                                        Color(0xFFE2E8F0)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) badgeColor else Color(0xFF162032))
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) badgeColor else Color(0xFF334155),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                currentStatus = status
                                                onStateChanged(
                                                    unitId,
                                                    crewCount,
                                                    status,
                                                    isTracking,
                                                    selectedEquipment.toList(),
                                                    lastSample
                                                )
                                            }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = status.label,
                                            color = textColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

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
                    }
                }

                item(key = "equipment") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Matériel embarqué",
                            color = Color(0xFF38BDF8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        equipmentRows.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { item ->
                                    val isSelected = selectedEquipment.contains(item)
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(
                                                if (isSelected) Color(0xFF1D4ED8) else Color(0xFF162032)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF334155),
                                                shape = RoundedCornerShape(9.dp)
                                            )
                                            .clickable {
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
                                            }
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        Text(
                                            text = item,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item(key = "csu_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = Color(0xFF334155))
                        Text(
                            text = "Messages CSU",
                            color = Color(0xFF38BDF8),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (messages.isEmpty()) {
                    item(key = "csu_empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Aucune mission ou message CSU en attente.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isAck = localAckIds.contains(msg.id) || !msg.acknowledgedBy.isNullOrBlank()
                        val ackActor = msg.acknowledgedBy?.takeIf { it.isNotBlank() } ?: unitId
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isAck) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4ADE80),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Validé par $ackActor",
                                                color = Color(0xFF4ADE80),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                    Button(
                                        onClick = {
                                            if (!isAck) {
                                                localAckIds.add(msg.id)
                                                uploader.acknowledgeMessage(
                                                    unitId = unitId,
                                                    message = msg,
                                                    onError = { err ->
                                                        localAckIds.remove(msg.id)
                                                        syncError = err.localizedMessage
                                                            ?: "Erreur lors de la validation"
                                                    }
                                                )
                                            }
                                        },
                                        enabled = !isAck,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF16A34A),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color(0xFF14532D),
                                            disabledContentColor = Color(0xFF86EFAC)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isAck) "Validé" else "Valider l'intervention",
                                            fontSize = 13.sp,
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
