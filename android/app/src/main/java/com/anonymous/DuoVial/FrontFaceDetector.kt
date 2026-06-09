package com.anonymous.DuoVial

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.MediaPlayer
import android.util.Log
import android.util.Size
import android.view.Surface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Detector de somnolencia del conductor usando la cámara frontal + ML Kit.
 *
 * Arquitectura:
 * - Camera2 API directa (ImageReader) — separado de CameraX
 * - ML Kit Face Detection para detectar rostros y puntos faciales
 * - EAR (Eye Aspect Ratio) para medir parpadeo y ojos cerrados
 * - Selección del conductor: solo el rostro más a la derecha en frame raw
 *   (que corresponde al conductor a la izquierda en el preview espejado)
 * - Alertas nativas: vibración + sonido cuando ojos cerrados > threshold
 */
class FrontFaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "FrontFaceDetector"
        private const val CAMERA_WIDTH = 640
        private const val CAMERA_HEIGHT = 480
        private const val TARGET_FPS = 10
    }

    // Configuración
    var earThreshold: Double = 0.2
    var closedEyeDurationMs: Long = 2000L
    var maxAlertsPerHour: Int = 3
    var snoozeMinutes: Int = 5

    // Estado
    var isActive: Boolean = false
        private set
    var isSnoozed: Boolean = false
        private set
    var alertCountThisHour: Int = 0
        private set
    var currentEar: Double = 0.0
        private set
    var isFaceDetected: Boolean = false
        private set
    var closedEyeDurationActual: Double = 0.0
        private set

    // Camera2
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    // ML Kit
    private var faceDetector: FaceDetector? = null

    // Timing
    private var closedEyeStartTime: Long = 0L
    private var lastAlertTime: Long = 0L
    private var snoozeEndTime: Long = 0L
    private var hourResetTime: Long = 0L

    // Vibración y sonido
    private var mediaPlayer: MediaPlayer? = null

    // Listener para eventos hacia el servicio
    var eventListener: FatigueEventListener? = null

    interface FatigueEventListener {
        fun onFaceStatusChanged(enabled: Boolean, faceDetected: Boolean, earValue: Double, closedEyeDuration: Double)
        fun onDrowsinessDetected(timestamp: Long, earValue: Double)
    }

    fun start() {
        if (isActive) {
            Log.w(TAG, "Ya está activo.")
            return
        }
        Log.i(TAG, "Iniciando FrontFaceDetector...")
        isActive = true
        hourResetTime = System.currentTimeMillis()
        setupFaceDetector()
        setupCamera()
    }

    fun stop() {
        if (!isActive) return
        Log.i(TAG, "Deteniendo FrontFaceDetector...")
        isActive = false
        isFaceDetected = false
        currentEar = 0.0
        closedEyeDurationActual = 0.0
        closedEyeStartTime = 0L
        releaseCamera()
        releaseFaceDetector()
        releaseMediaPlayer()
    }

    fun snooze(minutes: Int = snoozeMinutes) {
        snoozeEndTime = System.currentTimeMillis() + (minutes * 60L * 1000L)
        isSnoozed = true
        Log.i(TAG, "Snooze activado por $minutes minutos.")
    }

    fun resetAlertCount() {
        alertCountThisHour = 0
        hourResetTime = System.currentTimeMillis()
    }

    // ==========================================
    // ML Kit Face Detection Setup
    // ==========================================

    private fun setupFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()

        faceDetector = FaceDetection.getClient(options)
        Log.d(TAG, "ML Kit Face Detection configurado.")
    }

    private fun releaseFaceDetector() {
        try {
            faceDetector?.close()
            faceDetector = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar FaceDetector: ${e.message}")
        }
    }

    // ==========================================
    // Camera2 Setup
    // ==========================================

    @SuppressLint("MissingPermission")
    private fun setupCamera() {
        cameraThread = HandlerThread("FrontFaceCameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Buscar cámara frontal
        val frontCameraId = findFrontCamera(cameraManager) ?: run {
            Log.e(TAG, "No se encontró cámara frontal.")
            stop()
            return
        }

        try {
            // Configurar ImageReader
            imageReader = ImageReader.newInstance(
                CAMERA_WIDTH, CAMERA_HEIGHT,
                ImageFormat.YUV_420_888, 2
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    processFrame(image)
                } finally {
                    image.close()
                }
            }, cameraHandler)

            // Abrir cámara frontal
            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Cámara frontal abierta.")
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Cámara frontal desconectada.")
                    camera.close()
                    cameraDevice = null
                    if (isActive) stop()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Error en cámara frontal: $error")
                    camera.close()
                    cameraDevice = null
                    if (isActive) stop()
                }
            }, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar cámara frontal: ${e.message}")
            stop()
        }
    }

    private fun findFrontCamera(cameraManager: CameraManager): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return null
    }

    private fun createCaptureSession() {
        val camera = cameraDevice ?: return
        val surface = imageReader?.surface ?: return

        try {
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Sesión de captura frontal configurada.")
                        captureSession = session
                        startRepeatingPreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Falló configuración de sesión frontal.")
                        if (isActive) stop()
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear sesión: ${e.message}")
        }
    }

    private fun startRepeatingPreview() {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val surface = imageReader?.surface ?: return

        try {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            session.setRepeatingRequest(
                requestBuilder.build(),
                null,
                cameraHandler
            )
            Log.d(TAG, "Preview frontal iniciado.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar preview: ${e.message}")
        }
    }

    private fun releaseCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler = null
            Log.d(TAG, "Cámara frontal liberada.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar cámara: ${e.message}")
        }
    }

    // ==========================================
    // Procesamiento de Frames
    // ==========================================

    @SuppressLint("UnsafeOptInUsageError")
    private fun processFrame(image: android.media.Image) {
        if (!isActive) return

        val mediaImage = image
        if (mediaImage.planes.isEmpty()) return

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            0 // Rotation 0 — Camera2 front camera handles rotation
        )

        faceDetector?.process(inputImage)
            ?.addOnSuccessListener { faces ->
                if (!isActive) return@addOnSuccessListener
                handleDetectionResults(faces)
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error en ML Kit: ${e.message}")
            }
    }

    private fun handleDetectionResults(faces: List<Face>) {
        if (!isActive) return

        // Resetear estado
        isFaceDetected = false
        currentEar = 0.0

        if (faces.isEmpty()) {
            // No hay rostros → resetear si había detección previa
            if (closedEyeStartTime > 0) {
                closedEyeStartTime = 0L
                closedEyeDurationActual = 0.0
            }
            emitFaceStatus()
            return
        }

        // Seleccionar conductor: rostro con mayor x en frame raw
        // (izquierda en preview espejado = conductor)
        val driverFace = faces.maxByOrNull { it.boundingBox.centerX() } ?: return

        isFaceDetected = true

        // Calcular EAR
        val ear = calculateEAR(driverFace)
        currentEar = ear

        val now = System.currentTimeMillis()

        if (ear < earThreshold) {
            // Ojos cerrados detectados
            if (closedEyeStartTime == 0L) {
                closedEyeStartTime = now
                Log.d(TAG, "Ojos cerrados detectados. Iniciando contador.")
            }
            closedEyeDurationActual = (now - closedEyeStartTime) / 1000.0

            // Verificar si se alcanzó el umbral de tiempo
            if (now - closedEyeStartTime >= closedEyeDurationMs) {
                // Verificar anti-spam
                val hourElapsed = now - hourResetTime
                if (hourElapsed > 3600000L) {
                    // Resetear contador de alertas por hora
                    alertCountThisHour = 0
                    hourResetTime = now
                }

                if (alertCountThisHour < maxAlertsPerHour && !isSnoozed) {
                    if (now - lastAlertTime > 5000L) { // Mínimo 5s entre alertas
                        triggerDrowsinessAlert(ear)
                    }
                }
            }
        } else {
            // Ojos abiertos → resetear
            if (closedEyeStartTime > 0) {
                Log.d(TAG, "Ojos abiertos. Duración total: ${closedEyeDurationActual}s")
            }
            closedEyeStartTime = 0L
            closedEyeDurationActual = 0.0
        }

        // Verificar snooze
        if (isSnoozed && now >= snoozeEndTime) {
            isSnoozed = false
            Log.i(TAG, "Snooze finalizado.")
        }

        emitFaceStatus()
    }

    // ==========================================
    // Cálculo de EAR (Eye Aspect Ratio)
    // ==========================================

    private fun calculateEAR(face: Face): Double {
        val leftEye = face.getContour(FaceContour.LEFT_EYE)?.points
        val rightEye = face.getContour(FaceContour.RIGHT_EYE)?.points

        if (leftEye == null || rightEye == null || leftEye.size < 6 || rightEye.size < 6) {
            return 1.0 // Default: ojos abiertos si no hay datos suficientes
        }

        val leftEAR = computeSingleEyeEAR(leftEye)
        val rightEAR = computeSingleEyeEAR(rightEye)

        return (leftEAR + rightEAR) / 2.0
    }

    private fun computeSingleEyeEAR(points: List<PointF>): Double {
        // ML Kit returned 6 contour points for each eye in order:
        // p0=p1 (left corner), p1=top-left, p2=top-right, p3=p4 (right corner), p4=bottom-right, p5=bottom-left
        // More precisely for the 6-point contour returned by ML Kit:
        // p0: left corner, p1: upper lid left, p2: upper lid right, p3: right corner, p4: lower lid right, p5: lower lid left

        val p1 = points[0] // left corner
        val p2 = points[1] // upper left
        val p3 = points[2] // upper right
        val p4 = points[3] // right corner
        val p5 = points[4] // lower right
        val p6 = points[5] // lower left

        // Vertical distances
        val vertical1 = distance(p2, p6)
        val vertical2 = distance(p3, p5)

        // Horizontal distance
        val horizontal = distance(p1, p4)

        if (horizontal == 0f) return 1.0 // Evitar división por cero

        return ((vertical1 + vertical2) / (2.0 * horizontal))
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    // ==========================================
    // Alertas (Vibración + Sonido)
    // ==========================================

    private fun triggerDrowsinessAlert(ear: Double) {
        val now = System.currentTimeMillis()
        lastAlertTime = now
        alertCountThisHour++
        Log.w(TAG, "🚨 FATIGA DETECTADA! EAR=$ear, Alertas esta hora: $alertCountThisHour")

        // Vibrar
        triggerVibration()

        // Sonido
        triggerAlarmSound()

        // Emitir evento a JS
        eventListener?.onDrowsinessDetected(now, ear)
    }

    private fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 500, 200, 500)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en vibración: ${e.message}")
        }
    }

    private fun triggerAlarmSound() {
        try {
            releaseMediaPlayer()
            val alertResId = context.resources.getIdentifier(
                "alarm_alert", "raw", context.packageName
            )
            if (alertResId != 0) {
                mediaPlayer = MediaPlayer.create(context, alertResId)
                mediaPlayer?.setOnCompletionListener { releaseMediaPlayer() }
                mediaPlayer?.start()
            } else {
                // Si no hay archivo de sonido, usar tono del sistema
                val toneGen = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_ALARM, 100
                )
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 2000)
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    toneGen.release()
                }, 2100)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en alarma de sonido: ${e.message}")
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }

    // ==========================================
    // Emisión de Eventos a JS
    // ==========================================

    private fun emitFaceStatus() {
        eventListener?.onFaceStatusChanged(
            enabled = isActive,
            faceDetected = isFaceDetected,
            earValue = currentEar,
            closedEyeDuration = closedEyeDurationActual
        )
    }
}
