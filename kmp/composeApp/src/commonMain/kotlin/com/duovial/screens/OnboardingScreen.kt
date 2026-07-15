package com.duovial.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialOrange
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary
import kotlinx.coroutines.launch

/**
 * Pantalla de onboarding de DuoVial.
 *
 * Flujo:
 * 1. 5 páginas informativas (swipe horizontal) — B-01
 * 2. Pantalla de permisos con explicaciones — B-02
 * 3. Callback [onOnboardingCompleted] cuando el usuario termina
 *
 * El onboarding NO es skippable en MVP (excepto el paso de permisos que puede omitirse).
 */
@Composable
fun OnboardingScreen(
    onOnboardingCompleted: () -> Unit,
    onRequestPermissions: (List<String>) -> Unit = {},
    permissionStatuses: Map<String, Boolean> = emptyMap()
) {
    val totalInfoPages = 5
    val pagerState = rememberPagerState(pageCount = { totalInfoPages })
    val scope = rememberCoroutineScope()
    val showPermissions = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoVialBackground)
    ) {
        if (showPermissions.value) {
            // Fase 2: Pantalla de permisos (B-02)
            PermissionsScreen(
                permissionStatuses = permissionStatuses,
                onRequestPermissions = onRequestPermissions,
                onSkip = {
                    onOnboardingCompleted()
                },
                onContinue = {
                    onOnboardingCompleted()
                }
            )
        } else {
            // Fase 1: Pantallas informativas (B-01)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                OnboardingPage(page = page)
            }

            // Indicadores de página (dots) + botones de navegación
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                DuoVialBackground.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Dots indicator
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(totalInfoPages) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) DuoVialNeonGreen
                                        else DuoVialTextSecondary.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Botón de acción
                    Button(
                        onClick = {
                            if (pagerState.currentPage < totalInfoPages - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                // Última página → mostrar permisos
                                showPermissions.value = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuoVialNeonGreen
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage < totalInfoPages - 1) "Siguiente"
                            else "Configurar permisos",
                            style = MaterialTheme.typography.labelLarge,
                            color = DuoVialBackground,
                            fontWeight = FontWeight.W800
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Skip link (solo visible en páginas intermedias)
                    AnimatedVisibility(
                        visible = pagerState.currentPage < totalInfoPages - 1,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "Saltar introducción",
                            style = MaterialTheme.typography.labelMedium,
                            color = DuoVialTextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    showPermissions.value = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// Páginas informativas del onboarding (B-01)
// ─────────────────────────────────────────────────────

@Composable
private fun OnboardingPage(page: Int) {
    val (icon, title, subtitle, description, bullets) = when (page) {
        0 -> OnboardingPageData(
            icon = Icons.Outlined.Dashboard,
            title = "Bienvenido a DuoVial",
            subtitle = "Tu dash cam inteligente de bajo consumo",
            description = "DuoVial convierte tu teléfono en una cámara de vigilancia inteligente para tu vehículo. Detecta incidentes, monitorea fatiga y protege tu inversión.",
            bullets = emptyList()
        )
        1 -> OnboardingPageData(
            icon = Icons.Outlined.CameraAlt,
            title = "El Vigilante",
            subtitle = "Evidencia automática de incidentes",
            description = "La cámara trasera graba continuamente los últimos 30 segundos. Cuando ocurre un evento (frenada brusca, impacto o botón de pánico), el video se guarda automáticamente. Sin llenar almacenamiento, sin agotar batería.",
            bullets = listOf(
                "Buffer circular de bajo consumo",
                "Detección por acelerómetro y botón de pánico",
                "Guardado automático de evidencia"
            )
        )
        2 -> OnboardingPageData(
            icon = Icons.Outlined.Visibility,
            title = "Anti-Somnolencia",
            subtitle = "Tu copiloto de seguridad",
            description = "DuoVial monitorea tu estado de alerta usando la cámara frontal y wearables compatibles. Si detecta somnolencia, te alerta antes de que sea peligroso.",
            bullets = listOf(
                "Detección de parpadeo con IA",
                "Integración con wearables (frecuencia cardíaca)",
                "Alertas progresivas para prevenir accidentes"
            )
        )
        3 -> OnboardingPageData(
            icon = Icons.Outlined.Speed,
            title = "Monitoreo Completo",
            subtitle = "Datos de tu vehículo en tiempo real",
            description = "Velocidad, fuerza G, ubicación y más. DuoVial registra todo para que tengas evidencia completa de cada viaje.",
            bullets = listOf(
                "Velocímetro GPS en tiempo real",
                "Registro de fuerza G",
                "Historial de eventos y viajes"
            )
        )
        else -> OnboardingPageData(
            icon = Icons.Outlined.Policy,
            title = "Tú tienes el control",
            subtitle = "Todo es configurable",
            description = "Ajusta los umbrales de detección, habilita o deshabilita funciones, y personaliza DuoVial según tus necesidades. La app se adapta a ti.",
            bullets = listOf(
                "Umbral de G-Force configurable",
                "Auto-inicio opcional",
                "Preferencias de notificación"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 80.dp, bottom = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icono principal
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(DuoVialNeonGreen.copy(alpha = 0.1f))
                .border(2.dp, DuoVialNeonGreen.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DuoVialNeonGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        // Título
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DuoVialTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Subtítulo
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = DuoVialNeonGreen,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // Descripción
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = DuoVialTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        // Bullet points (si existen)
        if (bullets.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DuoVialCardBackground)
                    .border(1.dp, DuoVialBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bullets.forEach { bullet ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(DuoVialNeonGreen)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DuoVialTextSecondary
                        )
                    }
                }
            }
        }
    }
}

private data class OnboardingPageData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String,
    val bullets: List<String>
)

// ─────────────────────────────────────────────────────
// Pantalla de permisos (B-02)
// ─────────────────────────────────────────────────────

private data class PermissionInfo(
    val permission: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val required: Boolean
)

@Composable
private fun PermissionsScreen(
    permissionStatuses: Map<String, Boolean>,
    onRequestPermissions: (List<String>) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    val requiredPermissions = remember {
        listOf(
            PermissionInfo(
                permission = "CAMERA",
                icon = Icons.Outlined.CameraAlt,
                title = "Cámara",
                description = "Para grabar video del frente y la carretera. La cámara trasera es el Vigilante; la frontal es para detección de somnolencia.",
                required = true
            ),
            PermissionInfo(
                permission = "ACCESS_FINE_LOCATION",
                icon = Icons.Outlined.GpsFixed,
                title = "Ubicación precisa",
                description = "Para registrar tu velocidad y ubicación exacta durante los viajes. Necesario para el velocímetro y geolocalización de incidentes.",
                required = true
            ),
            PermissionInfo(
                permission = "POST_NOTIFICATIONS",
                icon = Icons.Outlined.NotificationsActive,
                title = "Notificaciones",
                description = "Para alertarte sobre incidentes, recordatorios de mantenimiento y avisos de seguridad.",
                required = true
            ),
            PermissionInfo(
                permission = "SYSTEM_ALERT_WINDOW",
                icon = Icons.Outlined.Dashboard,
                title = "Ventana superpuesta",
                description = "Para mostrar la burbuja flotante de pánico sobre otras apps. Permite registrar eventos sin abrir DuoVial.",
                required = true
            ),
            PermissionInfo(
                permission = "ACTIVITY_RECOGNITION",
                icon = Icons.Outlined.DirectionsRun,
                title = "Reconocimiento de actividad",
                description = "Para detectar automáticamente cuándo estás conduciendo y activar el Vigilante sin que lo hagas manualmente.",
                required = true
            )
        )
    }

    val optionalPermissions = remember {
        listOf(
            PermissionInfo(
                permission = "BLUETOOTH_CONNECT",
                icon = Icons.Outlined.Bluetooth,
                title = "Bluetooth",
                description = "Para conectar el dongle OBD II y leer datos mecánicos del vehículo.",
                required = false
            ),
            PermissionInfo(
                permission = "HEALTH_CONNECT",
                icon = Icons.Outlined.FavoriteBorder,
                title = "Health Connect",
                description = "Para leer datos de tu wearable (frecuencia cardíaca, HRV) y mejorar la detección de fatiga.",
                required = false
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 60.dp, bottom = 40.dp)
    ) {
        // Header
        Text(
            text = "Permisos necesarios",
            style = MaterialTheme.typography.titleLarge,
            color = DuoVialTextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "DuoVial necesita acceso a los siguientes servicios de tu teléfono para funcionar correctamente",
            style = MaterialTheme.typography.bodyMedium,
            color = DuoVialTextSecondary,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(24.dp))

        // Permisos requeridos
        Text(
            text = "REQUERIDOS",
            style = MaterialTheme.typography.labelMedium,
            color = DuoVialNeonGreen,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))

        requiredPermissions.forEach { info ->
            PermissionItem(
                info = info,
                isGranted = permissionStatuses[info.permission] == true
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(20.dp))

        // Permisos opcionales
        Text(
            text = "OPCIONALES (PREMIUM / FLEET)",
            style = MaterialTheme.typography.labelMedium,
            color = DuoVialOrange,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))

        optionalPermissions.forEach { info ->
            PermissionItem(
                info = info,
                isGranted = permissionStatuses[info.permission] == true
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(32.dp))

        // Botón: Conceder todos
        Button(
            onClick = {
                val allPermissions = requiredPermissions.map { it.permission } +
                        optionalPermissions.map { it.permission }
                onRequestPermissions(allPermissions)
            },
            colors = ButtonDefaults.buttonColors(containerColor = DuoVialNeonGreen),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Conceder permisos",
                style = MaterialTheme.typography.labelLarge,
                color = DuoVialBackground,
                fontWeight = FontWeight.W800
            )
        }

        Spacer(Modifier.height(10.dp))

        // Botón: Configurar después
        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, DuoVialBorder, RoundedCornerShape(14.dp))
        ) {
            Text(
                text = "Configurar después",
                style = MaterialTheme.typography.labelMedium,
                color = DuoVialTextSecondary
            )
        }

        Spacer(Modifier.height(12.dp))

        // Resumen de estado
        val grantedCount = permissionStatuses.values.count { it }
        val totalCount = requiredPermissions.size + optionalPermissions.size
        if (grantedCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DuoVialNeonGreen.copy(alpha = 0.05f))
                    .border(1.dp, DuoVialNeonGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "$grantedCount de $totalCount permisos concedidos",
                    style = MaterialTheme.typography.labelMedium,
                    color = DuoVialNeonGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    info: PermissionInfo,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DuoVialCardBackground)
            .border(
                1.dp,
                if (isGranted) DuoVialNeonGreen.copy(alpha = 0.3f) else DuoVialBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icono del permiso
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) DuoVialNeonGreen.copy(alpha = 0.1f)
                    else DuoVialTextSecondary.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = if (isGranted) DuoVialNeonGreen else DuoVialTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Texto
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = DuoVialTextPrimary
                )
                if (!info.required) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Opcional",
                        style = MaterialTheme.typography.labelSmall,
                        color = DuoVialOrange
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodySmall,
                color = DuoVialTextSecondary,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        // Estado
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) DuoVialNeonGreen.copy(alpha = 0.15f)
                    else DuoVialTextSecondary.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (isGranted) "Concedido" else "No concedido",
                tint = if (isGranted) DuoVialNeonGreen else DuoVialTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
