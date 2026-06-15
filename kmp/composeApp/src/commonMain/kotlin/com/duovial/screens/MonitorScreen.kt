package com.duovial.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
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
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialNeonRed
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary
import duovialkmp.composeapp.generated.resources.Res
import duovialkmp.composeapp.generated.resources.ic_duovial_logo
import org.jetbrains.compose.resources.painterResource

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
    val speedMph = (cameraState.speedKph * 0.621371).toInt()
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
        else -> Color.White.copy(alpha = 0.85f)
    }
    val gForceColor = when {
        gForce >= 3.5 -> DuoVialNeonRed
        gForce >= 2.0 -> DuoVialAmber
        else -> DuoVialNeonGreen
    }

    Box(modifier = Modifier.fillMaxSize().background(DuoVialBackground)) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
        ) {
            CameraPreview(modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DuoVialBackground.copy(alpha = 0.85f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = gForce.formatDecimal(2),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = gForceColor,
                            fontWeight = FontWeight.W900
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                            fontWeight = FontWeight.W700
                        )
                        AnimatedVisibility(visible = isRecording && !isSaving) {
                            Text(
                                text = "Grabando",
                                style = MaterialTheme.typography.labelSmall,
                                color = DuoVialNeonRed,
                                fontWeight = FontWeight.W700
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "MPH",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$speedMph",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = DuoVialTextPrimary,
                            fontWeight = FontWeight.W900
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable(enabled = !isSaving) {
                            if (isRecording) serviceManager?.stopRecording()
                            else serviceManager?.startRecording()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = if (isRecording) "Detener" else "Iniciar",
                        tint = if (isRecording) DuoVialNeonRed else DuoVialNeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(30.dp))
                        .clickable(enabled = isRecording && !isSaving) {
                            serviceManager?.triggerPanic()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSaving) "GUARDANDO..." else "EVENTO",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, letterSpacing = 2.sp),
                        color = if (isRecording) DuoVialAmber else Color.White.copy(alpha = 0.5f),
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.ic_duovial_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "DuoVial",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = DuoVialTextPrimary,
                    fontWeight = FontWeight.W900
                )
                if (isRecording && !isSaving) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DuoVialNeonRed)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DuoVialNeonGreen.copy(alpha = 0.15f))
                    .border(1.5.dp, DuoVialNeonGreen.copy(alpha = 0.4f), CircleShape)
                    .clickable { onOpenFatigue() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = "Abrir deteccion de somnolencia",
                    tint = DuoVialNeonGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
