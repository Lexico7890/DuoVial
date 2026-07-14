package com.duovial.services

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.duovial.logging.DuoVialLog

/**
 * Helper para detectar capacidades de cámara del dispositivo.
 * Específicamente verifica si el dispositivo soporta cámaras concurrentes
 * (trasera + frontal al mismo tiempo), necesario para:
 * - Modo Vigilante (cámara trasera) + Anti-somnolencia (cámara frontal)
 * 
 * Según el CONTEXT.md:
 * "Si el dispositivo no soporta cámaras concurrentes, la app sugiere usar un wearable
 * o pausa el modo Vigilante mientras se usa la cámara frontal"
 */
object CameraCapabilities {
    
    private const val TAG = "CameraCapabilities"
    
    /**
     * Resultado de la verificación de cámaras concurrentes.
     */
    data class ConcurrentCameraResult(
        val supported: Boolean,
        val reason: String? = null
    )
    
    /**
     * Verifica si el dispositivo soporta usar cámara trasera y frontal simultáneamente.
     * 
     * @return ConcurrentCameraResult con el resultado de la verificación
     */
    fun checkConcurrentCameraSupport(context: Context): ConcurrentCameraResult {
        // Requiere Android 9 (API 28) o superior
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return ConcurrentCameraResult(
                supported = false,
                reason = "Requiere Android 9 (API 28) o superior"
            )
        }
        
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            
            // Obtener IDs de cámaras disponibles
            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) {
                return ConcurrentCameraResult(
                    supported = false,
                    reason = "No se encontraron cámaras disponibles"
                )
            }
            
            // Buscar cámara trasera y frontal
            var backCameraId: String? = null
            var frontCameraId: String? = null
            
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> backCameraId = id
                    CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId = id
                }
            }
            
            if (backCameraId == null || frontCameraId == null) {
                return ConcurrentCameraResult(
                    supported = false,
                    reason = "No se encontró cámara ${if (backCameraId == null) "trasera" else "frontal"}"
                )
            }
            
            // Verificar soporte de cámaras concurrentes (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val concurrentCameraIds = cameraManager.concurrentCameraIds
                DuoVialLog.d(TAG, "Cámaras concurrentes disponibles: $concurrentCameraIds")
                
                // concurrentCameraIds es List<List<String>>
                // Cada lista interna contiene IDs de cámaras que pueden usarse simultáneamente
                val supported = concurrentCameraIds.any { cameraIdList ->
                    val hasBack = cameraIdList.contains(backCameraId)
                    val hasFront = cameraIdList.contains(frontCameraId)
                    hasBack && hasFront
                }
                
                return ConcurrentCameraResult(
                    supported = supported,
                    reason = if (!supported) "El dispositivo no soporta cámaras concurrentes" else null
                )
            } else {
                // En Android 9, no hay API directa para verificar concurrent cameras
                // Asumimos que no está soportado para ser conservadores
                return ConcurrentCameraResult(
                    supported = false,
                    reason = "Verificación de cámaras concurrentes requiere Android 11+"
                )
            }
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error verificando cámaras concurrentes: ${e.message}")
            return ConcurrentCameraResult(
                supported = false,
                reason = "Error al verificar: ${e.message}"
            )
        }
    }
    
    /**
     * Cache del resultado de verificación para evitar llamadas repetidas.
     */
    private var cachedResult: ConcurrentCameraResult? = null
    
    /**
     * Obtiene el resultado de verificación de cámaras concurrentes,
     * usando cache si está disponible.
     */
    fun getConcurrentCameraSupport(context: Context): ConcurrentCameraResult {
        return cachedResult ?: checkConcurrentCameraSupport(context).also {
            cachedResult = it
            DuoVialLog.i(TAG, "Soporte de cámaras concurrentes: ${it.supported} - ${it.reason ?: "OK"}")
        }
    }
    
    /**
     * Limpia el cache de verificación.
     * Útil si se necesita re-verificar después de cambios de hardware.
     */
    fun clearCache() {
        cachedResult = null
    }
}
