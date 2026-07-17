package com.duovial.platform

import android.util.Log
import com.duovial.realtime.TelemetryData
import com.duovial.realtime.TelemetryRepository
import com.duovial.supabase.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación de TelemetryRepository usando Supabase Realtime.
 *
 * Tabla: vehicle_telemetry
 * - Cada 30 segundos se envía: GPS, velocidad, G-Force, batería, estado
 * - El Dashboard Web recibe los datos via WebSocket
 */
class SupabaseRealtimeManager : TelemetryRepository {

    private val TAG = "DuoVial_Realtime"
    private val supabase get() = SupabaseClientProvider.getClient()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastTelemetry = MutableStateFlow<TelemetryData?>(null)
    override val lastTelemetry: StateFlow<TelemetryData?> = _lastTelemetry.asStateFlow()

    private var currentChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

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
            Log.d(TAG, "Telemetría enviada: ${data.vehicleId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando telemetría: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun startListening(vehicleId: String): Flow<TelemetryData> {
        stopListening()

        val channel = supabase.realtime.channel("telemetry-$vehicleId")
        currentChannel = channel

        val changeFlow = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction.Insert>(
            schema = "public",
            table = "vehicle_telemetry"
        )

        channel.subscribe()
        _isConnected.value = true

        Log.i(TAG, "Suscrito a telemetría del vehículo: $vehicleId")

        return changeFlow.map { action ->
            try {
                val record = action.record
                TelemetryData(
                    vehicleId = record["vehicle_id"]?.toString() ?: vehicleId,
                    orgId = record["org_id"]?.toString() ?: "",
                    driverId = record["driver_id"]?.toString(),
                    latitude = record["latitude"]?.toString()?.toDoubleOrNull() ?: 0.0,
                    longitude = record["longitude"]?.toString()?.toDoubleOrNull() ?: 0.0,
                    speedKmh = record["speed_kmh"]?.toString()?.toDoubleOrNull(),
                    heading = record["heading"]?.toString()?.toDoubleOrNull(),
                    altitude = record["altitude"]?.toString()?.toDoubleOrNull(),
                    gForce = record["g_force"]?.toString()?.toDoubleOrNull(),
                    batteryLevel = record["battery_level"]?.toString()?.toIntOrNull(),
                    isCharging = record["is_charging"]?.toString()?.toBooleanStrictOrNull(),
                    storageFreeMb = record["storage_free_mb"]?.toString()?.toLongOrNull(),
                    deviceTempCelsius = record["device_temp_celsius"]?.toString()?.toDoubleOrNull(),
                    serviceStatus = record["service_status"]?.toString() ?: "UNKNOWN",
                    recordedAt = record["recorded_at"]?.toString() ?: ""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parseando telemetría: ${e.message}")
                null
            }
        }.filterNotNull()
    }

    override suspend fun stopListening() {
        currentChannel?.let { channel ->
            try {
                channel.unsubscribe()
                Log.i(TAG, "Desuscrito del canal de telemetría")
            } catch (e: Exception) {
                Log.e(TAG, "Error desuscribiéndose: ${e.message}")
            }
        }
        currentChannel = null
        _isConnected.value = false
    }

    override suspend fun reconnect() {
        Log.i(TAG, "Reconectando canal de telemetría...")
        stopListening()
    }
}
