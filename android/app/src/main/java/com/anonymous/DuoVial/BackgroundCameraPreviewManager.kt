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
        
        // Intentar vincular de inmediato y programar reintentos en el hilo principal
        bindPreviewWithRetry(previewView, 0)
        
        return previewView
    }

    private fun bindPreviewWithRetry(previewView: PreviewView, attempt: Int) {
        val preview = BackgroundCameraService.activePreview
        if (preview != null) {
            previewView.post {
                try {
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    Log.i(TAG, "Vinculación exitosa de PreviewView (Intento $attempt)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al vincular SurfaceProvider en intento $attempt: ${e.message}")
                }
            }
        } else if (attempt < 6) {
            // Reintentar en 500ms si el servicio de cámara aún está iniciando
            previewView.postDelayed({
                bindPreviewWithRetry(previewView, attempt + 1)
            }, 500)
        } else {
            Log.w(TAG, "Se superó el límite de reintentos para vincular el preview.")
        }
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
