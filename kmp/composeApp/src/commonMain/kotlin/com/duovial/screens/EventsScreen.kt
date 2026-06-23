package com.duovial.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duovial.state.CameraServiceManager
import com.duovial.state.Incident
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialBorder
import com.duovial.theme.DuoVialCardBackground
import com.duovial.theme.DuoVialNeonGreen
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary

@Composable
fun EventsScreen(
    serviceManager: CameraServiceManager? = null,
    onIncidentSelected: (Incident) -> Unit = {}
) {
    var incidents by remember { mutableStateOf<List<Incident>?>(null) }

    LaunchedEffect(Unit) {
        incidents = serviceManager?.loadIncidents() ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoVialBackground)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Incidentes Guardados",
                style = MaterialTheme.typography.titleLarge,
                color = DuoVialTextPrimary,
                fontWeight = FontWeight.W700
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Videos grabados autom\u00e1ticamente en /Downloads/DuoVial",
                style = MaterialTheme.typography.bodySmall,
                color = DuoVialTextSecondary
            )
        }

        when {
            incidents == null -> {}
            incidents!!.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = DuoVialTextSecondary
                    )
                    Text(
                        text = "Sin incidentes",
                        style = MaterialTheme.typography.titleMedium,
                        color = DuoVialTextPrimary
                    )
                    Text(
                        text = "Los videos guardados por p\u00e1nico o colisiones aparecer\u00e1n aqu\u00ed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DuoVialTextSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 90.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(incidents!!, key = { it.timestampSec }) { incident ->
                        IncidentCard(
                            incident = incident,
                            onClick = { onIncidentSelected(incident) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(
    incident: Incident,
    onClick: () -> Unit
) {
    val clipCount = incident.parts.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DuoVialCardBackground)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DuoVialNeonGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = DuoVialNeonGreen
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Incidente ${incident.date}",
                style = MaterialTheme.typography.bodyLarge,
                color = DuoVialTextPrimary,
                fontWeight = FontWeight.W600
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$clipCount clip${if (clipCount > 1) "s" else ""} \u2022 ${incident.parts.size * 15}s",
                style = MaterialTheme.typography.bodySmall,
                color = DuoVialTextSecondary
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "Reproducir",
                modifier = Modifier.size(20.dp),
                tint = DuoVialNeonGreen
            )
        }
    }
}
