package com.duovial.platform

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Helper para manejar Google Sign-In.
 *
 * Flujo:
 * 1. showSignIn() abre el selector de cuentas Google
 * 2. El usuario selecciona una cuenta
 * 3. Se obtiene el ID token
 * 4. Se llama a onIdTokenReady con el token
 * 5. El caller usa el token con Supabase Auth
 */
class GoogleSignInHelper(private val context: Context) {

    private val TAG = "DuoVial_GoogleSignIn"

    /**
     * Callback que se ejecuta cuando se obtiene el ID token de Google.
     * @param idToken El ID token a enviar a Supabase Auth
     */
    fun interface OnIdTokenReady {
        fun onIdTokenReady(idToken: String)
    }

    /**
     * Callback que se ejecuta cuando hay un error.
     * @param error Mensaje de error
     */
    fun interface OnError {
        fun onError(error: String)
    }

    /**
     * Crea el cliente de Google Sign-In.
     * @param webClientId ID del cliente web de Google (configurado en Google Cloud Console)
     * @return GoogleSignInClient configurado
     */
    fun createSignInClient(webClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Lanza el intent de Google Sign-In.
     *
     * IMPORTANTE: webClientId debe ser el ID del cliente web (no de Android).
     * Se obtiene en Google Cloud Console > APIs & Services > Credentials.
     *
     * @param activity La activity actual (necesaria para el launcher)
     * @param launcher ActivityResultLauncher para recibir el resultado
     * @param webClientId ID del cliente web de Google
     */
    fun launchSignIn(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<Intent>,
        webClientId: String
    ) {
        try {
            val signInClient = createSignInClient(webClientId)
            val signInIntent = signInClient.signInIntent
            launcher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Google Sign-In: ${e.message}")
        }
    }

    /**
     * Procesa el resultado de Google Sign-In.
     *
     * @param data Intent resultado del selector de cuentas
     * @param onIdTokenReady Callback con el ID token
     * @param onError Callback de error
     */
    fun handleSignInResult(
        data: Intent?,
        onIdTokenReady: OnIdTokenReady,
        onError: OnError
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken

            if (idToken != null) {
                Log.i(TAG, "Google ID token obtenido: ${account.email}")
                onIdTokenReady.onIdTokenReady(idToken)
            } else {
                Log.e(TAG, "No se obtuvo ID token de Google")
                onError.onError("No se pudo obtener el token de Google")
            }
        } catch (e: ApiException) {
            val errorMessage = when (e.statusCode) {
                12501 -> "Inicio de sesión cancelado por el usuario"
                12502 -> "Error de conexión con Google"
                12500 -> "Error interno de Google"
                7 -> "Sin conexión a internet"
                else -> "Error de Google Sign-In: ${e.statusCode}"
            }
            Log.e(TAG, "Google Sign-In error: ${e.statusCode}")
            onError.onError(errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando resultado Google: ${e.message}")
            onError.onError("Error inesperado: ${e.message}")
        }
    }

    /**
     * Cierra la sesión de Google y limpia la cuenta seleccionada.
     * Útil para logout completo.
     */
    fun signOut(webClientId: String, onComplete: () -> Unit = {}) {
        val signInClient = createSignInClient(webClientId)
        signInClient.signOut().addOnCompleteListener {
            Log.i(TAG, "Google Sign-Out completado")
            onComplete()
        }
    }

    companion object {
        // Google Web Client ID se lee de BuildConfig.GOOGLE_WEB_CLIENT_ID
        // (inyectado desde local.properties en tiempo de compilación)
    }
}
