package com.anonymous.DuoVial

import android.util.Log
import android.view.View
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
        // CRITICAL: Usar TextureView en lugar de SurfaceView para compatibilidad con React Native.
        // SurfaceView crea una capa de renderizado separada que no se compone bien con la jerarquía de vistas de RN.
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        
        // Registrar OnAttachStateChangeListener para un enlace dinámico infalible
        previewView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Log.d(TAG, "PreviewView acoplado a la ventana (onViewAttachedToWindow).")
                BackgroundCameraService.activePreviewView = previewView
                BackgroundCameraService.instance?.bindPreviewUseCase(previewView)
            }

            override fun onViewDetachedFromWindow(v: View) {
                Log.d(TAG, "PreviewView desacoplado de la ventana (onViewDetachedFromWindow).")
                if (BackgroundCameraService.activePreviewView == previewView) {
                    BackgroundCameraService.activePreviewView = null
                }
                BackgroundCameraService.instance?.onPreviewViewDropped()
            }
        })
        
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
        
        // Informar al servicio que la vista fue destruida
        BackgroundCameraService.instance?.onPreviewViewDropped()
        
        super.onDropViewInstance(view)
    }
}
