package com.duovial.platform

import android.content.Context
import com.duovial.state.SettingsManager
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

class SettingsManagerAndroid(context: Context) : SettingsManager {

    private val settings: Settings = SharedPreferencesSettings(
        context.getSharedPreferences("duovial_prefs", Context.MODE_PRIVATE)
    )

    override suspend fun getGForceThreshold(): Double =
        settings.getDouble("gforce_threshold", 2.5)

    override suspend fun setGForceThreshold(value: Double) =
        settings.putDouble("gforce_threshold", value)

    override suspend fun getEarThreshold(): Double =
        settings.getDouble("ear_threshold", 0.2)

    override suspend fun setEarThreshold(value: Double) =
        settings.putDouble("ear_threshold", value)

    override suspend fun getDurationThresholdMs(): Long =
        settings.getLong("duration_threshold_ms", 2000L)

    override suspend fun setDurationThresholdMs(value: Long) =
        settings.putLong("duration_threshold_ms", value)

    override suspend fun getMaxAlertsPerHour(): Int =
        settings.getInt("max_alerts_per_hour", 3)

    override suspend fun setMaxAlertsPerHour(value: Int) =
        settings.putInt("max_alerts_per_hour", value)

    override suspend fun isFatigueEnabled(): Boolean =
        settings.getBoolean("fatigue_enabled", false)

    override suspend fun setFatigueEnabled(value: Boolean) =
        settings.putBoolean("fatigue_enabled", value)

    override suspend fun isAutoStartEnabled(): Boolean =
        settings.getBoolean("auto_start_enabled", false)

    override suspend fun setAutoStartEnabled(value: Boolean) =
        settings.putBoolean("auto_start_enabled", value)
}
