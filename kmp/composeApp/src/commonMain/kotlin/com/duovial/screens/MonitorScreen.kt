package com.duovial.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duovial.platform.CameraPreview
import com.duovial.platform.formatDecimal
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.state.CameraStatus
import com.duovial.theme.DuoVialAmber
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialNeonRed
import com.duovial.theme.DuoVialSurface
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary

@Composable
fun MonitorScreen(
    serviceManager: CameraServiceManager? = null,
    onOpenFatigue: () -> Unit = {}
) {
    val cameraState by AppStateManager.cameraState.collectAsState()
    val isRecording = cameraState.status != CameraStatus.INACTIVO
    val isSaving = cameraState.status == CameraStatus.GUARDANDO ||
        cameraState.status == CameraStatus.INICIANDO
    val gForce = cameraState.gForce
    val speed = cameraState.speedKph.toInt()
    val statusLabel = when (cameraState.status) {
        CameraStatus.INACTIVO -> "Vigilante apagado"
        CameraStatus.INICIANDO -> "Iniciando DuoVial..."
        CameraStatus.ACTIVO -> "DuoVial Activo"
        CameraStatus.GUARDANDO -> "Guardando incidente..."
        CameraStatus.ERROR -> "Error en camara"
    }
    val statusColor = when (cameraState.status) {
        CameraStatus.ACTIVO -> DuoVialNeonGreen
        CameraStatus.INICIANDO -> DuoVialAmber
        CameraStatus.GUARDANDO -> DuoVialAmber
        CameraStatus.ERROR -> DuoVialNeonRed
        else -> DuoVialTextSecondary
    }
    val gForceColor = when {
        gForce >= 3.5 -> DuoVialNeonRed
        gForce >= 2.0 -> DuoVialAmber
        else -> DuoVialNeonGreen
    }

    Box(modifier = Modifier.fillMaxSize().background(DuoVialBackground)) {
        CameraPreview(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DuoVialBackground.copy(alpha = 0.9f))
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("G", style = MaterialTheme.typography.labelSmall, color = DuoVialTextSecondary)
                Text(
                    text = gForce.formatDecimal(2),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = gForceColor, fontWeight = FontWeight.W900
                )
            }

            AnimatedVisibility(visible = isRecording && !isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DuoVialNeonRed.copy(alpha = 0.15f))
                        .border(1.dp, DuoVialNeonRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(DuoVialNeonRed, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("REC", style = MaterialTheme.typography.labelMedium, color = DuoVialNeonRed)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("km/h", style = MaterialTheme.typography.labelSmall, color = DuoVialTextSecondary)
                Text(
                    text = "$speed",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = DuoVialTextPrimary, fontWeight = FontWeight.W900
                )
            }
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
        )

        // Panic button area (clickable)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(160.dp)
                .clickable(enabled = isRecording && !isSaving) {
                    serviceManager?.triggerPanic()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSaving -> DuoVialAmber.copy(alpha = 0.2f)
                            isRecording -> DuoVialNeonGreen.copy(alpha = 0.15f)
                            else -> DuoVialTextSecondary.copy(alpha = 0.1f)
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSaving -> DuoVialAmber
                            isRecording -> DuoVialNeonGreen
                            else -> DuoVialSurface
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isSaving -> "GUARDANDO"
                        isRecording -> "PANICO"
                        else -> "OFF"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        isRecording || isSaving -> DuoVialBackground
                        else -> DuoVialTextSecondary
                    },
                    fontWeight = FontWeight.W900
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenFatigue, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.Visibility, "Deteccion Frontal",
                    tint = DuoVialTextSecondary, modifier = Modifier.size(28.dp)
                )
            }

            Button(
                onClick = {
                    if (isRecording) serviceManager?.stopRecording()
                    else if (!isSaving) serviceManager?.startRecording()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) DuoVialNeonRed.copy(alpha = 0.2f)
                    else DuoVialNeonGreen.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    if (isRecording) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    if (isRecording) "Detener" else "Iniciar",
                    tint = if (isRecording) DuoVialNeonRed else DuoVialNeonGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRecording) "DETENER" else "INICIAR",
                    color = if (isRecording) DuoVialNeonRed else DuoVialNeonGreen,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.width(48.dp))
        }
    }
}
