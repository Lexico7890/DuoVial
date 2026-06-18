package com.duovial.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

interface CameraStatusListener {
    fun onStatusChanged(status: String)
    fun onAccelChanged(gForce: Double)
    fun onSpeedChanged(speed: Double)
    fun onFaceStatusChanged(enabled: Boolean, faceDetected: Boolean, earValue: Double, closedEyeDuration: Double)
    fun onDrowsinessDetected(timestamp: Long, earValue: Double)
}

class BackgroundCameraService : LifecycleService() {

    private val TAG = "DuoVial_CameraService"
    private val NOTIFICATION_ID = 144
    private val CHANNEL_ID = "duovial_camera_service_channel"

    enum class ServiceState { STANDBY, RECORDING, SAVING }
    enum class SaveMode { SCENARIO_1, SAVE_PREV_AND_CURR, SAVE_ONLY_PREV, SAVE_ONLY_CURR }

    private var serviceState = ServiceState.STANDBY
    private var saveMode = SaveMode.SAVE_ONLY_CURR

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentActiveRecording: Recording? = null
    private var cameraReady = false

    private var currentRecordingIndex = 0
    private var recordingStartTime = 0L
    private var isSavingEvent = false
    private var postEventRecordingActive = false
    private var isStoppingService = false
    private var eventTimestamp = 0L
    private var cameraInitAttempts = 0
    private val maxCameraInitAttempts = 3
    private var hasCompletedSegment0 = false
    private var hasCompletedSegment1 = false

    private val handler = Handler(Looper.getMainLooper())

    private val cameraLifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val facialLifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var lastAccelUpdateTime = 0L
    private var locationManager: LocationManager? = null
    private var lastEventTriggerTime = 0L

    @Volatile private var lastKnownGForce: Double = -1.0
    @Volatile private var lastKnownSpeed: Double = -1.0
    @Volatile var gForceThreshold: Double = 2.5
        private set

    private var windowManager: WindowManager? = null
    private var floatingBubbleView: View? = null

    private var fatigueCameraManager: FatigueCameraManager? = null
    private var faceProcessor: FaceProcessor? = null

    private var earThreshold: Double = 0.2
    private var closedEyeDurationMs: Long = 2000L
    private var maxAlertsPerHour: Int = 3
    private var snoozeMinutes: Int = 5
    private var isSnoozed: Boolean = false
    private var snoozeEndTime: Long = 0L
    private var closedEyeStartTime: Long = 0L
    private var currentClosedEyeDurationMs: Long = 0L
    private var lastAlertTime: Long = 0L
    private var alertCountThisHour: Int = 0
    private var hourResetTime: Long = 0L
    @Volatile private var currentEar: Double = 0.0
    @Volatile private var currentFaceDetected: Boolean = false

    companion object {
        var instance: BackgroundCameraService? = null
        var activePreview: Preview? = null
        var activePreviewView: PreviewView? = null
        var statusListener: CameraStatusListener? = null

        @Volatile var serviceStarting: Boolean = false
            private set
        @Volatile var pendingPreviewView: PreviewView? = null
        @Volatile var pendingGForceThreshold: Double? = null
        @Volatile var pendingEarThreshold: Double? = null
        @Volatile var pendingFatigueEnabled: Boolean? = null
        @Volatile var activeFrontPreviewView: PreviewView? = null
        @Volatile var pendingFrontPreviewView: PreviewView? = null

        internal fun markServiceStarting() { serviceStarting = true }
        internal fun clearServiceStarting() { serviceStarting = false }

        const val ACTION_START_STANDBY = "ACTION_START_STANDBY"
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_TRIGGER_PANIC = "ACTION_TRIGGER_PANIC"
        const val ACTION_STOP_AND_SAVE = "ACTION_STOP_AND_SAVE"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_ENABLE_FATIGUE = "ACTION_ENABLE_FATIGUE"
        const val ACTION_DISABLE_FATIGUE = "ACTION_DISABLE_FATIGUE"
        const val ACTION_SET_EAR_THRESHOLD = "ACTION_SET_EAR_THRESHOLD"
        const val ACTION_SET_DURATION_THRESHOLD = "ACTION_SET_DURATION_THRESHOLD"
        const val ACTION_SET_MAX_ALERTS = "ACTION_SET_MAX_ALERTS"
        const val ACTION_SNOOZE_FATIGUE = "ACTION_SNOOZE_FATIGUE"
    }

    private val rotateRunnable = Runnable {
        Log.d(TAG, "Rotando buffer circular...")
        rotateCircularBuffer()
    }

