package com.duovial.state

interface SettingsManager {
    suspend fun getGForceThreshold(): Double
    suspend fun setGForceThreshold(value: Double)
    suspend fun getEarThreshold(): Double
    suspend fun setEarThreshold(value: Double)
    suspend fun getDurationThresholdMs(): Long
    suspend fun setDurationThresholdMs(value: Long)
    suspend fun getMaxAlertsPerHour(): Int
    suspend fun setMaxAlertsPerHour(value: Int)
    suspend fun isFatigueEnabled(): Boolean
    suspend fun setFatigueEnabled(value: Boolean)
    suspend fun isAutoStartEnabled(): Boolean
    suspend fun setAutoStartEnabled(value: Boolean)

    // Auto-inicio inteligente
    suspend fun getAutoStartCooldownHours(): Int
    suspend fun setAutoStartCooldownHours(value: Int)
    suspend fun isAutoStartAskBeforeActivate(): Boolean
    suspend fun setAutoStartAskBeforeActivate(value: Boolean)
    suspend fun getAutoStartCancelTimestamp(): Long
    suspend fun setAutoStartCancelTimestamp(value: Long)

    // Onboarding
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(value: Boolean)
}
