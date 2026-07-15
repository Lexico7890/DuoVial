# FASE B — Onboarding y UX

**Fecha**: Julio 14, 2026
**Objetivo**: Primera impresión del usuario. Onboarding informativo, solicitud de permisos, y validación del reproductor de video.
**Alcance**: Especificaciones y análisis para implementación. El código es escrito por el agente que implemente cada funcionalidad.
**Estado**: Reproductor de video ya implementado (IncidentPlayerScreen.kt). Onboarding y permisos pendientes.
**Dependencias**: Fase A completada (estabilidad del Vigilante), MainActivity.kt, EventsScreen.kt, IncidentPlayerScreen.kt

---

## TABLA DE CONTENIDOS

1. [Resumen de Cambios](#1-resumen-de-cambios)
2. [B-01: Pantalla de Onboarding con Características Principales](#2-b-01-pantalla-de-onboarding-con-características-principales)
3. [B-02: Solicitud Granular de Permisos](#3-b-02-solicitud-granular-de-permisos)
4. [B-03: Reproductor de Video Integrado — Validación y Mejoras](#4-b-03-reproductor-de-video-integrado--validación-y-mejoras)
5. [Matriz de Impacto por Archivo](#5-matriz-de-impacto-por-archivo)
6. [Priorización y Orden de Implementación](#6-priorización-y-orden-de-implementación)

---

## 1. RESUMEN DE CAMBIOS

| # | Requerimiento | Archivos Principales | Complejidad | Prioridad |
|---|--------------|----------------------|-------------|-----------|
| B-01 | Pantalla de Onboarding con características principales | Nuevo: `OnboardingScreen.kt`, `OnboardingManager.kt`. Modificar: `MainActivity.kt` | Media | Alta |
| B-02 | Solicitud granular de permisos integrada en Onboarding | `OnboardingScreen.kt`, `MainActivity.kt` | Media | Alta |
| B-03 | Validación del reproductor de video (clips secuenciales) | `IncidentPlayerScreen.kt`, `IncidentVideoView.kt` | Baja | Media |

---

## 2. B-01: PANTALLA DE ONBOARDING CON CARACTERÍSTICAS PRINCIPALES

### 2.1 Contexto

El onboarding es la primera experiencia del usuario con DuoVial. Debe comunicar claramente qué hace la app y por qué es útil, sin abrumar con información técnica. El objetivo es que el usuario entienda las 3 capacidades principales en menos de 60 segundos.

**Nota importante**: NO se mencionarán problemas técnicos potenciales (como daño OIS por vibración). El onboarding es informativo y positivo, enfocado en el valor que la app proporciona.

### 2.2 Estado Actual

No existe pantalla de onboarding. La app abre directamente en `MonitorScreen` después del login. El usuario no recibe explicación de las funcionalidades ni se le indica cómo empezar.

### 2.3 Comportamiento Deseado

El onboarding debe mostrarse **solo la primera vez** que el usuario abre la app (o después de un reset de fábrica). Se compone de 3-4 pantallas deslizables con las siguientes características:

#### Pantalla 1: Bienvenida

| Elemento | Contenido |
|----------|-----------|
| Imagen/Ícono | Logo de DuoVial o ilustración representativa |
| Título | "Bienvenido a DuoVial" |
| Subtítulo | "Tu dash cam inteligente de bajo consumo" |
| Texto | "DuoVial convierte tu teléfono en una cámara de vigilancia inteligente para tu vehículo. Detecta incidentes, monitorea fatiga y protege tu inversión." |

#### Pantalla 2: El Vigilante

| Elemento | Contenido |
|----------|-----------|
| Imagen/Ícono | Ícono de cámara o ilustración de buffer circular |
| Título | "El Vigilante" |
| Subtítulo | "Evidencia automática de incidentes" |
| Texto | "La cámara trasera graba continuamente los últimos 30 segundos. Cuando ocurre un evento (frenada brusca, impacto o botón de pánico), el video se guarda automáticamente. Sin llenar almacenamiento, sin agotar batería." |
| Bullet points | • Buffer circular de bajo consumo • Detección por acelerómetro y botón de pánico • Guardado automático de evidencia |

#### Pantalla 3: Anti-Somnolencia

| Elemento | Contenido |
|----------|-----------|
| Imagen/Ícono | Ícono de ojo o ilustración de detección facial |
| Título | "Anti-Somnolencia" |
| Subtítulo | "Tu copiloto de seguridad" |
| Texto | "DuoVial monitorea tu estado de alerta usando la cámara frontal y wearables compatibles. Si detecta somnolencia, te alerta antes de que sea peligroso." |
| Bullet points | • Detección de parpadeo con IA • Integración con wearables (frecuencia cardíaca) • Alertas progresivas para prevenir accidentes |

#### Pantalla 4: Monitoreo del Vehículo (Opcional)

| Elemento | Contenido |
|----------|-----------|
| Imagen/Ícono | Ícono de dashboard o ilustración de métricas |
| Título | "Monitoreo Completo" |
| Subtítulo | "Datos de tu vehículo en tiempo real" |
| Texto | "Velocidad, fuerza G, ubicación y más. DuoVial registra todo para que tengas evidencia completa de cada viaje." |
| Bullet points | • Velocímetro GPS en tiempo real • Registro de fuerza G • Historial de eventos y viajes |

#### Pantalla 5: Configuración Flexible

| Elemento | Contenido |
|----------|-----------|
| Imagen/Ícono | Ícono de engranaje o ilustración de settings |
| Título | "Tú tienes el control" |
| Subtítulo | "Todo es configurable" |
| Texto | "Ajusta los umbrales de detección, habilita o deshabilita funciones, y personaliza DuoVial según tus necesidades. La app se adapta a ti." |
| Bullet points | • Umbral de G-Force configurable • Auto-inicio opcional • Preferencias de notificación |

#### Botón final: "Empezar a usar DuoVial"

Al tocar este botón, se marca el onboarding como completado y se navega a la solicitud de permisos (B-02).

### 2.4 Puntos de Implementación

**Nuevo archivo: `OnboardingScreen.kt`** (en `commonMain`):

```
Responsabilidades:
- Mostrar las 5 pantallas del onboarding con navegación horizontal (swipe)
- Indicadores de página (dots) en la parte inferior
- Botón "Siguiente" en pantallas intermedias
- Botón "Empezar a usar DuoVial" en la última pantalla
- Animaciones suaves entre pantallas (fade o slide)
```

**Componentes UI recomendados**:
- `HorizontalPager` de Compose Foundation para navegación swipe
- `PageIndicator` custom o `PagerState` para los dots
- `Card` o `Column` para el contenido de cada pantalla
- Íconos de Material Icons o ilustraciones custom en `assets/`

**Nuevo archivo: `OnboardingManager.kt`** (en `commonMain`):

```
Responsabilidades:
- Persistir estado de onboarding completado (SharedPreferences/DataStore)
- Función `isOnboardingCompleted(): Boolean`
- Función `markOnboardingAsCompleted()`
- Función `resetOnboarding()` (para testing o settings)
```

**Modificación: `MainActivity.kt`** (o `DuoVialApp.kt`):

```
Cambios:
- En el inicio de la app, verificar si el onboarding fue completado
- Si NO fue completado → navegar a OnboardingScreen
- Si YA fue completado → navegar a MonitorScreen (comportamiento actual)
- Después de completar onboarding → navegar a solicitud de permisos (B-02)
```

### 2.5 Consideraciones

1. **Persistencia del estado**: Usar `SharedPreferences` (vía `russhwolf/settings`) para guardar `onboarding_completed = true`. Esta persistencia debe sobrevivir cierres de app y reinicios del dispositivo.

2. **Onboarding no skippable**: Para MVP, el usuario DEBE ver todas las pantallas. No hay botón "Saltar". Esto asegura que el usuario reciba la información mínima necesaria. En futuras versiones, se podría agregar un botón "Saltar" para usuarios que reinstantan.

3. **Imágenes vs Íconos**: Para MVP, usar íconos de Material Icons. Las ilustraciones custom pueden agregarse en una fase futura para mejorar la experiencia visual.

4. **Idioma**: Todo el texto del onboarding debe estar en español. La localización a otros idiomas es post-MVP.

5. **Performance**: El onboarding es una pantalla estática sin operaciones costosas. No hay impacto en rendimiento.

6. **Testing**: Crear una función `resetOnboarding()` accesible desde Settings para que los testers puedan repetir el flujo de onboarding sin reinstalar la app.

### 2.6 Estimación de Cambios

| Archivo | Líneas aproximadas |
|---------|-------------------|
| OnboardingScreen.kt (nuevo) | ~200 líneas |
| OnboardingManager.kt (nuevo) | ~40 líneas |
| MainActivity.kt o DuoVialApp.kt | ~15 líneas |
| **TOTAL** | **~255 líneas** |

---

## 3. B-02: SOLICITUD GRANULAR DE PERMISOS

### 3.1 Contexto

DuoVial requiere múltiples permisos del sistema para funcionar: cámara, ubicación, almacenamiento, notificaciones, overlay, entre otros. Android requiere que estos permisos se soliciten en tiempo de ejecución. La mejor práctica es solicitarlos todos juntos pero con explicaciones claras de por qué se necesita cada uno, antes de mostrar el diálogo del sistema.

### 3.2 Estado Actual

Los permisos se solicitan actualmente de forma directa al abrir ciertas pantallas (por ejemplo, la cámara se pide al iniciar el Vigilante). No hay una explicación previa al usuario ni un flujo centralizado de solicitud.

### 3.3 Comportamiento Deseado

Después de completar el onboarding (B-01), se muestra una pantalla que explica los permisos necesarios y los solicita todos juntos.

#### Pantalla de Permisos

| Elemento | Contenido |
|----------|-----------|
| Título | "Permisos necesarios" |
| Subtítulo | "DuoVial necesita acceso a los siguientes servicios de tu teléfono para funcionar correctamente" |

#### Lista de Permisos con Explicaciones

| Permiso | Ícono | Título | Explicación |
|---------|-------|--------|-------------|
| `CAMERA` | 📷 | Cámara | "Para grabar video del frente y la carretera. La cámara trasera es el Vigilante; la frontal es para detección de somnolencia." |
| `ACCESS_FINE_LOCATION` | 📍 | Ubicación precisa | "Para registrar tu velocidad y ubicación exacta durante los viajes. Necesario para el velocímetro y geolocalización de incidentes." |
| `POST_NOTIFICATIONS` | 🔔 | Notificaciones | "Para alertarte sobre incidentes, recordatorios de mantenimiento y avisos de seguridad." |
| `SYSTEM_ALERT_WINDOW` | 🪟 | Ventana superpuesta | "Para mostrar la burbuja flotante de pánico sobre otras apps. Permite registrar eventos sin abrir DuoVial." |
| `ACTIVITY_RECOGNITION` | 🏃 | Reconocimiento de actividad | "Para detectar automáticamente cuándo estás conduciendo y activar el Vigilante sin que lo hagas manualmente." |

#### Permisos Opcionales (Fleet/Premium)

| Permiso | Ícono | Título | Explicación | Plan |
|---------|-------|--------|-------------|------|
| `BLUETOOTH_CONNECT` | 🔵 | Bluetooth | "Para conectar el dongle OBD II y leer datos mecánicos del vehículo." | Premium/Fleet |
| `HEALTH_CONNECT` | ❤️ | Health Connect | "Para leer datos de tu wearable (frecuencia cardíaca, HRV) y mejorar la detección de fatiga." | Premium/Fleet |

#### Botones

- **"Conceder todos los permisos"**: Abre los diálogos del sistema uno por uno
- **"Configurar después"**: Permite omitir la solicitud (la app pedirá permisos individualmente cuando sean necesarios)

### 3.4 Puntos de Implementación

**Modificación: `OnboardingScreen.kt`** (o nueva pantalla separada):

```
Responsabilidades:
- Mostrar la lista de permisos con explicaciones antes de solicitarlos
- Agrupar permisos en "Requeridos" y "Opcionales"
- Mostrar indicador de estado para cada permiso (concedido/no concedido)
- Botón para solicitar todos los permisos secuencialmente
- Botón para omitir (configurar después)
```

**Flujo de solicitud**:

```
1. Usuario toca "Conceder permisos"
2. Se muestra explicación de Cámara → se solicita permiso de Cámara
3. Si concede → se muestra check verde, avanza al siguiente
4. Si deniega → se muestra X roja, avanza al siguiente (no bloquea)
5. Se repite para cada permiso
6. Al finalizar → se muestra resumen de permisos concedidos/denegados
7. Se navega a MonitorScreen
```

**Modificación: `MainActivity.kt`**:

```
Cambios:
- Verificar si todos los permisos requeridos fueron concedidos
- Si faltan permisos críticos (CÁMARA, UBICACIÓN) → mostrar advertencia en MonitorScreen
- Los permisos denegados se pueden solicitar nuevamente desde SettingsScreen
```

**Integración con SettingsScreen**:

```
Cambios en SettingsScreen.kt:
- Agregar sección "Permisos de la app"
- Mostrar estado de cada permiso (concedido/no concedido)
- Botón para abrir configuración de permisos de Android
- Botón para re-solicitar permiso específico
```

### 3.5 Permisos por Funcionalidad

Para claridad, esta tabla muestra qué funcionalidad requiere qué permiso:

| Funcionalidad | Permisos Requeridos |
|---------------|---------------------|
| Vigilante (buffer circular) | `CAMERA`, `FOREGROUND_SERVICE` |
| Velocímetro GPS | `ACCESS_FINE_LOCATION` |
| Acelerómetro | Ninguno (sensor del sistema) |
| Botón de pánico (notificación) | `POST_NOTIFICATIONS` |
| Burbuja flotante | `SYSTEM_ALERT_WINDOW` |
| Auto-inicio por actividad | `ACTIVITY_RECOGNITION` |
| Anti-somnolencia (cámara frontal) | `CAMERA` |
| OBD II (Bluetooth) | `BLUETOOTH_CONNECT` |
| Wearables (Health Connect) | `HEALTH_CONNECT` |
| Geofencing | `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` |

### 3.6 Consideraciones

1. **Permisos denegados no bloquean la app**: Si el usuario deniega un permiso, la app sigue funcionando con funcionalidad reducida. Por ejemplo, sin `CAMERA` no puede usar el Vigilante, pero puede ver el historial de eventos.

2. **Re-solicitud de permisos**: Si el usuario deniega un permiso y luego intenta usar la funcionalidad que lo requiere, la app debe mostrar un diálogo explicativo antes de re-solicitar el permiso. Si el usuario marcó "No preguntar de nuevo", debe redirigir a la configuración de Android.

3. **Permisos en Android 13+**: `POST_NOTIFICATIONS` se solicita explícitamente en Android 13+. En versiones anteriores, las notificaciones están habilitadas por defecto.

4. **Permisos en Android 14+**: `ACTIVITY_RECOGNITION` requiere verificación adicional en Android 14+.

5. **Privacidad**: La explicación de cada permiso debe ser clara y honesta. No exagerar la necesidad del permiso.

### 3.7 Estimación de Cambios

| Archivo | Líneas aproximadas |
|---------|-------------------|
| OnboardingScreen.kt (modificación) | ~120 líneas |
| MainActivity.kt (modificación) | ~20 líneas |
| SettingsScreen.kt (modificación) | ~40 líneas |
| **TOTAL** | **~180 líneas** |

---

## 4. B-03: REPRODUCTOR DE VIDEO INTEGRADO — VALIDACIÓN Y MEJORAS

### 4.1 Estado Actual

El reproductor de video ya está implementado en `IncidentPlayerScreen.kt` usando ExoPlayer. La implementación actual:

- ✅ Usa ExoPlayer para reproducción de video
- ✅ Tiene controles de reproducción (play/pause)
- ✅ Tiene slider de progreso
- ✅ Tiene botón de retroceso
- ✅ Muestra la fecha del incidente
- ✅ Tiene controles que se ocultan automáticamente tras 4 segundos
- ✅ Soporta múltiples clips (muestra "1 / 3" cuando hay más de un clip)

### 4.2 Validación del Comportamiento de Múltiples Clips

**Comportamiento esperado**: Cuando un incidente tiene múltiples clips (ej: 3 clips de 15 segundos cada uno), el reproductor debe reproducirlos secuencialmente como si fuera un solo video continuo de 45 segundos.

**Validación necesaria**:

| Punto de Validación | Descripción |
|---------------------|-------------|
| Reproducción secuencial | Los clips se reproducen uno tras otro sin interrupción visible |
| Transición suave | No hay pantalla negra ni parpadeo entre clips |
| Slider continuo | El slider de progreso muestra el avance total (no se reinicia entre clips) |
| Tiempo continuo | El contador de tiempo muestra el tiempo total acumulado (00:00 → 00:45) |
| Índice de clip actual | El indicador "1 / 3" se actualiza al cambiar de clip |
| Seek entre clips | El usuario puede buscar a cualquier posición, incluso en un clip diferente |
| Play/Pause global | Play/pause funciona correctamente en cualquier punto de la reproducción |

### 4.3 Análisis del Código Actual

Revisando `IncidentPlayerScreen.kt`:

```kotlin
// Línea 96-101: Creación del video view
IncidentVideoView(
    parts = incident.parts,
    modifier = Modifier.fillMaxSize(),
    onPlayerCreated = { exoPlayer -> player = exoPlayer },
    onPlayerError = {}
)
```

**Puntos a verificar en `IncidentVideoView`**:

1. **¿Cómo se configuran los múltiples clips?** → Deben agregarse como `MediaItem` secuenciales en el `ExoPlayer`
2. **¿Se usa `ExoPlayer.setMediaItems(lista)`?** → Esto permite reproducción secuencial automática
3. **¿El slider refleja la duración total?** → `p.duration` debe retornar la duración de TODOS los clips combinados
4. **¿El seek funciona entre clips?** → `p.seekTo(position)` debe funcionar con posiciones que abarcan múltiples clips

### 4.4 Puntos de Validación y Mejoras

**Archivo: `IncidentVideoView.kt`** (o donde se configure el ExoPlayer):

```
Validar que:
1. Los clips se agregan como lista de MediaItems: player.setMediaItems(mediaItems)
2. Se llama a player.prepare() después de setMediaItems
3. El player está configurado con playWhenReady = true para auto-reproducción
4. No hay buffers excesivos entre clips (usar setBufferingPreloadAheadMs si es necesario)
```

**Archivo: `IncidentPlayerScreen.kt`**:

```
Validar que:
1. El slider usa la duración TOTAL del player (no de un clip individual)
2. El seekTo funciona correctamente para posiciones en clips posteriores
3. El indicador de clip actual se actualiza en el LaunchedEffect del player
4. El tiempo mostrado es acumulado (no reinicia por clip)
```

### 4.5 Posibles Mejoras (Si se encuentran problemas)

| Mejora | Descripción | Prioridad |
|--------|-------------|-----------|
| Precarga de clips | Usar `ExoPlayer.Builder().setMediaSourceFactory()` con precarga para transiciones suaves | Alta |
| Buffer entre clips | Configurar `setBufferingPreloadAheadMs()` para reducir lag entre clips | Media |
| Indicador de carga | Mostrar spinner cuando el siguiente clip está cargando | Baja |
| Gestos de seek | Permitir seek con gestos de deslizamiento (adelante/atrás 10 segundos) | Baja |

### 4.6 Consideraciones

1. **Formato de los clips**: Los clips son archivos MP4 individuales guardados en `Downloads/DuoVial/`. ExoPlayer soporta reproducción secuencial de múltiples archivos nativamente.

2. **Duración total vs individual**: `ExoPlayer.getDuration()` retorna la duración del clip actual, no la total. Para obtener la duración total, se debe sumar la duración de todos los clips o usar `ExoPlayer.getContentDuration()` si se configura correctamente.

3. **Seek entre clips**: `ExoPlayer.seekTo(positionMs)` funciona con posiciones absolutas. Si el clip 1 dura 15s y el clip 2 dura 15s, `seekTo(20000)` debe posicionarse en el segundo clip a los 5 segundos.

4. **Error handling**: El callback `onPlayerError` está vacío actualmente. Debería mostrar un mensaje al usuario si un clip no se puede reproducir.

5. **Memory leaks**: El ExoPlayer debe liberarse en `onDispose()` del Composable. Verificar que esto esté implementado.

### 4.7 Estimación de Cambios

| Archivo | Líneas aproximadas |
|---------|-------------------|
| IncidentVideoView.kt (validación) | ~30 líneas (si hay mejoras) |
| IncidentPlayerScreen.kt (validación) | ~20 líneas (si hay mejoras) |
| **TOTAL** | **~50 líneas** (solo si se encuentran problemas) |

---

## 5. MATRIZ DE IMPACTO POR ARCHIVO

| Archivo | B-01 | B-02 | B-03 | Total |
|---------|------|------|------|-------|
| OnboardingScreen.kt (nuevo) | ~200 | ~120 | — | **~320** |
| OnboardingManager.kt (nuevo) | ~40 | — | — | **~40** |
| MainActivity.kt | ~15 | ~20 | — | **~35** |
| SettingsScreen.kt | — | ~40 | — | **~40** |
| IncidentPlayerScreen.kt | — | — | ~20 | **~20** |
| IncidentVideoView.kt | — | — | ~30 | **~30** |
| **TOTAL** | **~255** | **~180** | **~50** | **~485** |

---

## 6. PRIORIZACIÓN Y ORDEN DE IMPLEMENTACIÓN

### Fase B.1 — Onboarding (Alta prioridad) — 2-3 días

| # | Cambio | Razón |
|---|--------|-------|
| 1 | Crear OnboardingManager.kt | Persistencia del estado de onboarding |
| 2 | Crear OnboardingScreen.kt con las 5 pantallas | Primera impresión del usuario |
| 3 | Integrar en MainActivity.kt | Flujo de inicio de la app |
| 4 | Testing del flujo completo | Validar que se muestra solo la primera vez |

### Fase B.2 — Permisos (Alta prioridad) — 1-2 días

| # | Cambio | Razón |
|---|--------|-------|
| 5 | Agregar pantalla de permisos al onboarding | Solicitud centralizada de permisos |
| 6 | Implementar flujo de solicitud secuencial | UX clara con explicaciones |
| 7 | Agregar sección de permisos en SettingsScreen | Re-solicitud de permisos denegados |
| 8 | Testing en Android 12, 13, 14 | Validar comportamiento por versión |

### Fase B.3 — Reproductor (Media prioridad) — 1 día

| # | Cambio | Razón |
|---|--------|-------|
| 9 | Validar reproducción secuencial de múltiples clips | Core experience del reproductor |
| 10 | Validar seek entre clips | Funcionalidad esperada por el usuario |
| 11 | Corregir problemas encontrados (si hay) | Calidad del reproductor |
| 12 | Testing con incidentes de 1, 2 y 3 clips | Cobertura de casos |

### Fase B.4 — Validación Final (Después de implementar) — 1 día

| # | Actividad |
|---|-----------|
| 1 | Testing del flujo completo: Onboarding → Permisos → MonitorScreen |
| 2 | Testing de re-solicitud de permisos desde SettingsScreen |
| 3 | Testing del reproductor con múltiples clips en diferentes dispositivos |
| 4 | Verificar que el onboarding NO se muestra en la segunda apertura |
| 5 | Verificar que la app funciona correctamente con permisos denegados |

---

## NOTAS FINALES

- Este documento **no contiene código de implementación**. Cada funcionalidad debe ser implementada por el agente o desarrollador que la tome.
- El onboarding es informativo y positivo. NO mencionar problemas técnicos potenciales.
- Los permisos se solicitan todos juntos con explicaciones claras, pero el usuario puede omitir la solicitud.
- El reproductor de video ya está implementado. La fase B se enfoca en validar que los múltiples clips se reproduzcan como un solo video continuo.
- El orden de implementación es: Onboarding → Permisos → Reproductor.
- Cada cambio debe ser testeado antes de pasar al siguiente.

**Documento creado**: Julio 14, 2026
**Autor**: Tech Lead / Architect Agent
**Versión**: 1.0
