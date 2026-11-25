package com.xavier.policeoperator.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xavier.policeoperator.model.LocationRepository
import com.xavier.policeoperator.model.LocationTelemetry
import com.xavier.policeoperator.model.OnboardEquipmentCatalog
import com.xavier.policeoperator.model.OperationalStatus
import com.xavier.policeoperator.service.LocationService
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                OperatorConsoleScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorConsoleScreen() {
    val context = LocalContext.current
    val telemetry by LocationRepository.telemetry.collectAsState()

    var unitIdInput by remember { mutableStateOf(telemetry.unitId) }
    var customEquipmentInput by remember { mutableStateOf("") }

    fun applyUnitId(): String {
        val cleaned = unitIdInput.trim().uppercase(Locale.ROOT).ifEmpty { "UNITE-01" }
        unitIdInput = cleaned
        LocationRepository.setUnitId(cleaned)
        return cleaned
    }

    LaunchedEffect(Unit) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        LocationRepository.setBackgroundPermissionState(granted)
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        LocationRepository.setBackgroundPermissionState(isGranted)
        val id = applyUnitId()
        LocationService.start(
            context = context,
            unitId = id,
            status = telemetry.operationalStatus,
            equipment = telemetry.selectedEquipment.toList().sorted()
        )
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bgGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                LocationRepository.setBackgroundPermissionState(bgGranted)

                if (!bgGranted) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    return@rememberLauncherForActivityResult
                }
            }
            val id = applyUnitId()
            LocationService.start(
                context = context,
                unitId = id,
                status = telemetry.operationalStatus,
                equipment = telemetry.selectedEquipment.toList().sorted()
            )
        } else {
            Toast.makeText(context, "Permission GPS refusée", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPermissionsAndStart() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        foregroundPermissionLauncher.launch(perms)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Police Operator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${telemetry.unitId} · ${telemetry.statusMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { openAppSettings(context) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Paramètres"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LiveStatusAndSyncBar(telemetry = telemetry)

            UnitIdCard(
                unitId = unitIdInput,
                onUnitIdChange = { unitIdInput = it },
                onApplyUnitId = { applyUnitId() }
            )

            OperationalStatusGrid(
                selectedStatus = telemetry.operationalStatus,
                onSelectStatus = { LocationRepository.updateOperationalStatus(it) }
            )

            EquipmentChecklistCard(
                selectedEquipment = telemetry.selectedEquipment,
                customEquipmentInput = customEquipmentInput,
                onCustomEquipmentInputChange = { customEquipmentInput = it },
                onToggleItem = { LocationRepository.toggleEquipment(it) },
                onAddCustomItem = {
                    val trimmed = customEquipmentInput.trim()
                    if (trimmed.isNotEmpty()) {
                        val next = telemetry.selectedEquipment.toMutableSet().apply { add(trimmed) }
                        LocationRepository.setEquipmentList(next)
                        customEquipmentInput = ""
                    }
                }
            )

            GeolocationSummaryCard(
                telemetry = telemetry,
                onCopyCoords = {
                    val lat = telemetry.latitude
                    val lon = telemetry.longitude
                    if (lat != null && lon != null) {
                        val txt = String.format(Locale.US, "%.6f, %.6f", lat, lon)
                        copyToClipboard(context, "Position", txt)
                        Toast.makeText(context, "Position copiée", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            ActionButtonsRow(
                isTracking = telemetry.isTracking,
                onStart = { requestPermissionsAndStart() },
                onStop = {
                    val id = applyUnitId()
                    LocationService.stop(
                        context = context,
                        unitId = id,
                        status = telemetry.operationalStatus,
                        equipment = telemetry.selectedEquipment.toList().sorted()
                    )
                }
            )
        }
    }
}

@Composable
private fun LiveStatusAndSyncBar(telemetry: LocationTelemetry) {
    val bg = if (telemetry.isTracking) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (telemetry.isTracking) Color(0xFF14532D) else MaterialTheme.colorScheme.onSurface
    val dot = if (telemetry.isTracking) Color(0xFF16A34A) else Color(0xFF64748B)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = telemetry.operationalStatus.label.uppercase(Locale.FRANCE),
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasError = telemetry.lastSyncError != null
                Icon(
                    imageVector = if (hasError) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (hasError) MaterialTheme.colorScheme.error else fg
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (hasError) "Erreur synchro" else "Synchro ${telemetry.lastSyncFormatted}",
                    color = if (hasError) MaterialTheme.colorScheme.error else fg,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UnitIdCard(
    unitId: String,
    onUnitIdChange: (String) -> Unit,
    onApplyUnitId: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "INDICATIF DE L'UNITÉ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = unitId,
                    onValueChange = onUnitIdChange,
                    singleLine = true,
                    label = { Text("Indicatif (ex. UNITE-01)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onApplyUnitId,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("Valider")
                }
            }
        }
    }
}

@Composable
private fun OperationalStatusGrid(
    selectedStatus: OperationalStatus,
    onSelectStatus: (OperationalStatus) -> Unit
) {
    val rows = OperationalStatus.entries.chunked(2)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STATUT OPÉRATIONNEL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { status ->
                        val isSelected = status == selectedStatus
                        val accentColor = when (status) {
                            OperationalStatus.PATROUILLE -> Color(0xFF2563EB)
                            OperationalStatus.INTERVENTION -> Color(0xFFDC2626)
                            OperationalStatus.DISPONIBLE -> Color(0xFF16A34A)
                            OperationalStatus.PAUSE -> Color(0xFFD97706)
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clickable { onSelectStatus(status) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status.label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentChecklistCard(
    selectedEquipment: Set<String>,
    customEquipmentInput: String,
    onCustomEquipmentInputChange: (String) -> Unit,
    onToggleItem: (String) -> Unit,
    onAddCustomItem: () -> Unit
) {
    val allChoices = remember(selectedEquipment) {
        (OnboardEquipmentCatalog.defaultItems + selectedEquipment).distinct()
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MATÉRIEL EMBARQUÉ",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${selectedEquipment.size} coché(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            allChoices.forEach { item ->
                val checked = item in selectedEquipment
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleItem(item) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (checked) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item,
                            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(
                            imageVector = if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customEquipmentInput,
                    onValueChange = onCustomEquipmentInputChange,
                    singleLine = true,
                    label = { Text("Autre équipement") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddCustomItem) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Ajouter équipement"
                    )
                }
            }
        }
    }
}

@Composable
private fun GeolocationSummaryCard(
    telemetry: LocationTelemetry,
    onCopyCoords: () -> Unit
) {
    val hasCoords = telemetry.latitude != null && telemetry.longitude != null
    val coordsText = if (hasCoords) {
        String.format(Locale.US, "%.5f, %.5f", telemetry.latitude, telemetry.longitude)
    } else {
        "En attente du signal GPS..."
    }
    val accuracyText = telemetry.accuracyMeters?.let {
        String.format(Locale.FRANCE, "± %.0f m", it)
    } ?: "--"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GÉOLOCALISATION",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onCopyCoords, enabled = hasCoords) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copier coordonnées"
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = coordsText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.FRANCE, "%.1f km/h", telemetry.speedKmh),
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.GpsFixed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = accuracyText, fontWeight = FontWeight.SemiBold)
                }

                Text(
                    text = telemetry.lastUpdateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    isTracking: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onStart,
            enabled = !isTracking,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Démarrer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onStop,
            enabled = isTracking,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(imageVector = Icons.Outlined.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Arrêter", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
