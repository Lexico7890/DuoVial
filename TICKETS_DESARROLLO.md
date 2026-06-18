# TICKETS DE DESARROLLO — DuoVial MVP

**Versión**: 1.0 | **Fecha**: Junio 16, 2026
**Repositorio**: `C:\Users\camip\Desktop\ocdev\DuoVial`
**Convención**: `[FASE]-[ID]` — Ej: `A-01`, `C-03`

---

## RESUMEN POR SPRINT

| Sprint | Fase | Tickets | Prioridad |
|--------|------|---------|-----------|
| 1-2 | A: Estabilidad | A-01 → A-09 | P0-P1 |
| 3 | B: Onboarding/UX | B-01 → B-03 | P1 |
| 4-5 | C: Supabase | C-01 → C-04 | P1 |
| 5-6 | D: Anti-Somnolencia Avanzada | D-01 → D-04 | P1-P2 |
| 7-8 | E: Monetización | E-01 → E-05 | P2 |
| 9+ | F: Post-MVP | F-01 → F-03 | P2-P3 |

---

# FASE A: ESTABILIDAD DEL VIGILANTE (Sprint 1-2)

---

## A-01 — Migrar cámara frontal de Camera2 a CameraX

| Campo | Valor |
|-------|-------|
| **Tipo** | Refactor |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `FrontFaceDetector.kt`, `FatigueCameraManager.kt`, `FrontCameraPreview.kt`, `BackgroundCameraService.kt` (secciones de fatiga) |
| **Estimación** | 5 puntos |

### Descripción

La implementación actual de cámara frontal usa Camera2 manual con `ImageReader` + `CameraCaptureSession`. Hay un bug crítico donde el preview se congela tras ~1 segundo por mala gestión del `Surface`. Se debe migrar a CameraX usando `Preview` + `ImageAnalysis` para eliminar el manejo manual de sesiones.

### Criterios de Aceptación

- **Dado** que `FatigueScreen` está abierta
  **Cuando** el usuario activa la detección de fatiga
  **Entonces** el preview frontal se muestra fluido sin congelarse en ningún momento.

- **Dado** que la cámara frontal está activa
  **Cuando** el usuario sale de `FatigueScreen` o toca "DETENER"
  **Entonces** la cámara frontal se libera completamente (`ProcessCameraProvider.unbindAll()`).

- **Dado** que la app está en `FatigueScreen` por más de 10 minutos
  **Cuando** se revisa el preview
  **Entonces** sigue funcionando sin degradación de FPS ni memory leaks.

### Notas Técnicas

- Usar `Preview.Builder` + `ImageAnalysis.Builder` con `setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)`.
- Configurar `ImageAnalysis` a 640x480 @ 10fps (`setTargetResolution`, `setBackpressureStrategy`).
- El `FaceProcessor` existente recibe `ImageProxy` — mantener esa interfaz.
- Reutilizar `facialLifecycleOwner` que ya existe en `BackgroundCameraService`.
- Eliminar `FrontFaceDetector.kt` completo una vez migrado.

---

## A-02 — Refactorizar buffer circular a 3 videos (pre + evento + post)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `BackgroundCameraService.kt` (secciones de buffer circular y guardado) |
| **Estimación** | 8 puntos |

### Descripción

Cambiar la lógica actual de 2 segmentos × 15s a 3 videos al detectar evento:

- **Video 1**: Segmento anterior completo (siempre 15s completos).
- **Video 2**: Segmento actual incompleto (lo que llevaba grabado al momento del evento: 2s, 7s, 13s, etc.).
- **Video 3**: Post-evento, configurable por el usuario entre 15s y 30s (ver ticket A-08).

La única excepción es si el evento ocurre justo en el segundo 15 del segmento actual (improbable), donde el Video 2 tendría 15s también.

### Criterios de Aceptación

- **Dado** que el Vigilante está grabando y el segmento actual lleva 7s
  **Cuando** ocurre un evento (botón pánico o G-Force)
  **Entonces** se guardan exactamente 3 archivos: `incident_[ts]_part0.mp4` (15s, segmento anterior), `incident_[ts]_part1.mp4` (~7s, segmento actual), `incident_[ts]_part2.mp4` (duración configurable post-evento).

- **Dado** que el Vigilante ACABA de iniciar (no hay segmento anterior completo)
  **Cuando** ocurre un evento a los 5s de iniciar
  **Entonces** se guardan 2 archivos: `_part0.mp4` (~5s, lo que llevaba) + `_part1.mp4` (post-evento). El segmento anterior no existe, no se crea.

- **Dado** que el evento ocurre en el segundo 14 del segmento actual
  **Cuando** se guarda
  **Entonces** se guardan 3 archivos: `_part0.mp4` (15s anterior), `_part1.mp4` (~14s actual), `_part2.mp4` (post-evento configurable).

- **Dado** que se completa el guardado de los 3 videos
  **Cuando** el buffer circular se reanuda
  **Entonces** la grabación continúa normalmente desde un segmento nuevo limpio.

### Notas Técnicas

- Refactorizar `saveMode` enum y lógica en `saveEvent()` y `savePreEventSegments()`.
- El archivo `segment_post.mp4` se mantiene para el post-evento; su duración viene de `postEventDuration` (ver A-08).
- Eliminar lógica actual de `hasCompletedSegment0`/`hasCompletedSegment1` y reemplazar con tracking de si hay segmento anterior completo.
- La variable `currentRecordingIndex` alterna entre 0 y 1. Al detectar evento, `prevIndex = 1 - currentRecordingIndex` identifica el segmento anterior completo.

---

## A-03 — Stress test buffer circular >4h

| Campo | Valor |
|-------|-------|
| **Tipo** | Testing |
| **Prioridad** | P0 |
| **Dependencias** | A-02 |
| **Archivos afectados** | `BackgroundCameraService.kt` (posibles fixes de bugs encontrados) |
| **Estimación** | 3 puntos |

### Descripción

Ejecutar el modo Vigilante de forma continua durante al menos 4 horas y verificar:

1. No hay crashes ni ANRs.
2. El buffer circular rota correctamente sin memory leaks.
3. La batería consumida es <12%/hora.
4. La temperatura del dispositivo se mantiene estable (<45°C).
5. Después de múltiples eventos guardados, el buffer sigue funcionando.
6. El Foreground Service no es matado por Android.

### Criterios de Aceptación

- **Dado** que el Vigilante está activo por 4+ horas
  **Cuando** se revisan los logs
  **Entonces** no hay `OutOfMemoryError`, `ANR`, ni crashes del servicio.

