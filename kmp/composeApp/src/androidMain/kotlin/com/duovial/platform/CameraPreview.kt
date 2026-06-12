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
fun CameraPreviewView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val service = BackgroundCameraService.instance
        if (service != null && BackgroundCameraService.activePreview != null) {
            BackgroundCameraService.activePreviewView = previewView
            BackgroundCameraService.activePreview?.setSurfaceProvider(previewView.surfaceProvider)
        } else {
            BackgroundCameraService.pendingPreviewView = previewView
        }
        onDispose {
            BackgroundCameraService.activePreview?.setSurfaceProvider(null)
            if (BackgroundCameraService.activePreviewView == previewView) {
                BackgroundCameraService.activePreviewView = null
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
