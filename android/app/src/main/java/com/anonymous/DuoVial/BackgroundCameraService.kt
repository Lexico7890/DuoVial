package com.anonymous.DuoVial

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Servicio en primer plano (Foreground Service) nativo re-calibrado:
 * 1. Monitoreo por Acelerómetro nativo (Fuerza G > 2.5G) para detectar impactos reales.
 * 2. Remoción total del micrófono (evita grabaciones de voz y falsos positivos de audio).
 * 3. Bucle de buffer circular y lógica exacta de Escenarios 1 y 2.
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

    // Acelerómetro nativo para impactos
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null

    // Cooldown para Triggers (12 segundos)
    private var lastEventTriggerTime = 0L

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

    // Listener del acelerómetro para Fuerza G
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                // Magnitud total en m/s^2
                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                
                // Conversión a Fuerza G (dividiendo por gravedad terrestre ~9.81 m/s^2)
                val gForce = magnitude / SensorManager.GRAVITY_EARTH
                
                // Umbral recomendado de impacto > 2.5G
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Servicio iniciado...")
        
        if (intent != null && intent.action == "ACTION_TRIGGER_PANIC") {
            triggerCollisionEvent("Botón de Pánico Manual")
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destruyendo servicio de cámara...")
        stopCircularBufferTimers()
        stopSensors()
        
        currentActiveRecording?.stop()
        currentActiveRecording = null
        
        cameraProvider?.unbindAll()
        sendStatusUpdate("INACTIVO")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // Enviar broadcast local para actualizar la UI en JS
    private fun sendStatusUpdate(status: String) {
        try {
            val intent = Intent("com.anonymous.DuoVial.CAMERA_STATUS_CHANGED").apply {
                putExtra("status", status)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar broadcast: ${e.message}")
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
    // CONFIGURACIÓN DE CAMERAX
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
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    videoCapture
                )
                
                Log.d(TAG, "CameraX vinculada correctamente al servicio.")
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
        
        sendStatusUpdate("VIGILANDO")
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
            sendStatusUpdate("GUARDANDO PRE-EVENTO (Fase 1/2)")
            currentActiveRecording?.stop()
        } else {
            // ESCENARIO 2: Existe un frame previo completo
            isScenario1 = false
            if (duration < 14000) {
                // Buffer corto
                Log.d(TAG, "ESCENARIO 2 activo (Buffer Corto < 14s). Guardando previo + actual parcial.")
                shortSegmentSaving = true
                sendStatusUpdate("GUARDANDO PRE-EVENTO (Fase 1/3)")
                currentActiveRecording?.stop()
            } else {
                // Buffer largo
                Log.d(TAG, "ESCENARIO 2 activo (Buffer Largo >= 14s). Guardando segmento actual completo.")
                shortSegmentSaving = false
                sendStatusUpdate("GUARDANDO PRE-EVENTO (Fase 1/2)")
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
            sendStatusUpdate("VIGILANDO")
            
            val postFile = File(cacheDir, "segment_post.mp4")
            if (postFile.exists()) postFile.delete()
            
            startCircularBuffer()
        }
    }

    private fun startPostEventRecording() {
        if (videoCapture == null) {
            Log.e(TAG, "No se puede iniciar post-evento: videoCapture es nulo.")
            startCircularBuffer()
            return
        }
        
        sendStatusUpdate("GRABANDO POST-EVENTO (15s)")
        
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
                Log.d(TAG, "Acelerómetro registrado y activo para Fuerza G.")
            } else {
                Log.w(TAG, "El acelerómetro no está disponible en este dispositivo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar acelerómetro: ${e.message}")
        }
    }

    private fun stopSensors() {
        sensorManager?.unregisterListener(accelListener)
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
