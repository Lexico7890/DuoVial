package com.duovial.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glass(
    cornerRadius: Dp = 16.dp,
    blurRadius: Dp = 20.dp,
    borderAlpha: Float = 0.25f,
    fillAlpha: Float = 0.12f,
    fillColor: Color = Color.White,
    borderColor: Color = Color.White
): Modifier {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    return this
        .platformBlur(blurRadius)
        .background(
            color = fillColor.copy(alpha = fillAlpha),
            shape = shape
        )
        .border(
            width = 1.dp,
            color = borderColor.copy(alpha = borderAlpha),
            shape = shape
        )
}
