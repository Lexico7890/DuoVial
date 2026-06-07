package com.anonymous.DuoVial

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
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
 * View Manager nativo que expone la vista de Preview de CameraX.
 *
 * REGLA DE ORO (anti race-condition CameraX):
 *   El Service es el único dueño de la instancia `Preview` de CameraX.
 *   Este manager NUNCA debe llamar a `cameraProvider.bindToLifecycle()`
 *   por su cuenta. Si el Service está vivo, le pedimos que conecte su
 *   SurfaceProvider. Si está arrancando, encolamos la vista y el Service
 *   la conectará cuando `startCameraX` termine. Sólo como último recurso
 *   (Service descartado por el SO o nunca llamado) el ViewManager podría
 *   iniciar un preview local, pero ese camino debe estar EXPLÍCITAMENTE
 *   activado desde JS y es únicamente defensivo.
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

        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Log.w(TAG, ">>> onViewAttachedToWindow (serviceStarting=${BackgroundCameraService.serviceStarting}, hasService=${BackgroundCameraService.instance != null}, hasPreview=${BackgroundCameraService.activePreview != null})")
                routePreviewToService(previewView, reactContext)
            }

            override fun onViewDetachedFromWindow(v: View) {
                Log.w(TAG, ">>> onViewDetachedFromWindow")
                // Si la vista que se va era la pendiente, la soltamos.
                if (BackgroundCameraService.pendingPreviewView === previewView) {
                    BackgroundCameraService.pendingPreviewView = null
                }
                if (BackgroundCameraService.activePreviewView === previewView) {
                    BackgroundCameraService.activePreviewView = null
                }
                BackgroundCameraService.instance?.onPreviewViewDropped()
            }
        })

        // Registro optimista por si la vista se acopla antes de que llegue el onAttached.
        BackgroundCameraService.activePreviewView = previewView
        // Si el Service ya tiene Preview listo, lo conectamos en este mismo hilo.
        if (BackgroundCameraService.activePreview != null) {
            try {
                BackgroundCameraService.activePreview?.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando preview en createViewInstance: ${e.message}")
            }
        }

        return container
    }

    /**
     * Decide cómo conectar el PreviewView al Service. Tres caminos:
     *  1. Service vivo + Preview listo → conexión inmediata.
     *  2. Service arrancando (o vivo sin Preview aún) → encolar.
     *  3. No hay Service y no está arrancando → error log (no se inicia preview
     *     local paralelo: eso es lo que causaba el race original).
     */
    private fun routePreviewToService(previewView: PreviewView, reactContext: ThemedReactContext) {
        val service = BackgroundCameraService.instance
        val preview = BackgroundCameraService.activePreview

        when {
            service != null && preview != null -> {
                Log.w(TAG, ">>> Camino 1: Service + Preview listos, vinculando surface.")
                try {
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    BackgroundCameraService.activePreviewView = previewView
                } catch (e: Exception) {
                    Log.e(TAG, "Error vinculando surface (camino 1): ${e.message}")
                }
            }
            BackgroundCameraService.serviceStarting || service != null -> {
                Log.w(TAG, ">>> Camino 2: Service en curso — encolando PreviewView.")
                BackgroundCameraService.pendingPreviewView = previewView
            }
            else -> {
                // Caso defensivo: JS no pidió iniciar el Service. En vez de crear
                // un Preview local paralelo (que rompería CameraX al hacer
                // unbindAll() desde el Service más tarde), mostramos el view vacío
                // y logueamos. JS debe llamar a startStandby() antes de montar
                // esta vista.
                Log.e(TAG, ">>> Camino 3 (defensivo): sin Service y sin arranque. " +
                        "Llama a BackgroundGuard.startStandby() ANTES de montar el PreviewView.")
            }
        }
    }

    override fun onDropViewInstance(view: ReactPreviewContainer) {
        Log.w(TAG, ">>> onDropViewInstance: Limpiando ReactPreviewContainer")
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is PreviewView) {
                if (BackgroundCameraService.activePreviewView === child) {
                    BackgroundCameraService.activePreviewView = null
                }
                if (BackgroundCameraService.pendingPreviewView === child) {
                    BackgroundCameraService.pendingPreviewView = null
                }
            }
        }
        BackgroundCameraService.instance?.onPreviewViewDropped()
        super.onDropViewInstance(view)
    }
}

