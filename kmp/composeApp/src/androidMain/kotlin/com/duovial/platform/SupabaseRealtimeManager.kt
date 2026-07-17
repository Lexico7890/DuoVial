package com.duovial.platform

import android.util.Log
import com.duovial.realtime.TelemetryData
import com.duovial.realtime.TelemetryRepository
import com.duovial.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Implementación de TelemetryRepository usando Supabase.
 *
 * NOTA: La suscripción Realtime se implementará cuando se verifique
 * la API exacta del SDK v3. Por ahora, solo enviamos telemetría
 * y el Dashboard la recibe via polling.
 */
class SupabaseRealtimeManager : TelemetryRepository {

    private val TAG = "DuoVial_Realtime"
    private val supabase get() = SupabaseClientProvider.getClient()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastTelemetry = MutableStateFlow<TelemetryData?>(null)
    override val lastTelemetry: StateFlow<TelemetryData?> = _lastTelemetry.asStateFlow()

    override suspend fun sendTelemetry(data: TelemetryData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val telemetryMap = mapOf(
                "vehicle_id" to data.vehicleId,
                "org_id" to data.orgId,
                "driver_id" to data.driverId,
                "latitude" to data.latitude,
                "longitude" to data.longitude,
                "speed_kmh" to data.speedKmh,
                "heading" to data.heading,
                "altitude" to data.altitude,
                "g_force" to data.gForce,
                "battery_level" to data.batteryLevel,
                "is_charging" to data.isCharging,
                "storage_free_mb" to data.storageFreeMb,
                "device_temp_celsius" to data.deviceTempCelsius,
                "service_status" to data.serviceStatus,
                "recorded_at" to data.recordedAt
            )

            supabase.from("vehicle_telemetry").insert(telemetryMap)

            _lastTelemetry.value = data
            _isConnected.value = true
            Log.d(TAG, "Telemetría enviada: ${data.vehicleId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando telemetría: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun startListening(vehicleId: String): Flow<TelemetryData> {
        // Implementación pendiente: suscripción Realtime via WebSocket
        // Por ahora retorna un Flow vacío
        Log.i(TAG, "startListening pendiente de implementar con API correcta del SDK v3")
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override suspend fun stopListening() {
        _isConnected.value = false
        Log.i(TAG, "Stop listening")
    }

    override suspend fun reconnect() {
        Log.i(TAG, "Reconectando...")
        _isConnected.value = false
    }
}