    private val stopPostEventRunnable = Runnable {
        Log.d(TAG, "Deteniendo grabacion de post-evento...")
        currentActiveRecording?.stop()
    }

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                val gForce = magnitude / SensorManager.GRAVITY_EARTH
                lastKnownGForce = gForce
                val now = System.currentTimeMillis()
                if (now - lastAccelUpdateTime > 200) {
                    lastAccelUpdateTime = now
                    statusListener?.onAccelChanged(gForce)
                }
                if (gForce > gForceThreshold) {
                    triggerCollisionEvent("Acelerometro Impacto Detectado: ${String.format("%.2f", gForce)} G")
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedMph = location.speed * 2.23694
            lastKnownSpeed = speedMph
            statusListener?.onSpeedChanged(speedMph)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creando servicio de camara...")
        instance = this
        clearServiceStarting()

        pendingGForceThreshold?.let {
            gForceThreshold = it
            pendingGForceThreshold = null
            Log.i(TAG, "Aplicado umbral G-Force pendiente: $it G")
        }

        pendingEarThreshold?.let {
            earThreshold = it
            pendingEarThreshold = null
            Log.i(TAG, "Aplicado umbral EAR pendiente: $it")
        }

        fatigueCameraManager = FatigueCameraManager(this)
        faceProcessor = FaceProcessor(this).apply {
            setCallback(object : FaceProcessor.Callback {
                override fun onFaceProcessed(faceDetected: Boolean, earValue: Double) {
                    handleFaceProcessingResult(faceDetected, earValue)
                }
            })
        }

        if (pendingFatigueEnabled == true) {
            startFrontCameraSession()
            pendingFatigueEnabled = null
            Log.i(TAG, "Deteccion de fatiga auto-activada desde pending.")
        }

        startServiceNotification()
        startCameraX()
        startLocationUpdates()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Servicio iniciado con action: ${intent?.action}")
        if (intent != null) {
            when (intent.action) {
                ACTION_START_STANDBY -> startStandbyMode()
                ACTION_START_RECORDING -> startRecordingMode()
                ACTION_TRIGGER_PANIC -> triggerCollisionEvent("Boton de Panico Manual o Burbuja")
                ACTION_STOP_AND_SAVE -> stopAndSaveBuffer()
                ACTION_STOP_RECORDING -> stopRecordingWithoutSaving()
                ACTION_STOP_SERVICE -> stopSelf()
                ACTION_ENABLE_FATIGUE -> {
                    val enable = intent.getBooleanExtra("enable", true)
                    toggleFatigueDetection(enable)
                }
                ACTION_DISABLE_FATIGUE -> toggleFatigueDetection(false)
                ACTION_SET_EAR_THRESHOLD -> {
                    val threshold = intent.getDoubleExtra("ear_threshold", 0.2)
                    setEarThreshold(threshold)
                }
                ACTION_SET_DURATION_THRESHOLD -> {
                    val ms = intent.getLongExtra("duration_ms", 2000L)
                    closedEyeDurationMs = ms
                    Log.i(TAG, "Duracion ojos cerrados actualizada a ${ms}ms")
                }
                ACTION_SET_MAX_ALERTS -> {
                    val max = intent.getIntExtra("max_alerts", 3)
                    maxAlertsPerHour = max
                    Log.i(TAG, "Max alertas/hora actualizado a $max")
                }
                ACTION_SNOOZE_FATIGUE -> {
                    val minutes = intent.getIntExtra("minutes", 5)
                    snoozeFatigueAlert(minutes)
                }
            }
        } else {
            startStandbyMode()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destruyendo servicio de camara...")
        instance = null
        stopCircularBufferTimers()
        stopSensors()
        stopFatigueDetection()
        stopLocationUpdates()
        removeFloatingBubble()
        currentActiveRecording?.stop()
        currentActiveRecording = null
        activePreview?.setSurfaceProvider(null)
        activePreview = null
        cameraProvider?.unbindAll()
        cameraLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
        facialLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
        statusListener?.onStatusChanged("INACTIVO")
        super.onDestroy()
    }

    fun bindPreviewUseCase(previewView: PreviewView) {
        val preview = activePreview
        if (preview == null) {
            Log.i(TAG, "Preview aun no listo — encolando PreviewView como pendiente.")
            pendingPreviewView = previewView
            return
        }
        Log.i(TAG, "Asociando SurfaceProvider al preview activo...")
        handler.post {
            try {
                preview.setSurfaceProvider(previewView.surfaceProvider)
                Log.d(TAG, "SurfaceProvider asociado con exito.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al asociar SurfaceProvider: ${e.message}")
            }
        }
    }

    private fun flushPendingPreview() {
        val view = pendingPreviewView ?: return
        val preview = activePreview ?: return
        Log.i(TAG, "Conectando PreviewView pendiente de la cola...")
        try {
            preview.setSurfaceProvider(view.surfaceProvider)
            activePreviewView = view
            Log.d(TAG, "Pending preview vaciado con exito.")
        } catch (e: Exception) {
            Log.e(TAG, "Error vaciando pending preview: ${e.message}")
        } finally {
            pendingPreviewView = null
        }
    }

    fun unbindPreviewUseCase() {
        val preview = activePreview ?: return
        Log.i(TAG, "Desasociando SurfaceProvider...")
        handler.post {
            try {
                preview.setSurfaceProvider(null)
                Log.d(TAG, "SurfaceProvider desasociado.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al desasociar SurfaceProvider: ${e.message}")
            }
        }
    }

    fun startStandbyMode() {
        Log.i(TAG, "Cambiando a modo STANDBY...")
        serviceState = ServiceState.STANDBY
        isSavingEvent = false
        postEventRecordingActive = false
        saveMode = SaveMode.SAVE_ONLY_CURR
        isStoppingService = false
        stopCircularBufferTimers()
        stopSensors()
        currentActiveRecording?.stop()
        currentActiveRecording = null
        updateNotification("DuoVial", "Camara lista.")
        sendStatusUpdate("INACTIVO")
    }

    fun forceResetToStandby() {
        Log.w(TAG, "FORCE RESET TO STANDBY — abortando todo estado intermedio.")
        stopCircularBufferTimers()
        try { currentActiveRecording?.stop() } catch (e: Exception) { Log.e(TAG, "Error al detener grabacion durante forceReset: ${e.message}") }
        currentActiveRecording = null
        isSavingEvent = false
        isStoppingService = false
        postEventRecordingActive = false
        saveMode = SaveMode.SAVE_ONLY_CURR
        hasCompletedSegment0 = false
        hasCompletedSegment1 = false
        serviceState = ServiceState.STANDBY
        updateNotification("DuoVial", "Camara lista.")
        sendStatusUpdate("INACTIVO")
        Log.i(TAG, "Force reset completo. Servicio en STANDBY.")
    }

    fun setGForceThreshold(threshold: Double) {
        if (threshold < 1.5 || threshold > 5.0) {
            Log.w(TAG, "Umbral G-Force fuera de rango (1.5..5.0): $threshold — ignorado.")
            return
        }
        gForceThreshold = threshold
        Log.i(TAG, "Umbral G-Force actualizado a $threshold G")
    }

    fun startRecordingMode() {
        if (serviceState == ServiceState.RECORDING) {
            Log.d(TAG, "El servicio ya esta en modo RECORDING. Re-emitiendo estado para sincronizar JS.")
            sendStatusUpdate("DUOVIAL ACTIVO")
            return
        }
        Log.i(TAG, "Cambiando a modo RECORDING...")
        serviceState = ServiceState.RECORDING
        updateNotification("DuoVial - Vigilando", "Grabacion circular activa en segundo plano.")
        startSensors()
        if (cameraReady) {
            startCircularBuffer()
        } else {
            Log.w(TAG, "CameraX aun no esta lista. El buffer circular arrancara cuando termine la inicializacion.")
            sendStatusUpdate("INICIANDO DUOVIAL")
        }
    }

    fun onPreviewViewDropped() {
        activePreviewView = null
        activePreview?.setSurfaceProvider(null)
        Log.i(TAG, "Vista de previsualizacion descartada. Surface desconectado.")
    }

    fun onFrontPreviewAvailable(previewView: PreviewView) {
        pendingFrontPreviewView = previewView
        val facial = fatigueCameraManager
        if (facial != null && faceProcessor?.isActive == true) {
            try {
                facial.start(facialLifecycleOwner, previewView)
                Log.d(TAG, "Front PreviewView vinculado en caliente a FatigueCameraManager.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al vincular front PreviewView: ${e.message}")
            }
        }
    }

    fun onFrontPreviewDropped() {
        activeFrontPreviewView = null
        pendingFrontPreviewView = null
        fatigueCameraManager?.stop()
        Log.i(TAG, "Front PreviewView descartada. Camara frontal detenida.")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun sendStatusUpdate(status: String) {
        statusListener?.onStatusChanged(status)
        Log.d(TAG, "Estado actualizado: $status")
    }

    // ==========================================
    // NOTIFICACION PERSISTENTE (FOREGROUND)
    // ==========================================

    private fun startServiceNotification() {
        createNotificationChannel()
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DuoVial")
            .setContentText("Camara lista.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            val hasFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                serviceType = serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "DuoVial - Servicio de Camara", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // ==========================================
    // CONFIGURACION DE CAMERAX CON PREVIEW
    // ==========================================

    private fun startCameraX() {
        Log.i(TAG, "Iniciando CameraX...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                val preview = Preview.Builder().build()
                activePreview = preview
                val activeView = activePreviewView
                if (activeView != null) {
                    try {
                        preview.setSurfaceProvider(activeView.surfaceProvider)
                        Log.d(TAG, "PreviewView vinculado con exito en startCameraX.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al vincular vista: ${e.message}")
                    }
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                cameraLifecycleOwner.registry.currentState = Lifecycle.State.STARTED
                cameraLifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
                cameraProvider?.bindToLifecycle(cameraLifecycleOwner, cameraSelector, preview, videoCapture)
                Log.d(TAG, "CameraX vinculada correctamente (Preview + VideoCapture) con lifecycle RESUMED.")
                cameraReady = true
                flushPendingPreview()
                handler.postDelayed({
                    val view = activePreviewView
                    if (view != null && activePreview != null) {
                        activePreview?.setSurfaceProvider(view.surfaceProvider)
                        Log.d(TAG, "Surface provider re-aplicado con retraso de seguridad (500ms).")
                    }
                }, 500)
                if (serviceState == ServiceState.RECORDING) {
                    Log.i(TAG, "CameraX lista y modo RECORDING pendiente. Arrancando buffer circular...")
                    startCircularBuffer()
                } else {
                    sendStatusUpdate("INACTIVO")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar CameraX: ${e.message}")
                sendStatusUpdate("ERROR EN CAMARA")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ==========================================
    // LOGICA DE BUFFER CIRCULAR (15 SEGUNDOS)
    // ==========================================

    private fun startCircularBuffer() {
        isSavingEvent = false
        postEventRecordingActive = false
        saveMode = SaveMode.SAVE_ONLY_CURR
        isStoppingService = false
        hasCompletedSegment0 = false
        hasCompletedSegment1 = false
        try {
            val f0 = File(cacheDir, "segment_0.mp4")
            if (f0.exists()) f0.delete()
            val f1 = File(cacheDir, "segment_1.mp4")
            if (f1.exists()) f1.delete()
            val fPost = File(cacheDir, "segment_post.mp4")
            if (fPost.exists()) fPost.delete()
            Log.d(TAG, "Cache de segmentos de video limpiada con exito.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar cache de segmentos: ${e.message}")
        }
        sendStatusUpdate("INICIANDO DUOVIAL")
        startRecordingSegment(0)
    }

    private fun startRecordingSegment(index: Int) {
        if (videoCapture == null) { Log.e(TAG, "No se puede grabar: videoCapture es nulo."); return }
        currentRecordingIndex = index
        val segmentFile = File(cacheDir, "segment_$index.mp4")
        if (segmentFile.exists()) segmentFile.delete()
        val fileOutputOptions = FileOutputOptions.Builder(segmentFile).build()
        try {
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Start) {
                    Log.d(TAG, "Grabacion de segmento_$index iniciada con exito.")
                    if (!isSavingEvent) sendStatusUpdate("DUOVIAL ACTIVO")
                } else if (event is VideoRecordEvent.Finalize) {
                    onRecordingFinalized(event)
                }
            }
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Grabando segmento_$index en cache...")
            handler.postDelayed(rotateRunnable, 15000)
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar grabacion: ${e.message}")
        }
    }

    private fun rotateCircularBuffer() { currentActiveRecording?.stop() }

    private fun onRecordingFinalized(event: VideoRecordEvent.Finalize) {
        val index = currentRecordingIndex
        if (event.hasError()) {
            Log.e(TAG, "Grabacion finalizada con error: Codigo ${event.error}, Causa: ${event.cause?.message}")
        } else {
            Log.d(TAG, "Grabacion de segmento finalizada con exito.")
        }
        if (serviceState != ServiceState.RECORDING) {
            Log.w(TAG, "Finalize disparado pero serviceState=$serviceState. No se inicia nuevo segmento.")
            return
        }
        if (!isSavingEvent) {
            sendStatusUpdate("DUOVIAL ACTIVO")
            if (!event.hasError()) {
                if (index == 0) { hasCompletedSegment0 = true; Log.d(TAG, "Segmento 0 marcado como COMPLETADO.") }
                else if (index == 1) { hasCompletedSegment1 = true; Log.d(TAG, "Segmento 1 marcado como COMPLETADO.") }
            }
            val nextIndex = 1 - currentRecordingIndex
            startRecordingSegment(nextIndex)
        } else {
            handleEventSaveTransition()
        }
    }

    private fun stopCircularBufferTimers() {
        handler.removeCallbacks(rotateRunnable)
        handler.removeCallbacks(stopPostEventRunnable)
    }

    // ==========================================
    // LOGICA DE DETECCION Y GUARDADO DE EVENTO / PARADA
    // ==========================================

    private fun triggerCollisionEvent(reason: String) {
        if (isStoppingService) return
        val now = System.currentTimeMillis()
        if (now - lastEventTriggerTime < 5000) return
        lastEventTriggerTime = now
        Log.w(TAG, "EVENTO DETECTADO POR: $reason")
        saveEvent()
    }

    private fun saveEvent() {
        if (isSavingEvent) return
        triggerVibration()
        stopCircularBufferTimers()
        eventTimestamp = System.currentTimeMillis() / 1000
        isSavingEvent = true
        postEventRecordingActive = false
        val duration = System.currentTimeMillis() - recordingStartTime
        Log.d(TAG, "Guardando evento. Duracion del segmento actual: $duration ms")
        val prevIndex = 1 - currentRecordingIndex
        val hasCompletedPrevFile = if (prevIndex == 0) hasCompletedSegment0 else hasCompletedSegment1
        Log.d(TAG, "hasCompletedPrevFile ($prevIndex): $hasCompletedPrevFile (Segment0: $hasCompletedSegment0, Segment1: $hasCompletedSegment1)")
        if (!hasCompletedPrevFile) {
            saveMode = SaveMode.SCENARIO_1
        } else {
            if (duration < 3000) saveMode = SaveMode.SAVE_ONLY_PREV
            else if (duration < 14000) saveMode = SaveMode.SAVE_PREV_AND_CURR
            else saveMode = SaveMode.SAVE_ONLY_CURR
        }
        Log.d(TAG, "Modo de guardado de evento establecido: $saveMode")
        sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
        currentActiveRecording?.stop()
    }

    private fun stopAndSaveBuffer() {
        if (isSavingEvent || isStoppingService) return
        Log.i(TAG, "Deteniendo Vigilante. Guardando buffer actual y apagando...")
        stopCircularBufferTimers()
        eventTimestamp = System.currentTimeMillis() / 1000
        isSavingEvent = true
        isStoppingService = true
        postEventRecordingActive = false
        val duration = System.currentTimeMillis() - recordingStartTime
        val prevIndex = 1 - currentRecordingIndex
        val hasCompletedPrevFile = if (prevIndex == 0) hasCompletedSegment0 else hasCompletedSegment1
        if (!hasCompletedPrevFile) { saveMode = SaveMode.SCENARIO_1 }
        else {
            if (duration < 3000) saveMode = SaveMode.SAVE_ONLY_PREV
            else if (duration < 14000) saveMode = SaveMode.SAVE_PREV_AND_CURR
            else saveMode = SaveMode.SAVE_ONLY_CURR
        }
        Log.d(TAG, "stopAndSave: Modo de guardado establecido: $saveMode")
        sendStatusUpdate("INICIANDO DUOVIAL")
        currentActiveRecording?.stop()
    }

    private fun stopRecordingWithoutSaving() {
        if (serviceState != ServiceState.RECORDING) return
        if (isSavingEvent) return
        Log.i(TAG, "Deteniendo grabacion sin guardar buffer...")
        stopCircularBufferTimers()
        isStoppingService = true
        serviceState = ServiceState.STANDBY
        currentActiveRecording?.stop()
        cleanupCacheSegments()
        isSavingEvent = false
        isStoppingService = false
        postEventRecordingActive = false
        saveMode = SaveMode.SAVE_ONLY_CURR
        hasCompletedSegment0 = false
        hasCompletedSegment1 = false
        updateNotification("DuoVial", "Camara lista.")
        sendStatusUpdate("INACTIVO")
        Log.i(TAG, "Grabacion detenida sin guardar. Servicio en STANDBY.")
    }

    private fun cleanupCacheSegments() {
        try {
            listOf("segment_0.mp4", "segment_1.mp4", "segment_post.mp4").forEach { name ->
                File(cacheDir, name).let { if (it.exists()) it.delete() }
            }
            Log.d(TAG, "Segmentos de cache eliminados.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar cache: ${e.message}")
        }
    }

    private fun handleEventSaveTransition() {
        if (isStoppingService) {
            savePreEventSegments()
            Log.i(TAG, "Buffer pre-evento guardado. Volviendo a STANDBY.")
            isSavingEvent = false
            isStoppingService = false
            postEventRecordingActive = false
            startStandbyMode()
            return
        }
        if (!postEventRecordingActive) {
            savePreEventSegments()
            postEventRecordingActive = true
            startPostEventRecording()
        } else {
            val finalPartIndex = when (saveMode) {
                SaveMode.SCENARIO_1 -> 1
                SaveMode.SAVE_ONLY_PREV -> 1
                SaveMode.SAVE_ONLY_CURR -> 1
                SaveMode.SAVE_PREV_AND_CURR -> 2
            }
            copyFileToDownloads("segment_post.mp4", "incident_${eventTimestamp}_part$finalPartIndex.mp4")
            Log.i(TAG, "Incidente guardado con exito. Reanudando buffer circular...")
            sendStatusUpdate("DUOVIAL ACTIVO")
            val postFile = File(cacheDir, "segment_post.mp4")
            if (postFile.exists()) postFile.delete()
            startCircularBuffer()
        }
    }

    private fun savePreEventSegments() {
        val prevIndex = 1 - currentRecordingIndex
        when (saveMode) {
            SaveMode.SCENARIO_1 -> copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
            SaveMode.SAVE_PREV_AND_CURR -> {
                copyFileToDownloads("segment_$prevIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
                copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part1.mp4")
            }
            SaveMode.SAVE_ONLY_PREV -> copyFileToDownloads("segment_$prevIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
            SaveMode.SAVE_ONLY_CURR -> copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
        }
    }

    private fun startPostEventRecording() {
        if (videoCapture == null) { Log.e(TAG, "No se puede iniciar post-evento: videoCapture es nulo."); startCircularBuffer(); return }
        sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
        val postFile = File(cacheDir, "segment_post.mp4")
        if (postFile.exists()) postFile.delete()
        val fileOutputOptions = FileOutputOptions.Builder(postFile).build()
        try {
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) onRecordingFinalized(event)
            }
            Log.d(TAG, "Grabando 15s de post-evento...")
            handler.postDelayed(stopPostEventRunnable, 15000)
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar post-evento: ${e.message}")
            startCircularBuffer()
        }
    }

    // ==========================================
    // EXPORTACION A DOWNLOADS POR MEDIASTORE
    // ==========================================

    private fun copyFileToDownloads(sourceFileName: String, targetFileName: String) {
        val sourceFile = File(cacheDir, sourceFileName)
        if (!sourceFile.exists() || sourceFile.length() == 0L) { Log.w(TAG, "Archivo de origen no existe o esta vacio: $sourceFileName"); return }
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, targetFileName)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.RELATIVE_PATH, "Download/DuoVial")
        }
        val resolver = contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        try {
            val uri = resolver.insert(collectionUri, contentValues)
            if (uri != null) {
                outputStream = resolver.openOutputStream(uri)
                inputStream = FileInputStream(sourceFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream?.write(buffer, 0, bytesRead)
                }
                outputStream?.flush()
                Log.d(TAG, "Copiado exitoso a Downloads/DuoVial: $targetFileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepcion al exportar: ${e.message}")
        } finally {
            try { outputStream?.close(); inputStream?.close() } catch (e: Exception) {}
        }
    }

    // ==========================================
    // MONITOREO DE ACELEROMETRO NATIVO (G-FORCE)
    // ==========================================

    private fun startSensors() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelSensor != null) {
                sensorManager?.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Acelerometro registrado.")
            }
        } catch (e: Exception) { Log.e(TAG, "Error al registrar acelerometro: ${e.message}") }
    }

    private fun stopSensors() { sensorManager?.unregisterListener(accelListener) }

    private fun startLocationUpdates() {
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
                    val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("DuoVial")
                        .setContentText(if (serviceState == ServiceState.RECORDING) "Grabacion circular activa." else "Camara lista.")
                        .setSmallIcon(android.R.drawable.presence_video_online)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                        .build()
                    startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                }
                locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                    locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
                    Log.w(TAG, "GPS Provider registrado para velocimetro.")
                }
                if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                    locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener)
                    Log.w(TAG, "Network Provider registrado para velocimetro.")
                }
            } else { Log.e(TAG, "Sin permisos de GPS — no se puede iniciar velocimetro.") }
        } catch (e: SecurityException) { Log.e(TAG, "Error de seguridad al iniciar velocimetro: ${e.message}") }
        catch (e: Exception) { Log.e(TAG, "Error al iniciar velocimetro: ${e.message}") }
    }

    private fun stopLocationUpdates() {
        try { locationManager?.removeUpdates(locationListener); Log.w(TAG, "Velocimetro desvinculado con exito.") }
        catch (e: Exception) { Log.e(TAG, "Error al detener actualizaciones de ubicacion: ${e.message}") }
    }

    fun resyncJsState() {
        val currentStatus = when (serviceState) {
            ServiceState.STANDBY -> "INACTIVO"
            ServiceState.RECORDING -> "DUOVIAL ACTIVO"
            ServiceState.SAVING -> "GENERANDO CONTENIDO POST EVENTO"
        }
        statusListener?.onStatusChanged(currentStatus)
        if (lastKnownGForce >= 0) statusListener?.onAccelChanged(lastKnownGForce)
        if (lastKnownSpeed >= 0) statusListener?.onSpeedChanged(lastKnownSpeed)
        Log.i(TAG, "Estado re-sincronizado con JS: $currentStatus")
    }

    // ==========================================
    // DETECCION DE SOMNOLENCIA (FATIGA)
    // ==========================================

    fun toggleFatigueDetection(enable: Boolean) {
        if (enable) {
            Log.i(TAG, "Activando deteccion de fatiga...")
            startFrontCameraSession()
        } else {
            Log.i(TAG, "Desactivando deteccion de fatiga.")
            stopFatigueDetection()
        }
    }

    private fun startFrontCameraSession() {
        val facial = fatigueCameraManager ?: run {
            Log.e(TAG, "FatigueCameraManager no disponible.")
            return
        }
        val processor = faceProcessor ?: run {
            Log.e(TAG, "FaceProcessor no disponible.")
            return
        }
        if (processor.isActive) {
            Log.w(TAG, "FaceProcessor ya esta activo.")
            return
        }

        hourResetTime = System.currentTimeMillis()
        alertCountThisHour = 0
        closedEyeStartTime = 0L
        currentClosedEyeDurationMs = 0L

        val frontView = activeFrontPreviewView ?: pendingFrontPreviewView
        if (frontView == null) {
            Log.w(TAG, "Front PreviewView aun no disponible — encolando activacion para cuando llegue.")
            pendingFatigueEnabled = true
            return
        }

        facial.setOnFrameAvailable { imageProxy -> processor.processFrame(imageProxy) }
        processor.start()
        facialLifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        facial.start(facialLifecycleOwner, frontView)
        Log.i(TAG, "Camara frontal + FaceProcessor activados.")
        sendFatigueStatusEvent(true, false, 0.0, 0.0)
    }

    private fun stopFatigueDetection() {
        fatigueCameraManager?.stop()
        facialLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
        faceProcessor?.stop()
        pendingFatigueEnabled = false
        closedEyeStartTime = 0L
        currentClosedEyeDurationMs = 0L
        currentEar = 0.0
        currentFaceDetected = false
        Log.i(TAG, "Deteccion de fatiga completamente detenida.")
        sendFatigueStatusEvent(false, false, 0.0, 0.0)
    }

    fun setEarThreshold(threshold: Double) {
        if (threshold < 0.1 || threshold > 0.4) {
            Log.w(TAG, "Umbral EAR fuera de rango (0.1..0.4): $threshold — ignorado.")
            return
        }
        earThreshold = threshold
        Log.i(TAG, "Umbral EAR actualizado a $threshold")
    }

    fun getEarThreshold(): Double = earThreshold

    fun snoozeFatigueAlert(minutes: Int) {
        snoozeMinutes = minutes
        snoozeEndTime = System.currentTimeMillis() + (minutes * 60L * 1000L)
        isSnoozed = true
        Log.i(TAG, "Snooze de fatiga activado por $minutes minutos.")
    }

    fun getFatigueStatus(): Map<String, Any> {
        return mapOf<String, Any>(
            "enabled" to (faceProcessor?.isActive ?: false),
            "faceDetected" to currentFaceDetected,
            "earValue" to currentEar,
            "closedEyeDuration" to currentClosedEyeDurationMs.toDouble(),
            "isSnoozed" to isSnoozed,
            "alertCount" to alertCountThisHour,
            "earThreshold" to earThreshold,
            "maxAlertsPerHour" to maxAlertsPerHour
        )
    }

    private fun handleFaceProcessingResult(faceDetected: Boolean, earValue: Double) {
        currentFaceDetected = faceDetected
        currentEar = earValue
        val now = System.currentTimeMillis()

        if (!faceDetected || earValue >= earThreshold) {
            if (closedEyeStartTime > 0L) {
                Log.d(TAG, "Ojos abiertos. Duracion total cerrados: ${currentClosedEyeDurationMs}ms")
            }
            closedEyeStartTime = 0L
            currentClosedEyeDurationMs = 0L
        } else {
            if (closedEyeStartTime == 0L) {
                closedEyeStartTime = now
                Log.d(TAG, "Ojos cerrados detectados. Iniciando contador. EAR=$earValue")
            }
            currentClosedEyeDurationMs = now - closedEyeStartTime
            if (currentClosedEyeDurationMs >= closedEyeDurationMs) {
                val hourElapsed = now - hourResetTime
                if (hourElapsed > 3600000L) {
                    alertCountThisHour = 0
                    hourResetTime = now
                }
                if (isSnoozed && now >= snoozeEndTime) {
                    isSnoozed = false
                    Log.i(TAG, "Snooze finalizado.")
                }
                if (alertCountThisHour < maxAlertsPerHour && !isSnoozed) {
                    if (now - lastAlertTime > 5000L) {
                        triggerFatigueAlert(earValue)
                    }
                }
            }
        }

        if (isSnoozed && now >= snoozeEndTime) {
            isSnoozed = false
        }

        sendFatigueStatusEvent(
            enabled = faceProcessor?.isActive ?: false,
            faceDetected = faceDetected,
            earValue = earValue,
            closedEyeDuration = currentClosedEyeDurationMs.toDouble()
        )
    }

    private fun triggerFatigueAlert(earValue: Double) {
        val now = System.currentTimeMillis()
        lastAlertTime = now
        alertCountThisHour++
        Log.w(TAG, "FATIGA DETECTADA! EAR=$earValue, Alertas esta hora: $alertCountThisHour")
        triggerVibration()
        triggerFatigueAlarmSound()
        statusListener?.onDrowsinessDetected(now, earValue)
    }

    private fun triggerFatigueAlarmSound() {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 2000)
            Handler(Looper.getMainLooper()).postDelayed({
                try { toneGen.release() } catch (_: Exception) {}
            }, 2100)
        } catch (e: Exception) {
            Log.e(TAG, "Error en alarma de sonido (fatiga): ${e.message}")
        }
    }

    private fun sendFatigueStatusEvent(enabled: Boolean, faceDetected: Boolean, earValue: Double, closedEyeDuration: Double) {
        statusListener?.onFaceStatusChanged(enabled, faceDetected, earValue, closedEyeDuration)
    }

    private fun sendDrowsinessDetectedEvent(timestamp: Long, earValue: Double) {
        statusListener?.onDrowsinessDetected(timestamp, earValue)
    }

    // ==========================================
    // INTERFAZ DE BURBUJA FLOTANTE DRAGGABLE (PIP)
    // ==========================================

    private fun showFloatingBubble() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val density = resources.displayMetrics.density
            val size = (60 * density).toInt()
            val layoutParams = WindowManager.LayoutParams(
                size, size,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (resources.displayMetrics.widthPixels - size - (20 * density).toInt())
                y = (150 * density).toInt()
            }
            val bubble = FrameLayout(this)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFCC00"))
                setStroke((2.5 * density).toInt(), Color.parseColor("#FFFFFF"))
            }
            bubble.background = shape
            val icon = ImageView(this).apply {
                val padding = (16 * density).toInt()
                setPadding(padding, padding, padding, padding)
                setImageResource(android.R.drawable.presence_video_online)
                setColorFilter(Color.BLACK)
            }
            bubble.addView(icon)
            floatingBubbleView = bubble

            bubble.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isClick = false

                override fun onTouch(view: View?, event: MotionEvent?): Boolean {
                    if (event == null) return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isClick = true
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isClick = false
                            layoutParams.x = initialX + dx.toInt()
                            layoutParams.y = initialY + dy.toInt()
                            windowManager?.updateViewLayout(bubble, layoutParams)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isClick) {
                                triggerCollisionEvent("Boton de Panico Flotante")
                            }
                            return true
                        }
                    }
                    return false
                }
            })
            windowManager?.addView(bubble, layoutParams)
            Log.d(TAG, "Burbuja flotante mostrada con exito.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al mostrar burbuja flotante: ${e.message}")
        }
    }

    private fun removeFloatingBubble() {
        try {
            floatingBubbleView?.let { windowManager?.removeView(it) }
            floatingBubbleView = null
        } catch (e: Exception) { Log.e(TAG, "Error al remover burbuja: ${e.message}") }
    }

    private fun triggerVibration() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), intArrayOf(0, 255, 0, 255), -1))
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error en vibracion: ${e.message}") }
    }
}
