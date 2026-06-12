package com.duovial.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    const val CHANNEL_SERVICE = "duovial_service"
    const val CHANNEL_ALERTS = "duovial_alerts"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "DuoVial Servicio",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificación persistente del servicio de dash cam"
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "DuoVial Alertas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de somnolencia y eventos"
        }

        manager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
    }
}
