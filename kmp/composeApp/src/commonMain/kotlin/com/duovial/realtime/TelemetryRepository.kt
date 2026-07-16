package com.duovial.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface que define el contrato para la telemetría en tiempo real.
 * Implementada en androidMain por SupabaseRealtimeManager.
 *
 * Flujo:
 * - La app Android envía telemetría cada 30 segundos
 * - El Dashboard Web la recibe via WebSockets
 * - RLS garantiza que cada org solo vea sus datos
 */
interface TelemetryRepository {

    /**
     * Estado de la conexión WebSocket.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Último dato de telemetría recibido/enviado.
     */
    val lastTelemetry: StateFlow<TelemetryData?>

    /**
     * Envía telemetría a Supabase.
     *
     * @param data Datos de telemetría a enviar
     * @return Result Unit o error
     */
    suspend fun sendTelemetry(data: TelemetryData): Result<Unit>

    /**
     * Escucha cambios de telemetría para un vehículo específico.
     *
     * @param vehicleId ID del vehículo a escuchar
     * @return Flow con los datos de telemetría actualizados
     */
    suspend fun startListening(vehicleId: String): Flow<TelemetryData>

    /**
     * Detiene la escucha de telemetría.
     */
    suspend fun stopListening()

    /**
     * Reconecta el WebSocket.
     */
    suspend fun reconnect()
}

/**
 * Modelo de datos de telemetría.
 * Se envía a la tabla vehicle_telemetry en Supabase.
 */
data class TelemetryData(
    val vehicleId: String,
    val orgId: String,
    val driverId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double? = null,
    val heading: Double? = null,
    val altitude: Double? = null,
    val gForce: Double? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val storageFreeMb: Long? = null,
    val deviceTempCelsius: Double? = null,
    val serviceStatus: String = "INACTIVO",
    val recordedAt: String = kotlinx.datetime.Clock.System.now().toString()
)