- **Dado** que se disparan 10+ eventos durante la sesión de 4h
  **Cuando** se verifica `Downloads/DuoVial/`
  **Entonces** cada incidente tiene sus videos correctos y el buffer sigue rotando sin errores.

- **Dado** que el teléfono tiene batería 4000mAh
  **Cuando** han pasado 4h de Vigilante activo
  **Entonces** el consumo total es menor a 48% (12%/hora).

### Notas Técnicas

- Usar `adb shell dumpsys batterystats` para medir consumo real.
- Monitorear heap con Android Profiler durante la sesión.
- Probar en al menos 2 dispositivos: gama media (Oppo A80) y gama alta.
- Si se detectan memory leaks, usar LeakCanary.

---

## A-04 — Verificar Foreground Service en Android 14/15

| Campo | Valor |
|-------|-------|
| **Tipo** | Testing / Bug Fix |
| **Prioridad** | P0 |
| **Dependencias** | A-02 |
| **Archivos afectados** | `BackgroundCameraService.kt`, `AndroidManifest.xml` |
| **Estimación** | 3 puntos |

### Descripción

Android 14+ introduce restricciones adicionales a Foreground Services. Verificar que el servicio sobrevive en background en:

- Android 14 (API 34)
- Android 15 (API 35)
- Escenario: usuario hace swipe para cerrar la app
- Escenario: pantalla apagada por 30+ minutos
- Escenario: batería baja (<15%)
- Escenario: otras apps consumiendo mucha memoria

### Criterios de Aceptación

- **Dado** que el Vigilante está activo en Android 14+
  **Cuando** el usuario hace swipe para cerrar la app
  **Entonces** la notificación permanece y el buffer sigue rotando.

- **Dado** que la pantalla está apagada por 30 minutos
  **Cuando** el usuario enciende la pantalla
  **Entonces** el Vigilante sigue activo y grabando.

- **Dado** que la batería baja del 15%
  **Cuando** Android intenta matar servicios en background
  **Entonces** el Vigilante sobrevive o se reinicia vía WorkManager Watchdog en <30 segundos.

### Notas Técnicas

- Verificar que `android:foregroundServiceType="camera|location|microphone"` cubre todos los casos.
- Para Android 15, verificar `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` si aplica.
- El WorkManager Watchdog debe seguir funcionando con las nuevas restricciones de background work en Android 14+.

---

## A-05 — Reparar EventsScreen con lista real de incidentes

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | A-02 |
| **Archivos afectados** | `EventsScreen.kt` (reescritura), posible nuevo archivo `IncidentRepository.kt` |
| **Estimación** | 5 puntos |

### Descripción

La pantalla de "Incidentes Guardados" actualmente es un placeholder (ícono de carpeta vacía + texto). Debe mostrar una lista real de incidentes encontrados en `Downloads/DuoVial/`, con thumbnail, timestamp, y G-Force. Al tocar un incidente, se abre un reproductor de video (ver B-03).

### Criterios de Aceptación

- **Dado** que hay incidentes en `Downloads/DuoVial/`
  **Cuando** el usuario navega a la pestaña "Eventos"
  **Entonces** ve una lista con cada incidente mostrando: thumbnail del video, fecha/hora formateada, G-Force máxima.

- **Dado** que NO hay incidentes guardados
  **Cuando** el usuario navega a "Eventos"
  **Entonces** ve un estado vacío: ícono + "No hay incidentes guardados aún" + texto explicativo.

- **Dado** que el usuario toca un incidente de la lista
  **Cuando** se navega a la pantalla de detalle
  **Entonces** se reproduce el video (usando el reproductor de B-03).

### Notas Técnicas

- Escanear `MediaStore.Downloads` con `RELATIVE_PATH = "Download/DuoVial"` para listar archivos.
- Agrupar archivos por timestamp (`incident_[ts]_part0`, `_part1`, `_part2`).
- Generar thumbnail con `MediaMetadataRetriever` para `_part0.mp4`.
- Extraer G-Force del nombre del archivo si se incluye, o leer metadata (ver A-05b).
- Opcional: crear `IncidentRepository` para abstraer la lectura de archivos.

---

## A-06 — Reducir cooldown entre eventos de 12s a 5s

| Campo | Valor |
|-------|-------|
| **Tipo** | Bug Fix |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `BackgroundCameraService.kt:653` |
| **Estimación** | 1 punto |

### Descripción

Cambiar la constante `12000` (12 segundos) a `5000` (5 segundos) en `triggerCollisionEvent()`. Esto aplica a ambos triggers (botón y acelerómetro).

### Criterios de Aceptación

- **Dado** que se guardó un evento en el segundo 0
  **Cuando** ocurre otro trigger a los 4 segundos
  **Entonces** el evento se ignora (cooldown activo).

- **Dado** que se guardó un evento en el segundo 0
  **Cuando** ocurre otro trigger a los 6 segundos
  **Entonces** el evento se procesa normalmente.

### Notas Técnicas

- Cambiar `if (now - lastEventTriggerTime < 12000)` → `if (now - lastEventTriggerTime < 5000)`.
- Opcional: hacer el cooldown configurable en Settings (post-MVP).

---

## A-07 — Botón EVENTO en notificación persistente

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `BackgroundCameraService.kt` (métodos `startServiceNotification()`, `updateNotification()`), `NotificationHelper.kt` |
| **Estimación** | 2 puntos |

### Descripción

Agregar una acción "EVENTO" en la notificación persistente del Foreground Service. Esto permite disparar el guardado de incidente sin desbloquear el teléfono ni abrir la app.

### Criterios de Aceptación

- **Dado** que el Vigilante está grabando
  **Cuando** el usuario expande la notificación y toca "EVENTO"
  **Entonces** se dispara `triggerCollisionEvent("Notificacion")` igual que el botón de pánico.

- **Dado** que el Vigilante está en STANDBY
  **Cuando** el usuario expande la notificación
  **Entonces** el botón EVENTO no aparece o aparece deshabilitado.

- **Dado** que la pantalla está bloqueada
  **Cuando** el usuario expande la notificación desde la lock screen
  **Entonces** el botón EVENTO es visible y funcional.

### Notas Técnicas

- Usar `NotificationCompat.Action` con un `PendingIntent` que envíe `ACTION_TRIGGER_PANIC` al servicio.
- La acción debe usar un ícono recognoscible (ej. `android.R.drawable.ic_media_play` o un ícono de alerta).
- Probar en lock screen: Android puede requerir desbloqueo para ciertas acciones si la notificación es "sensitive".

