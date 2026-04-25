package com.anonymous.DuoVial.frameprocessor

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.frameprocessors.VisionCameraProxy
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque

class CircularBufferPlugin(proxy: VisionCameraProxy, options: Map<String, Any>?) : FrameProcessorPlugin() {

    private val TAG = "CircularBufferPlugin"
    private val MAX_BUFFER_DURATION_US = 15_000_000L // 15 segundos pre-impacto
    private val POST_IMPACT_DURATION_US = 15_000_000L // 15 segundos post-impacto

    private var isEncoderInitialized = false
    private var mediaCodec: MediaCodec? = null
    private var outputFormat: MediaFormat? = null

    data class EncodedFrame(
        val buffer: ByteArray,
        val info: MediaCodec.BufferInfo
    )

    private val frameQueue = ConcurrentLinkedDeque<EncodedFrame>()
    private var isImpactDetected = false
    private var impactTimestampUs = 0L
    private var isDumping = false
    private var lastSavedPath = ""

    override fun callback(frame: Frame, params: Map<String, Any>?): Any? {
        val image: Image = frame.image

        // Detectar trigger de impacto enviado desde JS
        val triggerImpact = params?.get("triggerImpact") == true
        if (triggerImpact && !isImpactDetected && !isDumping) {
            Log.d(TAG, "💥 IMPACTO detectado. Iniciando cuenta regresiva post-impacto...")
            isImpactDetected = true
            impactTimestampUs = image.timestamp / 1000
        }

        if (!isEncoderInitialized) {
            try {
                initEncoder(image.width, image.height)
                isEncoderInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar encoder: ${e.message}")
                return null
            }
        }

        if (!isDumping) {
            try {
                encodeFrame(image)
                drainEncoder()
                manageQueue()
                checkAndDump(image.timestamp / 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Error en pipeline de encoding: ${e.message}")
            }
        }

        val res = mutableMapOf<String, Any>()
        if (lastSavedPath.isNotEmpty()) {
            res["savedPath"] = lastSavedPath
            lastSavedPath = ""
        }
        return res
    }

    private fun initEncoder(width: Int, height: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000) // 2 Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe cada segundo

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec!!.start()
        Log.d(TAG, "Encoder inicializado: ${width}x${height}")
    }

    private fun encodeFrame(image: Image) {
        val codec = mediaCodec ?: return
        val inputBufferIndex = codec.dequeueInputBuffer(10_000)
        if (inputBufferIndex < 0) return

        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: return
        inputBuffer.clear()

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val yuv = ByteArray(ySize + uSize + vSize)
        yBuffer.get(yuv, 0, ySize)
        // Intercalar U/V en orden NV21 (V primero, luego U)
        vBuffer.get(yuv, ySize, vSize)
        uBuffer.get(yuv, ySize + vSize, uSize)

        inputBuffer.put(yuv)
        codec.queueInputBuffer(inputBufferIndex, 0, yuv.size, image.timestamp / 1000, 0)
    }

    private fun drainEncoder() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = codec.outputFormat
                }
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.get(outData)

                    val infoCopy = MediaCodec.BufferInfo()
                    infoCopy.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                    frameQueue.addLast(EncodedFrame(outData, infoCopy))
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun manageQueue() {
        if (isImpactDetected) return // No descartar frames post-impacto
        if (frameQueue.size < 2) return

        val newestTime = frameQueue.last.info.presentationTimeUs
        while (frameQueue.size > 1) {
            val oldestTime = frameQueue.first.info.presentationTimeUs
            if (newestTime - oldestTime > MAX_BUFFER_DURATION_US) {
                frameQueue.removeFirst()
            } else {
                break
            }
        }
    }

    private fun checkAndDump(currentTimestampUs: Long) {
        if (!isImpactDetected || isDumping) return
        if (currentTimestampUs - impactTimestampUs >= POST_IMPACT_DURATION_US) {
            Log.d(TAG, "Pasaron 15s post-impacto. Volcando a MP4...")
            isDumping = true
            dumpToDisk()
        }
    }

    private fun dumpToDisk() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "DuoVial_Impact_${System.currentTimeMillis()}.mp4")

            val fmt = outputFormat
            if (fmt == null) {
                Log.e(TAG, "outputFormat es null, abortando volcado")
                reset()
                return
            }

            val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(fmt)
            muxer.start()

            var foundKeyframe = false
            while (frameQueue.isNotEmpty()) {
                val encodedFrame = frameQueue.removeFirst()
                val isKeyframe = (encodedFrame.info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                if (!foundKeyframe) {
                    if (isKeyframe) foundKeyframe = true else continue
                }

                val byteBuffer = ByteBuffer.wrap(encodedFrame.buffer)
                muxer.writeSampleData(trackIndex, byteBuffer, encodedFrame.info)
            }

            muxer.stop()
            muxer.release()

            Log.d(TAG, "✅ Guardado exitosamente en: ${file.absolutePath}")
            lastSavedPath = file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al volcar a disco: ${e.message}", e)
        } finally {
            reset()
        }
    }

    private fun reset() {
        isImpactDetected = false
        isDumping = false
        impactTimestampUs = 0L
        frameQueue.clear()
    }
}
