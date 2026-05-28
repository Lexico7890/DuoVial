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
        
        // Si el servicio ya está corriendo y el preview de CameraX ya existe, vincular de inmediato
        val preview = BackgroundCameraService.activePreview
        if (preview != null) {
            try {
                preview.setSurfaceProvider(previewView.surfaceProvider)
                Log.i(TAG, "Viculación exitosa de PreviewView con el servicio de cámara activo.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al vincular SurfaceProvider en createViewInstance: ${e.message}")
            }
        } else {
            Log.d(TAG, "Aún no hay servicio de cámara activo para vincular el preview.")
        }
        
        return previewView
    }

    override fun onDropViewInstance(view: PreviewView) {
        Log.d(TAG, "Destruyendo instancia nativa de PreviewView.")
        if (BackgroundCameraService.activePreviewView == view) {
            BackgroundCameraService.activePreviewView = null
        }
        try {
            BackgroundCameraService.activePreview?.setSurfaceProvider(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error al desvincular SurfaceProvider: ${e.message}")
        }
        super.onDropViewInstance(view)
    }
}