---

## A-08 — Configuración de duración post-evento (15-30s)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | A-02 |
| **Archivos afectados** | `BackgroundCameraService.kt`, `SettingsScreen.kt`, `SettingsManager.kt`, `SettingsManagerAndroid.kt`, `CameraServiceManager.kt` |
| **Estimación** | 3 puntos |

### Descripción

Agregar un slider en Settings para configurar la duración del video post-evento entre 15 y 30 segundos. El valor se persiste y se usa al guardar incidentes.

### Criterios de Aceptación

- **Dado** que el usuario está en Settings
  **Cuando** ajusta "Duración post-evento" a 20s
  **Entonces** los próximos incidentes guardan 20s de video post-evento.

- **Dado** que el usuario configuró post-evento en 30s
  **Cuando** ocurre un evento
  **Entonces** el segmento post-evento graba exactamente 30 segundos.

- **Dado** que el usuario intenta poner <15s o >30s
  **Cuando** ajusta el slider
  **Entonces** el valor se fuerza dentro del rango 15-30s.

- **Dado** que el usuario cierra y reabre la app
  **Cuando** revisa Settings
  **Entonces** la duración post-evento conserva el último valor configurado.

### Notas Técnicas

- Agregar `suspend fun getPostEventDurationMs(): Long` y `suspend fun setPostEventDurationMs(value: Long)` en `SettingsManager`.
- Agregar `fun setPostEventDuration(durationMs: Long)` en `CameraServiceManager`.
- En `BackgroundCameraService`, usar `handler.postDelayed(stopPostEventRunnable, postEventDurationMs)` en lugar de hardcoded `15000`.
- Default: 25 segundos.

---

## A-09 — Detener NO guarda buffer

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `BackgroundCameraService.kt` (método `stopAndSaveBuffer()`), `CameraServiceManager.kt` |
| **Estimación** | 2 puntos |

### Descripción

Actualmente `stopRecording()` llama a `stopAndSaveBuffer()` que guarda el buffer como incidente y luego va a STANDBY. El nuevo comportamiento: `stopRecording()` debe simplemente detener la grabación y volver a STANDBY **sin guardar** el buffer. Solo se guarda mediante EVENTO (botón, burbuja, notificación, o G-Force).

### Criterios de Aceptación

- **Dado** que el Vigilante está grabando
  **Cuando** el usuario toca "Detener" (ícono stop en MonitorScreen)
  **Entonces** el buffer circular se detiene, los segmentos de cache se eliminan, y el servicio vuelve a STANDBY. NO se guarda nada en Downloads.

- **Dado** que el Vigilante está en STANDBY después de detener
  **Cuando** el usuario revisa `Downloads/DuoVial/`
  **Entonces** no hay archivos nuevos (solo incidentes guardados previamente vía EVENTO).

- **Dado** que el Vigilante está grabando
  **Cuando** el usuario toca "EVENTO"
  **Entonces** se guarda el incidente normalmente (este flujo NO cambia).

### Notas Técnicas

- Modificar `stopAndSaveBuffer()` para que NO llame a `savePreEventSegments()`. Solo detenga el buffer, limpie cache, y vaya a STANDBY.
- Alternativa: renombrar el método a `stopRecordingWithoutSaving()` o crear un `ACTION_STOP_RECORDING` separado de `ACTION_STOP_AND_SAVE`.
- El `ACTION_STOP_AND_SAVE` actual se puede mantener para casos donde sí se requiera guardar al detener (post-MVP, opcional).

---

# FASE B: ONBOARDING Y UX (Sprint 3)

---

## B-01 — Pantalla de Onboarding con disclaimer OIS

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | Nuevo: `OnboardingScreen.kt`. Modificar: `App.kt`, `DuoVialApplication.kt` o `MainActivity.kt` |
| **Estimación** | 5 puntos |

### Descripción

Crear un onboarding de 3-4 pantallas que se muestra solo la primera vez que el usuario abre la app (o después de reinstalar). Debe incluir un disclaimer obligatorio sobre el desgaste del estabilizador óptico (OIS) de la cámara y la limitación legal de los videos.

### Criterios de Aceptación

- **Dado** que es la primera vez que el usuario abre DuoVial
  **Cuando** la app inicia
  **Entonces** se muestra el onboarding (no el MonitorScreen).

- **Dado** que el usuario está en el onboarding
  **Cuando** desliza por las pantallas
  **Entonces** ve: (1) "Qué es DuoVial" — dash cam inteligente, (2) "Permisos necesarios" — cámara, ubicación, notificaciones, (3) "Aviso importante" — disclaimer OIS y limitación legal, (4) "Comencemos" — botón final.

- **Dado** que el usuario completa el onboarding
  **Cuando** toca "Comenzar"
  **Entonces** se marca `onboarding_completed = true` en SharedPreferences y se navega a MonitorScreen.

- **Dado** que el usuario ya completó el onboarding
  **Cuando** abre la app de nuevo
  **Entonces** va directo a MonitorScreen (no ve onboarding).

- **Dado** que el usuario está en la pantalla 3 (disclaimer)
  **Cuando** lee el texto
  **Entonces** ve: "DuoVial puede causar desgaste prematuro en el estabilizador óptico (OIS) de la cámara de tu teléfono. Los videos generados no constituyen prueba legal vinculante. Usa la app bajo tu propia responsabilidad."

### Notas Técnicas

- Usar `HorizontalPager` de Compose para las pantallas.
- Persistir `onboarding_completed` con Multiplatform Settings.
- Diseñar cada pantalla con ilustración/icono + título + descripción corta.
- La cuarta pantalla tiene un `Button` "Comenzar" prominente.

---

## B-02 — Solicitud granular de permisos (en contexto)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | B-01 |
| **Archivos afectados** | `Permissions.android.kt`, `MonitorScreen.kt`, `FatigueScreen.kt`, `MainActivity.kt` |
| **Estimación** | 3 puntos |

### Descripción

En lugar de pedir todos los permisos de golpe al iniciar, solicitarlos en el momento exacto en que se necesitan, con una breve explicación de por qué.

### Criterios de Aceptación

- **Dado** que el usuario toca "Iniciar" por primera vez
  **Cuando** no ha otorgado permiso CAMERA
  **Entonces** se muestra el diálogo nativo de cámara con una UI previa explicando: "DuoVial necesita acceder a tu cámara trasera para grabar el buffer circular. No se guarda video sin tu acción."

