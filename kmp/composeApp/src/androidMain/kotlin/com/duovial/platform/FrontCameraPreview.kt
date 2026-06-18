package com.duovial.platform

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.duovial.services.BackgroundCameraService

@Composable
actual fun FrontCameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        BackgroundCameraService.activeFrontPreviewView = previewView
        val service = BackgroundCameraService.instance
        if (service != null) {
            service.onFrontPreviewAvailable(previewView)
        }
        onDispose {
            if (BackgroundCameraService.activeFrontPreviewView == previewView) {
                BackgroundCameraService.activeFrontPreviewView = null
                BackgroundCameraService.instance?.onFrontPreviewDropped()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            previewView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    )
}
