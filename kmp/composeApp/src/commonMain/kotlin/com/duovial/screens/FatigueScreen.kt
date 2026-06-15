package com.duovial.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duovial.platform.FrontCameraPreview
import com.duovial.platform.formatDecimal
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.theme.DuoVialAmber
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialNeonRed
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary

@Composable
fun FatigueScreen(
    serviceManager: CameraServiceManager? = null,
    onBack: () -> Unit = {}
) {
    val faceStatus by AppStateManager.faceStatus.collectAsState()
    val fatigueConfig by AppStateManager.fatigueConfig.collectAsState()

    var earThreshold by remember { mutableFloatStateOf(fatigueConfig.earThreshold.toFloat()) }
    var durationThreshold by remember { mutableLongStateOf(fatigueConfig.durationThresholdMs) }
    var maxAlerts by remember { mutableIntStateOf(fatigueConfig.maxAlertsPerHour) }
    var fatigueEnabled by remember { mutableStateOf(faceStatus.enabled) }
    var showSettings by remember { mutableStateOf(false) }

    val statusLabel = when {
        !fatigueEnabled -> "Deteccion apagada"
        !faceStatus.faceDetected -> "Buscando rostro..."
        faceStatus.earValue < earThreshold.toDouble() -> "Fatiga detectada"
        faceStatus.earValue < 0.25 -> "Ojos cerrandose"
        else -> "Conductor alerta"
    }

    val statusColor = when {
        !fatigueEnabled -> Color.White.copy(alpha = 0.7f)
        !faceStatus.faceDetected -> DuoVialAmber
        faceStatus.earValue < earThreshold.toDouble() -> DuoVialNeonRed
        faceStatus.earValue < 0.25 -> DuoVialAmber
        else -> DuoVialNeonGreen
    }

    val earPercent = (faceStatus.earValue / 0.4).coerceIn(0.0, 1.0)

    Box(modifier = Modifier.fillMaxSize().background(DuoVialBackground)) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 12.dp, end = 12.dp, bottom = 80.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
        ) {
            FrontCameraPreview(modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DuoVialBackground.copy(alpha = 0.9f))
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.W700
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { showSettings = !showSettings },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Ajustes",
                        tint = if (showSettings) DuoVialNeonGreen else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                        .clickable {
                            fatigueEnabled = !fatigueEnabled
                            serviceManager?.enableFatigueDetection(fatigueEnabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (fatigueEnabled) "DETENER" else "ACTIVAR",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 2.sp),
                        color = if (fatigueEnabled) DuoVialNeonRed else DuoVialNeonGreen,
                        fontWeight = FontWeight.W900
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Volver",
                    tint = DuoVialTextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Anti-somnolencia",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = DuoVialTextPrimary,
                fontWeight = FontWeight.W900
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.0", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text("EAR Value", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text("0.4", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { earPercent.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showSettings = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(DuoVialBackground.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .height(420.dp)
                        .clickable(enabled = false) {}
                ) {
                    Text(
                        "Configuracion",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = DuoVialTextPrimary,
                        fontWeight = FontWeight.W900
                    )
                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfigSliderRow(
                            label = "EAR Umbral",
                            description = "Sensibilidad de deteccion de ojos cerrados. Menor valor = mas sensible.",
                            value = earThreshold,
                            onValueChange = {
                                earThreshold = it
                                serviceManager?.setEarThreshold(it.toDouble())
                            },
                            valueDisplay = earThreshold.toDouble().formatDecimal(2),
                            valueRange = 0.1f..0.4f
                        )

                        ConfigSliderRow(
                            label = "Duracion ojos cerrados",
                            description = "Tiempo minimo con ojos cerrados para activar alerta.",
                            value = durationThreshold.toFloat(),
                            onValueChange = {
                                durationThreshold = it.toLong()
                                serviceManager?.setDurationThreshold(it.toLong())
                            },
                            valueDisplay = "${durationThreshold / 1000}s",
                            valueRange = 1000f..5000f,
                            steps = 4
                        )

                        ConfigSliderRow(
                            label = "Max alertas/hora",
                            description = "Limite de alertas por hora para evitar molestias.",
                            value = maxAlerts.toFloat(),
                            onValueChange = {
                                maxAlerts = it.toInt()
                                serviceManager?.setMaxAlertsPerHour(it.toInt())
                            },
                            valueDisplay = "$maxAlerts",
                            valueRange = 1f..5f,
                            steps = 4
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSliderRow(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = DuoVialTextPrimary,
                        fontWeight = FontWeight.W700
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = description,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                valueDisplay,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = DuoVialNeonGreen,
                fontWeight = FontWeight.W800
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = DuoVialNeonGreen,
                activeTrackColor = DuoVialNeonGreen,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}