- **Dado** que el usuario otorgó cámara y toca "Iniciar"
  **Cuando** no ha otorgado ubicación
  **Entonces** se solicita permiso de ubicación explicando: "El GPS permite registrar velocidad y ubicación en tus incidentes."

- **Dado** que el usuario está en FatigueScreen
  **Cuando** toca "ACTIVAR" por primera vez sin permiso de cámara
  **Entonces** se solicita cámara frontal.

- **Dado** que el usuario niega un permiso
  **Cuando** la funcionalidad no puede operar sin él
  **Entonces** se muestra un mensaje claro indicando que esa función no está disponible sin el permiso, con un botón para ir a Settings.

### Notas Técnicas

- Usar `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission)`.
- Para permisos ya denegados permanentemente, abrir `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`.

---

## B-03 — Reproductor de video integrado (ExoPlayer/Media3)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | A-05 |
| **Archivos afectados** | Nuevo: `VideoPlayerScreen.kt` o componente `VideoPlayer.kt`. `build.gradle.kts` (agregar dependencia) |
| **Estimación** | 5 puntos |

### Descripción

Integrar un reproductor de video (Media3/ExoPlayer) para que el usuario pueda ver los videos de incidentes dentro de la app. Debe soportar reproducción, pausa, adelantar/retroceder, y manejar múltiples partes de un mismo incidente.

### Criterios de Aceptación

- **Dado** que el usuario toca un incidente en la lista de Eventos
  **Cuando** se abre el reproductor
  **Entonces** reproduce `_part0.mp4` automáticamente y encadena `_part1.mp4` y `_part2.mp4` si existen.

- **Dado** que el video se está reproduciendo
  **Cuando** el usuario toca la pantalla
  **Entonces** aparecen controles: play/pause, seek bar, tiempo actual/total.

- **Dado** que el usuario arrastra la seek bar
  **Cuando** el video avanza/retrocede
  **Entonces** la posición se actualiza fluidamente.

- **Dado** que el usuario rota el teléfono
  **Cuando** cambia a landscape
  **Entonces** el video se reproduce en fullscreen.

- **Dado** que el incidente tiene 2 o 3 partes
  **Cuando** `_part0.mp4` termina
  **Entonces** se reproduce automáticamente la siguiente parte sin pausa visible.

### Notas Técnicas

- Agregar `androidx.media3:media3-exoplayer` y `androidx.media3:media3-ui` al `build.gradle.kts`.
- Usar `PlayerView` dentro de un `AndroidView` en Compose.
- Para encadenar partes: `ConcatenatingMediaSource` con los archivos del incidente.
- El reproductor debe ser parte de una pantalla de detalle de incidente (puede ser nueva o navegar desde EventsScreen).

---

# FASE C: SUPABASE (Sprint 4-5)

---

## C-01 — Reemplazar AWS Cognito por Supabase Auth

| Campo | Valor |
|-------|-------|
| **Tipo** | Refactor |
| **Prioridad** | P1 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `AuthService.kt` (interfaz se mantiene), `AuthServiceAndroid.kt`, `AuthState.kt`, `AuthLocator.kt`, `LoginScreen.kt`, `DuoVialConfig.kt`, `build.gradle.kts` |
| **Estimación** | 8 puntos |

### Descripción

Reemplazar completamente la implementación de AWS Cognito por Supabase Auth. La interfaz `AuthService` (commonMain) se mantiene igual para no romper la UI. Solo cambia la implementación concreta en `androidMain`.

### Criterios de Aceptación

- **Dado** que el usuario ingresa email + contraseña
  **Cuando** toca "INICIAR SESIÓN"
  **Entonces** se autentica contra Supabase Auth y `AuthStateManager.setUser()` recibe el usuario autenticado.

- **Dado** que el usuario ingresa email + contraseña nueva
  **Cuando** toca "CREAR CUENTA"
  **Entonces** se registra en Supabase y recibe email de confirmación.

- **Dado** que el usuario está en modo CONFIRM
  **Cuando** ingresa el código recibido por email
  **Entonces** se confirma la cuenta y queda logueado.

- **Dado** que el usuario tiene sesión activa
  **Cuando** toca "CERRAR SESIÓN"
  **Entonces** `AuthStateManager.setLoggedOut()` limpia el estado.

- **Dado** que el usuario cerró sesión
  **Cuando** reabre la app
  **Entonces** NO hay sesión persistente a menos que la implementación de Supabase la mantenga vía token refresh.

### Notas Técnicas

- Eliminar dependencias `aws-cognitoidentityprovider` y `aws-cognitoidentity` de `build.gradle.kts`.
- Agregar dependencia de Supabase Kotlin SDK o usar API REST con Ktor (que ya está en el proyecto).
- Opción recomendada: usar Supabase Kotlin Multiplatform SDK si existe, o `io.github.jan-tennert.supabase:gotrue-kt`.
- Mantener `AuthService` como interface en `commonMain`. Crear `SupabaseAuthService` en `androidMain`.
- La persistencia de sesión se maneja con el token JWT de Supabase almacenado en SharedPreferences o DataStore.

---

## C-02 — Login/Registro opcional (sin bloquear uso gratuito)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | C-01 |
| **Archivos afectados** | `AccountScreen.kt`, `App.kt`, `LoginScreen.kt` |
| **Estimación** | 3 puntos |

### Descripción

El login NO debe ser obligatorio para usar el modo Vigilante ni la anti-somnolencia básica. Solo se requiere cuenta para funcionalidades de pago. La app debe funcionar completamente en modo "invitado" para el plan gratuito.

### Criterios de Aceptación

- **Dado** que el usuario NO ha iniciado sesión
  **Cuando** abre la app y toca "Iniciar" en MonitorScreen
  **Entonces** el Vigilante funciona normalmente sin pedir login.

- **Dado** que el usuario NO ha iniciado sesión
  **Cuando** toca "Generar Informe" (post-MVP)
  **Entonces** se le redirige al login/registro con el mensaje: "Crea una cuenta gratuita para obtener tu informe."

- **Dado** que el usuario NO ha iniciado sesión
  **Cuando** navega a la pestaña "Cuenta"
  **Entonces** ve el estado "Sin sesión iniciada" con opción de login, pero sin presión.

- **Dado** que el usuario cierra la app sin loguearse
  **Cuando** la reabre
  **Entonces** sigue en modo invitado con todas las funciones gratuitas disponibles.

### Notas Técnicas

- `AuthService` y `authService` en `App.kt` pueden ser null cuando no hay sesión.
- La app no debe bloquear ninguna funcionalidad gratuita por falta de auth.
- Para funcionalidades premium, verificar `authService?.getCurrentUser() != null`.

