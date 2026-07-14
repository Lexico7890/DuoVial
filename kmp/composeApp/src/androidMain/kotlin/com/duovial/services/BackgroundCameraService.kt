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
import com.duovial.logging.DuoVialLog
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

interface CameraStatusListener {
    fun onStatusChanged(status: String)
    fun onAccelChanged(gForce: Double)
    fun onSpeedChanged(speed: Double)
    fun onTemperatureChanged(tempCelsius: Float)
    fun onFaceStatusChanged(enabled: Boolean, faceDetected: Boolean, earValue: Double, closedEyeDuration: Double)
    fun onDrowsinessDetected(timestamp: Long, earValue: Double)
    fun onConcurrentCamerasNotSupported()
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
    @Volatile private var isDestroyed = false

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
    private var temperatureCheckRunnable: Runnable? = null
    @Volatile private var autoStartEnabled: Boolean = false
    @Volatile private var autoStartPending: Boolean = false
    private var autoStartRunnable: Runnable? = null
    private var lastAutoStartActivationTime: Long = 0L
    // Auto-inicio inteligente
    @Volatile private var autoStartAskBeforeActivate: Boolean = true
    @Volatile private var autoStartCooldownHours: Int = 1
    @Volatile private var autoStartCancelTimestamp: Long = 0L
    private var autoStartCountdownRunnable: Runnable? = null
    @Volatile private var autoStartCountdownValue: Int = 5
    private var autoStartCooldownCheckRunnable: Runnable? = null
    
    // Capacidad de cámaras concurrentes (trasera + frontal)
    private var concurrentCamerasSupported: Boolean = false
    private var concurrentCamerasChecked: Boolean = false

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
        @Volatile var pendingAutoStartEnabled: Boolean? = null
        @Volatile var pendingAutoStartAskBeforeActivate: Boolean? = null
        @Volatile var pendingAutoStartCooldownHours: Int? = null
        @Volatile var activeFrontPreviewView: PreviewView? = null
        @Volatile var pendingFrontPreviewView: PreviewView? = null
        @Volatile var bubbleActive: Boolean = false

        internal fun markServiceStarting() { serviceStarting = true }
        internal fun clearServiceStarting() { serviceStarting = false }

        const val MIN_SPEED_FOR_EVENT_KMH = 30.0

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
        const val ACTION_NOTIFICATION_EVENT = "ACTION_NOTIFICATION_EVENT"
        const val ACTION_NOTIFICATION_STOP = "ACTION_NOTIFICATION_STOP"
        const val ACTION_TEMPERATURE_STOPPED = "ACTION_TEMPERATURE_STOPPED"
        const val ACTION_CANCEL_AUTO_START = "ACTION_CANCEL_AUTO_START"
        const val ACTION_AUTO_START_ACTIVATE = "ACTION_AUTO_START_ACTIVATE"
        const val ACTION_AUTO_START_CANCEL = "ACTION_AUTO_START_CANCEL"
        
        const val AUTO_START_COOLDOWN_DEFAULT_MS = 60 * 60 * 1000L // 1 hora por defecto
        const val AUTO_START_DELAY_MS = 5000L // 5 segundos
        const val AUTO_START_COUNTDOWN_NOTIFICATION_ID = 145
        
