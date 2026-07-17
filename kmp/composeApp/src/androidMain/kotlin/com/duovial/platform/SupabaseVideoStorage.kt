package com.duovial.platform

import android.content.Context
import android.util.Log
import com.duovial.storage.VideoStorageRepository
import com.duovial.supabase.SupabaseClientProvider
import com.duovial.supabase.SupabaseErrorHandler
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Implementación de VideoStorageRepository usando Supabase Storage.
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
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024L
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

            val path = "$userId/$incidentId/${file.name}"
            val fileBytes = file.readBytes()

            Log.i(TAG, "Subiendo video: $path (${fileBytes.size} bytes)")

            // Subir a Supabase Storage
            supabase.storage.from(BUCKET_NAME).upload(path, fileBytes)

            _uploadState.value = VideoStorageRepository.UploadState.Uploading(0.8f)

            // Generar URL firmada (expira en 7 días)
            val signedUrl = supabase.storage.from(BUCKET_NAME).createSignedUrl(path, 7.days)

            _uploadState.value = VideoStorageRepository.UploadState.Success(signedUrl)
            Log.i(TAG, "Video subido exitosamente")

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
            // Convertir segundos a Duration
            val duration = expiresIn.seconds
            val signedUrl = supabase.storage.from(BUCKET_NAME).createSignedUrl(path, duration)
            Result.success(signedUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo URL firmada: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteVideo(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.storage.from(BUCKET_NAME).delete(path)
            Log.i(TAG, "Video eliminado: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando video: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun videoExists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = path.substringBeforeLast("/")
            val fileName = path.substringAfterLast("/")
            supabase.storage.from(BUCKET_NAME).list(folder)
                .any { it.name == fileName }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando existencia: ${e.message}")
            false
        }
    }

    fun resetUploadState() {
        _uploadState.value = VideoStorageRepository.UploadState.Idle
    }
}