---

## C-03 — Supabase Storage para videos de incidentes

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 (post-MVP, pero preparar schema) |
| **Dependencias** | C-01, C-04 |
| **Archivos afectados** | Nuevo: `SupabaseStorageService.kt` o similar. `build.gradle.kts` |
| **Estimación** | 5 puntos |

### Descripción

Configurar Supabase Storage para alojar los videos cuando el usuario paga por exportación. Se debe crear un bucket `incident-videos` con políticas RLS que permitan upload solo al usuario autenticado dueño del incidente.

### Criterios de Aceptación

- **Dado** que un usuario autenticado pagó por un informe
  **Cuando** se sube el video
  **Entonces** el archivo se almacena en `incident-videos/{user_id}/{incident_id}/` y se genera una URL firmada.

- **Dado** que un usuario NO autenticado
  **Cuando** intenta subir un video
  **Entonces** la operación falla con error de autenticación.

- **Dado** que el video se subió exitosamente
  **Cuando** se genera el link compartible
  **Entonces** el link tiene expiración configurable (default 7 días para usuario, 30 días para enterprise).

### Notas Técnicas

- Crear bucket en Supabase Dashboard: `incident-videos`.
- Configurar RLS: `(auth.uid() = owner_id)` para reads y writes.
- Usar Supabase Storage API para upload.
- Para MVP, el upload puede ser directo desde el dispositivo Android.
- Evaluar costo de ancho de banda: video 30s HD ≈ 7-8MB. 100 informes/mes ≈ 800MB.

---

## C-04 — Crear schema de base de datos en Supabase

| Campo | Valor |
|-------|-------|
| **Tipo** | Chore |
| **Prioridad** | P1 |
| **Dependencias** | Ninguna (se puede hacer en paralelo con C-01) |
| **Archivos afectados** | SQL schema (no es código de la app) |
| **Estimación** | 3 puntos |

### Descripción

Definir y crear las tablas en Supabase PostgreSQL para soportar el MVP y estar listos para fases de monetización.

### Schema propuesto

```sql
-- Perfil de usuario (extiende auth.users de Supabase)
CREATE TABLE profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  full_name TEXT,
  plan TEXT DEFAULT 'free', -- 'free', 'paid'
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- Incidentes guardados (metadata, no el video en sí)
CREATE TABLE incidents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
  timestamp TIMESTAMPTZ NOT NULL,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  g_force_max DOUBLE PRECISION,
  speed_kph DOUBLE PRECISION,
  video_parts TEXT[], -- ['part0_url', 'part1_url', 'part2_url']
  report_generated BOOLEAN DEFAULT false,
  report_url TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Sesiones de anti-somnolencia (para plan pago por día)
CREATE TABLE fatigue_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  avg_ear DOUBLE PRECISION,
  total_alerts INTEGER DEFAULT 0,
  closed_eye_duration_total_ms BIGINT DEFAULT 0,
  wearable_data JSONB, -- HR, HRV, movimiento (solo si wearable conectado)
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Pagos (para post-MVP)
CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
  type TEXT NOT NULL, -- 'event_report', 'fatigue_day'
  amount_cop INTEGER NOT NULL, -- en pesos colombianos
  status TEXT DEFAULT 'pending', -- 'pending', 'completed', 'failed'
  incident_id UUID REFERENCES incidents(id), -- nullable (solo si es pago por evento)
  fatigue_session_id UUID REFERENCES fatigue_sessions(id), -- nullable
  payment_method TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
```

### Criterios de Aceptación

- **Dado** que las tablas están creadas en Supabase
  **Cuando** se ejecutan las queries desde la app
  **Entonces** RLS permite solo al usuario dueño leer/escribir sus propios datos.

- **Dado** que un usuario se registra vía Supabase Auth
  **Cuando** se crea el usuario
  **Entonces** un trigger crea automáticamente su perfil en `profiles`.

### Notas Técnicas

- Ejecutar el SQL en Supabase SQL Editor.
- Configurar RLS para todas las tablas: `USING (auth.uid() = user_id)`.
- Crear trigger `on_auth_user_created` para insert automático en `profiles`.
- Para enterprise post-MVP, agregar tabla `fleets` y `fleet_members`.

---

# FASE D: ANTI-SOMNOLENCIA AVANZADA (Sprint 5-6)

---

## D-01 — Health Connect: lectura de datos de wearable

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | A-01 |
| **Archivos afectados** | Nuevo: `HealthConnectManager.kt`. `AndroidManifest.xml`, `build.gradle.kts` |
| **Estimación** | 8 puntos |

### Descripción

Integrar Health Connect API para leer datos de frecuencia cardíaca (FC), variabilidad de frecuencia cardíaca (HRV) y movimiento desde wearables compatibles. Estos datos se usarán para detección de fatiga en background (D-02).

### Criterios de Aceptación

- **Dado** que el usuario tiene un wearable vinculado a Health Connect
  **Cuando** la app solicita permisos de Health Connect
  **Entonces** se muestran los tipos de datos: HeartRate, HeartRateVariability, Steps, SleepSession.

- **Dado** que el usuario otorgó permisos
  **Cuando** el Vigilante está activo
  **Entonces** la app lee FC cada 1 minuto y HRV cada 5 minutos en background.

- **Dado** que el usuario NO otorgó permisos de Health Connect
  **Cuando** inicia el Vigilante
  **Entonces** la app funciona normalmente. Solo se muestra un aviso de que la detección de fatiga por wearable no estará disponible.

- **Dado** que el dispositivo NO tiene Health Connect (Android < 14)
  **Cuando** se intenta acceder a datos de wearable
  **Entonces** se muestra un mensaje indicando que esta función requiere Android 14+.

### Notas Técnicas

- Agregar `androidx.health.connect:connect-client` a `build.gradle.kts`.
- Agregar `<uses-permission android:name="android.permission.health.READ_HEART_RATE"/>` etc.
- Crear `HealthConnectManager` con métodos: `requestPermissions()`, `readHeartRate()`, `readHRV()`, `readSteps()`.
- Usar `HealthConnectClient.readRecords()` con `TimeRangeFilter` para obtener datos recientes.
- Hacer polling cada 1-5 minutos (no en tiempo real, para ahorrar batería).
- Los datos se mantienen SOLO en memoria local. No se persisten ni se suben sin consentimiento.

---

## D-02 — Lógica de detección de fatiga por wearable

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | D-01 |
| **Archivos afectados** | Nuevo: `WearableFatigueDetector.kt`. `BackgroundCameraService.kt` |
| **Estimación** | 5 puntos |

