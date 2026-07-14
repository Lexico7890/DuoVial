package com.duovial.logging

/**
 * Sistema de logging centralizado para DuoVial.
 * Permite controlar el nivel de detalle de los logs en runtime.
 * 
 * Niveles (de menor a mayor severidad):
 * - VERBOSE: Logs muy detallados, solo para desarrollo
 * - DEBUG: Información de debug general
 * - INFO: Información importante del flujo de la app
 * - WARN: Advertencias que no bloquean la ejecución
 * - ERROR: Errores que afectan funcionalidad
 * - NONE: Desactiva todos los logs
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    NONE(Int.MAX_VALUE)
}

object DuoVialLog {
    /**
     * Nivel mínimo de log que se mostrará.
     * Cambiar en runtime para controlar verbosity.
     * Default: INFO para producción, VERBOSE para desarrollo.
     */
    var minLogLevel: LogLevel = LogLevel.INFO
    
    /**
     * Habilita modo desarrollo (todos los logs visibles).
     * Llamar en onCreate de Application para debug builds.
     */
    fun enableDevMode() {
        minLogLevel = LogLevel.VERBOSE
    }
    
    /**
     * Habilita modo producción (solo warnings y errores).
     */
    fun enableProdMode() {
        minLogLevel = LogLevel.WARN
    }
    
    fun v(tag: String, message: String) {
        if (minLogLevel.priority <= LogLevel.VERBOSE.priority) {
            android.util.Log.v(tag, message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (minLogLevel.priority <= LogLevel.DEBUG.priority) {
            android.util.Log.d(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (minLogLevel.priority <= LogLevel.INFO.priority) {
            android.util.Log.i(tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (minLogLevel.priority <= LogLevel.WARN.priority) {
            android.util.Log.w(tag, message)
        }
    }
    
    fun w(tag: String, message: String, throwable: Throwable) {
        if (minLogLevel.priority <= LogLevel.WARN.priority) {
            android.util.Log.w(tag, message, throwable)
        }
    }
    
    fun e(tag: String, message: String) {
        if (minLogLevel.priority <= LogLevel.ERROR.priority) {
            android.util.Log.e(tag, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable) {
        if (minLogLevel.priority <= LogLevel.ERROR.priority) {
            android.util.Log.e(tag, message, throwable)
        }
    }
}
