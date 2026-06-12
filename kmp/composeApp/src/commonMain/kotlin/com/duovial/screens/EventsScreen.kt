package com.duovial.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.duovial.theme.DuoVialBackground
import com.duovial.theme.DuoVialTextPrimary
import com.duovial.theme.DuoVialTextSecondary

@Composable
fun EventsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoVialBackground)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Incidentes Guardados",
                style = MaterialTheme.typography.titleLarge,
                color = DuoVialTextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Videos grabados autom\u00e1ticamente en /Downloads/DuoVial",
                style = MaterialTheme.typography.bodySmall,
                color = DuoVialTextSecondary
            )
        }

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
                text = "Galer\u00eda Lista",
                style = MaterialTheme.typography.titleMedium,
                color = DuoVialTextPrimary
            )
            Text(
                text = "Los videos guardados por p\u00e1nico o colisiones se sincronizan en tu almacenamiento.",
                style = MaterialTheme.typography.bodySmall,
                color = DuoVialTextSecondary,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
