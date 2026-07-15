package com.duovial.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {
    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun allRequired(): Array<String> = requiredPermissions.toTypedArray()

    fun areAllGranted(context: Context): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verifica si un permiso específico está concedido.
     * @param context Contexto de Android
     * @param permissionKey Clave del permiso (ej: "CAMERA", "ACCESS_FINE_LOCATION")
     * @return true si el permiso está concedido
     */
    fun isPermissionGranted(context: Context, permissionKey: String): Boolean {
        val manifestPermission = mapPermissionKeyToManifest(permissionKey) ?: return false
        return ContextCompat.checkSelfPermission(context, manifestPermission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Retorna un mapa con el estado de todos los permisos relevantes.
     * Útil para mostrar el estado en la UI (SettingsScreen, OnboardingScreen).
     */
    fun getAllPermissionStatuses(context: Context): Map<String, Boolean> {
        return mapOf(
            "CAMERA" to isPermissionGranted(context, "CAMERA"),
            "ACCESS_FINE_LOCATION" to isPermissionGranted(context, "ACCESS_FINE_LOCATION"),
            "POST_NOTIFICATIONS" to isPermissionGranted(context, "POST_NOTIFICATIONS"),
            "SYSTEM_ALERT_WINDOW" to canDrawOverlays(context),
            "ACTIVITY_RECOGNITION" to isPermissionGranted(context, "ACTIVITY_RECOGNITION"),
            "BLUETOOTH_CONNECT" to isPermissionGranted(context, "BLUETOOTH_CONNECT")
        )
    }

    /**
     * Mapea una clave de permiso (usada en la UI) a su equivalente en AndroidManifest.
     */
    fun mapPermissionKeyToManifest(permissionKey: String): String? {
        return when (permissionKey) {
            "CAMERA" -> Manifest.permission.CAMERA
            "ACCESS_FINE_LOCATION" -> Manifest.permission.ACCESS_FINE_LOCATION
            "ACCESS_COARSE_LOCATION" -> Manifest.permission.ACCESS_COARSE_LOCATION
            "POST_NOTIFICATIONS" -> {
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
            }
            "ACTIVITY_RECOGNITION" -> {
                if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACTIVITY_RECOGNITION else null
            }
            "BLUETOOTH_CONNECT" -> {
                if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
            }
            else -> null
        }
    }

    /**
     * Retorna los permisos de Android Manifest que deben solicitarse para las claves dadas.
     */
    fun mapKeysToManifestPermissions(keys: List<String>): Array<String> {
        return keys.mapNotNull { mapPermissionKeyToManifest(it) }.toTypedArray()
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /**
     * Abre la configuración de permisos de la app en Android.
     * Útil cuando el usuario denegó un permiso y marcó "No preguntar de nuevo".
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}
