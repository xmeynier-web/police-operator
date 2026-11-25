package com.example.geotracker.xavier.policeoperator.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.geotracker.R
import com.example.geotracker.xavier.policeoperator.data.FirestoreLocationUploader
import com.example.geotracker.xavier.policeoperator.location.ActiveLocationTracker
import com.example.geotracker.xavier.policeoperator.model.CsuDispatchMessage
import com.example.geotracker.xavier.policeoperator.model.GpsSample
import com.example.geotracker.xavier.policeoperator.model.PatrolStatus
import com.example.geotracker.xavier.policeoperator.service.LocationService

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
                onRequestStart = { unitId, status ->
                    startTrackingService(unitId, status)
                },
                onRequestStop = { unitId, status, lastSample ->
                    stopTrackingService(unitId, status, lastSample)
                },
                onStatusChanged = { unitId, status, isTracking, lastSample ->
                    uploader.upsertUnitState(
                        unitId = unitId,
                        status = status,
                        isTracking = isTracking,
                        sample = lastSample,
                        recordHistory = false
                    )
                    if (isTracking) {
                        startTrackingService(unitId, status)
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

    private fun startTrackingService(unitId: String, status: PatrolStatus) {
        ensureLocationPermissions()
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra(LocationService.EXTRA_UNIT_ID, unitId)
            putExtra(LocationService.EXTRA_STATUS_CODE, status.code)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTrackingService(unitId: String, status: PatrolStatus, lastSample: GpsSample?) {
        tracker?.stop()
        uploader.upsertUnitState(
            unitId = unitId,
            status = status,
            isTracking = false,
            sample = lastSample,
            recordHistory = false
        )
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
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
    onRequestStart: (String, PatrolStatus) -> Unit,
    onRequestStop: (String, PatrolStatus, GpsSample?) -> Unit,
    onStatusChanged: (String, PatrolStatus, Boolean, GpsSample?) -> Unit,
    bindSampleListener: (((GpsSample) -> Unit)?) -> Unit
) {
    var unitId by remember { mutableStateOf("ALPHA 1") }
    var currentStatus by remember { mutableStateOf(PatrolStatus.PATROUILLE) }
    var isTracking by remember { mutableStateOf(false) }
    var lastSample by remember { mutableStateOf<GpsSample?>(null) }
    val messages = remember { mutableStateListOf<CsuDispatchMessage>() }
    val localAckIds = remember { mutableStateListOf<String>() }

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
            status = currentStatus,
            isTracking = false,
            sample = null,
            recordHistory = false
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF162032))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(9.dp))
                        .border(1.5.dp, Color(currentStatus.colorHex), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_police_op_logo),
                        contentDescription = "Icône Police OP",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Police OP",
                    color = Color(0xFFF8FAFC),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val nextTracking = !isTracking
                        isTracking = nextTracking
                        if (nextTracking) {
                            onRequestStart(unitId, currentStatus)
                        } else {
                            onRequestStop(unitId, currentStatus, lastSample)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) Color(0xFFDC2626) else Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isTracking) "Stop" else "Actif",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = if (isTracking) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val gpsSummary = lastSample?.let { s ->
                    String.format(
                        "%.5f, %.5f · %.1f km/h",
                        s.latitude,
                        s.longitude,
                        s.speedKmh
                    )
                } ?: "GPS en attente"
                Text(
                    text = gpsSummary,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = unitId,
                    onValueChange = { unitId = it.uppercase() },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.width(104.dp)
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PatrolStatus.entries.forEach { status ->
                    val selected = currentStatus == status
                    val badgeColor = Color(status.colorHex)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            currentStatus = status
                            onStatusChanged(unitId, status, isTracking, lastSample)
                        },
                        label = {
                            Text(
                                text = status.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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

            HorizontalDivider(color = Color(0xFF334155))

            Text(
                text = "Messages CSU",
                color = Color(0xFF38BDF8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isAck = localAckIds.contains(msg.id) || !msg.acknowledgedBy.isNullOrBlank()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isAck) Color(0xFF16A34A) else Color(0xFF38BDF8),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = msg.title,
                                color = Color(0xFFF8FAFC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (msg.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.notes,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAck) Color(0xFF1E293B) else Color(0xFF16A34A),
                                        disabledContainerColor = Color(0xFF1E293B),
                                        contentColor = Color.White,
                                        disabledContentColor = Color(0xFF4ADE80)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    if (isAck) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (isAck) "Validé" else "Valider",
                                        fontSize = 12.sp,
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