### Descripción

Implementar un detector de fatiga basado en datos de wearable que funcione en background (sin necesidad de cámara frontal activa). Usa tendencias de FC, HRV y movimiento para determinar si el conductor muestra signos de somnolencia.

### Criterios de Aceptación

- **Dado** que el wearable reporta FC sostenidamente baja (<55bpm en reposo para adulto promedio) y HRV elevada
  **Cuando** esta condición persiste por >5 minutos
  **Entonces** se emite una alerta de fatiga (vibración + sonido) incluso si FatigueScreen no está abierta.

- **Dado** que el wearable reporta falta de movimiento por >10 minutos
  **Cuando** la FC también muestra patrones de reposo
  **Entonces** se eleva el score de riesgo de fatiga.

- **Dado** que se emitió una alerta de fatiga por wearable
  **Cuando** el usuario reanuda movimiento (Steps detectado)
  **Entonces** el score de fatiga se resetea.

- **Dado** que NO hay datos de wearable (sin permisos o sin dispositivo)
  **Cuando** se evalúa fatiga en background
  **Entonces** esta fuente simplemente no contribuye (no hay error).

### Notas Técnicas

- Implementar un scoring system simple: peso(FC) + peso(HRV) + peso(movimiento) → score 0-100.
- Umbral de alerta configurable. Fase inicial como "beta" con disclaimer.
- Recolectar datos anónimos para calibrar umbrales (con consentimiento explícito post-MVP).
- Integrar con `BackgroundCameraService` existente: `statusListener?.onDrowsinessDetected(...)`.

---

## D-03 — Detección de cámaras concurrentes al iniciar

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | A-01 |
| **Archivos afectados** | `BackgroundCameraService.kt`, `FatigueScreen.kt` |
| **Estimación** | 3 puntos |

### Descripción

Al iniciar la app, consultar `CameraManager.getConcurrentCameraIds()` para determinar si el dispositivo soporta usar la cámara trasera y frontal simultáneamente. Si no, informar al usuario y pausar el Vigilante al entrar a FatigueScreen.

### Criterios de Aceptación

- **Dado** que el dispositivo SÍ soporta cámaras concurrentes
  **Cuando** el usuario abre FatigueScreen y activa la cámara frontal
  **Entonces** el Vigilante sigue grabando en background sin interrupción.

- **Dado** que el dispositivo NO soporta cámaras concurrentes
  **Cuando** el usuario abre FatigueScreen
  **Entonces** se muestra un mensaje: "Tu dispositivo no permite usar ambas cámaras al mismo tiempo. El Vigilante se pausará mientras uses la cámara frontal."

- **Dado** que el dispositivo no soporta cámaras concurrentes
  **Cuando** el usuario activa la cámara frontal
  **Entonces** el Vigilante se pausa automáticamente. Al salir de FatigueScreen, se ofrece reanudar.

- **Dado** que el dispositivo no soporta cámaras concurrentes
  **Cuando** se muestra el mensaje
  **Entonces** se sugiere usar un wearable para detección de fatiga en background sin interrumpir el Vigilante.

### Notas Técnicas

- `cameraManager.concurrentCameraIds` devuelve `Set<Set<String>>` con los pares de cámaras que pueden abrirse juntas.
- Verificar si `(backCameraId, frontCameraId)` está en algún set.
- Cachear el resultado para no consultarlo cada vez.

---

## D-04 — Pantalla de sugerencia de wearables compatibles

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | D-01 |
| **Archivos afectados** | Nuevo: `WearableRecommendationScreen.kt` o integrado en Settings/Onboarding |
| **Estimación** | 2 puntos |

### Descripción

Mostrar una lista de dispositivos wearable recomendados y compatibles con Health Connect/Google Fit, con links de búsqueda en Mercado Libre Colombia para facilitar la compra.

### Criterios de Aceptación

- **Dado** que el usuario está en Settings o en FatigueScreen
  **Cuando** toca "Wearables compatibles"
  **Entonces** ve una lista con marcas/modelos, rango de precio estimado en COP, y nivel de compatibilidad.

- **Dado** que el usuario toca un dispositivo de la lista
  **Cuando** se abre el link
  **Entonces** se abre una búsqueda en Mercado Libre Colombia en el navegador.

### Notas Técnicas

- La lista de dispositivos viene de `CONTEXT.md` (sección Anti-Somnolencia y Wearables).
- Usar `Intent(Intent.ACTION_VIEW, Uri.parse(url))` para abrir las URLs.
- Mantener la lista como un recurso estático en un archivo JSON o data class.

---

# FASE E: MONETIZACIÓN (Sprint 7-8) — POST-MVP

---

## E-01 — Integrar pasarela de pagos para Colombia

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-01 |
| **Archivos afectados** | Nuevo: `PaymentService.kt`, `PaymentScreen.kt`. `build.gradle.kts` |
| **Estimación** | 8 puntos |

### Descripción

Integrar una pasarela de pagos que soporte métodos colombianos: tarjeta débito/crédito, PSE, Nequi. Opciones: Wompi (Bancolombia), PayU Latam, Stripe (con soporte limitado en Colombia). El pago se procesa, se verifica, y se desbloquea la funcionalidad correspondiente.

### Criterios de Aceptación

- **Dado** que un usuario quiere pagar por un informe (20,000 COP)
  **Cuando** selecciona método de pago y completa la transacción
  **Entonces** el pago se procesa exitosamente y se desbloquea la generación del informe.

- **Dado** que un usuario quiere pagar por un día de anti-somnolencia (7,000 COP)
  **Cuando** completa el pago
  **Entonces** se activa el dashboard de métricas por 24h desde el momento del pago.

- **Dado** que el pago falla (fondos insuficientes, error de red)
  **Cuando** la pasarela devuelve error
  **Entonces** se muestra un mensaje claro al usuario y NO se genera el informe.

- **Dado** que el pago fue exitoso
  **Cuando** se registra en Supabase (`payments` table)
  **Entonces** el incidente queda marcado como `report_generated = true`.

### Notas Técnicas

- Investigar SDK de Wompi para Android (tiene soporte para Nequi y PSE).
- Flujo: App → `PaymentService.createTransaction()` → SDK de pasarela → webhook/verify → Supabase.
- Almacenar `payment_id` de la pasarela para reconciliación.
- El webhook de confirmación debe ser manejado por Supabase Edge Functions o un endpoint de Ktor.
- Para MVP de monetización, evaluar si la pasarela permite checkout web en WebView.

---

