package com.duovial

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.duovial.logging.DuoVialLog
import com.duovial.platform.NotificationHelper

class DuoVialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        
        // Configurar nivel de logging según build type
        // DEBUG builds: todos los logs visibles
        // RELEASE builds: solo warnings y errores
        // En KMP no hay BuildConfig, usamos ApplicationInfo para detectar debug
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            DuoVialLog.enableDevMode()
        } else {
            DuoVialLog.enableProdMode()
        }
    }
}
