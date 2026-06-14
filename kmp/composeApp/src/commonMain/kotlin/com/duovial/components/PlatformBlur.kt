package com.duovial.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
expect fun Modifier.platformBlur(radius: Dp): Modifier
