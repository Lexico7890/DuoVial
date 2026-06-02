package com.anonymous.DuoVial

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Interfaz estática para comunicar telemetría y estado directamente entre el servicio nativo
 * y el módulo de puente React Native sin retrasos ni bloqueos por restricciones de Android.
 */
interface CameraStatusListener {
    fun onStatusChanged(status: String)
    fun onAccelChanged(gForce: Double)
}

/**
 * Servicio en primer plano nativo optimizado con:
 * 1. Interface estática statusListener para garantizar la sincronización al 100% con JS.
 * 2. Lógica de detención con autoguardado del buffer circular (sin grabar post-evento).
 * 3. Acelerómetro Fuerza G rate-limitado y burbuja flotante PIP.
 */
class BackgroundCameraService : LifecycleService() {

    private val TAG = "DuoVial_CameraService"
    private val NOTIFICATION_ID = 144
    private val CHANNEL_ID = "duovial_camera_service_channel"

    enum class ServiceState {
        STANDBY,
        RECORDING,
        SAVING
    }

    private var serviceState = ServiceState.STANDBY

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentActiveRecording: Recording? = null

    // Variables del buffer circular
    private var currentRecordingIndex = 0
    private var recordingStartTime = 0L
    private var isSavingEvent = false
    private var shortSegmentSaving = false
    private var postEventRecordingActive = false
    private var isScenario1 = false
    private var isStoppingService = false // Indica si la detención guarda y apaga
    private var eventTimestamp = 0L
    private var cameraInitAttempts = 0
    private val maxCameraInitAttempts = 3

    private val handler = Handler(Looper.getMainLooper())

    // Acelerómetro nativo
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var lastAccelUpdateTime = 0L

    // Cooldown para Triggers (12 segundos)
    private var lastEventTriggerTime = 0L

    // Elementos de la burbuja flotante
    private var windowManager: WindowManager? = null
    private var floatingBubbleView: View? = null

    companion object {
        var instance: BackgroundCameraService? = null
        var activePreview: Preview? = null
        var activePreviewView: PreviewView? = null
        var statusListener: CameraStatusListener? = null
    }

    // Runnable para la rotación del buffer cada 15 segundos
    private val rotateRunnable = Runnable {
        Log.d(TAG, "Rotando buffer circular...")
        rotateCircularBuffer()
    }

    // Runnable para detener la grabación de post-evento (15s)
    private val stopPostEventRunnable = Runnable {
        Log.d(TAG, "Deteniendo grabación de post-evento...")
        currentActiveRecording?.stop()
    }

    // Listener del acelerómetro
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                val gForce = magnitude / SensorManager.GRAVITY_EARTH
                
                // Emitir Fuerza G rate-limitada a 200ms
                val now = System.currentTimeMillis()
                if (now - lastAccelUpdateTime > 200) {
                    lastAccelUpdateTime = now
                    statusListener?.onAccelChanged(gForce)
                }

