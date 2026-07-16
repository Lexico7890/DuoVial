package com.duovial.platform

import android.content.Context
import android.util.Log
import com.duovial.storage.VideoStorageRepository
import com.duovial.supabase.SupabaseClientProvider
import com.duovial.supabase.SupabaseErrorHandler
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementación de VideoStorageRepository usando Supabase Storage.
 *
 * Bucket: "incident-videos" (privado)
 * - Cada usuario tiene su carpeta: {userId}/{incidentId}/{filename}
 * - Solo el propietario puede ver sus videos (RLS)
 * - Videos se suben como MP4
 *
 * Limites:
 * - Tamaño máximo: 50MB por video
 * - Tipos permitidos: video/mp4, video/quicktime
 */
class SupabaseVideoStorage(
    private val context: Context
) : VideoStorageRepository {

    private val TAG = "DuoVial_Storage"
    private val supabase get() = SupabaseClientProvider.getClient()

    private val _uploadState = MutableStateFlow<VideoStorageRepository.UploadState>(
        VideoStorageRepository.UploadState.Idle
    )
    override val uploadState: StateFlow<VideoStorageRepository.UploadState> =
        _uploadState.asStateFlow()

    companion object {
        private const val BUCKET_NAME = "incident-videos"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50MB
    }

    override suspend fun uploadVideo(
        localUri: String,
        incidentId: String,
        userId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _uploadState.value = VideoStorageRepository.UploadState.Uploading(0f)

            val file = File(localUri)
            if (!file.exists()) {
                val error = "El archivo no existe: $localUri"
                _uploadState.value = VideoStorageRepository.UploadState.Error(error)
                return@withContext Result.failure(Exception(error))
            }

            if (file.length() > MAX_FILE_SIZE) {
                val error = "El video supera el límite de 50MB"
                _uploadState.value = VideoStorageRepository.UploadState.Error(error)
                return@withContext Result.failure(Exception(error))
            }

            // Construir ruta: {userId}/{incidentId}/{filename}
            val path = "$userId/$incidentId/${file.name}"
            val fileBytes = file.readBytes()

            Log.i(TAG, "Subiendo video: $path (${fileBytes.size} bytes)")

            // Subir a Supabase Storage
            supabase.storage
                .from(BUCKET_NAME)
                .upload(path, fileBytes) {
                    upsert = false
                }

            _uploadState.value = VideoStorageRepository.UploadState.Uploading(0.8f)

            // Generar URL firmada (expira en 7 días)
            val signedUrl = supabase.storage
                .from(BUCKET_NAME)
                .createSignedUrl(path) {
                    expiresIn = 604800 // 7 días
                }

            _uploadState.value = VideoStorageRepository.UploadState.Success(signedUrl)
            Log.i(TAG, "Video subido exitosamente: $signedUrl")

            Result.success(signedUrl)
        } catch (e: Exception) {
            val errorMessage = SupabaseErrorHandler.mapError(e)
            _uploadState.value = VideoStorageRepository.UploadState.Error(errorMessage)
            Log.e(TAG, "Error subiendo video: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getSignedUrl(
        path: String,
        expiresIn: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val signedUrl = supabase.storage
                .from(BUCKET_NAME)
                .createSignedUrl(path) {
                    this.expiresIn = expiresIn
                }
            Result.success(signedUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo URL firmada: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteVideo(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.storage
                .from(BUCKET_NAME)
                .remove(path)
            Log.i(TAG, "Video eliminado: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando video: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun videoExists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.storage
                .from(BUCKET_NAME)
                .list(path.substringBeforeLast("/"))
                .any { it.name == path.substringAfterLast("/") }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando existencia: ${e.message}")
            false
        }
    }

    /**
     * Resetea el estado de subida.
     */
    fun resetUploadState() {
        _uploadState.value = VideoStorageRepository.UploadState.Idle
    }
}
