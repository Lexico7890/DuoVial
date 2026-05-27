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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
 * Servicio en primer plano (Foreground Service) nativo que maneja:
 * 1. Inicialización de CameraX para grabación en segundo plano sin UI.
 * 2. Buffer circular de 15 segundos alternando entre dos segmentos.
 * 3. Lógica de guardado preciso ante colisiones o pánico (2 o 3 partes).
 * 4. Sensores nativos (Giroscopio y Micrófono) de bajo consumo en background.
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
    private var eventTimestamp = 0L

    private val handler = Handler(Looper.getMainLooper())

    // Giroscopio
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null

    // Micrófono (Detección de decibeles)
    private var isMonitoringAudio = false
    private var audioRecordThread: Thread? = null

    // Cooldown para Triggers (10 segundos)
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

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                if (magnitude > 3.0) { // Rotación mayor a 3.0 rad/s
                    triggerCollisionEvent("Giroscopio Rotación Brusca: ${String.format("%.2f", magnitude)} rad/s")
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
        startAudioMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Servicio iniciado...")
        
        // Manejar llamadas manuales desde el módulo JS (p. ej. disparar Pánico)
        if (intent != null && intent.action == "ACTION_TRIGGER_PANIC") {
            triggerCollisionEvent("Botón de Pánico Manual")
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Destruyendo servicio de cámara...")
        stopCircularBufferTimers()
        stopSensors()
        stopAudioMonitoring()
        
        currentActiveRecording?.stop()
        currentActiveRecording = null
        
        cameraProvider?.unbindAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
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
                
                // Configurar el grabador (HD / 1080p o 720p según hardware)
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                    
                videoCapture = VideoCapture.withOutput(recorder)
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Vincular videoCapture al ciclo de vida del servicio nativo
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
        
        // Empezar grabando el primer segmento (index 0)
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
            // Nota: No grabamos audio en el archivo de video (bitrate reducido, ahorro 30% CPU)
            val pendingRecording = videoCapture!!.output.prepareRecording(this, fileOutputOptions)
            
            currentActiveRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    onRecordingFinalized(event)
                }
            }
            
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Grabando segmento_$index en caché...")
            
            // Agendar la rotación en 15 segundos
            handler.postDelayed(rotateRunnable, 15000)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al iniciar grabación: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar grabación: ${e.message}")
        }
    }

    private fun rotateCircularBuffer() {
        // Detener la grabación actual. 
        // El finalize listener gatillará el inicio del siguiente segmento (1 - index).
        currentActiveRecording?.stop()
    }

    private fun onRecordingFinalized(event: VideoRecordEvent.Finalize) {
        val error = event.error
        if (error != VideoRecordEvent.Finalize.ERROR_NONE) {
            Log.e(TAG, "Segmento finalizado con error: $error")
        } else {
            Log.d(TAG, "Segmento finalizado con éxito: ${event.outputResults.outputUri}")
        }
        
        if (!isSavingEvent) {
            // Continuar con el ciclo normal del buffer circular
            val nextIndex = 1 - currentRecordingIndex
            startRecordingSegment(nextIndex)
        } else {
            // Lógica de transición de guardado de evento
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
        if (now - lastEventTriggerTime < 10000) { // Cooldown de 10 segundos
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
        
        if (duration < 14000) {
            // CASO A (CORTO): Menos de 14 segundos en el segmento actual
            // Conservaremos el segmento previo COMPLETO (15s) y el actual PARCIAL.
            shortSegmentSaving = true
            currentActiveRecording?.stop()
        } else {
            // CASO B (LARGO): 14 o más segundos en el segmento actual
            // Este segmento ya sirve como pre-evento completo.
            shortSegmentSaving = false
            currentActiveRecording?.stop()
        }
    }

    private fun handleEventSaveTransition() {
        if (!postEventRecordingActive) {
            // Paso 1: Copiar los segmentos previos de pre-evento desde la caché
            if (shortSegmentSaving) {
                // Copiar el previo completo (si existe y tiene tamaño)
                val prevIndex = 1 - currentRecordingIndex
                copyFileToDownloads("segment_$prevIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
                // Copiar el actual parcial
                copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part1.mp4")
            } else {
                // Copiar únicamente el actual (ya que contiene >= 14 segundos)
                copyFileToDownloads("segment_$currentRecordingIndex.mp4", "incident_${eventTimestamp}_part0.mp4")
            }
            
            // Paso 2: Iniciar la grabación de post-evento de 15 segundos adicionales
            postEventRecordingActive = true
            startPostEventRecording()
        } else {
            // Paso 3: Acaba de finalizar el segmento de post-evento
            val postPartSuffix = if (shortSegmentSaving) "part2" else "part1"
            copyFileToDownloads("segment_post.mp4", "incident_${eventTimestamp}_$postPartSuffix.mp4")
            
            Log.i(TAG, "✅ Incidente guardado con éxito. Reanudando buffer circular...")
            
            // Limpiar archivos de post-evento
            val postFile = File(cacheDir, "segment_post.mp4")
            if (postFile.exists()) postFile.delete()
            
            // Reanudar el ciclo de buffer circular normal
            startCircularBuffer()
        }
    }

    private fun startPostEventRecording() {
        if (videoCapture == null) {
            Log.e(TAG, "No se puede iniciar post-evento: videoCapture es nulo.")
            startCircularBuffer()
            return
        }
        
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
            
            // Detener automáticamente en 15 segundos
            handler.postDelayed(stopPostEventRunnable, 15000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar grabación de post-evento: ${e.message}")
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
        
        Log.d(TAG, "Exportando $sourceFileName a descargas públicas como $targetFileName")
        
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
                Log.d(TAG, "Copiado exitoso a MediaStore: $targetFileName")
            } else {
                Log.e(TAG, "No se pudo crear el Uri en MediaStore para: $targetFileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al exportar archivo: ${e.message}")
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                // Ignorar
            }
        }
    }

    // ==========================================
    // CAPTURA DE SENSORES Y AUDIO EN BACKGROUND
    // ==========================================

    private fun startSensors() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            
            if (gyroSensor != null) {
                sensorManager?.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Giroscopio registrado y activo.")
            } else {
                Log.w(TAG, "El giroscopio no está disponible en este dispositivo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar sensores: ${e.message}")
        }
    }

    private fun stopSensors() {
        sensorManager?.unregisterListener(gyroListener)
    }

    private fun startAudioMonitoring() {
        isMonitoringAudio = true
        audioRecordThread = Thread {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (bufferSize <= 0) {
                Log.e(TAG, "Tamaño de búfer de audio inválido: $bufferSize")
                return@Thread
            }
            
            try {
                // Requiere permiso RECORD_AUDIO
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buffer = ShortArray(bufferSize)
                    Log.d(TAG, "Monitoreo nativo de micrófono activo.")
                    
                    while (isMonitoringAudio) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var max = 0
                            for (i in 0 until read) {
                                val absVal = Math.abs(buffer[i].toInt())
                                if (absVal > max) {
                                    max = absVal
                                }
                            }
                            
                            val amplitude = max.toDouble()
                            if (amplitude > 0) {
                                val db = 20 * Math.log10(amplitude / 32767.0)
                                // Si supera los -20dB (sonido extremadamente fuerte, ej. choque)
                                if (db > -20.0) {
                                    handler.post {
                                        triggerCollisionEvent("Pico de Audio: ${db.toInt()} dB")
                                    }
                                }
                            }
                        }
                        Thread.sleep(150)
                    }
                    try {
                        recorder.stop()
                        recorder.release()
                    } catch (e: Exception) {
                        // Ignorar
                    }
                } else {
                    Log.e(TAG, "No se pudo inicializar AudioRecord. ¿Permiso faltante?")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permiso de audio denegado en background: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error en hilo de monitoreo de audio: ${e.message}")
            }
        }
        audioRecordThread?.start()
    }

    private fun stopAudioMonitoring() {
        isMonitoringAudio = false
        audioRecordThread = null
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