                // Evaluar colisión > 2.5G
                if (gForce > 2.5) {
                    triggerCollisionEvent("Acelerómetro Impacto Detectado: ${String.format("%.2f", gForce)} G")
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creando servicio de cámara...")
        instance = this
        startServiceNotification()
        startCameraX()
        startSensors()
        
        // Mostrar burbuja flotante si hay autorización
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Servicio iniciado con action: ${intent?.action}")
        
        if (intent != null) {
            when (intent.action) {
                "ACTION_START_STANDBY" -> {
                    startStandbyMode()
                }
                "ACTION_START_RECORDING" -> {
                    startRecordingMode()
                }
                "ACTION_TRIGGER_PANIC" -> {
                    triggerCollisionEvent("Botón de Pánico Manual o Burbuja")
                }
                "ACTION_STOP_AND_SAVE" -> {
                    stopAndSaveBuffer()
                }
                "ACTION_STOP_SERVICE" -> {
                    stopSelf()
                }
            }
        } else {
            startStandbyMode()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destruyendo servicio de cámara...")
        instance = null
        stopCircularBufferTimers()
        stopSensors()
        removeFloatingBubble()
        
        currentActiveRecording?.stop()
        currentActiveRecording = null
        
        activePreview?.setSurfaceProvider(null)
        activePreview = null
        
        cameraProvider?.unbindAll()
        statusListener?.onStatusChanged("INACTIVO")
        super.onDestroy()
    }

    fun bindPreviewUseCase(previewView: PreviewView) {
        val preview = activePreview ?: return
        Log.i(TAG, "Asociando SurfaceProvider al preview activo...")
        handler.post {
            try {
                preview.setSurfaceProvider(previewView.surfaceProvider)
                Log.d(TAG, "SurfaceProvider asociado con éxito.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al asociar SurfaceProvider: ${e.message}")
            }
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
        shortSegmentSaving = false
        isScenario1 = false
        isStoppingService = false
        
        stopCircularBufferTimers()
        stopSensors()
        
        currentActiveRecording?.stop()
        currentActiveRecording = null
        
        updateNotification("DuoVial", "Cámara lista.")
        sendStatusUpdate("INACTIVO")
    }

    fun startRecordingMode() {
        if (serviceState == ServiceState.RECORDING) {
            Log.d(TAG, "El servicio ya está en modo RECORDING.")
            return
        }
        Log.i(TAG, "Cambiando a modo RECORDING...")
        serviceState = ServiceState.RECORDING
        
        updateNotification("🎥 DuoVial - Vigilando", "Grabación circular activa en segundo plano.")
        startCircularBuffer()
        startSensors()
    }

    fun onPreviewViewDropped() {
        activePreviewView = null
        // Desconectar el surface provider pero NO destruir el servicio.
        // Destruir y recrear el servicio causa race conditions con CameraX y notificación spam.
        activePreview?.setSurfaceProvider(null)
        Log.i(TAG, "Vista de previsualización descartada. Surface desconectado.")
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
    // NOTIFICACIÓN PERSISTENTE (FOREGROUND)
    // ==========================================

    private fun startServiceNotification() {
        createNotificationChannel()
        
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DuoVial")
            .setContentText("Cámara lista.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Actualiza la notificación persistente dinámicamente según el estado del servicio.
     */
    private fun updateNotification(title: String, text: String) {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
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
                CHANNEL_ID,
                "DuoVial - Servicio de Cámara",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // ==========================================
    // CONFIGURACIÓN DE CAMERAX CON PREVIEW
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
                
                // Asociar de inmediato el surface provider si la vista ya existe
                val activeView = activePreviewView
                if (activeView != null) {
                    try {
                        preview.setSurfaceProvider(activeView.surfaceProvider)
                        Log.d(TAG, "PreviewView vinculado con éxito en startCameraX.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al vincular vista: ${e.message}")
                    }
                }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider?.unbindAll()
                // Vincular AMBOS (Preview y VideoCapture) juntos para evitar session re-configurations
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                Log.d(TAG, "CameraX vinculada correctamente (Preview + VideoCapture).")
                
                if (serviceState == ServiceState.RECORDING) {
                    startCircularBuffer()
                } else {
                    sendStatusUpdate("INACTIVO")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar CameraX: ${e.message}")
                sendStatusUpdate("ERROR EN CÁMARA")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ==========================================
    // LÓGICA DE BUFFER CIRCULAR (15 SEGUNDOS)
    // ==========================================

    private fun startCircularBuffer() {
        isSavingEvent = false
        postEventRecordingActive = false
        shortSegmentSaving = false
        isScenario1 = false
        isStoppingService = false
        
        sendStatusUpdate("INICIANDO DUOVIAL")
        startRecordingSegment(0)
    }

    private fun startRecordingSegment(index: Int) {
        if (videoCapture == null) {
            Log.e(TAG, "No se puede grabar: videoCapture es nulo.")
            return
        }
        
        currentRecordingIndex = index
        val segmentFile = File(cacheDir, "segment_$index.mp4")
        if (segmentFile.exists()) {
            segmentFile.delete()
        }
        
        val fileOutputOptions = FileOutputOptions.Builder(segmentFile).build()
        
        try {
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Start) {
                    Log.d(TAG, "Grabación de segmento_$index iniciada con éxito.")
                    if (!isSavingEvent) {
                        sendStatusUpdate("DUOVIAL ACTIVO")
                    }
                } else if (event is VideoRecordEvent.Finalize) {
                    onRecordingFinalized(event)
                }
            }
            
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Grabando segmento_$index en caché...")
            
            handler.postDelayed(rotateRunnable, 15000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar grabación: ${e.message}")
        }
    }

    private fun rotateCircularBuffer() {
        currentActiveRecording?.stop()
    }

    private fun onRecordingFinalized(event: VideoRecordEvent.Finalize) {
        if (!isSavingEvent) {
            // El primer frame se completó con éxito, pasamos a vigilancia activa estable.
            sendStatusUpdate("DUOVIAL ACTIVO")
            
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
    // LÓGICA DE DETECCIÓN Y GUARDADO DE EVENTO / PARADA
    // ==========================================

    private fun triggerCollisionEvent(reason: String) {
        if (isStoppingService) return
        val now = System.currentTimeMillis()
        if (now - lastEventTriggerTime < 12000) {
            return
        }
        lastEventTriggerTime = now
        Log.w(TAG, "⚠️ EVENTO DETECTADO POR: $reason")
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
        Log.d(TAG, "Guardando evento. Duración del segmento actual: $duration ms")
        
        val prevIndex = 1 - currentRecordingIndex
        val prevFile = File(cacheDir, "segment_$prevIndex.mp4")
        val hasCompletedPrevFile = prevFile.exists() && prevFile.length() > 500000L
        
        if (!hasCompletedPrevFile) {
            // ESCENARIO 1: Ocurre antes de completarse el primer frame
            isScenario1 = true
            shortSegmentSaving = true
            sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
            currentActiveRecording?.stop()
        } else {
            // ESCENARIO 2: Existe un frame previo completo
            isScenario1 = false
            if (duration < 14000) {
                shortSegmentSaving = true
                sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
                currentActiveRecording?.stop()
            } else {
                shortSegmentSaving = false
                sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
                currentActiveRecording?.stop()
            }
        }
    }

    /**
     * Gatilla el apagado seguro de la app guardando el buffer circular
     * de pre-evento actual pero SIN iniciar la grabación de los 15s post-evento.
     */
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
        val prevFile = File(cacheDir, "segment_$prevIndex.mp4")
        val hasCompletedPrevFile = prevFile.exists() && prevFile.length() > 500000L
        
        if (!hasCompletedPrevFile) {
            isScenario1 = true
            shortSegmentSaving = true
        } else {
            isScenario1 = false
            shortSegmentSaving = duration < 14000
        }
        
        sendStatusUpdate("INICIANDO DUOVIAL") // Mostrar animación de guardado final
        currentActiveRecording?.stop()
    }

    private fun handleEventSaveTransition() {
        if (isStoppingService) {
            // Caso de detención de la app: Copiar únicamente el buffer de pre-evento
            savePreEventSegments()
            Log.i(TAG, "✅ Vigilante apagado de forma segura y buffer circular guardado. Apagando servicio.")
            stopSelf() // Detiene el servicio nativo inmediatamente
            return
        }

        if (!postEventRecordingActive) {
            // Lógica normal de evento: Guardar pre-evento y empezar post-evento
            savePreEventSegments()
            postEventRecordingActive = true
            startPostEventRecording()
        } else {
            // Fin de la grabación de post-evento normal
            val finalPartIndex = if (isScenario1) 1 else (if (shortSegmentSaving) 2 else 1)
            copyFileToDownloads("segment_post.mp4", "incident_${eventTimestamp}_part$finalPartIndex.mp4")
            
            Log.i(TAG, "✅ Incidente guardado con éxito. Reanudando buffer circular...")
            sendStatusUpdate("DUOVIAL ACTIVO")
            
            val postFile = File(cacheDir, "segment_post.mp4")
            if (postFile.exists()) postFile.delete()
            
            startCircularBuffer()
        }
    }

    private fun savePreEventSegments() {
        if (isScenario1) {
            copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
        } else {
            if (shortSegmentSaving) {
                val prevIndex = 1 - currentRecordingIndex
                copyFileToDownloads("segment_$prevIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
                copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part1.mp4")
            } else {
                copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
            }
        }
    }

    private fun startPostEventRecording() {
        if (videoCapture == null) {
            Log.e(TAG, "No se puede iniciar post-evento: videoCapture es nulo.")
            startCircularBuffer()
            return
        }
        
        sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
        
        val postFile = File(cacheDir, "segment_post.mp4")
        if (postFile.exists()) {
            postFile.delete()
        }
        
        val fileOutputOptions = FileOutputOptions.Builder(postFile).build()
        
        try {
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    onRecordingFinalized(event)
                }
            }
            
            Log.d(TAG, "Grabando 15s de post-evento...")
            handler.postDelayed(stopPostEventRunnable, 15000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar post-evento: ${e.message}")
            startCircularBuffer()
        }
    }

    // ==========================================
    // EXPORTACIÓN A DOWNLOADS POR MEDIASTORE
    // ==========================================

    private fun copyFileToDownloads(sourceFileName: String, targetFileName: String) {
        val sourceFile = File(cacheDir, sourceFileName)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.w(TAG, "Archivo de origen no existe o está vacío: $sourceFileName")
            return
        }
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, targetFileName)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/DuoVial")
            }
        }
        
        val resolver = contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        var uri: Uri? = null
        
        try {
            uri = resolver.insert(collectionUri, contentValues)
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
            Log.e(TAG, "Excepción al exportar: ${e.message}")
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {}
        }
    }

    // ==========================================
    // MONITOREO DE ACELERÓMETRO NATIVO (G-FORCE)
    // ==========================================

    private fun startSensors() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            if (accelSensor != null) {
                sensorManager?.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Acelerómetro registrado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar acelerómetro: ${e.message}")
        }
    }

    private fun stopSensors() {
        sensorManager?.unregisterListener(accelListener)
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
                size,
                size,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                else 
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
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
                setColor(Color.parseColor("#FFCC00")) // Cambiado a AMARILLO de acuerdo a la nueva interfaz
                setStroke((2.5 * density).toInt(), Color.parseColor("#FFFFFF"))
            }
            bubble.background = shape
            
            val icon = ImageView(this).apply {
                val padding = (16 * density).toInt()
                setPadding(padding, padding, padding, padding)
                setImageResource(android.R.drawable.presence_video_online)
                setColorFilter(Color.BLACK) // Icono en negro para contrastar con amarillo
            }
            bubble.addView(icon)
            
            floatingBubbleView = bubble
            
            bubble.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isClick = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = layoutParams.x
                            initialY = layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isClick = true
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isClick) {
                                triggerVibration()
                                triggerCollisionEvent("Burbuja Flotante Manual")
                            }
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isClick = false
                            }
                            
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            
                            try {
                                windowManager?.updateViewLayout(bubble, layoutParams)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al actualizar burbuja: ${e.message}")
                            }
                            return true
                        }
                    }
                    return false
                }
            })
            
            windowManager?.addView(bubble, layoutParams)
            Log.i(TAG, "Burbuja flotante activa.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear la burbuja flotante: ${e.message}")
        }
    }

    private fun removeFloatingBubble() {
        if (floatingBubbleView != null) {
            try {
                windowManager?.removeView(floatingBubbleView)
                floatingBubbleView = null
            } catch (e: Exception) {
                Log.e(TAG, "Error al remover burbuja flotante: ${e.message}")
            }
        }
    }

    private fun triggerVibration() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 500, 200, 500)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                }
            }
        } catch (e: Exception) {}
    }
}
