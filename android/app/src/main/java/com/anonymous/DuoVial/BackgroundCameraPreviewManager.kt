package com.anonymous.DuoVial

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

/**
 * Contenedor de vista personalizado para resolver problemas de layout en React Native.
 * Sobrescribe requestLayout para forzar un pase de measure/layout en sus hijos.
 */
class ReactPreviewContainer(context: Context) : FrameLayout(context) {
    override fun requestLayout() {
        super.requestLayout()
        post(measureAndLayout)
    }

    private val measureAndLayout = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout(left, top, right, bottom)
    }
}

/**
 * View Manager nativo que expone la vista de Preview de CameraX (a través de ReactPreviewContainer) a React Native JS.
 */
class BackgroundCameraPreviewManager : SimpleViewManager<ReactPreviewContainer>() {

    private val TAG = "DuoVial_PreviewMgr"

    override fun getName(): String {
        return "BackgroundCameraPreview"
    }

    override fun createViewInstance(reactContext: ThemedReactContext): ReactPreviewContainer {
        Log.w(TAG, ">>> createViewInstance: Creando ReactPreviewContainer y PreviewView")

        val previewView = PreviewView(reactContext)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        val container = ReactPreviewContainer(reactContext)
        container.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Listener para cuando la vista se acopla/desacopla de la ventana
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Log.w(TAG, ">>> onViewAttachedToWindow: ReactPreviewContainer acoplado")
                BackgroundCameraService.activePreviewView = previewView
                
                // Intentar conectar con el servicio si existe
                val servicePreview = BackgroundCameraService.activePreview
                if (servicePreview != null) {
                    Log.w(TAG, ">>> Servicio activo — reconectando surface provider")
                    servicePreview.setSurfaceProvider(previewView.surfaceProvider)
                } else {
                    // Si no hay servicio, iniciar preview local con lifecycle de Activity
                    Log.w(TAG, ">>> Sin servicio — iniciando preview local con Activity lifecycle")
                    startLocalPreview(reactContext, previewView)
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                Log.w(TAG, ">>> onViewDetachedFromWindow: ReactPreviewContainer desacoplado")
                if (BackgroundCameraService.activePreviewView == previewView) {
                    BackgroundCameraService.activePreviewView = null
                }
                BackgroundCameraService.instance?.onPreviewViewDropped()
            }
        })

        // Guardar referencia para el servicio
        BackgroundCameraService.activePreviewView = previewView

        // Intentar vincular inmediatamente si el servicio ya está listo
        val servicePreview = BackgroundCameraService.activePreview
        if (servicePreview != null) {
            Log.w(TAG, ">>> createViewInstance: Servicio ya tiene preview — conectando surface")
            servicePreview.setSurfaceProvider(previewView.surfaceProvider)
        }

        return container
    }

    /**
     * Inicia el preview de CameraX usando la Activity como LifecycleOwner.
     * Esto SOLO se usa cuando el servicio aún no ha arrancado.
     * Cuando el servicio arranque, hará unbindAll() y rebindeará con su propio lifecycle.
     */
    private fun startLocalPreview(reactContext: ThemedReactContext, previewView: PreviewView) {
        val activity = reactContext.currentActivity
        if (activity == null || activity !is LifecycleOwner) {
            Log.e(TAG, "Activity null o no es LifecycleOwner — no se puede iniciar preview local")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(reactContext)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Guardar referencia para que el servicio pueda reconectar después
                BackgroundCameraService.activePreview = preview

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    activity as LifecycleOwner,
                    cameraSelector,
                    preview
                )
                Log.w(TAG, ">>> CameraX preview local vinculado con éxito a Activity lifecycle")
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar preview local: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(reactContext))
    }

    override fun onDropViewInstance(view: ReactPreviewContainer) {
        Log.w(TAG, ">>> onDropViewInstance: Limpiando ReactPreviewContainer")
        val childCount = view.childCount
        for (i in 0 until childCount) {
            val child = view.getChildAt(i)
            if (child is PreviewView) {
                if (BackgroundCameraService.activePreviewView == child) {
                    BackgroundCameraService.activePreviewView = null
                }
            }
        }
        BackgroundCameraService.instance?.onPreviewViewDropped()
        super.onDropViewInstance(view)
    }
}
