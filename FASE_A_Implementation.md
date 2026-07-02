# FASE A — Mejoras al Modo Vigilante (BackgroundCameraService)

**Fecha**: Julio 2, 2026
**Objetivo**: Análisis técnico, validación de mejores prácticas, y especificaciones de mejora para el servicio BackgroundCameraService.
**Alcance**: Solo especificaciones y análisis. No contiene código de implementación. El código es escrito por el agente que implemente cada funcionalidad.
**Estado**: La mayoría de la funcionalidad ya está implementada. Este documento analiza qué está bien, qué tiene inconsistencias, y qué falta agregar.
**Dependencias**: BackgroundCameraService.kt, CameraServiceManagerAndroid.kt, AppState.kt, MonitorScreen.kt, SettingsScreen.kt, NotificationHelper.kt, FaceProcessor.kt, FatigueCameraManager.kt

---

## TABLA DE CONTENIDOS

1. [Resumen de Cambios](#1-resumen-de-cambios)
2. [Inconsistencias Encontradas en la Implementación Actual](#2-inconsistencias-encontradas-en-la-implementación-actual)
3. [A: Burbuja Flotante — Visibilidad Condicional + Indicador en Configurar](#3-a-burbuja-flotante--visibilidad-condicional--indicador-en-configurar)
4. [B: Ciclo de Vida del Vigilante — Segundo Plano vs App Cerrada](#4-b-ciclo-de-vida-del-vigilante--segundo-plano-vs-app-cerrada)
5. [C: Filtro de Velocidad para Eventos del Acelerómetro](#5-c-filtro-de-velocidad-para-eventos-del-acelerómetro)
6. [D: Validación de Rendimiento y Optimizaciones](#6-d-validación-de-rendimiento-y-optimizaciones)
7. [E: Monitoreo de Temperatura del Dispositivo](#7-e-monitoreo-de-temperatura-del-dispositivo)
8. [F: Botón de Evento en Pantalla de Bloqueo](#8-f-botón-de-evento-en-pantalla-de-bloqueo)
9. [G: Auto-Inicio del Vigilante a 30 km/h](#9-g-auto-inicio-del-vigilante-a-30-kmh)
10. [H: Borrado Automático de Videos Mayores a 72 Horas](#10-h-borrado-automático-de-videos-mayores-a-72-horas)
11. [Matriz de Impacto por Archivo](#11-matriz-de-impacto-por-archivo)
12. [Priorización y Orden de Implementación](#12-priorización-y-orden-de-implementación)

---

## 1. RESUMEN DE CAMBIOS

| # | Requerimiento | Archivos Principales | Complejidad | Prioridad |
|---|--------------|----------------------|-------------|-----------|
| A | Burbuja Flotante condicional + indicador en Configurar | BackgroundCameraService.kt, SettingsScreen.kt, AppState.kt | Baja | Alta |
| B | Vigilante: segundo plano sí, app cerrada no | BackgroundCameraService.kt, CameraServiceManagerAndroid.kt, DuoVialApplication.kt | Media | Alta |
| C | Filtro de velocidad >30 km/h para eventos del acelerómetro | BackgroundCameraService.kt | Baja | Alta |
| D | Validación de rendimiento y optimizaciones | BackgroundCameraService.kt | Media | Media |
| E | Monitoreo de temperatura (50°C threshold) | BackgroundCameraService.kt, AppState.kt, MonitorScreen.kt, CameraServiceManager.kt | Media | Media |
| F | Botón de "Registrar Evento" en notificación de pantalla de bloqueo | BackgroundCameraService.kt, NotificationHelper.kt | Baja | Alta |
| G | Auto-inicio del Vigilante a 30 km/h (configurable) | BackgroundCameraService.kt, SettingsScreen.kt, SettingsManager.kt, NotificationHelper.kt | Media | Media |
| H | Borrado automático de videos mayores a 72 horas | IncidentRepository.kt, MainActivity.kt | Baja | Alta |

---

## 2. INCONSISTENCIAS ENCONTRADAS EN LA IMPLEMENTACIÓN ACTUAL

Esta sección documenta problemas, inconsistencias y riesgos encontrados al analizar el código actual. No son mejoras nuevas, sino correcciones de lo que ya existe.

### 2.1 Problemas de Riesgo Alto

| # | Problema | Archivo | Línea | Descripción |
|---|---------|---------|-------|-------------|
| I1 | `activePreviewView` no se limpia en `onDestroy` | BackgroundCameraService.kt | 298-315 | En `onDestroy()` se hace `instance = null` y `activePreview = null`, pero `activePreviewView` solo se limpia en `onPreviewViewDropped()`. Si el servicio se destruye sin pasar por `onPreviewViewDropped()`, la referencia estática al `PreviewView` permanece, causando un memory leak. **Corrección**: Agregar `activePreviewView = null` en `onDestroy()`. |
| I2 | `pendingPreviewView` y `pendingFrontPreviewView` no se limpian en `onDestroy` | BackgroundCameraService.kt | 298-315 | Los campos estáticos `pendingPreviewView` y `pendingFrontPreviewView` del companion object no se limpian al destruir el servicio. **Corrección**: Agregar limpieza explícita en `onDestroy()`. |
| I3 | Handler callbacks pueden ejecutarse después de `onDestroy` | BackgroundCameraService.kt | 95, 298-315 | El `handler` del main looper puede tener `rotateRunnable` o `stopPostEventRunnable` pendientes cuando el servicio se destruye. Aunque `stopCircularBufferTimers()` se llama en `onDestroy()`, si el servicio se destruye por el sistema (no por `stopSelf()`), puede haber una ventana donde el runnable se ejecuta después de que las referencias son nulas. **Corrección**: Agregar null-checks en los runnables, o usar un flag `isDestroyed` que se verifique antes de ejecutar. |

### 2.2 Problemas de Riesgo Medio

| # | Problema | Archivo | Línea | Descripción |
|---|---------|---------|-------|-------------|
| I4 | `copyFileToDownloads` ejecuta I/O en main thread | BackgroundCameraService.kt | 806-841 | La función `copyFileToDownloads()` lee archivos de ~4-6 MB desde cacheDir y los escribe a MediaStore usando `FileInputStream` y `OutputStream`. Esta operación se ejecuta en el main thread (a través del handler). Si el almacenamiento es lento, puede bloquear la UI por 1-2 segundos justo cuando ocurre un evento — el peor momento para un lag. **Corrección**: Mover la operación de copia a un coroutine en `Dispatchers.IO` o un `ExecutorService` dedicado. |
| I5 | Notificación no se actualiza al cambiar de STANDBY a RECORDING | BackgroundCameraService.kt | 489-504 | La función `updateNotification()` se llama en `startStandbyMode()` y `startRecordingMode()`, pero solo actualiza el texto. No verifica si el estado cambió para agregar o quitar acciones de la notificación. Esto es relevante para el requerimiento F (botón de pantalla de bloqueo). **Corrección**: `updateNotification()` debe reconstruir la notificación completa con o sin acciones según el estado. |
| I6 | Sin verificación de capacidad de cámara antes de iniciar | BackgroundCameraService.kt | 520-567 | `startCameraX()` solicita `Quality.HD` (1080p) sin verificar si el dispositivo soporta esa resolución. En dispositivos gama baja, esto puede causar un crash o un fallback silencioso a una calidad inferior. **Corrección**: Verificar las capacidades de la cámara antes de configurar el `QualitySelector`. Si 1080p no está soportado, usar 720p como fallback. |

### 2.3 Problemas de Riesgo Bajo

| # | Problema | Archivo | Línea | Descripción |
|---|---------|---------|-------|-------------|
| I7 | Audio no desactivado explícitamente en CameraX | BackgroundCameraService.kt | 526-529 | El `Recorder` de CameraX graba audio por defecto a menos que se configure explícitamente lo contrario. El código actual no desactiva el audio, lo que desperdicia CPU para procesar audio que nunca se usa y aumenta el tamaño de los archivos de video. **Corrección**: Configurar el `Recorder` para desactivar el audio. |
| I8 | Bitrate no configurado explícitamente | BackgroundCameraService.kt | 526-529 | El bitrate de CameraX por defecto es aproximadamente 5 Mbps para 1080p@30fps. El CONTEXT.md del proyecto especifica 2 Mbps como target. Sin configurar el bitrate explícitamente, se usan 2.5x más datos de los necesarios, lo que aumenta la temperatura del dispositivo y el desgaste del almacenamiento. **Corrección**: Configurar el bitrate a 2 Mbps en el `Recorder`. |
| I9 | Logs verbosos en producción | BackgroundCameraService.kt | Múltiples líneas | El servicio tiene muchos `Log.d()` que son útiles para desarrollo pero generan overhead de I/O en logcat durante operación normal. En producción, estos logs deberían ser `Log.v()` o eliminarse. **Corrección**: Cambiar `Log.d` verbosos a `Log.v` o eliminarlos, mantener `Log.i` y `Log.w`. |

---

## 3. A: BURBUJA FLOTANTE — VISIBILIDAD CONDICIONAL + INDICADOR EN CONFIGURAR

### 3.1 Estado Actual

La burbuja flotante está implementada en `BackgroundCameraService.kt`. La función `showFloatingBubble()` crea un `View` circular arrastrable en `WindowManager` que, al tocarse, ejecuta `triggerCollisionEvent()`.

**Problema principal**: La burbuja se muestra siempre que el servicio se crea, sin importar si el usuario está en modo STANDBY o RECORDING. La llamada a `showFloatingBubble()` está en `onCreate()` (línea 252-254), lo que significa que la burbuja aparece inmediatamente al abrir la app — incluso si el usuario no ha presionado "Iniciar" en el MonitorScreen.

**Estado del toggle en SettingsScreen**: El botón "AUTORIZAR BURBUJA FLOTANTE" (líneas 98-106 de SettingsScreen.kt) solo abre la configuración de permisos de Android para `SYSTEM_ALERT_WINDOW`. No gestiona el estado de visibilidad de la burbuja ni muestra si el permiso ya fue concedido.

### 3.2 Comportamiento Deseado

La burbuja debe cumplir dos condiciones simultáneas para mostrarse:

1. **Condición 1 — Permiso del sistema**: El usuario ha otorgado el permiso `SYSTEM_ALERT_WINDOW` (overlay) a DuoVial.
2. **Condición 2 — Vigilante activo**: El servicio está en modo RECORDING (grabación circular activa).

Si ambas condiciones se cumplen → la burbuja se muestra.
Si alguna condición no se cumple → la burbuja no se muestra.

**Comportamiento específico**:

| Escenario | ¿Burbuja visible? | Razón |
|-----------|-------------------|-------|
| App abierta, Vigilante en STANDBY, permiso concedido | NO | El Vigilante no está corriendo — la burbuja no tiene sentido |
| App abierta, Vigilante en RECORDING, permiso concedido | SÍ | Ambas condiciones cumplidas |
| App en background, Vigilante en RECORDING, permiso concedido | SÍ | La burbuja debe seguir visible sobre otras apps |
| App abierta, Vigilante en RECORDING, permiso NO concedido | NO | No se puede mostrar sin permiso |
| Vigilante se detiene (STANDBY) | NO (ocultar) | El Vigilante dejó de correr |

**Indicador en SettingsScreen**: La tarjeta de "Burbuja Flotante de Panico" debe mostrar el estado actual del permiso del sistema:
- Si el permiso `SYSTEM_ALERT_WINDOW` está concedido: mostrar un indicador verde con texto "Habilitada en el sistema" o similar.
- Si el permiso NO está concedido: mostrar un indicador gris con texto "No autorizada" o similar.
- Este indicador es independiente de si la burbuja está visible ahora mismo — solo refleja si el permiso está concedido.

### 3.3 Puntos de Implementación

**BackgroundCameraService.kt**:
- **Eliminar** la llamada a `showFloatingBubble()` en `onCreate()` (línea 252-254). La burbuja NO debe crearse al iniciar el servicio.
- **Agregar** llamada a `showFloatingBubble()` en `startRecordingMode()` (línea 404-420), solo si `Settings.canDrawOverlays(this)` es verdadero.
- **Agregar** llamada a `removeFloatingBubble()` en `startStandbyMode()` (línea 363-376). Cuando el Vigilante pasa a STANDBY, la burbuja desaparece.
- **Agregar** limpieza de `floatingBubbleView` en `onDestroy()` (línea 298-315) como respaldo.
- **Agregar** un nuevo campo `bubbleActive: Boolean` en el companion object para comunicar el estado a la UI.

**SettingsScreen.kt**:
- **Agregar** un indicador visual dentro de la tarjeta de "Burbuja Flotante de Panico" que muestre si el permiso `SYSTEM_ALERT_WINDOW` está concedido. Este indicador debe leer el estado del permiso usando `Settings.canDrawOverlays(context)` y mostrar "Habilitada" (verde) o "No autorizada" (gris).
- El botón "AUTORIZAR BURBUJA FLOTANTE" debe seguir existiendo para usuarios que no han concedido el permiso.

**AppState.kt**:
- **Agregar** un campo `bubbleActive: Boolean = false` al data class `CameraState` para que la UI pueda observar si la burbuja está actualmente visible.

**CameraServiceManagerAndroid.kt**:
- **Agregar** un callback en `CameraStatusListener` para comunicar el estado de la burbuja a la UI, o usar el campo `bubbleActive` de `CameraState`.

### 3.4 Consideraciones

- **Permiso SYSTEM_ALERT_WINDOW**: Verificar `Settings.canDrawOverlays(this)` antes de llamar a `showFloatingBubble()`. Si el permiso no está concedido, no intentar crear la burbuja.
- **Cambio de permiso en caliente**: Si el usuario activa el permiso de overlay desde la configuración de Android mientras el servicio ya está en RECORDING, la burbuja debe aparecer inmediatamente. Esto requiere que `CameraServiceManagerAndroid` envíe un Intent al servicio para que verifique el permiso y muestre la burbuja si corresponde.
- **Impacto en rendimiento**: Ninguno. La burbuja es solo un `View` en `WindowManager`, con costo de renderizado mínimo.

### 3.5 Estimación de Cambios

- BackgroundCameraService.kt: aproximadamente 15 líneas modificadas
- SettingsScreen.kt: aproximadamente 20 líneas añadidas
- AppState.kt: aproximadamente 3 líneas (nuevo campo)
- CameraServiceManagerAndroid.kt: aproximadamente 5 líneas (callback)

---

## 4. B: CICLO DE VIDA DEL VIGILANTE — SEGUNDO PLANO VS APP CERRADA

### 4.1 Estado Actual

El servicio se inicia con `START_STICKY` en `onStartCommand()` (línea 295). Esto significa que Android intenta recrear el servicio automáticamente si lo mata por recursos.

**Comportamiento actual problemático**:
1. El usuario abre la app → el servicio se crea en `onCreate()`.
2. El usuario activa el Vigilante → modo RECORDING.
3. El usuario cierra la app desde la lista de aplicaciones recientes (swipe up).
4. El servicio **sigue corriendo** porque `START_STICKY` le dice a Android que lo reinicie si lo mata.
5. El usuario ve que el servicio sigue activo en la notificación persistente, pero no puede interactuar con la app.

**Esto da una mala experiencia** porque el usuario espera que al cerrar la app, todo se detenga.

### 4.2 Comportamiento Deseado

El Vigilante tiene dos modos de operación fuera del foreground:

| Escenario | Comportamiento Correcto | Justificación |
|-----------|------------------------|---------------|
| App en foreground (MonitorScreen visible) | Servicio ACTIVO, usuario ve preview y controles | Uso normal |
| App en background (otra app abierta, pantalla apagada) | Servicio ACTIVO, no se puede cerrar desde notificación | El Vigilante debe seguir grabando mientras el usuario conduce |
| App cerrada desde recientes (swipe up) | Servicio se DETIENE | El usuario decidió cerrar la app; respetar su decisión |
| Sistema mata el servicio por recursos | Servicio NO se reinicia | No hay razón para reiniciar si el usuario no lo pidió |

**Punto clave — "no se puede cerrar hasta que el usuario quiera"**: Cuando el servicio está en modo RECORDING y la app pasa a background, la notificación persistente con `setOngoing(true)` ya evita que el usuario pueda deslizar para eliminar la notificación. Esto es correcto. El usuario solo puede detener el Vigilante tocando el botón "DETENER" en la notificación (requerimiento F) o volviendo a la app y tocando "Stop" en MonitorScreen.

**Punto clave — "si la app se cierra por completo, el servicio se detiene"**: Cuando el usuario cierra la app desde recientes, Android destruye el proceso junto con todos sus servicios. Con `START_NOT_STICKY`, el servicio no se reinicia. Esto es el comportamiento deseado.

### 4.3 Puntos de Implementación

**BackgroundCameraService.kt**:
- **Cambiar** `return START_STICKY` (línea 295) a `return START_NOT_STICKY`. Esto evita que Android reinicie el servicio automáticamente.
- **Verificar** que `onDestroy()` (líneas 298-315) limpia todo correctamente: sensores, cámaras, burbuja, grabaciones activas, handler callbacks. Actualmente parece correcto, pero se debe validar que no queden callbacks pendientes del handler que puedan ejecutarse después de la destrucción.
- **Considerar** agregar un flag `isDestroyed` que se establezca en `onDestroy()` y se verifique en los runnables del handler antes de ejecutar cualquier operación.

**DuoVialApplication.kt**:
- **Verificar** si existe algún `WorkManager`, `BroadcastReceiver`, o `AlarmManager` que intente reiniciar el servicio automáticamente. Si existe, eliminarlo o modificarlo para que NO reinicie el servicio después de que el usuario lo cerró.

**CameraServiceManagerAndroid.kt**:
- **Verificar** que la app solo inicie el servicio a través de Intents explícitos desde la UI (botones de MonitorScreen), no desde receivers de sistema o workers en background.

### 4.4 Mecanismo de Detección de "App Cerrada"

En Android, cuando el usuario cierra la app desde recientes:
1. El sistema destruye la `Activity` principal (`MainActivity.onDestroy()`).
2. El sistema destruye todos los servicios del proceso.
3. Con `START_NOT_STICKY`, el servicio no se reinicia.

**Edge case — algunos fabricantes**: En dispositivos de ciertos fabricantes (Xiaomi, Huawei, Samsung con One UI), matar la app desde recientes puede NO destruir el foreground service inmediatamente. Para estos casos, se puede usar `ProcessLifecycleOwner` para detectar cuándo la app pierde el estado "en uso" y enviar un Intent `ACTION_STOP_SERVICE` al servicio.

**Recomendación para MVP**: Implementar `START_NOT_STICKY` y validar en 3+ dispositivos de diferentes fabricantes. Si se detecta que algún fabricante no destruye el servicio al cerrar la app, agregar la lógica de `ProcessLifecycleOwner` como solución complementaria.

### 4.5 Consideraciones

- **Impacto en UX**: El usuario debe entender que "cerrar la app = detener el Vigilante". Esto debería comunicarse en el onboarding o en un tooltip en MonitorScreen la primera vez que el usuario activa el Vigilante.
- **Edge case — permisos revocados**: Si el usuario revoca permisos de cámara mientras el servicio está en background, el servicio debe detenerse gracefully sin crash. Actualmente no hay manejo explícito de este caso.
- **Edge case — batería**: Algunos fabricantes matan servicios agresivamente para ahorrar batería. Con `START_NOT_STICKY`, el servicio no se reinicia después de ser matado, lo cual es el comportamiento deseado.

### 4.6 Estimación de Cambios

- BackgroundCameraService.kt: aproximadamente 5 líneas modificadas
- DuoVialApplication.kt: verificar y posiblemente eliminar lógica existente
- CameraServiceManagerAndroid.kt: verificar flujo de inicio

---

## 5. C: FILTRO DE VELOCIDAD PARA EVENTOS DEL ACELERÓMETRO

### 5.1 Estado Actual

El listener del acelerómetro (`accelListener`, líneas 180-198) calcula la fuerza G a partir de los datos del sensor y, si supera el umbral configurado, ejecuta `triggerCollisionEvent()` para guardar el video del buffer circular.

**Problema**: El listener NO verifica la velocidad actual del vehículo antes de disparar el evento. Si el usuario está estacionado en una gasolinera, pasando por un tope, o frenando bruscamente en un estacionamiento, el evento se guarda sin importar la velocidad. Esto genera falsos positivos que llenan el almacenamiento con videos innecesarios.

**Dato relevante**: La variable `lastKnownSpeed` ya está disponible en la clase. Se actualiza en `locationListener` (líneas 202-213) con un filtro pasa-bajo y un filtro de precisión GPS de 20 metros.

### 5.2 Comportamiento Deseado

| Velocidad Actual | ¿Guardar video al detectar G-Force? | Justificación |
|-----------------|--------------------------------------|---------------|
| 0 km/h (estacionado) | NO | Falsos positivos por tocar el teléfono, viento, baches |
| 5 km/h (gasolinera, estacionamiento) | NO | Movimientos bruscos al estacionar |
| 15 km/h (zona residencial) | NO | Aún muchas fuentes de falsos positivos |
| 30 km/h (ciudad) | SÍ | Velocidad mínima para considerar evento significativo |
| 40+ km/h (carretera) | SÍ | Evento de alto impacto potencial |

**Umbral propuesto**: 30 km/h. Este valor es fijo para MVP. En el futuro podría hacerse configurable desde SettingsScreen.

### 5.3 Puntos de Implementación

**BackgroundCameraService.kt**:
- **Agregar** una constante `MIN_SPEED_FOR_EVENT_KMH = 30.0` en el companion object para facilitar ajustes futuros.
- **Modificar** la lógica dentro de `accelListener.onSensorChanged()` (líneas 194-196) para que, antes de llamar a `triggerCollisionEvent()`, verifique que `lastKnownSpeed >= MIN_SPEED_FOR_EVENT_KMH`.
- **Agregar** un log cuando un evento se descarta por velocidad: incluir la velocidad actual y el umbral para debugging.
- **Agregar** un log cuando un evento SÍ se dispara, incluyendo la velocidad actual para trazabilidad.

### 5.4 Casos Especiales

**GPS no disponible o sin señal**: Cuando `lastKnownSpeed` es -1.0 (valor inicial, sin datos GPS), el filtro de velocidad no debe aplicarse. Si no hay datos de velocidad, es mejor guardar el evento (falso positivo) que perder un evento real. La condición debe ser: si `lastKnownSpeed` es menor a 0, permitir el evento.

**Botón de pánico manual**: El botón de pánico en MonitorScreen, la burbuja flotante, y el botón de pantalla de bloqueo (requerimiento F) NO deben tener filtro de velocidad. Si el usuario presiona "EVENTO" manualmente, es porque vio algo importante sin importar la velocidad. El filtro solo se aplica en el `accelListener`, no en `triggerCollisionEvent()` directamente.

**Filtro de precisión GPS**: El `locationListener` ya filtra ubicaciones con precisión mayor a 20 metros (línea 205). Si la precisión GPS es mala, `lastKnownSpeed` no se actualiza con datos nuevos, lo que puede causar que se use un valor de velocidad antiguo. Esto es aceptable para MVP.

### 5.5 Consideraciones

- **Impacto en rendimiento**: Ninguno. Es solo una comparación numérica adicional en el listener del acelerómetro.
- **Compatibilidad con Trigger 3 del TICKETS_DESARROLLO.md**: El Trigger 3 (Colisión + Filtro de Velocidad) tiene un umbral diferente: G-Force > 3.5G + Velocidad > 40 km/h + Duración > 150ms. Este Trigger 3 es para colisiones graves que activan subida automática de video y llamada Twilio. El filtro de 30 km/h del acelerómetro es más conservador y se aplica a TODOS los eventos del acelerómetro. Son filtros complementarios, no redundantes.

### 5.6 Estimación de Cambios

- BackgroundCameraService.kt: aproximadamente 10 líneas modificadas

---

## 6. D: VALIDACIÓN DE RENDIMIENTO Y OPTIMIZACIONES

### 6.1 Análisis de Rendimiento Actual

#### 6.1.1 CameraX — Cámara Trasera (Buffer Circular)

| Métrica | Estado Actual | Target del Proyecto | Evaluación |
|---------|--------------|---------------------|------------|
| Resolución | 1920×1080 (HD) | 1080p | Correcto |
| FPS | 30 (default CameraX) | 30 | Correcto |
| Bitrate | Default CameraX (~5 Mbps) | 2 Mbps | **Sobre el target — 2.5x más datos de los necesarios** |
| Codec | H.264 (default) | H.264 | Correcto |
| Audio | No configurado (CameraX graba audio por defecto) | DESACTIVADO | **Audio activo desperdicia CPU y espacio** |
| Segmentos | 2 segmentos de 15 segundos | 2-3 segmentos | Correcto |
| Almacenamiento | cacheDir | cacheDir | Correcto |

**Problemas encontrados**:

1. **Audio no desactivado** (inconsistencia I7): CameraX graba audio por defecto. Esto desperdicia CPU para procesar audio que nunca se usa, y aumenta el tamaño de los archivos de video. El audio debe desactivarse explícitamente en la configuración del `Recorder`.

2. **Bitrate no configurado** (inconsistencia I8): El bitrate por defecto de CameraX es aproximadamente 5 Mbps para 1080p@30fps. El proyecto especifica 2 Mbps como target. Configurar el bitrate explícitamente reducirá significativamente la cantidad de datos escritos al almacenamiento y la temperatura del dispositivo.

3. **Sin verificación de capacidad de cámara** (inconsistencia I6): No se verifica si el dispositivo soporta la resolución solicitada antes de iniciar CameraX. En dispositivos gama baja, 1080p puede no estar soportado o causar sobrecalentamiento. Se debe verificar y usar 720p como fallback si es necesario.

#### 6.1.2 Acelerómetro

| Métrica | Estado Actual | Target | Evaluación |
|---------|--------------|--------|------------|
| Sensor Delay | SENSOR_DELAY_NORMAL (~200ms) | 200ms | Correcto |
| Throttle de UI | Cada 200ms | 200ms | Correcto |
| Precisión | Default del dispositivo | Default | Aceptable |

El acelerómetro está configurado correctamente. `SENSOR_DELAY_NORMAL` equivale a 50-200ms entre eventos, y el throttle de UI a 200ms evita actualizaciones excesivas. Sin problemas detectados.

#### 6.1.3 GPS (Velocímetro)

| Métrica | Estado Actual | Target | Evaluación |
|---------|--------------|--------|------------|
| Provider | GPS_PROVIDER | GPS_PROVIDER | Correcto |
| Min Time | 1000ms | 1000ms | Correcto |
| Min Distance | 0 metros | — | Correcto para velocímetro |
| Filtro de precisión | accuracy > 20 metros | — | Correcto |
| Filtro pasa-bajo | 0.6×anterior + 0.4×nuevo | — | Correcto |

El GPS está razonablemente bien configurado. `minDistance=0` significa que reporta cada cambio de ubicación, lo cual es correcto para un velocímetro. El filtro pasa-bajo suaviza lecturas ruidosas.

#### 6.1.4 FaceProcessor (Detección de Fatiga)

| Métrica | Estado Actual | Target | Evaluación |
|---------|--------------|--------|------------|
| Executor | Single thread (newSingleThreadExecutor) | Single thread | Correcto |
| Throttle | AtomicBoolean guard | — | Correcto |
| Resolución | 640×480 (VGA) | VGA | Correcto |
| FPS | 10 fps | 10 fps | Correcto |
| ML Kit | Face Detection (FAST mode) | Face Detection | Correcto |
| Shutdown en stop() | Sí, llama a shutdownNow() | — | Correcto |

FaceProcessor está bien implementado. El executor se crea en `start()` y se destruye correctamente en `stop()` con `shutdownNow()`. El `AtomicBoolean` previene procesamiento concurrente. Sin problemas detectados.

#### 6.1.5 Manejo de Handler y Runnables

| Métrica | Estado Actual | Target | Evaluación |
|---------|--------------|--------|------------|
| Handler principal | Handler(Looper.getMainLooper()) | Main looper | **Riesgo de bloquear main thread** |
| Runnables pendientes | removeCallbacks en stopCircularBufferTimers | — | Correcto |
| Rotación cada 15s | handler.postDelayed(rotateRunnable, 15000) | 15s | Correcto |

**Problema** (inconsistencia I4): El handler está en el main looper. Las operaciones de `rotateCircularBuffer()` y `onRecordingFinalized()` se ejecutan en el main thread. La función `copyFileToDownloads()` (líneas 806-841) realiza I/O de archivos de 4-6 MB, lo que puede bloquear la UI por 1-2 segundos. Esto debe moverse a un hilo secundario.

#### 6.1.6 Gestión de Memoria

| Métrica | Estado Actual | Target | Evaluación |
|---------|--------------|--------|------------|
| companion object con instance | var instance: BackgroundCameraService? | — | Riesgo de memory leak |
| activePreview estático | var activePreview: Preview? | — | Referencia estática a CameraX |
| activePreviewView estático | var activePreviewView: PreviewView? | — | **No se limpia en onDestroy** (inconsistencia I1) |

Los campos estáticos en `companion object` mantienen referencias al servicio y sus vistas. En `onDestroy()` se limpia `instance` y `activePreview`, pero `activePreviewView` solo se limpia en `onPreviewViewDropped()`. Esto debe corregirse.

### 6.2 Optimizaciones Propuestas (Por Prioridad)

#### Prioridad ALTA — Impacto directo en rendimiento

| # | Optimización | Archivo | Impacto Estimado |
|---|-------------|---------|------------------|
| D1 | Desactivar audio en CameraX Recorder | BackgroundCameraService.kt (línea 526-529) | Reducción de 5-8% en CPU, 10% en tamaño de archivos |
| D2 | Configurar bitrate a 2 Mbps | BackgroundCameraService.kt (línea 526-529) | Reducción de 60% en datos escritos, menor temperatura |
| D3 | Mover copyFileToDownloads a hilo secundario | BackgroundCameraService.kt (líneas 806-841) | Elimina bloqueo del main thread durante guardado |

#### Prioridad MEDIA — Robustez y estabilidad

| # | Optimización | Archivo | Impacto Estimado |
|---|-------------|---------|------------------|
| D4 | Verificar resolución soportada antes de iniciar cámara | BackgroundCameraService.kt (líneas 520-567) | Previene crashes en dispositivos gama baja |
| D5 | Limpiar activePreviewView en onDestroy | BackgroundCameraService.kt (líneas 298-315) | Previene memory leaks |
| D6 | Agregar verificación de isDestroyed en runnables del handler | BackgroundCameraService.kt | Previene crashes por ejecución post-destrucción |

#### Prioridad BAJA — Mejoras menores

| # | Optimización | Archivo | Impacto Estimado |
|---|-------------|---------|------------------|
| D7 | Reducir logs verbosos (Log.d → Log.v o eliminar) | BackgroundCameraService.kt (múltiples) | Menor overhead de I/O en logcat |
| D8 | Usar Lifecycle-aware para registrar/eliminar listeners | BackgroundCameraService.kt | Código más limpio, menos propenso a bugs |

### 6.3 Validación de Consumo Real

Para validar que las optimizaciones funcionan, el agente que implemente debe:

1. **Antes de optimizar**: Medir CPU%, memoria RSS y temperatura del dispositivo durante 5 minutos de operación en modo RECORDING usando herramientas de Android Studio o comandos ADB.
2. **Después de optimizar**: Repetir las mismas mediciones.
3. **Comparar**: Crear tabla comparativa con los resultados antes y después.
4. **Documentar**: Incluir los resultados en el commit message o PR description.

### 6.4 Consideraciones

- **D1 y D2 son las optimizaciones de mayor impacto**: Desactivar audio y reducir bitrate tienen el mayor beneficio con el menor esfuerzo de implementación.
- **D3 es crítico para UX**: Si `copyFileToDownloads()` bloquea el main thread, el usuario puede ver un freeze en la UI justo cuando ocurre un evento — el peor momento para un lag.
- **D5 y D6 son prevención de bugs**: No tienen impacto visible inmediato, pero previenen memory leaks y crashes que se manifestarían a largo plazo.

---

## 7. E: MONITOREO DE TEMPERATURA DEL DISPOSITIVO

### 7.1 Contexto del Problema

El dispositivo del usuario se sobrecalienta en días soleados porque:
1. El auto no tiene aire acondicionado, por lo que el teléfono está expuesto al sol directo.
2. Trabaja con aplicaciones que consumen muchos recursos (Uber, Waze, etc.) que aumentan la temperatura del dispositivo.
3. DuoVial usa la cámara trasera en modo continuo, lo que añade carga térmica adicional.

En estas condiciones, la temperatura del dispositivo puede superar los 50°C, lo que puede dañar el hardware (especialmente la batería y el sensor de la cámara) y degradar el rendimiento del sistema operativo.

### 7.2 Estado Actual

No existe monitoreo de temperatura en la implementación actual. El servicio no verifica la temperatura del dispositivo en ningún momento. Si el dispositivo se sobrecalienta, el sistema operativo puede matar la aplicación sin previo aviso, perdiendo el buffer circular y dejando al usuario sin entender qué pasó.

### 7.3 Comportamiento Deseado

| Temperatura del Dispositivo | Estado Visual | Acción del Sistema |
|---------------------------|---------------|-------------------|
| Menor a 40°C | Normal (sin indicador o indicador gris) | Operación completa |
| 40°C a 45°C | Caliente (indicador amarillo) | Advertencia en UI, el Vigilante sigue activo |
| 45°C a 50°C | Muy caliente (indicador naranja) | Advertencia fuerte + sugerencia de pausa, el Vigilante sigue activo |
| Mayor o igual a 50°C | Crítico (indicador rojo) | **DETENER Vigilante automáticamente** + notificación al usuario |

**Comportamientos adicionales**:

1. **Al intentar iniciar el Vigilante**: Si la temperatura actual del dispositivo es mayor o igual a 50°C, el botón de inicio en MonitorScreen debe estar **bloqueado** y debe mostrar un mensaje: "Dispositivo demasiado caliente. Espera a que se enfríe para activar el Vigilante."

2. **Durante operación del Vigilante**: Si la temperatura supera los 50°C mientras el Vigilante está en modo RECORDING, el servicio debe **detener automáticamente** la grabación, volver a STANDBY, y enviar una notificación al usuario.

3. **Indicador en MonitorScreen**: Mostrar la temperatura actual del dispositivo en el header de MonitorScreen (junto al badge de G-Force y KPH). El indicador debe mostrar: un ícono de termómetro + el valor en grados Celsius + un color que cambie según el umbral (gris, amarillo, naranja, rojo).

4. **Re-activación después de enfriamiento**: Si el servicio se detuvo por temperatura, el botón de inicio debe reactivarse cuando la temperatura baje de 45°C (con un hysteresis de 5°C para evitar que el botón se active y desactive repetidamente cuando la temperatura oscila alrededor del umbral).

### 7.4 Obtención de Temperatura en Android

**Método recomendado para MVP**: Usar `BatteryManager` a través de `Intent.ACTION_BATTERY_CHANGED`. Este método retorna la temperatura de la batería en décimas de grado Celsius (por ejemplo, 310 = 31.0°C). Es el método más confiable y no requiere permisos especiales.

**Consideraciones del método**:
- La temperatura de la batería no es exactamente la temperatura del CPU, pero es la métrica que el sistema operativo usa para mostrar advertencias de sobrecalentamiento al usuario.
- Para obtener la temperatura, se hace una llamada a `registerReceiver` con un receiver nulo, lo que retorna el último broadcast de batería sin registrar un listener permanente. Esto tiene un costo de aproximadamente 0.1 milisegundos.
- La temperatura de la batería típicamente está 5-10°C por debajo de la temperatura del CPU en uso intensivo.

**Alternativa complementaria**: En muchos dispositivos, la temperatura del CPU se puede leer desde el archivo del sistema `/sys/class/thermal/thermal_zone0/temp`. Sin embargo, no todos los dispositivos exponen esta información de la misma manera, y puede requerir permisos especiales. Se recomienda usar como complemento, no como única fuente.

### 7.5 Puntos de Implementación

**BackgroundCameraService.kt**:
- **Agregar** una función `getDeviceTemperature()` que lea la temperatura de la batería usando `BatteryManager.EXTRA_TEMPERATURE`.
- **Agregar** un `Runnable` periódico que verifique la temperatura cada 30 segundos mientras el servicio está en modo RECORDING. El intervalo de 30 segundos es suficiente para detectar sobrecalentamiento sin consumir recursos significativos.
- **Agregar** lógica en `startRecordingMode()` que verifique la temperatura ANTES de iniciar la grabación. Si es mayor o igual a 50°C, no iniciar y notificar al usuario.
- **Agregar** lógica en el `Runnable` de temperatura que, si la temperatura supera 50°C durante la operación, llame a `stopRecordingWithoutSaving()` y envíe una notificación al usuario explicando la razón.
- **Agregar** un companion object con las constantes `TEMP_WARNING_CELSIUS = 40.0`, `TEMP_DANGER_CELSIUS = 45.0`, `TEMP_CRITICAL_CELSIUS = 50.0`, y `TEMP_RECOVERY_CELSIUS = 45.0` (hysteresis).
- **Agregar** una nueva acción `ACTION_TEMPERATURE_STOPPED` en el companion object para que la notificación pueda diferenciarse de una parada manual.

**AppState.kt**:
- **Agregar** un campo `temperature: Float = 0f` al data class `CameraState`.
- **Agregar** un enum `TemperatureStatus` con valores: `NORMAL`, `WARNING`, `DANGER`, `CRITICAL`.
- **Agregar** un campo `temperatureStatus: TemperatureStatus = TemperatureStatus.NORMAL` al data class `CameraState`.

**MonitorScreen.kt**:
- **Agregar** un indicador de temperatura en el header de la pantalla, junto al badge de G-Force y KPH. El indicador debe mostrar un ícono de termómetro, el valor numérico en grados Celsius, y un color que cambie según el `temperatureStatus` de `CameraState`.

**CameraServiceManager.kt**:
- **Agregar** la función `getTemperature(): Float` a la interfaz.

**CameraServiceManagerAndroid.kt**:
- **Implementar** `getTemperature()` leyendo el valor de `AppStateManager.cameraState.value.temperature`.

### 7.6 Texto de Notificación Cuando se Detiene por Temperatura

Cuando el Vigilante se detiene automáticamente por temperatura, la notificación debe mostrar:

- **Título**: "DuoVial — Vigilante Detenido"
- **Texto**: "Temperatura del dispositivo: [XX]°C. El Vigilante se detuvo para proteger tu teléfono. Podrás reactivarlo cuando la temperatura baje de 45°C."

Este texto deja claro al usuario QUÉ pasó y POR QUÉ pasó, cumpliendo con el requerimiento de "notificar porque se detiene".

### 7.7 Impacto en Rendimiento del Monitoreo

| Componente | Impacto | Justificación |
|------------|---------|---------------|
| Lectura de BatteryManager | ~0.1ms por lectura | Una sola llamada a registerReceiver con receiver nulo |
| Timer cada 30 segundos | ~0% CPU | Handler.postDelayed con intervalo de 30s es negligible |
| Actualización de UI | ~0% CPU | Solo actualiza un campo numérico en CameraState |
| **Total** | **Menos de 0.1% CPU adicional** | **Impacto insignificante en rendimiento** |

**Conclusión**: El monitoreo de temperatura tiene un impacto prácticamente nulo en el rendimiento del dispositivo. Es altamente recomendable implementarlo, especialmente considerando el caso de uso del usuario (auto sin aire acondicionado, días soleados, múltiples aplicaciones consumiendo recursos).

### 7.8 Consideraciones

- **No confundir temperatura de batería con temperatura del SoC**: La batería puede estar a 40°C mientras el CPU está a 65°C. Pero para efectos de UX, la temperatura de batería es la métrica que el sistema operativo usa para mostrar "Dispositivo sobrecalentado".
- **Algunos fabricantes muestran su propio warning de temperatura**: Si el sistema operativo ya muestra un overlay de "dispositivo sobrecalentado", nuestro monitoreo es complementario y no duplica esfuerzos.
- **Temperatura > 50°C es rara en uso normal**: Solo ocurre con uso intensivo de cámara + CPU en ambientes cálidos. Pero cuando ocurre, es crítico detener la grabación para evitar daño al hardware.
- **Hysteresis para evitar flickering**: Si el servicio se detuvo por temperatura y la temperatura baja a 49°C, no debe reactivarse inmediatamente. Se debe esperar a que baje de 45°C (hysteresis de 5°C) para evitar que el botón de inicio se active y desactive repetidamente.

### 7.9 Estimación de Cambios

- BackgroundCameraService.kt: aproximadamente 40 líneas
- AppState.kt: aproximadamente 10 líneas
- MonitorScreen.kt: aproximadamente 25 líneas
- CameraServiceManager.kt: aproximadamente 2 líneas
- CameraServiceManagerAndroid.kt: aproximadamente 5 líneas

---

## 8. F: BOTÓN DE EVENTO EN PANTALLA DE BLOQUEO

### 8.1 Estado Actual

La notificación persistente del servicio (líneas 462-514) es una notificación básica con título, texto e ícono. No tiene botones de acción ni está configurada para ser visible en la pantalla de bloqueo.

**Componentes actuales de la notificación**:
- Título: "DuoVial" o "DuoVial - Vigilando"
- Texto: "Camara lista." o "Grabacion circular activa en segundo plano."
- Ícono: android.R.drawable.presence_video_online
- Sin acciones (botones)
- Sin configuración de visibilidad en pantalla de bloqueo
- `setOngoing(true)` — no se puede deslizar para eliminar

### 8.2 Comportamiento Deseado

Cuando el Vigilante está activo (modo RECORDING), la notificación persistente debe mostrar dos botones de acción que funcionen tanto en la barra de notificaciones como en la pantalla de bloqueo:

**Botón 1 — "REGISTRAR EVENTO"**: Al tocarlo, ejecuta la misma lógica que el botón "EVENTO" de MonitorScreen — guarda el buffer circular actual (los últimos 15 segundos) y graba 15 segundos adicionales post-evento.

**Botón 2 — "DETENER"**: Al tocarlo, guarda el buffer actual y pone el servicio en STANDBY. Equivalente a la acción de `stopAndSaveBuffer()`.

**Comportamiento según estado del servicio**:

| Estado del Servicio | ¿Botones visibles? | Razón |
|--------------------|--------------------|----|
| RECORDING (Vigilando) | SÍ — ambos botones | El usuario puede registrar eventos o detener el Vigilante |
| STANDBY (Camara lista) | NO — sin botones | No hay nada que detener ni registrar |
| GUARDANDO (Guardando incidente) | NO — sin botones | No se puede registrar otro evento mientras se guarda el anterior |

### 8.3 Especificaciones de la Notificación

| Propiedad | Valor | Justificación |
|-----------|-------|---------------|
| setVisibility | VISIBILITY_PUBLIC | Visible en pantalla de bloqueo sin desbloquear |
| setOngoing | true | No descartable — servicio foreground |
| setCategory | CATEGORY_SERVICE | Categoría de servicio |
| addAction | 2 acciones (Evento + Detener) | Solo cuando el servicio está en RECORDING |
| setPriority | PRIORITY_LOW | No intrusivo — ya es IMPORTANCE_LOW en el channel |
| Datos sensibles | NO exponer | Sin ubicación, sin nombre del conductor, sin preview de video |

### 8.4 Comparación de Enfoques para Pantalla de Bloqueo

El usuario comparó el comportamiento deseado con los reproductores de música, que muestran controles de reproducción en la pantalla de bloqueo. Hay dos enfoques posibles:

**Enfoque A — Notificación estándar con addAction() (Recomendado para MVP)**:
- Se agregan dos botones de acción a la notificación usando `addAction()`.
- Los botones aparecen tanto en la barra de notificaciones como en la pantalla de bloqueo.
- Es el enfoque más simple y ampliamente compatible.
- No requiere permisos adicionales.
- Funciona en Android 12+ sin restricciones.

**Enfoque B — Notificación MediaStyle (Mejor integración visual)**:
- Se usa `NotificationCompat.MediaStyle` o `DecoratedMediaCustomViewStyle` para crear una notificación que se integra nativamente con los controles de multimedia de la pantalla de bloqueo.
- Visualmente más atractivo y consistente con otros controles de la pantalla de bloqueo.
- Requiere configuración adicional y puede tener comportamiento diferente entre fabricantes.
- Más complejo de implementar y mantener.

**Recomendación**: Usar el Enfoque A (addAction estándar) para MVP. Es más simple, más compatible, y cumple con el requerimiento funcional. El Enfoque B puede evaluarse como mejora visual en una fase futura.

### 8.5 Puntos de Implementación

**BackgroundCameraService.kt**:
- **Agregar** dos nuevas constantes en el companion object: `ACTION_NOTIFICATION_EVENT` y `ACTION_NOTIFICATION_STOP`.
- **Reescribir** la función `startServiceNotification()` (líneas 462-487) para que construya la notificación inicial sin acciones (ya que el servicio inicia en STANDBY).
- **Reescribir** la función `updateNotification()` (líneas 489-504) para que acepte un parámetro que indique si el servicio está en RECORDING. Si está en RECORDING, incluir las dos acciones. Si está en STANDBY, no incluir acciones.
- **Agregar** manejo de las nuevas acciones en `onStartCommand()` (líneas 257-296): `ACTION_NOTIFICATION_EVENT` ejecuta `triggerCollisionEvent()` con razón "Boton de Pantalla de Bloqueo", y `ACTION_NOTIFICATION_STOP` ejecuta `stopAndSaveBuffer()`.
- **Crear** los `PendingIntents` correspondientes para cada acción, usando `PendingIntent.FLAG_UPDATE_CURRENT` y `PendingIntent.FLAG_IMMUTABLE`. Los `requestCode` deben ser diferentes (1 y 2) para que Android distinga los PendingIntents.
- **Agregar** `setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` a la notificación para que sea visible en pantalla de bloqueo.

**NotificationHelper.kt**:
- **Considerar** agregar las acciones de notificación como constantes o funciones helper compartidas, para mantener la consistencia si se necesitan en otros lugares en el futuro.

### 8.6 Construcción de PendingIntents

Cada acción de notificación requiere un `PendingIntent` que envíe un `Intent` al servicio. Es importante usar `requestCode` diferentes para cada PendingIntent, ya que Android usa el `requestCode` para distinguir PendingIntents que apuntan al mismo servicio con la misma acción.

Para el botón de EVENTO: `requestCode = 1`, acción = `ACTION_NOTIFICATION_EVENT`.
Para el botón de DETENER: `requestCode = 2`, acción = `ACTION_NOTIFICATION_STOP`.

Ambos deben usar `PendingIntent.FLAG_IMMUTABLE` (requerido en Android 12+) y `PendingIntent.FLAG_UPDATE_CURRENT`.

### 8.7 Compatibilidad con Android 12+

En Android 12+, las notificaciones de servicios foreground tienen restricciones adicionales:
- Las acciones de notificación **sí funcionan** en foreground services.
- No se necesitan permisos adicionales para acciones en notificaciones de servicio.
- `PendingIntent.FLAG_IMMUTABLE` es obligatorio.
- Las notificaciones de servicio foreground no pueden ser descartadas por el usuario (ya manejado con `setOngoing(true)`).

### 8.8 Consideraciones

- **Pantalla de bloqueo — privacidad**: `VISIBILITY_PUBLIC` muestra el título y texto en la pantalla de bloqueo. El texto actual ("DuoVial - Vigilando" / "Grabacion circular activa") no contiene información sensible, así que es seguro.
- **Cooldown de eventos**: El botón de la notificación debe respetar el mismo cooldown de 5 segundos que el botón de la UI (ya implementado en `triggerCollisionEvent()` línea 657).
- **Actualización dinámica de la notificación**: Cuando el servicio cambia de STANDBY a RECORDING, la notificación debe actualizarse para mostrar los botones. Cuando vuelve a STANDBY, los botones deben desaparecer. Esto se logra llamando a `updateNotification()` en `startRecordingMode()` y `startStandbyMode()`.
- **Acción "DETENER" accidental**: En la pantalla de bloqueo, es fácil tocar un botón accidentalmente. Para MVP, la acción de la notificación ejecuta directamente `stopAndSaveBuffer()` sin confirmación adicional. El usuario puede re-iniciar el Vigilante fácilmente. En una fase futura, se podría agregar un segundo paso de confirmación.

### 8.9 Estimación de Cambios

- BackgroundCameraService.kt: aproximadamente 50 líneas
- NotificationHelper.kt: aproximadamente 5 líneas (opcional)

---

## 9. G: AUTO-INICIO DEL VIGILANTE A 30 KM/H

### 9.1 Contexto

El usuario quiere que el Vigilante se active automáticamente cuando el vehículo alcance los 30 km/h, sin necesidad de presionar el botón de inicio manualmente. Esto es especialmente útil para conductores que olvidan activar la app antes de conducir.

La funcionalidad debe ser **configurable** — el usuario puede habilitarla o deshabilitarla desde SettingsScreen. Por defecto, debe estar **deshabilitada** para no sorprender al usuario.

### 9.2 Estado Actual

La infraestructura necesaria ya existe en el código:

- **Monitoreo de velocidad**: El `locationListener` (líneas 222-233 de BackgroundCameraService.kt) ya actualiza `lastKnownSpeed` con datos GPS cada segundo, con filtro pasa-bajo y filtro de precisión de 20 metros.
- **Constante de velocidad**: Ya existe `MIN_SPEED_FOR_EVENT_KMH = 30.0` en el companion object (línea 159).
- **Persistencia de settings**: `SettingsManagerAndroid` usa SharedPreferences a través de la librería `russhwolf/settings`. Agregar un toggle es trivial.
- **Ciclo de vida del servicio**: El servicio ya se inicia en `MainActivity.onCreate()` llamando a `serviceManager.startStandby()`.

### 9.3 Comportamiento Deseado

| Paso | Descripción |
|------|-------------|
| 1 | Usuario abre la app → servicio inicia en STANDBY → GPS monitorea velocidad |
| 2 | Usuario habilita "Auto-inicio" en SettingsScreen (toggle) |
| 3 | Usuario conduce y alcanza 30 km/h |
| 4 | Servicio detecta velocidad >= 30 km/h + servicio en STANDBY + autoStart habilitado |
| 5 | Servicio envía notificación: "DuoVial se activará en 5 segundos. Toca para cancelar." |
| 6 | Si el usuario toca la notificación para cancelar → se cancela el auto-inicio |
| 7 | Si pasan 5 segundos sin cancelar → servicio cambia a modo RECORDING |

### 9.4 Puntos de Implementación

**BackgroundCameraService.kt**:
- En `locationListener.onLocationChanged()` (línea 222-233): agregar lógica que verifique si `lastKnownSpeed >= 30.0` AND `serviceState == STANDBY` AND `autoStartEnabled == true`. Si se cumplen las condiciones, iniciar el proceso de auto-inicio.
- Agregar un `Runnable` de 5 segundos de delay antes de activar el modo RECORDING. Si el usuario cancela, se remueve el runnable.
- Agregar una nueva acción `ACTION_CANCEL_AUTO_START` en el companion object para manejar la cancelación desde la notificación.
- Agregar un campo `autoStartEnabled: Boolean` en el companion object (o leerlo de SharedPreferences directamente).
- Agregar un campo `autoStartPending: Boolean` para rastrear si hay un auto-inicio pendiente.

**SettingsManager.kt (interfaz)**:
- Agregar `suspend fun isAutoStartEnabled(): Boolean` y `suspend fun setAutoStartEnabled(value: Boolean)`.

**SettingsManagerAndroid.kt**:
- Implementar los nuevos getters/setters usando `settings.getBoolean("auto_start_enabled", false)` y `settings.putBoolean("auto_start_enabled", value)`.

**SettingsScreen.kt**:
- Agregar un nuevo toggle en la sección de configuración para habilitar/deshabilitar el auto-inicio. El toggle debe tener una descripción que explique el comportamiento: "El Vigilante se activará automáticamente cuando alcances 30 km/h."

**NotificationHelper.kt**:
- Agregar un nuevo canal de notificación para las notificaciones de auto-inicio (importance HIGH para que sea visible inmediatamente).

**CameraServiceManager.kt**:
- Agregar `fun setAutoStartEnabled(enabled: Boolean)` y `fun isAutoStartEnabled(): Boolean` a la interfaz.

**CameraServiceManagerAndroid.kt**:
- Implementar los nuevos métodos delegando al servicio o a SettingsManager.

### 9.5 Consideraciones

1. **El servicio debe estar corriendo**: El auto-inicio solo funciona si el usuario abrió la app al menos una vez (para que el servicio inicie en STANDBY). Si la app nunca se abrió, no hay servicio monitoreando velocidad. Esto es aceptable para MVP.

2. **Notificación de cancelación**: La notificación debe tener un botón "CANCELAR" que el usuario pueda tocar antes de que pasen los 5 segundos. Esto requiere un `PendingIntent` con la acción `ACTION_CANCEL_AUTO_START`.

3. **Cooldown de auto-inicio**: Para evitar que el servicio se active y desactive repetidamente en tráfico urbano (parar en un semáforo, arrancar, parar), se debe agregar un cooldown. Por ejemplo: una vez que el auto-inicio se activa, no puede activarse nuevamente hasta que pasen 10 minutos.

4. **Desactivación al detener**: Si el usuario detiene manualmente el Vigilante (botón Stop), el auto-inicio debería desactivarse temporalmente hasta que la velocidad baje de 10 km/h (para evitar que se re-active inmediatamente al seguir conduciendo).

5. **Consumo de recursos**: El GPS ya está monitoreando velocidad cuando el servicio está en STANDBY. La lógica adicional de comparación de velocidad es negligible (una comparación numérica). El único costo adicional es la notificación, que es un evento único. **Impacto: prácticamente nulo.**

### 9.6 Estimación de Cambios

| Archivo | Líneas aproximadas |
|---------|-------------------|
| BackgroundCameraService.kt | ~40 líneas |
| SettingsManager.kt | ~3 líneas |
| SettingsManagerAndroid.kt | ~6 líneas |
| SettingsScreen.kt | ~25 líneas |
| CameraServiceManager.kt | ~3 líneas |
| CameraServiceManagerAndroid.kt | ~10 líneas |
| NotificationHelper.kt | ~10 líneas |
| **TOTAL** | **~97 líneas** |

---

## 10. H: BORRADO AUTOMÁTICO DE VIDEOS MAYORES A 72 HORAS

### 10.1 Contexto

Los videos de incidentes se guardan en `Downloads/DuoVial/` y se acumulan indefinidamente. Si el usuario no procesa un video en las primeras 72 horas, es probable que no le sea útil. Estos videos ocupan espacio valioso en el almacenamiento del dispositivo y deben eliminarse automáticamente.

Esta funcionalidad **no es configurable** — se ejecuta siempre, de forma silenciosa, para mantener el almacenamiento limpio.

### 10.2 Estado Actual

- **Ubicación de los videos**: Los videos se guardan en `Downloads/DuoVial/` a través de MediaStore (líneas 806-841 de BackgroundCameraService.kt). Cada archivo tiene el formato `incident_[timestamp]_part[N].mp4`.
- **Escaneo de incidentes**: `IncidentRepository.scanIncidents()` (líneas 17-59 de IncidentRepository.kt) ya consulta MediaStore con un filtro `DISPLAY_NAME LIKE 'incident_%'` y ordena por `DATE_ADDED DESC`.
- **Sin mecanismo de limpieza actual**: No existe ningún job, worker, o función que elimine videos antiguos.

### 10.3 Comportamiento Deseado

| Paso | Descripción |
|------|-------------|
| 1 | La app se abre (MainActivity.onCreate()) |
| 2 | Se ejecuta la función de limpieza de videos antiguos en un coroutine en Dispatchers.IO |
| 3 | La función consulta MediaStore buscando archivos `incident_%` en Downloads |
| 4 | Para cada archivo, compara `DATE_ADDED` con la fecha actual |
| 5 | Si la diferencia es mayor a 72 horas (72 × 60 × 60 × 1000 ms), elimina el archivo |
| 6 | Registra cuántos archivos se eliminaron en el log (silencioso para el usuario) |

### 10.4 Puntos de Implementación

**IncidentRepository.kt**:
- Agregar una nueva función `cleanupOldIncidents(context: Context): Int` que:
  - Consulte MediaStore con la misma query que `scanIncidents()` pero seleccionando también la columna `DATE_ADDED`.
  - Para cada resultado, calcule la antigüedad: `System.currentTimeMillis() - (DATE_ADDED * 1000L)`.
  - Si la antigüedad es mayor a 72 horas, elimine el archivo usando `contentResolver.delete(uri, null, null)`.
  - Retorne la cantidad de archivos eliminados para logging.
- Agregar una constante `MAX_INCIDENT_AGE_MS = 72 * 60 * 60 * 1000L` (72 horas en milisegundos).

**MainActivity.kt**:
- En `onCreate()`, después de restaurar los settings y antes de iniciar el servicio en STANDBY, llamar a la función de limpieza en un coroutine en `Dispatchers.IO`.
- No es necesario mostrar nada al usuario — la limpieza es silenciosa.

### 10.5 Consideraciones

1. **DATE_ADDED vs DATE_MODIFIED**: MediaStore tiene dos columnas de fecha. Para el borrado automático, debemos usar `DATE_ADDED` porque queremos borrar videos antiguos, no videos que se copiaron o movieron recientemente.

2. **Videos en proceso de subida**: Si en el futuro se implementa la subida automática de videos a Supabase (para el plan Fleet), se debe verificar que el video no esté siendo subido antes de eliminarlo. Para MVP, esto no es un problema porque los videos no se suben automáticamente.

3. **Permisos en Android 10+**: En Android 10 y superior (scoped storage), se pueden eliminar archivos de MediaStore usando `contentResolver.delete()` sin necesidad de permisos adicionales, siempre que la app sea la que creó los archivos.

4. **Notificación al usuario**: No se requiere notificación. La limpieza es automática y silenciosa. Si el usuario quiere ver qué videos tiene, puede ir a la pantalla de Eventos.

5. **Consumo de recursos**: La consulta a MediaStore es una operación de base de datos ligera. El borrado de archivos es I/O de disco, pero se ejecuta una sola vez al abrir la app. **Impacto: negligible.**

6. **El umbral de 72 horas no es configurable**: Como se indicó, esto no debe ser configurable. La constante debe estar hardcodeada en el código.

### 10.6 Estimación de Cambios

| Archivo | Líneas aproximadas |
|---------|-------------------|
| IncidentRepository.kt | ~35 líneas |
| MainActivity.kt | ~5 líneas |
| **TOTAL** | **~40 líneas** |

---

## 11. MATRIZ DE IMPACTO POR ARCHIVO

| Archivo | A | B | C | D | E | F | G | H | Incons. | Total |
|---------|---|---|---|---|---|---|---|---|---------|-------|
| BackgroundCameraService.kt | ~15 | ~5 | ~10 | ~30 | ~40 | ~50 | ~40 | — | ~20 | **~210** |
| AppState.kt | ~3 | — | — | — | ~10 | — | — | — | — | **~13** |
| CameraServiceManager.kt | ~2 | — | — | — | ~2 | — | ~3 | — | — | **~7** |
| CameraServiceManagerAndroid.kt | ~5 | ~10 | — | — | ~5 | — | ~10 | — | — | **~30** |
| MonitorScreen.kt | — | — | — | — | ~25 | — | — | — | — | **~25** |
| SettingsScreen.kt | ~20 | — | — | — | — | — | ~25 | — | — | **~45** |
| SettingsManager.kt | — | — | — | — | — | — | ~3 | — | — | **~3** |
| SettingsManagerAndroid.kt | — | — | — | — | — | — | ~6 | — | — | **~6** |
| NotificationHelper.kt | — | — | — | — | — | ~5 | ~10 | — | — | **~15** |
| DuoVialApplication.kt | — | ~10 | — | — | — | — | — | — | — | **~10** |
| IncidentRepository.kt | — | — | — | — | — | — | — | ~35 | — | **~35** |
| MainActivity.kt | — | — | — | — | — | — | — | ~5 | — | **~5** |
| **TOTAL** | **~45** | **~25** | **~10** | **~30** | **~82** | **~55** | **~97** | **~40** | **~20** | **~404** |

---

## 12. PRIORIZACIÓN Y ORDEN DE IMPLEMENTACIÓN

### Fase A.1 — Quick Wins (Alta prioridad, bajo esfuerzo) — 1-2 días

| # | Cambio | Razón |
|---|--------|-------|
| C | Filtro de velocidad >30 km/h | Corrige falsos positivos críticos — el usuario se queja de videos guardados al bajarse del auto |
| A | Burbuja condicional + indicador en Configurar | Mejora UX inmediata — la burbuja no debería aparecer si el Vigilante no está corriendo |
| F | Botón de evento en pantalla de bloqueo | Funcionalidad pedida por el usuario — permite registrar eventos sin abrir la app |
| H | Borrado automático de videos >72 horas | Limpieza de almacenamiento — evita acumulación de videos innecesarios |

### Fase A.2 — Rendimiento (Alta prioridad, esfuerzo medio) — 1 día

| # | Cambio | Razón |
|---|--------|-------|
| I7 + I8 | Desactivar audio + configurar bitrate 2 Mbps | Ahorro de CPU y temperatura — el usuario reporta problemas de temperatura |
| I4 | Mover copyFile a hilo secundario | Previene freeze de UI durante guardado de eventos |
| I6 | Verificar resolución soportada de cámara | Previene crashes en dispositivos gama baja |

### Fase A.3 — Lifecycle y Robustez (Media prioridad) — 1-2 días

| # | Cambio | Razón |
|---|--------|-------|
| B | START_NOT_STICKY + verificaciones de lifecycle | Comportamiento correcto: servicio se detiene al cerrar la app |
| I1 + I2 + I3 | Limpiar referencias estáticas en onDestroy | Previene memory leaks y crashes |
| E | Monitoreo de temperatura | Seguridad del hardware — el usuario tiene problemas de temperatura en días soleados |

### Fase A.4 — Nuevas Features (Media prioridad) — 1-2 días

| # | Cambio | Razón |
|---|--------|-------|
| G | Auto-inicio del Vigilante a 30 km/h | Conveniencia — el usuario olvida activar la app antes de conducir |

### Fase A.5 — Validación (Después de implementar todo) — 1 día

| # | Actividad |
|---|-----------|
| 1 | Testing en 3+ dispositivos (gama baja, media, alta) |
| 2 | Benchmark de rendimiento antes/después de optimizaciones D1 y D2 |
| 3 | Validar comportamiento en background durante 2+ horas de conducción |
| 4 | Verificar que el servicio se detiene al cerrar la app desde recientes |
| 5 | Probar botón de pantalla de bloqueo en diferentes fabricantes (Samsung, Xiaomi, Huawei) |
| 6 | Probar monitoreo de temperatura con dispositivo expuesto al sol |
| 7 | Probar auto-inicio: alcanzar 30 km/h y verificar que el servicio se activa |
| 8 | Probar borrado automático: crear video manual con timestamp antiguo y verificar que se elimina |

---

## NOTAS FINALES

- Este documento **no contiene código de implementación**. Cada funcionalidad debe ser implementada por el agente o desarrollador que la tome.
- Los archivos a modificar están identificados con líneas específicas para minimizar el tiempo de búsqueda.
- Las consideraciones de rendimiento están cuantificadas donde es posible.
- El orden de implementación está priorizado por impacto y esfuerzo.
- Cada cambio debe ser testeado antes de pasar al siguiente, especialmente los cambios de lifecycle (B) que pueden tener efectos secundarios en diferentes fabricantes.
- Las inconsistencias documentadas en la sección 2 deben resolverse como parte de los cambios, no como tareas separadas.

**Documento creado**: Julio 2, 2026
**Autor**: Tech Lead / Architect Agent
**Versión**: 3.0 (agregadas features G y H)