## E-02 — Generación de informe PDF completo

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | E-01, C-03 |
| **Archivos afectados** | Nuevo: `ReportGenerator.kt`. `build.gradle.kts` (librería PDF) |
| **Estimación** | 8 puntos |

### Descripción

Al confirmarse el pago, generar un PDF que contenga: fecha/hora del incidente, coordenadas GPS, mapa estático (Google Static Maps API), gráfico de G-Force durante el evento, velocidad al momento, y metadata del dispositivo. Adjuntar links de los videos en Supabase Storage.

### Criterios de Aceptación

- **Dado** que el pago por un incidente fue exitoso
  **Cuando** se genera el informe
  **Entonces** el PDF contiene: (1) fecha y hora formateada, (2) ubicación en texto (ej. "Calle 80 #45-23, Bogotá") y coordenadas, (3) mapa estático con marcador en la ubicación, (4) gráfico simple de G-Force, (5) velocidad en km/h.

- **Dado** que el PDF se generó correctamente
  **Cuando** se sube a Supabase Storage
  **Entonces** el usuario recibe el link de descarga.

- **Dado** que la generación falla (sin internet, error de API)
  **Cuando** ocurre el error
  **Entonces** se notifica al usuario y NO se cobra (el pago se reversa o no se procesa).

### Notas Técnicas

- Usar `android.graphics.pdf.PdfDocument` nativo de Android (sin librería externa) para el PDF base.
- Para el mapa estático: `https://maps.googleapis.com/maps/api/staticmap?center={lat},{lon}&zoom=15&size=600x300&markers=color:red%7C{lat},{lon}&key=API_KEY`. Requiere Google Maps API key.
- Para el gráfico G-Force: dibujar con `Canvas` de Android sobre un `Bitmap` y embeberlo en el PDF.
- Coordenadas → dirección: usar Geocoder de Android (`getFromLocation()`) o Google Geocoding API.
- El PDF se genera localmente y se sube a Supabase Storage.

---

## E-03 — Flujo de pago por evento (20,000 COP)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | E-01, E-02, C-03 |
| **Archivos afectados** | `EventsScreen.kt` (nuevo botón "Obtener Informe"), `IncidentDetailScreen.kt` |
| **Estimación** | 3 puntos |

### Descripción

Integrar el flujo completo: usuario ve el video gratis → toca "Obtener Informe Completo (20,000 COP)" → login/registro (si no logueado) → pago → generación de informe PDF → upload a Supabase → link compartible.

### Criterios de Aceptación

- **Dado** que el usuario está viendo un incidente (video gratis)
  **Cuando** toca "Obtener Informe Completo - $20,000 COP"
  **Entonces** se inicia el flujo de pago. Si no está logueado, se le pide crear cuenta/login primero.

- **Dado** que el pago fue exitoso
  **Cuando** se genera el informe
  **Entonces** el usuario ve una pantalla de confirmación con el link para compartir y opción de copiarlo.

- **Dado** que el usuario ya pagó por este incidente anteriormente
  **Cuando** vuelve a ver el mismo incidente
  **Entonces** el botón muestra "Ver Informe" en lugar de "Obtener Informe", y abre el PDF existente.

- **Dado** que el usuario cancela el pago a mitad del flujo
  **Cuando** vuelve a la app
  **Entonces** el incidente sigue accesible en modo gratuito (video sin informe).

### Notas Técnicas

- El estado de pago por incidente se consulta contra Supabase: `SELECT report_generated FROM incidents WHERE id = ?`.
- Si `report_generated = true`, no se ofrece pagar de nuevo.
- El link de Supabase Storage puede ser una signed URL con expiración.

---

## E-04 — Flujo de pago por día anti-somnolencia (7,000 COP)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | E-01, D-02, C-04 |
| **Archivos afectados** | `FatigueScreen.kt`, `SettingsScreen.kt`, nuevo: `FatigueDashboardScreen.kt` |
| **Estimación** | 5 puntos |

### Descripción

Permitir al usuario pagar 7,000 COP por acceso a métricas avanzadas de fatiga durante 24 horas. Las métricas combinan datos de cámara frontal + wearable si ambos están disponibles.

### Criterios de Aceptación

- **Dado** que el usuario está en FatigueScreen (plan gratuito)
  **Cuando** toca "Ver Métricas - $7,000 COP/día"
  **Entonces** se inicia flujo de pago. Al completar, se desbloquea `FatigueDashboardScreen` por 24h.

- **Dado** que el usuario pagó por el día
  **Cuando** abre el dashboard
  **Entonces** ve: promedio EAR del día, total de alertas, tiempo total con ojos cerrados, gráfico de fatiga por hora, datos de wearable (FC promedio, HRV) si disponible.

- **Dado** que pasaron 24h desde el pago
  **Cuando** el usuario intenta acceder al dashboard
  **Entonces** se bloquea y se ofrece pagar de nuevo.

- **Dado** que el usuario usó solo wearable (sin cámara frontal) durante el período pago
  **Cuando** ve el dashboard
  **Entonces** solo se muestran las métricas disponibles del wearable.

### Notas Técnicas

- Crear `fatigue_sessions` en Supabase al inicio del período pago con `started_at`.
- El dashboard se alimenta de datos locales almacenados durante las 24h.
- Persistir datos localmente con Room o DataStore durante el período activo.
- El conteo de 24h es server-side (Supabase) para evitar manipulación de reloj del dispositivo.

---

## E-05 — Dashboard de métricas de fatiga (móvil)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | E-04, D-02 |
| **Archivos afectados** | Nuevo: `FatigueDashboardScreen.kt` |
| **Estimación** | 5 puntos |

### Descripción

Pantalla de dashboard que muestra métricas combinadas de fatiga (cámara frontal + wearable) durante el período de pago activo. Diseño visual claro con gráficos de barras, indicadores y resumen.

### Criterios de Aceptación

- **Dado** que el usuario tiene acceso pago activo
  **Cuando** abre el dashboard
  **Entonces** ve:
  - **Indicador principal**: estado actual (Alerta / Normal / Fatiga) con código de color.
  - **EAR promedio**: valor numérico grande + mini gráfico de tendencia (últimos 15 min).
  - **Alertas hoy**: número total + hora de cada alerta.
  - **Tiempo ojos cerrados**: acumulado en minutos.
  - **Wearable (si disponible)**: FC actual, FC promedio, HRV, movimiento.
  - **Gráfico de fatiga por hora**: barras mostrando intensidad de fatiga por cada hora del día.

