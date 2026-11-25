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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    var unitNameInput by remember { mutableStateOf(telemetry.unitName) }
    var missionNoteInput by remember { mutableStateOf(telemetry.missionNote) }
    var customEquipmentInput by remember { mutableStateOf("") }

    fun syncFormState(): Triple<String, String, String> {
        val cleanedId = unitIdInput.trim().uppercase(Locale.ROOT).ifEmpty { "UNITE-01" }
        val cleanedName = unitNameInput.trim().ifEmpty { cleanedId }
        val cleanedNote = missionNoteInput.trim()
        LocationRepository.setUnitIdentity(cleanedId, cleanedName)
        LocationRepository.updateMissionNote(cleanedNote)
        return Triple(cleanedId, cleanedName, cleanedNote)
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
        val (id, name, note) = syncFormState()
        LocationService.start(
            context = context,
            unitId = id,
            unitName = name,
            status = telemetry.operationalStatus,
            equipment = telemetry.selectedEquipment.toList().sorted(),
            missionNote = note
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
            val (id, name, note) = syncFormState()
            LocationService.start(
                context = context,
                unitId = id,
                unitName = name,
                status = telemetry.operationalStatus,
                equipment = telemetry.selectedEquipment.toList().sorted(),
                missionNote = note
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
                            text = "${telemetry.unitName} · ${telemetry.unitId}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = telemetry.statusMessage,
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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

            UnitNamingCard(
                unitId = unitIdInput,
                unitName = unitNameInput,
                missionNote = missionNoteInput,
                lockUnitId = telemetry.isTracking,
                onUnitIdChange = { unitIdInput = it },
                onUnitNameChange = {
                    unitNameInput = it
                    LocationRepository.setUnitIdentity(unitIdInput, it)
                },
                onMissionNoteChange = {
                    missionNoteInput = it
                    LocationRepository.updateMissionNote(it)
                }
            )

            OperationalStatusSection(
                selectedStatus = telemetry.operationalStatus,
                onSelectStatus = { LocationRepository.updateOperationalStatus(it) }
            )

            OnboardEquipmentSection(
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

            CompactGeolocationCard(
                telemetry = telemetry,
                onCopyCoords = {
                    val lat = telemetry.latitude
                    val lon = telemetry.longitude
                    if (lat != null && lon != null) {
                        val txt = String.format(Locale.US, "%.6f, %.6f", lat, lon)
                        copyToClipboard(context, "Position", txt)
                        Toast.makeText(context, "Position copiée : $txt", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            ActionButtonsRow(
                isTracking = telemetry.isTracking,
                onStart = { requestPermissionsAndStart() },
                onStop = {
                    val (id, name, note) = syncFormState()
                    LocationService.stop(
                        context = context,
                        unitId = id,
                        unitName = name,
                        status = telemetry.operationalStatus,
                        equipment = telemetry.selectedEquipment.toList().sorted(),
                        missionNote = note
                    )
                }
            )
        }
    }
}

@Composable
private fun LiveStatusAndSyncBar(telemetry: LocationTelemetry) {
    val bg = if (telemetry.isTracking) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (telemetry.isTracking) Color(0xFF14532D) else MaterialTheme.colorScheme.onSurfaceVariant
    val dot = if (telemetry.isTracking) Color(0xFF16A34A) else Color(0xFF6B7280)

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
private fun UnitNamingCard(
    unitId: String,
    unitName: String,
    missionNote: String,
    lockUnitId: Boolean,
    onUnitIdChange: (String) -> Unit,
    onUnitNameChange: (String) -> Unit,
    onMissionNoteChange: (String) -> Unit
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
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "IDENTIFICATION DE L'UNITÉ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = unitId,
                    onValueChange = onUnitIdChange,
                    enabled = !lockUnitId,
                    singleLine = true,
                    label = { Text("Indicatif") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Badge, contentDescription = null)
                    },
                    modifier = Modifier.weight(0.45f)
                )

                OutlinedTextField(
                    value = unitName,
                    onValueChange = onUnitNameChange,
                    singleLine = true,
                    label = { Text("Nom d'unité") },
                    modifier = Modifier.weight(0.55f)
                )
            }

            OutlinedTextField(
                value = missionNote,
                onValueChange = onMissionNoteChange,
                singleLine = true,
                label = { Text("Secteur / consigne courte") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Description, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OperationalStatusSection(
    selectedStatus: OperationalStatus,
    onSelectStatus: (OperationalStatus) -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STATUT OPÉRATIONNEL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OperationalStatus.entries.forEach { status ->
                    val selected = status == selectedStatus
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectStatus(status) },
                        label = { Text(status.label) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardEquipmentSection(
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MATÉRIEL EMBARQUÉ",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${selectedEquipment.size} actif(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                allChoices.forEach { item ->
                    val active = item in selectedEquipment
                    FilterChip(
                        selected = active,
                        onClick = { onToggleItem(item) },
                        label = { Text(item) },
                        leadingIcon = if (active) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customEquipmentInput,
                    onValueChange = onCustomEquipmentInputChange,
                    singleLine = true,
                    label = { Text("Ajouter un matériel") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddCustomItem) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Ajouter le matériel"
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactGeolocationCard(
    telemetry: LocationTelemetry,
    onCopyCoords: () -> Unit
) {
    val hasCoords = telemetry.latitude != null && telemetry.longitude != null
    val coordsText = if (hasCoords) {
        String.format(Locale.US, "%.5f, %.5f", telemetry.latitude, telemetry.longitude)
    } else {
        "En attente du signal..."
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GÉOLOCALISATION",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onCopyCoords, enabled = hasCoords) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copier la position"
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
