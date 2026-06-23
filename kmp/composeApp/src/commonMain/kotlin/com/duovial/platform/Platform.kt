package com.duovial.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.duovial.state.Incident

@Composable
expect fun CameraPreview(modifier: Modifier = Modifier)

@Composable
expect fun FrontCameraPreview(modifier: Modifier = Modifier)

@Composable
expect fun IncidentPlayerScreen(incident: Incident, onBack: () -> Unit)
