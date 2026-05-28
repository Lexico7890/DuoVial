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
 * Servicio en primer plano nativo de alto rendimiento re-calibrado con:
 * 1. Integración de la vista de Preview de CameraX en tiempo real.
 * 2. Emisión rate-limitada de valores del acelerómetro (200ms) para actualizar la UI en vivo.
 * 3. Estados específicos solicitados: "INICIANDO DUOVIAL", "DUOVIAL ACTIVO", "GENERANDO CONTENIDO POST EVENTO".
 * 4. Burbuja flotante (Overlay) interactiva y arrastrable sobre otras aplicaciones.
 */
class BackgroundCameraService : LifecycleService() {

    private val TAG = "DuoVial_CameraService"
    private val NOTIFICATION_ID = 144
    private val CHANNEL_ID = "duovial_camera_service_channel"

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
    private var eventTimestamp = 0L

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
        // Referencia estática del Preview de CameraX y la vista nativa activa para la UI de React Native
        var activePreview: Preview? = null
        var activePreviewView: PreviewView? = null
    }

    // Runnable para la rotación automática del buffer cada 15 segundos
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
                
                // 1. Emitir la Fuerza G en vivo a la UI JS rate-limitada a 200ms para evitar lags de puente
                val now = System.currentTimeMillis()
                if (now - lastAccelUpdateTime > 200) {
                    lastAccelUpdateTime = now
                    sendAccelUpdate(gForce)
                }

                // 2. Evaluar umbral de impacto > 2.5G
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
        Log.d(TAG, "Servicio iniciado...")
        
        if (intent != null && intent.action == "ACTION_TRIGGER_PANIC") {
            triggerCollisionEvent("Botón de Pánico Manual o Burbuja")
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destruyendo servicio de cámara...")
        stopCircularBufferTimers()
        stopSensors()
        removeFloatingBubble()
        
        currentActiveRecording?.stop()
        currentActiveRecording = null
        
        activePreview?.setSurfaceProvider(null)
        activePreview = null
        
        cameraProvider?.unbindAll()
        sendStatusUpdate("INACTIVO")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // ==========================================
    // EMISIÓN DE EVENTOS Y TELEMETRÍA A JS
    // ==========================================

    private fun sendStatusUpdate(status: String) {
        try {
            val intent = Intent("com.anonymous.DuoVial.CAMERA_STATUS_CHANGED").apply {
                putExtra("status", status)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar broadcast de estado: ${e.message}")
        }
    }

    private fun sendAccelUpdate(gForce: Double) {
        try {
            val intent = Intent("com.anonymous.DuoVial.ACCEL_CHANGED").apply {
                putExtra("gForce", gForce)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar broadcast de aceleración: ${e.message}")
        }
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
            .setContentTitle("🎥 DuoVial - Vigilando")
            .setContentText("Grabación circular activa en segundo plano.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "DuoVial - Servicio de Cámara",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // ==========================================
    // CONFIGURACIÓN DE CAMERAX CON PREVIEW
    // ==========================================

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                    
                videoCapture = VideoCapture.withOutput(recorder)
                
                // 1. Crear el caso de uso del Preview para la UI
                val preview = Preview.Builder().build()
                activePreview = preview
                
                // Vincular SurfaceProvider si el PreviewView ya se encuentra montado en la UI
                val activeView = activePreviewView
                if (activeView != null) {
                    try {
                        preview.setSurfaceProvider(activeView.surfaceProvider)
                        Log.d(TAG, "PreviewView montado previamente vinculado con éxito.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al vincular vista existente: ${e.message}")
                    }
                }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview, // Vincular Preview también
                    videoCapture
                )
                
                Log.d(TAG, "CameraX vinculada correctamente al servicio nativo.")
                startCircularBuffer()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar CameraX: ${e.message}")
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
        
        // Al iniciar por primera vez
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
                if (event is VideoRecordEvent.Finalize) {
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
            // El primer frame se completó con éxito, rotamos al segundo. 
            // A partir de este momento pasamos al estado de vigilancia activa estable.
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
    // LÓGICA DE DETECCIÓN Y GUARDADO DE EVENTO
    // ==========================================

    private fun triggerCollisionEvent(reason: String) {
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
            Log.d(TAG, "ESCENARIO 1 activo: Sin frame previo completo. Guardando segmento actual parcial.")
            isScenario1 = true
            shortSegmentSaving = true
            sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
            currentActiveRecording?.stop()
        } else {
            // ESCENARIO 2: Existe un frame previo completo
            isScenario1 = false
            if (duration < 14000) {
                // Buffer corto
                Log.d(TAG, "ESCENARIO 2 activo (Buffer Corto < 14s). Guardando previo + actual parcial.")
                shortSegmentSaving = true
                sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
                currentActiveRecording?.stop()
            } else {
                // Buffer largo
                Log.d(TAG, "ESCENARIO 2 activo (Buffer Largo >= 14s). Guardando segmento actual completo.")
                shortSegmentSaving = false
                sendStatusUpdate("GENERANDO CONTENIDO POST EVENTO")
                currentActiveRecording?.stop()
            }
        }
    }

    private fun handleEventSaveTransition() {
        if (!postEventRecordingActive) {
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
            
            postEventRecordingActive = true
            startPostEventRecording()
        } else {
            val finalPartIndex = if (isScenario1) {
                1
            } else {
                if (shortSegmentSaving) 2 else 1
            }
            
            copyFileToDownloads("segment_post.mp4", "incident_${eventTimestamp}_part$finalPartIndex.mp4")
            
            Log.i(TAG, "✅ Incidente guardado con éxito. Reanudando buffer circular...")
            
            val postFile = File(cacheDir, "segment_post.mp4")
            if (postFile.exists()) postFile.delete()
            
            // Reanudar buffer circular volviendo a DUOVIAL ACTIVO
            startCircularBuffer()
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
                Log.d(TAG, "Acelerómetro registrado y activo.")
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
            val size = (60 * density).toInt() // Sleek circular size
            
            // Programar LayoutParams del overlay flotante
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
                x = (resources.displayMetrics.widthPixels - size - (20 * density).toInt()) // Posición lateral inicial
                y = (150 * density).toInt()
            }
            
            // Crear el layout contenedor de la burbuja
            val bubble = FrameLayout(this)
            
            // Diseñar el círculo en código usando GradientDrawable (cero dependencias extras, 100% estable)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF2A55")) // Rojo Neón de DuoVial
                setStroke((2.5 * density).toInt(), Color.parseColor("#FFFFFF")) // Borde blanco
            }
            bubble.background = shape
            
            // Colocar un icono representativo de cámara nativo dentro de la burbuja
            val icon = ImageView(this).apply {
                val padding = (16 * density).toInt()
                setPadding(padding, padding, padding, padding)
                setImageResource(android.R.drawable.presence_video_online) // Icono nativo de punto de grabación
                setColorFilter(Color.WHITE)
            }
            bubble.addView(icon)
            
            floatingBubbleView = bubble
            
            // Lógica de arrastre (Draggable) y Click
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
                                // Vibrar e indicar que fue gatillado
                                triggerVibration()
                                triggerCollisionEvent("Burbuja Flotante Manual")
                            }
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            
                            // Tolerancia para discernir movimiento de click
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isClick = false
                            }
                            
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            
                            try {
                                windowManager?.updateViewLayout(bubble, layoutParams)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al actualizar posición de burbuja: ${e.message}")
                            }
                            return true
                        }
                    }
                    return false
                }
            })
            
            windowManager?.addView(bubble, layoutParams)
            Log.i(TAG, "Burbuja flotante activa y dibujada con éxito.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear la burbuja flotante: ${e.message}")
        }
    }

    private fun removeFloatingBubble() {
        if (floatingBubbleView != null) {
            try {
                windowManager?.removeView(floatingBubbleView)
                floatingBubbleView = null
                Log.d(TAG, "Burbuja flotante removida con éxito.")
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
