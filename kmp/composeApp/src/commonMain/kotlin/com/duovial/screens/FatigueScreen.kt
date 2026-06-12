package com.duovial.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duovial.platform.formatDecimal
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.theme.DuoVialAmber
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialNeonRed
import com.duovial.theme.DuoVialSurface
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
    var showAlert by remember { mutableStateOf(false) }

    val eyeStatus = when {
        !fatigueEnabled -> "Apagado"
        !faceStatus.faceDetected -> "Buscando rostro..."
        faceStatus.earValue < earThreshold.toDouble() -> "Fatiga detectada"
        faceStatus.earValue < 0.25 -> "Ojos cerrados"
        else -> "Ojos abiertos"
    }

    val statusColor = when {
        !fatigueEnabled -> DuoVialTextSecondary
        !faceStatus.faceDetected -> DuoVialAmber
        faceStatus.earValue < earThreshold.toDouble() -> DuoVialNeonRed
        faceStatus.earValue < 0.25 -> DuoVialAmber
        else -> DuoVialNeonGreen
    }

    val earPercent = (faceStatus.earValue / 0.4).coerceIn(0.0, 1.0)

    Column(
        modifier = Modifier.fillMaxSize().background(DuoVialBackground).padding(top = 48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = DuoVialTextPrimary)
            }
            Text("Anti-Somnolencia", style = MaterialTheme.typography.titleLarge, color = DuoVialTextPrimary)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !fatigueEnabled -> DuoVialSurface.copy(alpha = 0.3f)
                            statusColor == DuoVialNeonRed -> DuoVialNeonRed.copy(alpha = 0.15f)
                            statusColor == DuoVialAmber -> DuoVialAmber.copy(alpha = 0.15f)
                            else -> DuoVialNeonGreen.copy(alpha = 0.15f)
                        }
                    )
                    .border(3.dp, statusColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        faceStatus.earValue.formatDecimal(3),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                        color = statusColor, fontWeight = FontWeight.W900
                    )
                    Text("EAR", style = MaterialTheme.typography.labelSmall, color = DuoVialTextSecondary)
                }
            }

            Text(eyeStatus, style = MaterialTheme.typography.titleMedium, color = statusColor)

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.0", style = MaterialTheme.typography.labelSmall, color = DuoVialTextSecondary)
                    Text("EAR Value", style = MaterialTheme.typography.labelSmall, color = DuoVialTextSecondary)
                    Text("0.4", style = MaterialTheme.typography.labelSmall, color = DuoVialTextSecondary)
                }
                LinearProgressIndicator(
                    progress = { earPercent.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = statusColor, trackColor = DuoVialBorder,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Deteccion Frontal", style = MaterialTheme.typography.titleSmall, color = DuoVialTextPrimary)
                Button(
                    onClick = {
                        fatigueEnabled = !fatigueEnabled
                        serviceManager?.enableFatigueDetection(fatigueEnabled)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (fatigueEnabled) DuoVialNeonRed.copy(alpha = 0.2f)
                        else DuoVialNeonGreen.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (fatigueEnabled) "DETENER" else "ACTIVAR",
                        color = if (fatigueEnabled) DuoVialNeonRed else DuoVialNeonGreen,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            ConfigSlider(
                label = "EAR Umbral (${earThreshold.toDouble().formatDecimal(2)})",
                value = earThreshold,
                onValueChange = {
                    earThreshold = it
                    serviceManager?.setEarThreshold(it.toDouble())
                },
                valueRange = 0.1f..0.4f
            )

            ConfigSlider(
                label = "Duracion c/ojos cerrados (${durationThreshold / 1000}s)",
                value = durationThreshold.toFloat(),
                onValueChange = {
                    durationThreshold = it.toLong()
                    serviceManager?.setDurationThreshold(it.toLong())
                },
                valueRange = 1000f..5000f,
                steps = 4
            )

            ConfigSlider(
                label = "Max alertas/hora ($maxAlerts)",
                value = maxAlerts.toFloat(),
                onValueChange = {
                    maxAlerts = it.toInt()
                    serviceManager?.setMaxAlertsPerHour(it.toInt())
                },
                valueRange = 1f..5f,
                steps = 4
            )

            if (fatigueEnabled) {
                Button(
                    onClick = { serviceManager?.snoozeFatigueAlert(5) },
                    colors = ButtonDefaults.buttonColors(containerColor = DuoVialAmber.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("POSPONER ALERTAS (5 min)",
                        style = MaterialTheme.typography.labelLarge, color = DuoVialAmber)
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    AnimatedVisibility(visible = showAlert) {
        Box(
            modifier = Modifier.fillMaxSize().background(DuoVialNeonRed.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text("FATIGA DETECTADA!",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp),
                color = DuoVialNeonRed, fontWeight = FontWeight.W900)
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DuoVialCardBackground)
            .border(1.dp, DuoVialBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = DuoVialTextPrimary)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = DuoVialNeonGreen,
                activeTrackColor = DuoVialNeonGreen,
                inactiveTrackColor = DuoVialBorder
            )
        )
    }
}
