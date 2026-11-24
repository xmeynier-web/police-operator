package com.example.geotracker.ui

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
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Update
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.geotracker.model.LocationRepository
import com.example.geotracker.model.LocationTelemetry
import com.example.geotracker.service.LocationService
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                GeoTrackerMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoTrackerMainScreen() {
    val context = LocalContext.current
    val telemetry by LocationRepository.telemetry.collectAsState()
    var unitIdInput by remember { mutableStateOf(telemetry.unitId) }

    fun effectiveUnitId(): String {
        val cleaned = unitIdInput.trim().ifEmpty { "UNITE-01" }
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
        if (!isGranted) {
            Toast.makeText(
                context,
                "Arrière-plan non accordé : suivi actif uniquement écran allumé.",
                Toast.LENGTH_LONG
            ).show()
        }
        LocationService.start(context, effectiveUnitId())
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
            LocationService.start(context, effectiveUnitId())
        } else {
            Toast.makeText(
                context,
                "Autorisation de localisation refusée.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestPermissionsAndStart() {
        val permissionsToRequest = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        foregroundPermissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Géolocalisation Active",
                            style = MaterialTheme.typography.titleLarge,
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
                            contentDescription = "Paramètres de l'application"
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
            UnitIdentityAndSyncCard(
                unitId = unitIdInput,
                enabled = !telemetry.isTracking,
                lastSyncFormatted = telemetry.lastSyncFormatted,
                lastSyncError = telemetry.lastSyncError,
                onUnitIdChange = { unitIdInput = it }
            )

            StatusBanner(telemetry = telemetry)

            SpeedHeroCard(telemetry = telemetry)

            CoordinatesSectionCard(
                telemetry = telemetry,
                onCopyCoordinates = {
                    val lat = telemetry.latitude
                    val lon = telemetry.longitude
                    if (lat != null && lon != null) {
                        val formatted = String.format(Locale.US, "%.6f, %.6f", lat, lon)
                        copyToClipboard(context, "Coordonnées GPS", formatted)
                        Toast.makeText(context, "Coordonnées copiées : $formatted", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            TelemetryDetailsGrid(telemetry = telemetry)

            Spacer(modifier = Modifier.height(6.dp))

            ActionControls(
                isTracking = telemetry.isTracking,
                onStartClick = { requestPermissionsAndStart() },
                onStopClick = { LocationService.stop(context, effectiveUnitId()) }
            )
        }
    }
}

@Composable
private fun UnitIdentityAndSyncCard(
    unitId: String,
    enabled: Boolean,
    lastSyncFormatted: String,
    lastSyncError: String?,
    onUnitIdChange: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = unitId,
                onValueChange = onUnitIdChange,
                enabled = enabled,
                singleLine = true,
                label = { Text("Identifiant unité / indicatif") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Badge, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasError = lastSyncError != null
                    Icon(
                        imageVector = if (hasError) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasError) {
                            "Sync Firestore : $lastSyncError"
                        } else {
                            "Dernier envoi Firestore : $lastSyncFormatted"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(telemetry: LocationTelemetry) {
    val containerColor = if (telemetry.isTracking) {
        Color(0xFFDCFCE7)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (telemetry.isTracking) {
        Color(0xFF14532D)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor = if (telemetry.isTracking) Color(0xFF16A34A) else Color(0xFF6B7280)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (telemetry.isTracking) "SUIVI ACTIF" else "SUIVI INACTIF",
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = if (telemetry.hasBackgroundPermission) "Arrière-plan OK" else "1er plan",
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SpeedHeroCard(telemetry: LocationTelemetry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "VITESSE INSTANTANÉE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = String.format(Locale.FRANCE, "%.1f km/h", telemetry.speedKmh),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = String.format(Locale.FRANCE, "%.2f m/s", telemetry.speedMetersPerSec),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CoordinatesSectionCard(
    telemetry: LocationTelemetry,
    onCopyCoordinates: () -> Unit
) {
    val hasCoords = telemetry.latitude != null && telemetry.longitude != null
    val latText = telemetry.latitude?.let { String.format(Locale.US, "%.6f°", it) } ?: "En attente..."
    val lonText = telemetry.longitude?.let { String.format(Locale.US, "%.6f°", it) } ?: "En attente..."

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "COORDONNÉES GPS (WGS84)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onCopyCoordinates,
                    enabled = hasCoords
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copier les coordonnées"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoordinateBox(
                    label = "LATITUDE",
                    value = latText,
                    modifier = Modifier.weight(1f)
                )
                CoordinateBox(
                    label = "LONGITUDE",
                    value = lonText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CoordinateBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TelemetryDetailsGrid(telemetry: LocationTelemetry) {
    val accuracyText = telemetry.accuracyMeters?.let {
        String.format(Locale.FRANCE, "± %.1f m", it)
    } ?: "--"

    val bearingText = telemetry.bearingDegrees?.let { deg ->
        val cardinal = degreesToCardinal(deg)
        String.format(Locale.FRANCE, "%.0f° (%s)", deg, cardinal)
    } ?: "--"

    val altitudeText = telemetry.altitudeMeters?.let {
        String.format(Locale.FRANCE, "%.1f m", it)
    } ?: "--"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                icon = Icons.Outlined.GpsFixed,
                label = "PRÉCISION",
                value = accuracyText,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Outlined.Explore,
                label = "CAP / AZIMUT",
                value = bearingText,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                icon = Icons.Outlined.Terrain,
                label = "ALTITUDE",
                value = altitudeText,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Outlined.Update,
                label = "DERNIER RELEVÉ (${telemetry.fixCount})",
                value = telemetry.lastUpdateFormatted,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActionControls(
    isTracking: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onStartClick,
            enabled = !isTracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Démarrer le suivi GPS",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedButton(
            onClick = onStopClick,
            enabled = isTracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(imageVector = Icons.Outlined.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Arrêter le suivi",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun degreesToCardinal(degrees: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val normalized = ((degrees % 360f) + 360f) % 360f
    val index = ((normalized + 22.5f) / 45f).toInt() % 8
    return directions[index]
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
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
