package com.duovial.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
actual fun Modifier.platformBlur(radius: Dp): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val density = LocalDensity.current
    val px = with(density) { radius.toPx() }
    return this.then(
        Modifier.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                px, px, Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    )
}