        const val TEMP_WARNING_CELSIUS = 40.0
        const val TEMP_DANGER_CELSIUS = 45.0
        const val TEMP_CRITICAL_CELSIUS = 50.0
        const val TEMP_RECOVERY_CELSIUS = 45.0
    }

    private val rotateRunnable = Runnable {
        if (isDestroyed) return@Runnable
        DuoVialLog.v(TAG, "Rotando buffer circular...")
        rotateCircularBuffer()
    }

    private val stopPostEventRunnable = Runnable {
        if (isDestroyed) return@Runnable
        DuoVialLog.v(TAG, "Deteniendo grabacion de post-evento...")
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
                    if (lastKnownSpeed >= MIN_SPEED_FOR_EVENT_KMH) {
                        DuoVialLog.i(TAG, "Evento de acelerometro DISPARADO: G=${String.format("%.2f", gForce)}, Velocidad=${String.format("%.1f", lastKnownSpeed)} km/h")
                        triggerCollisionEvent("Acelerometro Impacto Detectado: ${String.format("%.2f", gForce)} G")
                    } else {
                        DuoVialLog.v(TAG, "Evento de acelerometro DESCARTADO por velocidad insuficiente: G=${String.format("%.2f", gForce)}, Velocidad=${String.format("%.1f", lastKnownSpeed)} km/h (min requerido: ${MIN_SPEED_FOR_EVENT_KMH} km/h)")
                    }
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            if (location.accuracy > 20f) return
            val rawKph = location.speed * 3.6
            lastKnownSpeed = lastKnownSpeed * 0.6 + rawKph * 0.4
            statusListener?.onSpeedChanged(lastKnownSpeed)
            checkAutoStart()
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        DuoVialLog.v(TAG, "Creando servicio de camara...")
        instance = this
        clearServiceStarting()

        pendingGForceThreshold?.let {
            gForceThreshold = it
            pendingGForceThreshold = null
            DuoVialLog.i(TAG, "Aplicado umbral G-Force pendiente: $it G")
        }

        pendingEarThreshold?.let {
            earThreshold = it
            pendingEarThreshold = null
            DuoVialLog.i(TAG, "Aplicado umbral EAR pendiente: $it")
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
            DuoVialLog.i(TAG, "Deteccion de fatiga auto-activada desde pending.")
        }

        pendingAutoStartEnabled?.let {
            autoStartEnabled = it
            pendingAutoStartEnabled = null
            DuoVialLog.i(TAG, "Auto-inicio aplicado desde pending: $it")
        }

        pendingAutoStartAskBeforeActivate?.let {
            autoStartAskBeforeActivate = it
            pendingAutoStartAskBeforeActivate = null
            DuoVialLog.i(TAG, "Auto-inicio preguntar antes de activar aplicado desde pending: $it")
        }

        pendingAutoStartCooldownHours?.let {
            autoStartCooldownHours = it
            pendingAutoStartCooldownHours = null
            DuoVialLog.i(TAG, "Auto-inicio cooldown aplicado desde pending: ${it}h")
        }

        // Iniciar verificación periódica de cooldown expirado
        startCooldownExpirationCheck()

        startServiceNotification()
        startCameraX()
        startLocationUpdates()
        // Lectura inicial de temperatura para el UI + monitoreo continuo
        val initialTemp = getDeviceTemperature()
        statusListener?.onTemperatureChanged(initialTemp)
        startTemperatureMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        DuoVialLog.v(TAG, "Servicio iniciado con action: ${intent?.action}")
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
                    DuoVialLog.i(TAG, "Duracion ojos cerrados actualizada a ${ms}ms")
                }
                ACTION_SET_MAX_ALERTS -> {
                    val max = intent.getIntExtra("max_alerts", 3)
                    maxAlertsPerHour = max
                    DuoVialLog.i(TAG, "Max alertas/hora actualizado a $max")
                }
                ACTION_SNOOZE_FATIGUE -> {
                    val minutes = intent.getIntExtra("minutes", 5)
                    snoozeFatigueAlert(minutes)
                }
                ACTION_NOTIFICATION_EVENT -> triggerCollisionEvent("Boton de Pantalla de Bloqueo")
                ACTION_NOTIFICATION_STOP -> stopAndSaveBuffer()
                ACTION_CANCEL_AUTO_START -> cancelAutoStart()
                ACTION_AUTO_START_ACTIVATE -> onAutoStartActivate()
                ACTION_AUTO_START_CANCEL -> onAutoStartCancel()
            }
        } else {
            startStandbyMode()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DuoVialLog.v(TAG, "Destruyendo servicio de camara...")
        isDestroyed = true
        instance = null
        stopCircularBufferTimers()
        stopSensors()
        stopFatigueDetection()
        stopLocationUpdates()
        removeFloatingBubble()
        stopTemperatureMonitoring()
        stopCooldownExpirationCheck()
        autoStartCountdownRunnable?.let { handler.removeCallbacks(it) }
        autoStartCountdownRunnable = null
        autoStartRunnable?.let { handler.removeCallbacks(it) }
        autoStartRunnable = null
        currentActiveRecording?.stop()
        currentActiveRecording = null
        activePreview?.setSurfaceProvider(null)
        activePreview = null
        activePreviewView = null
        pendingPreviewView = null
        pendingFrontPreviewView = null
        cameraProvider?.unbindAll()
        cameraLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
        facialLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
        statusListener?.onStatusChanged("INACTIVO")
        super.onDestroy()
    }

    fun bindPreviewUseCase(previewView: PreviewView) {
        val preview = activePreview
        if (preview == null) {
            DuoVialLog.i(TAG, "Preview aun no listo — encolando PreviewView como pendiente.")
            pendingPreviewView = previewView
            return
        }
        DuoVialLog.i(TAG, "Asociando SurfaceProvider al preview activo...")
        handler.post {
            try {
                preview.setSurfaceProvider(previewView.surfaceProvider)
                DuoVialLog.v(TAG, "SurfaceProvider asociado con exito.")
            } catch (e: Exception) {
                DuoVialLog.e(TAG, "Error al asociar SurfaceProvider: ${e.message}")
            }
        }
    }

    private fun flushPendingPreview() {
        val view = pendingPreviewView ?: return
        val preview = activePreview ?: return
        DuoVialLog.i(TAG, "Conectando PreviewView pendiente de la cola...")
        try {
            preview.setSurfaceProvider(view.surfaceProvider)
            activePreviewView = view
            DuoVialLog.v(TAG, "Pending preview vaciado con exito.")
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error vaciando pending preview: ${e.message}")
        } finally {
            pendingPreviewView = null
        }
    }

    fun unbindPreviewUseCase() {
        val preview = activePreview ?: return
        DuoVialLog.i(TAG, "Desasociando SurfaceProvider...")
        handler.post {
            try {
                preview.setSurfaceProvider(null)
                DuoVialLog.v(TAG, "SurfaceProvider desasociado.")
            } catch (e: Exception) {
                DuoVialLog.e(TAG, "Error al desasociar SurfaceProvider: ${e.message}")
            }
        }
    }

    fun startStandbyMode() {
        DuoVialLog.i(TAG, "Cambiando a modo STANDBY...")
        serviceState = ServiceState.STANDBY
        isSavingEvent = false
        postEventRecordingActive = false
        saveMode = SaveMode.SAVE_ONLY_CURR
        isStoppingService = false
        stopCircularBufferTimers()
        stopSensors()
        removeFloatingBubble()
        currentActiveRecording?.stop()
        currentActiveRecording = null
        updateNotification("DuoVial", "Camara lista.", showActions = false)
        sendStatusUpdate("INACTIVO")
    }

    fun forceResetToStandby() {
        DuoVialLog.w(TAG, "FORCE RESET TO STANDBY — abortando todo estado intermedio.")
        stopCircularBufferTimers()
        try { currentActiveRecording?.stop() } catch (e: Exception) { DuoVialLog.e(TAG, "Error al detener grabacion durante forceReset: ${e.message}") }
        currentActiveRecording = null
        isSavingEvent = false
        isStoppingService = false
        postEventRecordingActive = false
        saveMode = SaveMode.SAVE_ONLY_CURR
        hasCompletedSegment0 = false
        hasCompletedSegment1 = false
        serviceState = ServiceState.STANDBY
        removeFloatingBubble()
        updateNotification("DuoVial", "Camara lista.", showActions = false)
        sendStatusUpdate("INACTIVO")
        DuoVialLog.i(TAG, "Force reset completo. Servicio en STANDBY.")
    }

    fun setGForceThreshold(threshold: Double) {
        if (threshold < 1.5 || threshold > 5.0) {
            DuoVialLog.w(TAG, "Umbral G-Force fuera de rango (1.5..5.0): $threshold — ignorado.")
            return
        }
        gForceThreshold = threshold
        DuoVialLog.i(TAG, "Umbral G-Force actualizado a $threshold G")
    }

    fun startRecordingMode() {
        if (serviceState == ServiceState.RECORDING) {
            DuoVialLog.v(TAG, "El servicio ya esta en modo RECORDING. Re-emitiendo estado para sincronizar JS.")
            sendStatusUpdate("DUOVIAL ACTIVO")
            return
        }
        val temp = getDeviceTemperature()
        if (temp >= TEMP_CRITICAL_CELSIUS) {
            DuoVialLog.w(TAG, "No se puede iniciar Vigilante: temperatura critica ($temp°C)")
            sendStatusUpdate("ERROR EN CAMARA")
            return
        }
        DuoVialLog.i(TAG, "Cambiando a modo RECORDING...")
        serviceState = ServiceState.RECORDING
        updateNotification("DuoVial - Vigilando", "Grabacion circular activa en segundo plano.", showActions = true)
        startSensors()
        startTemperatureMonitoring()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        }
        if (cameraReady) {
            startCircularBuffer()
        } else {
            DuoVialLog.w(TAG, "CameraX aun no esta lista. El buffer circular arrancara cuando termine la inicializacion.")
            sendStatusUpdate("INICIANDO DUOVIAL")
        }
    }

    fun onPreviewViewDropped() {
        activePreviewView = null
        activePreview?.setSurfaceProvider(null)
        DuoVialLog.i(TAG, "Vista de previsualizacion descartada. Surface desconectado.")
    }

    fun onFrontPreviewAvailable(previewView: PreviewView) {
        pendingFrontPreviewView = previewView
        val facial = fatigueCameraManager
        if (facial != null && faceProcessor?.isActive == true) {
            try {
                facial.start(facialLifecycleOwner, previewView)
                DuoVialLog.v(TAG, "Front PreviewView vinculado en caliente a FatigueCameraManager.")
            } catch (e: Exception) {
                DuoVialLog.e(TAG, "Error al vincular front PreviewView: ${e.message}")
            }
        }
    }

    fun onFrontPreviewDropped() {
        activeFrontPreviewView = null
        pendingFrontPreviewView = null
        fatigueCameraManager?.stop()
        DuoVialLog.i(TAG, "Front PreviewView descartada. Camara frontal detenida.")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun sendStatusUpdate(status: String) {
        statusListener?.onStatusChanged(status)
        DuoVialLog.v(TAG, "Estado actualizado: $status")
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

    private fun updateNotification(title: String, text: String, showActions: Boolean = false) {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        
        if (showActions) {
            val eventIntent = Intent(this, BackgroundCameraService::class.java).apply {
                action = ACTION_NOTIFICATION_EVENT
            }
            val eventPendingIntent = PendingIntent.getService(this, 1, eventIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "REGISTRAR EVENTO", eventPendingIntent)
            
            val stopIntent = Intent(this, BackgroundCameraService::class.java).apply {
                action = ACTION_NOTIFICATION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "DETENER", stopPendingIntent)
        }
        
        val notification = builder.build()
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
        DuoVialLog.i(TAG, "Iniciando CameraX...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val qualitySelector = try {
                    QualitySelector.from(Quality.HD)
                } catch (e: Exception) {
                    DuoVialLog.w(TAG, "Quality.HD no soportado, usando Fallback a SD")
                    QualitySelector.from(Quality.SD)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                // I7: Audio desactivado explícitamente con withAudioEnabled(false)
                // I8: Bitrate manejado por QualitySelector (Quality.HD ~2-3 Mbps nativo de CameraX)
                val preview = Preview.Builder().build()
                activePreview = preview
                val activeView = activePreviewView
                if (activeView != null) {
                    try {
                        preview.setSurfaceProvider(activeView.surfaceProvider)
                        DuoVialLog.v(TAG, "PreviewView vinculado con exito en startCameraX.")
                    } catch (e: Exception) {
                        DuoVialLog.e(TAG, "Error al vincular vista: ${e.message}")
                    }
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                cameraLifecycleOwner.registry.currentState = Lifecycle.State.STARTED
                cameraLifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
                cameraProvider?.bindToLifecycle(cameraLifecycleOwner, cameraSelector, preview, videoCapture)
                DuoVialLog.v(TAG, "CameraX vinculada correctamente (Preview + VideoCapture) con lifecycle RESUMED.")
                cameraReady = true
                flushPendingPreview()
                handler.postDelayed({
                    val view = activePreviewView
                    if (view != null && activePreview != null) {
                        activePreview?.setSurfaceProvider(view.surfaceProvider)
                        DuoVialLog.v(TAG, "Surface provider re-aplicado con retraso de seguridad (500ms).")
                    }
                }, 500)
                if (serviceState == ServiceState.RECORDING) {
                    DuoVialLog.i(TAG, "CameraX lista y modo RECORDING pendiente. Arrancando buffer circular...")
                    startCircularBuffer()
                } else {
                    sendStatusUpdate("INACTIVO")
                }
            } catch (e: Exception) {
                DuoVialLog.e(TAG, "Error al iniciar CameraX: ${e.message}")
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
            DuoVialLog.v(TAG, "Cache de segmentos de video limpiada con exito.")
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al limpiar cache de segmentos: ${e.message}")
        }
        sendStatusUpdate("INICIANDO DUOVIAL")
        startRecordingSegment(0)
    }

    private fun startRecordingSegment(index: Int) {
        if (videoCapture == null) { DuoVialLog.e(TAG, "No se puede grabar: videoCapture es nulo."); return }
        currentRecordingIndex = index
        val segmentFile = File(cacheDir, "segment_$index.mp4")
        if (segmentFile.exists()) segmentFile.delete()
        val fileOutputOptions = FileOutputOptions.Builder(segmentFile).build()
        try {
            // Audio desactivado por defecto en CameraX (no se llama a withAudioEnabled)
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Start) {
                    DuoVialLog.v(TAG, "Grabacion de segmento_$index iniciada con exito.")
                    if (!isSavingEvent) sendStatusUpdate("DUOVIAL ACTIVO")
                } else if (event is VideoRecordEvent.Finalize) {
                    onRecordingFinalized(event)
                }
            }
            recordingStartTime = System.currentTimeMillis()
            DuoVialLog.v(TAG, "Grabando segmento_$index en cache...")
            handler.postDelayed(rotateRunnable, 15000)
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al iniciar grabacion: ${e.message}")
        }
    }

    private fun rotateCircularBuffer() { currentActiveRecording?.stop() }

    private fun onRecordingFinalized(event: VideoRecordEvent.Finalize) {
        val index = currentRecordingIndex
        if (event.hasError()) {
            DuoVialLog.e(TAG, "Grabacion finalizada con error: Codigo ${event.error}, Causa: ${event.cause?.message}")
        } else {
            DuoVialLog.v(TAG, "Grabacion de segmento finalizada con exito.")
        }
        if (serviceState != ServiceState.RECORDING) {
            DuoVialLog.w(TAG, "Finalize disparado pero serviceState=$serviceState. No se inicia nuevo segmento.")
            return
        }
        if (!isSavingEvent) {
            sendStatusUpdate("DUOVIAL ACTIVO")
            if (!event.hasError()) {
                if (index == 0) { hasCompletedSegment0 = true; DuoVialLog.v(TAG, "Segmento 0 marcado como COMPLETADO.") }
                else if (index == 1) { hasCompletedSegment1 = true; DuoVialLog.v(TAG, "Segmento 1 marcado como COMPLETADO.") }
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
        DuoVialLog.w(TAG, "EVENTO DETECTADO POR: $reason")
        saveEvent()
    }

    private fun saveEvent() {
        if (isSavingEvent) return
        triggerEventSound()
        stopCircularBufferTimers()
        eventTimestamp = System.currentTimeMillis() / 1000
        isSavingEvent = true
        postEventRecordingActive = false
        val duration = System.currentTimeMillis() - recordingStartTime
        DuoVialLog.v(TAG, "Guardando evento. Duracion del segmento actual: $duration ms")
        val prevIndex = 1 - currentRecordingIndex
        val hasCompletedPrevFile = if (prevIndex == 0) hasCompletedSegment0 else hasCompletedSegment1
        DuoVialLog.v(TAG, "hasCompletedPrevFile ($prevIndex): $hasCompletedPrevFile (Segment0: $hasCompletedSegment0, Segment1: $hasCompletedSegment1)")
        if (!hasCompletedPrevFile) {
            saveMode = SaveMode.SCENARIO_1
        } else {
            if (duration < 3000) saveMode = SaveMode.SAVE_ONLY_PREV
            else if (duration < 14000) saveMode = SaveMode.SAVE_PREV_AND_CURR
            else saveMode = SaveMode.SAVE_ONLY_CURR
        }
        DuoVialLog.v(TAG, "Modo de guardado de evento establecido: $saveMode")
        sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
        currentActiveRecording?.stop()
    }

    private fun stopAndSaveBuffer() {
        if (isSavingEvent || isStoppingService) return
        DuoVialLog.i(TAG, "Deteniendo Vigilante. Guardando buffer actual y apagando...")
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
        DuoVialLog.v(TAG, "stopAndSave: Modo de guardado establecido: $saveMode")
        sendStatusUpdate("INICIANDO DUOVIAL")
        currentActiveRecording?.stop()
    }

    private fun stopRecordingWithoutSaving() {
        if (serviceState != ServiceState.RECORDING) return
        if (isSavingEvent) return
        DuoVialLog.i(TAG, "Deteniendo grabacion sin guardar buffer...")
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
        removeFloatingBubble()
        updateNotification("DuoVial", "Camara lista.", showActions = false)
        sendStatusUpdate("INACTIVO")
        DuoVialLog.i(TAG, "Grabacion detenida sin guardar. Servicio en STANDBY.")
    }

    private fun cleanupCacheSegments() {
        try {
            listOf("segment_0.mp4", "segment_1.mp4", "segment_post.mp4").forEach { name ->
                File(cacheDir, name).let { if (it.exists()) it.delete() }
            }
            DuoVialLog.v(TAG, "Segmentos de cache eliminados.")
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al limpiar cache: ${e.message}")
        }
    }

    private fun handleEventSaveTransition() {
        if (isStoppingService) {
            savePreEventSegments()
            DuoVialLog.i(TAG, "Buffer pre-evento guardado. Volviendo a STANDBY.")
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
            DuoVialLog.i(TAG, "Incidente guardado con exito. Reanudando buffer circular...")
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
        if (videoCapture == null) { DuoVialLog.e(TAG, "No se puede iniciar post-evento: videoCapture es nulo."); startCircularBuffer(); return }
        sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
        val postFile = File(cacheDir, "segment_post.mp4")
        if (postFile.exists()) postFile.delete()
        val fileOutputOptions = FileOutputOptions.Builder(postFile).build()
        try {
            // Audio desactivado por defecto en CameraX (no se llama a withAudioEnabled)
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) onRecordingFinalized(event)
            }
            DuoVialLog.v(TAG, "Grabando 15s de post-evento...")
            handler.postDelayed(stopPostEventRunnable, 15000)
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al iniciar post-evento: ${e.message}")
            startCircularBuffer()
        }
    }

    // ==========================================
    // EXPORTACION A DOWNLOADS POR MEDIASTORE
    // ==========================================

    private fun copyFileToDownloads(sourceFileName: String, targetFileName: String) {
        val sourceFile = File(cacheDir, sourceFileName)
        if (!sourceFile.exists() || sourceFile.length() == 0L) { DuoVialLog.w(TAG, "Archivo de origen no existe o esta vacio: $sourceFileName"); return }
        Thread {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, targetFileName)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            }
            val resolver = contentResolver
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
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
                    DuoVialLog.i(TAG, "Copiado exitoso a Downloads (hilo secundario): $targetFileName")
                } else {
                    DuoVialLog.e(TAG, "MediaStore insert devolvio null para $targetFileName")
                }
            } catch (e: Exception) {
                DuoVialLog.e(TAG, "Excepcion al exportar $targetFileName: ${e.message}")
            } finally {
                try { outputStream?.close(); inputStream?.close() } catch (e: Exception) {}
            }
        }.start()
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
                DuoVialLog.v(TAG, "Acelerometro registrado.")
            }
        } catch (e: Exception) { DuoVialLog.e(TAG, "Error al registrar acelerometro: ${e.message}") }
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
                    DuoVialLog.w(TAG, "GPS Provider registrado para velocimetro.")
                }

            } else { DuoVialLog.e(TAG, "Sin permisos de GPS — no se puede iniciar velocimetro.") }
        } catch (e: SecurityException) { DuoVialLog.e(TAG, "Error de seguridad al iniciar velocimetro: ${e.message}") }
        catch (e: Exception) { DuoVialLog.e(TAG, "Error al iniciar velocimetro: ${e.message}") }
    }

    private fun stopLocationUpdates() {
        try { locationManager?.removeUpdates(locationListener); DuoVialLog.w(TAG, "Velocimetro desvinculado con exito.") }
        catch (e: Exception) { DuoVialLog.e(TAG, "Error al detener actualizaciones de ubicacion: ${e.message}") }
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
        DuoVialLog.i(TAG, "Estado re-sincronizado con JS: $currentStatus")
    }

    // ==========================================
    // DETECCION DE SOMNOLENCIA (FATIGA)
    // ==========================================

    fun toggleFatigueDetection(enable: Boolean) {
        if (enable) {
            DuoVialLog.i(TAG, "Activando deteccion de fatiga...")
            startFrontCameraSession()
        } else {
            DuoVialLog.i(TAG, "Desactivando deteccion de fatiga.")
            stopFatigueDetection()
        }
    }

    private fun startFrontCameraSession() {
        val facial = fatigueCameraManager ?: run {
            DuoVialLog.e(TAG, "FatigueCameraManager no disponible.")
            return
        }
        val processor = faceProcessor ?: run {
            DuoVialLog.e(TAG, "FaceProcessor no disponible.")
            return
        }
        if (processor.isActive) {
            DuoVialLog.w(TAG, "FaceProcessor ya esta activo.")
            return
        }

        // Verificar soporte de cámaras concurrentes
        val supportsConcurrent = isConcurrentCamerasSupported()
        if (!supportsConcurrent && serviceState == ServiceState.RECORDING) {
            // El dispositivo no soporta cámaras concurrentes
            // Pausar el modo Vigilante para evitar conflictos
            DuoVialLog.w(TAG, "Dispositivo no soporta cámaras concurrentes. Pausando modo Vigilante para activar cámara frontal.")
            statusListener?.onConcurrentCamerasNotSupported()
            // Pausar grabación circular temporalmente
            stopCircularBufferTimers()
            try { currentActiveRecording?.stop() } catch (e: Exception) { 
                DuoVialLog.e(TAG, "Error al pausar grabación para cámara frontal: ${e.message}") 
            }
            currentActiveRecording = null
        }

        hourResetTime = System.currentTimeMillis()
        alertCountThisHour = 0
        closedEyeStartTime = 0L
        currentClosedEyeDurationMs = 0L

        val frontView = activeFrontPreviewView ?: pendingFrontPreviewView
        if (frontView == null) {
            DuoVialLog.w(TAG, "Front PreviewView aun no disponible — encolando activacion para cuando llegue.")
            pendingFatigueEnabled = true
            return
        }

        facial.setOnFrameAvailable { imageProxy -> processor.processFrame(imageProxy) }
        processor.start()
        facialLifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        facial.start(facialLifecycleOwner, frontView)
        DuoVialLog.i(TAG, "Camara frontal + FaceProcessor activados.")
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
        DuoVialLog.i(TAG, "Deteccion de fatiga completamente detenida.")
        sendFatigueStatusEvent(false, false, 0.0, 0.0)
        
        // Reanudar modo Vigilante si fue pausado por falta de cámaras concurrentes
        if (!isConcurrentCamerasSupported() && serviceState == ServiceState.RECORDING && cameraReady) {
            DuoVialLog.i(TAG, "Reanudando modo Vigilante tras desactivar cámara frontal.")
            startCircularBuffer()
        }
    }

    fun setEarThreshold(threshold: Double) {
        if (threshold < 0.1 || threshold > 0.4) {
            DuoVialLog.w(TAG, "Umbral EAR fuera de rango (0.1..0.4): $threshold — ignorado.")
            return
        }
        earThreshold = threshold
        DuoVialLog.i(TAG, "Umbral EAR actualizado a $threshold")
    }

    fun getEarThreshold(): Double = earThreshold

    fun snoozeFatigueAlert(minutes: Int) {
        snoozeMinutes = minutes
        snoozeEndTime = System.currentTimeMillis() + (minutes * 60L * 1000L)
        isSnoozed = true
        DuoVialLog.i(TAG, "Snooze de fatiga activado por $minutes minutos.")
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
                DuoVialLog.v(TAG, "Ojos abiertos. Duracion total cerrados: ${currentClosedEyeDurationMs}ms")
            }
            closedEyeStartTime = 0L
            currentClosedEyeDurationMs = 0L
        } else {
            if (closedEyeStartTime == 0L) {
                closedEyeStartTime = now
                DuoVialLog.v(TAG, "Ojos cerrados detectados. Iniciando contador. EAR=$earValue")
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
                    DuoVialLog.i(TAG, "Snooze finalizado.")
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
        DuoVialLog.w(TAG, "FATIGA DETECTADA! EAR=$earValue, Alertas esta hora: $alertCountThisHour")
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
            DuoVialLog.e(TAG, "Error en alarma de sonido (fatiga): ${e.message}")
        }
    }

    private fun triggerEventSound() {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
            Handler(Looper.getMainLooper()).postDelayed({
                try { toneGen.release() } catch (_: Exception) {}
            }, 1100)
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error en alarma de sonido (evento): ${e.message}")
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
            bubbleActive = true
            DuoVialLog.v(TAG, "Burbuja flotante mostrada con exito.")
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al mostrar burbuja flotante: ${e.message}")
        }
    }

    private fun removeFloatingBubble() {
        try {
            floatingBubbleView?.let { windowManager?.removeView(it) }
            floatingBubbleView = null
            bubbleActive = false
        } catch (e: Exception) { DuoVialLog.e(TAG, "Error al remover burbuja: ${e.message}") }
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
        } catch (e: Exception) { DuoVialLog.e(TAG, "Error en vibracion: ${e.message}") }
    }

    // ==========================================
    // MONITOREO DE TEMPERATURA DEL DISPOSITIVO
    // ==========================================

    fun getDeviceTemperature(): Float {
        return try {
            val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val temp = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10f
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al leer temperatura: ${e.message}")
            0f
        }
    }

    fun getTemperature(): Float = getDeviceTemperature()

    private fun startTemperatureMonitoring() {
        stopTemperatureMonitoring()
        temperatureCheckRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed) return
                val temp = getDeviceTemperature()
                statusListener?.onTemperatureChanged(temp)
                if (temp >= TEMP_CRITICAL_CELSIUS && serviceState == ServiceState.RECORDING) {
                    DuoVialLog.w(TAG, "Temperatura critica: ${temp}°C. Deteniendo Vigilante automaticamente.")
                    stopRecordingWithoutSaving()
                    updateNotification("DuoVial - Vigilante Detenido", "Temperatura del dispositivo: ${temp.toInt()}°C. El Vigilante se detuvo para proteger tu telefono.", showActions = false)
                }
                // Monitoreo continuo en cualquier estado (30s)
                handler.postDelayed(this, 30000)
            }
        }
        // Primera lectura inmediata, luego cada 30 segundos
        handler.post(temperatureCheckRunnable!!)
    }

    private fun stopTemperatureMonitoring() {
        temperatureCheckRunnable?.let { handler.removeCallbacks(it) }
        temperatureCheckRunnable = null
    }

    // ==========================================
    // AUTO-INICIO INTELIGENTE DEL VIGILANTE
    // ==========================================

    fun setAutoStartEnabled(enabled: Boolean) {
        autoStartEnabled = enabled
        DuoVialLog.i(TAG, "Auto-inicio ${if (enabled) "habilitado" else "deshabilitado"}")
    }

    fun isAutoStartEnabled(): Boolean = autoStartEnabled

    fun setAutoStartAskBeforeActivate(ask: Boolean) {
        autoStartAskBeforeActivate = ask
        DuoVialLog.i(TAG, "Auto-inicio preguntar antes de activar: $ask")
    }

    fun setAutoStartCooldownHours(hours: Int) {
        autoStartCooldownHours = hours.coerceIn(1, 5)
        DuoVialLog.i(TAG, "Auto-inicio cooldown configurado a ${autoStartCooldownHours}h")
    }

    fun getAutoStartCooldownHours(): Int = autoStartCooldownHours

    fun isAutoStartAskBeforeActivate(): Boolean = autoStartAskBeforeActivate

    /**
     * Verifica si el dispositivo soporta cámaras concurrentes (trasera + frontal).
     * El resultado se cachea para evitar verificaciones repetidas.
     */
    fun isConcurrentCamerasSupported(): Boolean {
        if (!concurrentCamerasChecked) {
            val result = CameraCapabilities.getConcurrentCameraSupport(this)
            concurrentCamerasSupported = result.supported
            concurrentCamerasChecked = true
            DuoVialLog.i(TAG, "Cámaras concurrentes soportadas: $concurrentCamerasSupported${result.reason?.let { " ($it)" } ?: ""}")
        }
        return concurrentCamerasSupported
    }

    /**
     * Cancela el auto-inicio pendiente y guarda el timestamp de cancelación
     * para respetar el cooldown configurable.
     */
    fun cancelAutoStart() {
        autoStartPending = false
        autoStartCountdownRunnable?.let { handler.removeCallbacks(it) }
        autoStartCountdownRunnable = null
        autoStartRunnable?.let { handler.removeCallbacks(it) }
        autoStartRunnable = null
        autoStartCountdownValue = 5
        // Guardar timestamp de cancelación para cooldown
        autoStartCancelTimestamp = System.currentTimeMillis()
        // Cancelar notificación de cuenta regresiva
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(AUTO_START_COUNTDOWN_NOTIFICATION_ID)
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al cancelar notificación de auto-inicio: ${e.message}")
        }
        DuoVialLog.i(TAG, "Auto-inicio cancelado por el usuario. Cooldown de ${autoStartCooldownHours}h activado.")
    }

    /**
     * Acción cuando el usuario presiona "ACTIVAR" en la notificación de cuenta regresiva.
     * Activa el Vigilante inmediatamente.
     */
    private fun onAutoStartActivate() {
        if (isDestroyed) return
        autoStartPending = false
        autoStartCountdownRunnable?.let { handler.removeCallbacks(it) }
        autoStartCountdownRunnable = null
        autoStartRunnable?.let { handler.removeCallbacks(it) }
        autoStartRunnable = null
        autoStartCountdownValue = 5
        // Cancelar notificación de cuenta regresiva
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(AUTO_START_COUNTDOWN_NOTIFICATION_ID)
        } catch (e: Exception) {}
        lastAutoStartActivationTime = System.currentTimeMillis()
        DuoVialLog.i(TAG, "Auto-inicio activado manualmente por el usuario desde notificación")
        startRecordingMode()
    }

    /**
     * Acción cuando el usuario presiona "CANCELAR" en la notificación de cuenta regresiva.
     * Cancela el auto-inicio y activa el cooldown.
     */
    private fun onAutoStartCancel() {
        cancelAutoStart()
    }

    /**
     * Verifica si el auto-inicio está en cooldown (el usuario canceló recientemente).
     * @return true si está en cooldown, false si puede activarse
     */
    private fun isInAutoStartCooldown(): Boolean {
        if (autoStartCancelTimestamp <= 0L) return false
        val cooldownMs = autoStartCooldownHours.toLong() * 60L * 60L * 1000L
        val elapsed = System.currentTimeMillis() - autoStartCancelTimestamp
        return elapsed < cooldownMs
    }

    /**
     * Verifica el cooldown y muestra notificación cuando expira.
     */
    private fun startCooldownExpirationCheck() {
        stopCooldownExpirationCheck()
        autoStartCooldownCheckRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed) return
                // Verificar si el cooldown acaba de expirar
                if (autoStartCancelTimestamp > 0L && !isInAutoStartCooldown()) {
                    // El cooldown expiró, resetear timestamp y notificar
                    autoStartCancelTimestamp = 0L
                    if (autoStartEnabled) {
                        showCooldownExpiredNotification()
                    }
                }
                // Verificar cada 60 segundos
                handler.postDelayed(this, 60000L)
            }
        }
        handler.postDelayed(autoStartCooldownCheckRunnable!!, 60000L)
    }

    private fun stopCooldownExpirationCheck() {
        autoStartCooldownCheckRunnable?.let { handler.removeCallbacks(it) }
        autoStartCooldownCheckRunnable = null
    }

    /**
     * Muestra notificación cuando el cooldown expira.
     */
    private fun showCooldownExpiredNotification() {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DuoVial - Auto-Inicio Reactivado")
            .setContentText("El auto-inicio del Vigilante se ha reactivado. Se activará automáticamente al alcanzar 30 km/h.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(AUTO_START_COUNTDOWN_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al mostrar notificación de cooldown expirado: ${e.message}")
        }
        DuoVialLog.i(TAG, "Cooldown de auto-inicio expirado. Auto-inicio reactivado.")
    }

    /**
     * Lógica principal de verificación de auto-inicio.
     * Se llama desde locationListener cada vez que se recibe una actualización de velocidad.
     */
    private fun checkAutoStart() {
        if (!autoStartEnabled) return
        if (serviceState != ServiceState.STANDBY) return
        if (autoStartPending) return
        if (lastKnownSpeed < MIN_SPEED_FOR_EVENT_KMH) return

        // Verificar cooldown
        if (isInAutoStartCooldown()) {
            DuoVialLog.v(TAG, "Auto-inicio en cooldown. Tiempo restante: ${getCooldownRemainingMinutes()} min")
            return
        }

        DuoVialLog.i(TAG, "Auto-inicio detectado: velocidad=${String.format("%.1f", lastKnownSpeed)} km/h")
        autoStartPending = true

        if (autoStartAskBeforeActivate) {
            // Modo con notificación y cuenta regresiva
            autoStartCountdownValue = 5
            showAutoStartCountdownNotification(autoStartCountdownValue)
            autoStartCountdownRunnable = object : Runnable {
                override fun run() {
                    if (isDestroyed || !autoStartPending) return
                    autoStartCountdownValue--
                    if (autoStartCountdownValue > 0) {
                        showAutoStartCountdownNotification(autoStartCountdownValue)
                        handler.postDelayed(this, 1000L)
                    } else {
                        // Tiempo agotado, activar automáticamente
                        autoStartPending = false
                        autoStartCountdownRunnable = null
                        lastAutoStartActivationTime = System.currentTimeMillis()
                        try {
                            val manager = getSystemService(NotificationManager::class.java)
                            manager?.cancel(AUTO_START_COUNTDOWN_NOTIFICATION_ID)
                        } catch (e: Exception) {}
                        DuoVialLog.i(TAG, "Auto-inicio ejecutado automáticamente tras cuenta regresiva")
                        startRecordingMode()
                    }
                }
            }
            handler.postDelayed(autoStartCountdownRunnable!!, 1000L)
        } else {
            // Modo directo (sin preguntar)
            autoStartRunnable = Runnable {
                if (isDestroyed || !autoStartPending) return@Runnable
                autoStartPending = false
                autoStartRunnable = null
                lastAutoStartActivationTime = System.currentTimeMillis()
                DuoVialLog.i(TAG, "Auto-inicio ejecutado directamente (sin preguntar)")
                startRecordingMode()
            }
            handler.postDelayed(autoStartRunnable!!, AUTO_START_DELAY_MS)
        }
    }

    /**
     * Muestra la notificación de cuenta regresiva con los segundos restantes
     * y el botón de CANCELAR.
     */
    private fun showAutoStartCountdownNotification(seconds: Int) {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Acción ACTIVAR (mano izquierda)
        val activateIntent = Intent(this, BackgroundCameraService::class.java).apply {
            action = ACTION_AUTO_START_ACTIVATE
        }
        val activatePendingIntent = PendingIntent.getService(this, 3, activateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Acción CANCELAR (mano derecha)
        val cancelIntent = Intent(this, BackgroundCameraService::class.java).apply {
            action = ACTION_AUTO_START_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(this, 4, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 DuoVial - Auto-Inicio")
            .setContentText("El Vigilante se activará en $seconds segundos")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "ACTIVAR", activatePendingIntent)
            .addAction(0, "CANCELAR", cancelPendingIntent)
            .build()

        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(AUTO_START_COUNTDOWN_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error al mostrar notificación de cuenta regresiva: ${e.message}")
        }
    }

    /**
     * Obtiene los minutos restantes de cooldown.
     */
    private fun getCooldownRemainingMinutes(): Long {
        if (autoStartCancelTimestamp <= 0L) return 0L
        val cooldownMs = autoStartCooldownHours.toLong() * 60L * 60L * 1000L
        val elapsed = System.currentTimeMillis() - autoStartCancelTimestamp
        val remaining = cooldownMs - elapsed
        return if (remaining > 0) remaining / 60000L else 0L
    }
}
