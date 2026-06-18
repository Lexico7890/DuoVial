package com.duovial.services

import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FaceProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceProcessor"
    }

    interface Callback {
        fun onFaceProcessed(faceDetected: Boolean, earValue: Double)
    }

    private var faceDetector: FaceDetector? = null
    private var executor: ExecutorService? = null
    private val isProcessing = AtomicBoolean(false)
    private var callback: Callback? = null

    @Volatile
    var isActive: Boolean = false
        private set

    fun setCallback(cb: Callback) {
        callback = cb
    }

    fun start() {
        isActive = true
        executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "face-mlkit").apply { priority = Thread.NORM_PRIORITY }
        }
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        faceDetector = FaceDetection.getClient(options)
        Log.d(TAG, "FaceProcessor iniciado.")
    }

    fun stop() {
        isActive = false
        isProcessing.set(false)
        try { faceDetector?.close() } catch (_: Exception) {}
        faceDetector = null
        try { executor?.shutdownNow() } catch (_: Exception) {}
        executor = null
        callback = null
        Log.d(TAG, "FaceProcessor detenido.")
    }

    @OptIn(ExperimentalGetImage::class)
    fun processFrame(imageProxy: ImageProxy) {
        if (!isActive) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val detector = faceDetector
        if (detector == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        detector.process(inputImage)
            .addOnSuccessListener(executor!!) { faces ->
                if (isActive) {
                    val (detected, ear) = handleDetectionResults(faces)
                    callback?.onFaceProcessed(detected, ear)
                }
            }
            .addOnFailureListener(executor!!) { e ->
                Log.e(TAG, "Error en ML Kit: ${e.message}")
            }
            .addOnCompleteListener(executor!!) {
                imageProxy.close()
                isProcessing.set(false)
            }
    }

    private fun handleDetectionResults(faces: List<Face>): Pair<Boolean, Double> {
        if (faces.isEmpty()) return false to 0.0
        val driverFace = faces.maxByOrNull { it.boundingBox.centerX() } ?: return false to 0.0
        val ear = calculateEAR(driverFace)
        return true to ear
    }

    private fun calculateEAR(face: Face): Double {
        val leftEye = face.getContour(FaceContour.LEFT_EYE)?.points
        val rightEye = face.getContour(FaceContour.RIGHT_EYE)?.points
        if (leftEye == null || rightEye == null || leftEye.size < 6 || rightEye.size < 6) return 1.0
        val leftEAR = computeSingleEyeEAR(leftEye)
        val rightEAR = computeSingleEyeEAR(rightEye)
        return (leftEAR + rightEAR) / 2.0
    }

    private fun computeSingleEyeEAR(points: List<PointF>): Double {
        val p1 = points[0]; val p2 = points[1]; val p3 = points[2]
        val p4 = points[3]; val p5 = points[4]; val p6 = points[5]
        val vertical1 = distance(p2, p6)
        val vertical2 = distance(p3, p5)
        val horizontal = distance(p1, p4)
        if (horizontal == 0f) return 1.0
        return ((vertical1 + vertical2) / (2.0 * horizontal)).toDouble()
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
