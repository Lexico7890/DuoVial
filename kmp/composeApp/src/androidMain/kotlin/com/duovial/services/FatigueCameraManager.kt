package com.duovial.services

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class FatigueCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "FatigueCameraManager"
        private val TARGET_RESOLUTION = Size(640, 480)
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var frameCallback: ((ImageProxy) -> Unit)? = null

    fun setOnFrameAvailable(callback: (ImageProxy) -> Unit) {
        frameCallback = callback
    }

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        Log.d(TAG, "Iniciando camara frontal con CameraX...")
        val provider = try {
            ProcessCameraProvider.getInstance(context).get()
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error al obtener ProcessCameraProvider: ${e.message}")
            return
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Interrumpido al obtener ProcessCameraProvider.")
            return
        }
        cameraProvider = provider

        preview = Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(TARGET_RESOLUTION)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(Executors.newSingleThreadExecutor { r ->
                    Thread(r, "camerax-analysis").apply { priority = Thread.NORM_PRIORITY }
                }) { imageProxy ->
                    val cb = frameCallback
                    if (cb != null) {
                        cb.invoke(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }
            }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            imageAnalysis
        )
        Log.d(TAG, "Camara frontal bindeada a lifecycle.")
    }

    fun stop() {
        Log.d(TAG, "Deteniendo camara frontal...")
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        preview = null
        imageAnalysis = null
        cameraProvider = null
        frameCallback = null
    }
}
