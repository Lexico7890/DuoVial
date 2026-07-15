package com.duovial.state

/**
 * Gestiona el estado del onboarding de DuoVial.
 *
 * Responsibility:
 * - Determinar si el onboarding ya fue completado
 * - Marcar el onboarding como completado
 * - Permitir reset del onboarding (para testing o settings)
 *
 * Persiste el estado via [SettingsManager] (SharedPreferences en Android).
 */
class OnboardingManager(private val settingsManager: SettingsManager) {

    /**
     * Verifica si el usuario ya completó el onboarding.
     * @return true si el onboarding fue completado, false si es la primera vez
     */
    suspend fun isOnboardingCompleted(): Boolean {
        return settingsManager.isOnboardingCompleted()
    }

    /**
     * Marca el onboarding como completado.
     * Se llama después de que el usuario completa las pantallas de onboarding
     * y la solicitud de permisos.
     */
    suspend fun markAsCompleted() {
        settingsManager.setOnboardingCompleted(true)
    }

    /**
     * Resetea el estado del onboarding.
     * Útil para testing o cuando el usuario quiere repetir el flujo de onboarding
     * desde Settings.
     */
    suspend fun reset() {
        settingsManager.setOnboardingCompleted(false)
    }
}
