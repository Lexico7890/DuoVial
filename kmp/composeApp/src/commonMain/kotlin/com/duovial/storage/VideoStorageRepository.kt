package com.duovial.storage

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface que define el contrato para el almacenamiento de videos en la nube.
 * Implementada en androidMain por SupabaseVideoStorage.
 *
 * Patrón: Interface en commonMain, implementación en androidMain.
 */
interface VideoStorageRepository {

    /**
     * Estados posibles de una operación de subida.
     */
    sealed class UploadState {
        /** Sin operación en curso */
        data object Idle : UploadState()

        /** Subida en progreso con porcentaje (0.0 - 1.0) */
        data class Uploading(val progress: Float) : UploadState()

        /** Subida exitosa con URL del video */
        data class Success(val url: String) : UploadState()

        /** Error en la subida */
        data class Error(val message: String) : UploadState()
    }

    /**
     * Estado actual de la subida (reactivo para UI).
     */
    val uploadState: StateFlow<UploadState>

    /**
     * Sube un video de incidente a Supabase Storage.
     *
     * @param localUri URI local del video a subir
     * @param incidentId ID del incidente asociado
     * @param userId ID del usuario propietario
     * @return Result con la URL firmada del video o error
     */
    suspend fun uploadVideo(
        localUri: String,
        incidentId: String,
        userId: String
    ): Result<String>

    /**
     * Obtiene una URL firmada para acceder a un video.
     *
     * @param path Ruta del video en Storage
     * @param expiresIn Tiempo de expiración en segundos (default: 7 días)
     * @return Result con la URL firmada o error
     */
    suspend fun getSignedUrl(
        path: String,
        expiresIn: Long = 604800
    ): Result<String>

    /**
     * Elimina un video de Storage.
     *
     * @param path Ruta del video a eliminar
     * @return Result Unit o error
     */
    suspend fun deleteVideo(path: String): Result<Unit>

    /**
     * Verifica si un video existe en Storage.
     *
     * @param path Ruta del video
     * @return true si existe
     */
    suspend fun videoExists(path: String): Boolean
}
