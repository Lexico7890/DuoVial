package com.duovial.platform

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.duovial.logging.DuoVialLog
import com.duovial.state.Incident
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IncidentRepository {

    private val TAG = "IncidentRepository"
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    private val pattern = Regex("incident_(\\d+)_part(\\d+)\\.mp4")
    private const val MAX_INCIDENT_AGE_MS = 72 * 60 * 60 * 1000L // 72 horas
    private const val MIN_VALID_FILE_SIZE = 1024L // 1 KB mínimo para considerar un video válido

    /**
     * Escanea los incidentes guardados en Downloads.
     * Valida que los archivos existan y sean accesibles antes de incluirlos.
     */
    fun scanIncidents(context: Context): List<Incident> {
        val incidents = mutableListOf<Incident>()
        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("incident_%")
        val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

        val partIndexCache = mutableMapOf<String, Int>()
        var skippedCount = 0

        try {
            val cursor = resolver.query(collectionUri, projection, selection, selectionArgs, sortOrder)
            if (cursor != null) {
                try {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    
                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn)
                            if (name == null) {
                                skippedCount++
                                continue
                            }
                            val size = cursor.getLong(sizeColumn)
                            
                            // Validar timestamp del nombre
                            val timestampSec = extractTimestamp(name)
                            if (timestampSec == null) {
                                DuoVialLog.w(TAG, "Archivo con nombre inválido ignorado: $name")
                                skippedCount++
                                continue
                            }
                            
                            // Validar tamaño del archivo
                            if (size < MIN_VALID_FILE_SIZE) {
                                DuoVialLog.w(TAG, "Archivo demasiado pequeño o vacío ignorado: $name (${size} bytes)")
                                skippedCount++
                                continue
                            }
                            
                            val uri = Uri.withAppendedPath(collectionUri, id.toString())
                            val uriStr = uri.toString()
                            
                            // Validar que el archivo sea accesible
                            if (!isUriAccessible(resolver, uri)) {
                                DuoVialLog.w(TAG, "Archivo no accesible ignorado: $name")
                                skippedCount++
                                continue
                            }
                            
                            val partIndex = extractPartIndexFromName(name)
                            partIndexCache[uriStr] = partIndex

                            val existing = incidents.indexOfFirst { it.timestampSec == timestampSec }
                            if (existing >= 0) {
                                val incident = incidents[existing]
                                val sortedParts = (incident.parts + uriStr).sortedBy { partIndexCache[it] ?: 0 }
                                incidents[existing] = incident.copy(parts = sortedParts)
                            } else {
                                val date = dateFormat.format(Date(timestampSec * 1000L))
                                incidents.add(Incident(timestampSec = timestampSec, parts = listOf(uriStr), date = date))
                            }
                        } catch (e: Exception) {
                            DuoVialLog.e(TAG, "Error procesando entrada de cursor: ${e.message}")
                            skippedCount++
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error consultando MediaStore: ${e.message}", e)
        }

        if (skippedCount > 0) {
            DuoVialLog.i(TAG, "Se ignoraron $skippedCount archivos inválidos o corruptos")
        }
        
        DuoVialLog.d(TAG, "Escaneo completado: ${incidents.size} incidentes válidos encontrados")
        return incidents.sortedByDescending { it.timestampSec }
    }

    /**
     * Verifica si un URI es accesible (el archivo existe y se puede leer).
     */
    private fun isUriAccessible(resolver: android.content.ContentResolver, uri: Uri): Boolean {
        return try {
            resolver.openInputStream(uri)?.use { inputStream ->
                // Intentar leer al menos 1 byte para verificar accesibilidad
                inputStream.read() >= 0
            } ?: false
        } catch (e: Exception) {
            DuoVialLog.v(TAG, "URI no accesible: ${uri.lastPathSegment} - ${e.message}")
            false
        }
    }

    private fun extractTimestamp(filename: String): Long? {
        return pattern.find(filename)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun extractPartIndexFromName(filename: String): Int {
        return pattern.find(filename)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    }

    /**
     * Limpia incidentes antiguos (>72 horas).
     * Maneja errores de eliminación individual sin detener el proceso.
     */
    fun cleanupOldIncidents(context: Context): Int {
        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_ADDED
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("incident_%")
        
        var deletedCount = 0
        var errorCount = 0
        val now = System.currentTimeMillis()
        
        try {
            val cursor = resolver.query(collectionUri, projection, selection, selectionArgs, null)
            if (cursor != null) {
                try {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    
                    while (cursor.moveToNext()) {
                        try {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn)
                            if (name == null) continue
                            val dateAdded = cursor.getLong(dateAddedColumn) * 1000L // DATE_ADDED está en segundos
                            val ageMs = now - dateAdded
                            
                            if (ageMs > MAX_INCIDENT_AGE_MS) {
                                val uri = Uri.withAppendedPath(collectionUri, id.toString())
                                try {
                                    resolver.delete(uri, null, null)
                                    deletedCount++
                                    DuoVialLog.d(TAG, "Video antiguo eliminado: $name")
                                } catch (e: Exception) {
                                    errorCount++
                                    DuoVialLog.w(TAG, "Error eliminando video $name: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            DuoVialLog.e(TAG, "Error procesando entrada para limpieza: ${e.message}")
                            errorCount++
                        }
                    }
                } finally {
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            DuoVialLog.e(TAG, "Error consultando MediaStore para limpieza: ${e.message}", e)
        }
        
        if (deletedCount > 0) {
            DuoVialLog.i(TAG, "Limpieza completada: $deletedCount videos antiguos eliminados")
        }
        if (errorCount > 0) {
            DuoVialLog.w(TAG, "Limpieza completada con $errorCount errores")
        }
        
        return deletedCount
    }
}
