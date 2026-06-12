package com.duovial.screens

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
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duovial.platform.formatDecimal
import com.duovial.state.AppStateManager
import com.duovial.state.CameraServiceManager
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialGreenDim
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialOrange
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary

@Composable
fun SettingsScreen(serviceManager: CameraServiceManager? = null) {
    val cameraState by AppStateManager.cameraState.collectAsState()
    val threshold = cameraState.gForceThreshold

    Box(modifier = Modifier.fillMaxSize().background(DuoVialBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(10.dp))
            Text("Configuracion", style = MaterialTheme.typography.titleLarge, color = DuoVialTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Calibracion fina y herramientas avanzadas de DuoVial",
                style = MaterialTheme.typography.bodySmall, color = DuoVialTextSecondary)
            Spacer(Modifier.height(24.dp))

            // Overlay permission card
            SettingsCard(
                icon = Icons.Outlined.AdsClick,
                iconColor = DuoVialOrange,
                title = "Burbuja Flotante de Panico",
                description = "Habilita un boton flotante arrastrable que permanece visible sobre otras aplicaciones para registrar incidentes instantaneamente."
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DuoVialOrange.copy(alpha = 0.05f))
                        .border(1.dp, DuoVialOrange.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = DuoVialOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Bloqueado por seguridad de Android?",
                            style = MaterialTheme.typography.labelSmall, color = DuoVialOrange)
                        Text("Si el sistema te niega el acceso, ve a Ajustes -> Aplicaciones -> DuoVial -> Permitir ajustes restringidos.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = DuoVialTextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { serviceManager?.requestOverlayPermission() },
                    colors = ButtonDefaults.buttonColors(containerColor = DuoVialOrange.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AUTORIZAR BURBUJA FLOTANTE",
                        style = MaterialTheme.typography.labelSmall, color = DuoVialOrange)
                }
            }

            // G-Force threshold card
            SettingsCard(
                icon = Icons.Outlined.FlashOn,
                iconColor = DuoVialNeonGreen,
                title = "Sensibilidad del Acelerometro",
                description = "Umbral actual: ${threshold.formatDecimal(1)}G. Valor configurable entre 1.5G y 5.0G."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val next = (threshold - 0.1).coerceAtLeast(1.5)
                            serviceManager?.setGForceThreshold(next)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .border(1.5.dp, DuoVialNeonGreen, CircleShape)
                            .background(DuoVialGreenDim, CircleShape)
                    ) { Text("-", color = DuoVialNeonGreen, style = MaterialTheme.typography.labelLarge) }

                    Text(threshold.formatDecimal(1) + "G",
                        style = MaterialTheme.typography.labelMedium, color = DuoVialTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp))

                    IconButton(
                        onClick = {
                            val next = (threshold + 0.1).coerceAtMost(5.0)
                            serviceManager?.setGForceThreshold(next)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .border(1.5.dp, DuoVialNeonGreen, CircleShape)
                            .background(DuoVialGreenDim, CircleShape)
                    ) { Text("+", color = DuoVialNeonGreen, style = MaterialTheme.typography.labelLarge) }
                }

                Spacer(Modifier.height(10.dp))

                // Progress bar
                val progress = ((threshold - 1.5) / 3.5).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DuoVialBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress).height(6.dp)
                            .background(DuoVialNeonGreen, RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DuoVialCardBackground)
            .border(1.dp, DuoVialBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = DuoVialTextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = DuoVialTextSecondary)
        Spacer(Modifier.height(15.dp))
        content()
    }
}