- **Dado** que el período de pago expiró
  **Cuando** el usuario abre el dashboard
  **Entonces** ve un resumen congelado del último período + botón para pagar de nuevo.

- **Dado** que no hay datos suficientes (menos de 10 minutos de uso)
  **Cuando** el usuario abre el dashboard
  **Entonces** se muestra "Recolectando datos..." con indicador de progreso.

### Notas Técnicas

- Usar `Canvas` de Compose para gráficos de barras simples (sin librería externa).
- Los datos se alimentan de `FatigueDataRepository` que guarda snapshots periódicos durante la sesión.
- Actualizar el dashboard cada 5 segundos mientras está visible.
- Para gráficos más complejos (líneas de tendencia), evaluar `Vico` (librería de charts para Compose Multiplatform).

---

# FASE F: POST-MVP ADICIONALES (Sprint 9+)

---

## F-01 — Geo-fencing: alertas de perímetro para empresas

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P3 |
| **Dependencias** | C-01, C-04 (tabla `fleets`) |
| **Archivos afectados** | Nuevo: `GeofenceManager.kt`. Dashboard web (fuera del scope de la app Android). |
| **Estimación** | 8 puntos |

### Descripción

Permitir que un administrador de flota configure zonas geográficas permitidas para cada vehículo. Si el GPS del dispositivo sale de la zona configurada, se envía una notificación push al admin.

### Criterios de Aceptación

- **Dado** que un admin configuró una zona (radio o polígono) para un vehículo
  **Cuando** el GPS del dispositivo sale de esa zona
  **Entonces** el admin recibe una notificación push en tiempo real.

- **Dado** que el vehículo está fuera de la zona
  **Cuando** el admin revisa el dashboard
  **Entonces** ve la ubicación actual del vehículo y un registro de cuándo salió de la zona.

- **Dado** que no hay conexión a internet
  **Cuando** el vehículo sale de la zona
  **Entonces** la alerta se encola y se envía cuando se recupera la conexión.

### Notas Técnicas

- Usar `GeofencingClient` de Google Play Services en el dispositivo.
- Las zonas se sincronizan desde Supabase al iniciar la app.
- La notificación push al admin se puede implementar con FCM + Supabase Edge Functions.
- Esta funcionalidad requiere un dashboard web (fuera del scope de la app móvil).

---

## F-02 — Detección de estado de salud de alto riesgo (post-MVP)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P3 |
| **Dependencias** | D-01, D-02 |
| **Archivos afectados** | `WearableFatigueDetector.kt` (extensión), nuevo: `HealthRiskDetector.kt` |
| **Estimación** | 8 puntos |

### Descripción

Extender la detección por wearable para identificar patrones asociados con intoxicación o resaca: FC elevada en reposo, HRV anormalmente baja, temperatura periférica alterada, patrones de movimiento erráticos. La app alerta: "Tu estado actual de salud indica alto riesgo de accidente. Recomendamos no conducir." — sin mencionar alcohol.

### Criterios de Aceptación

- **Dado** que el wearable reporta FC en reposo >100bpm sostenida por >10 minutos y HRV baja
  **Cuando** el score de riesgo de salud supera el umbral configurable
  **Entonces** se emite una alerta silenciosa en la app (no sonora) con el mensaje de advertencia.

- **Dado** que se emitió la alerta de salud
  **Cuando** los signos vitales vuelven a la normalidad
  **Entonces** la alerta se desactiva automáticamente.

- **Dado** que la funcionalidad está en fase beta
  **Cuando** se emite una alerta
  **Entonces** se incluye un disclaimer: "Esta detección está en fase experimental. No sustituye una evaluación médica."

### Notas Técnicas

- Requiere calibración de umbrales con datos reales (posiblemente en colaboración con una universidad o estudio).
- Métricas combinadas: FC reposo > 100bpm + HRV < 20ms (valores de referencia, sujetos a calibración).
- Los datos de temperatura periférica requieren wearables con sensor de temperatura (limitado).
- Fase inicial: recolectar datos anónimos (con consentimiento) para entrenar umbrales.

---

## F-03 — Dashboard Enterprise (web, tiempo real, multi-vehículo)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P3 |
| **Dependencias** | C-01, C-04, E-01 |
| **Archivos afectados** | Proyecto separado: web app (React/Next.js o similar). Cambios en app Android para telemetría en tiempo real. |
| **Estimación** | 20+ puntos |

### Descripción

Dashboard web para administradores de flota que muestra en tiempo real: ubicación de todos los vehículos, estado del Vigilante (activo/inactivo), incidentes recientes, métricas de fatiga de cada conductor, y alertas de perímetro. Precio según tamaño de flota.

### Criterios de Aceptación

- **Dado** que un admin tiene vehículos activos
  **Cuando** abre el dashboard
  **Entonces** ve un mapa con la ubicación en tiempo real de cada vehículo (marcadores con color según estado).

- **Dado** que un vehículo genera un incidente
  **Cuando** el admin está en el dashboard
  **Entonces** recibe una notificación inmediata con resumen del incidente y acceso al informe completo.

- **Dado** que el admin selecciona un vehículo
  **Cuando** ve el detalle
  **Entonces** accede a: historial de incidentes, métricas de fatiga del conductor, rutas recientes, alertas de perímetro.

### Notas Técnicas

- Web app: React/Next.js + Supabase realtime subscriptions.
- Android: enviar telemetría periódica (ubicación, estado) a Supabase cada 30s-1min mientras el Vigilante está activo.
- Usar `Supabase Realtime` (WebSockets) para actualizaciones en vivo.
- Pricing: basado en número de vehículos activos (a definir con stakeholder).

---

## RESUMEN: ESTIMACIONES TOTALES POR FASE

| Fase | Tickets | Puntos totales | Prioridad |
|------|---------|---------------|-----------|
| A: Estabilidad | 9 | 32 puntos | P0-P1 |
| B: Onboarding/UX | 3 | 13 puntos | P1 |
| C: Supabase | 4 | 19 puntos | P1-P2 |
| D: Anti-Somnolencia Avanzada | 4 | 18 puntos | P1-P2 |
| E: Monetización | 5 | 29 puntos | P2 |
| F: Post-MVP | 3 | 36+ puntos | P2-P3 |
| **TOTAL MVP (A+B)** | **12** | **45 puntos** | |
| **TOTAL MVP Completo (A+B+C+D)** | **20** | **82 puntos** | |
| **TOTAL con Monetización** | **25** | **111 puntos** | |

---

*Documento generado como fuente única para tickets de desarrollo. Cualquier cambio en alcance debe reflejarse aquí.*
