package com.anonymous.DuoVial

import android.util.Log
import androidx.camera.view.PreviewView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

/**
 * View Manager nativo que expone la vista de Preview de CameraX (PreviewView) a React Native JS.
 * Esto permite al usuario ver exactamente lo que capta la cámara trasera y ajustar el soporte del móvil.
 */
class BackgroundCameraPreviewManager : SimpleViewManager<PreviewView>() {

    private val TAG = "DuoVial_PreviewManager"

    override fun getName(): String {
        return "BackgroundCameraPreview"
    }

    override fun createViewInstance(reactContext: ThemedReactContext): PreviewView {
        Log.d(TAG, "Creando instancia nativa de PreviewView...")
        val previewView = PreviewView(reactContext)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        
        // Guardar referencia en el servicio para vinculación bidireccional
        BackgroundCameraService.activePreviewView = previewView
        
        // Vincular el preview de forma dinámica si el servicio está activo
        BackgroundCameraService.instance?.bindPreviewUseCase(previewView)
        
        return previewView
    }

    override fun onDropViewInstance(view: PreviewView) {
        Log.d(TAG, "Destruyendo instancia nativa de PreviewView.")
        if (BackgroundCameraService.activePreviewView == view) {
            BackgroundCameraService.activePreviewView = null
        }
        
        // Desvincular el preview de forma dinámica en el servicio
        BackgroundCameraService.instance?.unbindPreviewUseCase()
        
        super.onDropViewInstance(view)
    }
}
