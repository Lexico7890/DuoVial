# 🤖 AGENT.md — GUÍA PARA AGENTES DE DESARROLLO DUOVIAL

**Versión**: 1.0
**Última actualización**: Junio 30, 2026
**Propósito**: Contexto, reglas, buenas prácticas y estándares para todos los agentes (IA y humanos) que trabajen en este proyecto.

---

## 📌 TABLA DE CONTENIDOS

1. [Resumen del Proyecto](#1-resumen-del-proyecto)
2. [Arquitectura Técnica](#2-arquitectura-técnica)
3. [Estructura del Código](#3-estructura-del-código)
4. [Patrones de Diseño Obligatorios](#4-patrones-de-diseño-obligatorios)
5. [Convenciones de Código](#5-convenciones-de-código)
6. [Gestión de Estado](#6-gestión-de-estado)
7. [Comunicación UI ↔ Servicio](#7-comunicación-ui--servicio)
8. [Reglas de Seguridad y Privacidad](#8-reglas-de-seguridad-y-privacidad)
9. [Buenas Prácticas de Rendimiento](#9-buenas-prácticas-de-rendimiento)
10. [Testing](#10-testing)
11. [Git y Commits](#11-git-y-commits)
12. [Delegación de Tareas](#12-delegación-de-tareas)
13. [Checklist Antes de Entregar](#13-checklist-antes-de-entregar)
14. [Referencias](#14-referencias)

---

## 1. RESUMEN DEL PROYECTO

### Qué es DuoVial
Una **app Android** (Kotlin Multiplatform + Compose Multiplatform) que convierte cualquier teléfono en una **dash cam inteligente de bajo consumo**. Incluye un **Dashboard web** para empresas (plan Fleet).

### Problema que resuelve
- Accidentes sin evidencia → video como prueba
- Fatiga al volante → detección anti-somnolencia
- Falta de control de flotas → monitoreo centralizado

### Lo que NO es
- ❌ No es una dash cam de hardware
- ❌ No funciona en iPhone (iOS bloquea cámara en background)
- ❌ No graba continuamente 2+ horas (sobrecalienta)
- ❌ No bloquea ni apaga vehículos (facial es solo alerta)
- ❌ No reemplaza un sistema GPS dedicado

### Planes
| Plan | Precio | Características clave |
|------|--------|----------------------|
| Free | $0 | Buffer circular, auto-inicio, anti-somnolencia básico |
| Por Evento | $19,900 COP | Procesamiento y descarga de videos por evento |
| Premium | $10,900 COP/mes | 3 videos/mes, anti-somnolencia 3 niveles, OBD II, colisión + llamada |
| Fleet | $9,900 COP/mes/vehículo | Todo Premium + Dashboard web, geofencing, facial, métricas |

---

## 2. ARQUITECTURA TÉCNICA

### Stack tecnológico
| Componente | Tecnología |
|------------|-----------|
| **App Móvil** | Kotlin Multiplatform + Compose Multiplatform |
| **Cámara Trasera** | CameraX (Preview) — Buffer circular |
| **Cámara Frontal** | CameraX (Preview + ImageAnalysis) — Anti-somnolencia |
| **Sensores** | SensorManager nativo (acelerómetro) |
| **GPS** | LocationManager nativo (velocímetro) |
| **Background** | LifecycleService + Foreground Service |
| **Auto-Inicio** | Activity Recognition API |
| **Persistencia local** | Multiplatform Settings (SharedPreferences) + SQLite |
| **Watchdog** | WorkManager |
| **Comunicación UI↔Servicio** | StateFlow / SharedFlow + callbacks nativos |
| **IA Facial** | ML Kit Face Detection |
| **Wearables** | Health Connect |
| **Geofencing** | Geofencing API (Google Play Services) |
| **OBD II** | Bluetooth LE + ELM327 |
| **Backend/DB** | Supabase (PostgreSQL + pgvector) |
| **Auth** | Supabase Auth (migrando desde AWS Cognito) |
| **Realtime** | Supabase Realtime |
| **Storage** | Supabase Storage |
| **Video Processing** | Mux (HLS/DASH, CDN) |
| **Pagos** | Stripe + Supabase Stripe Sync Engine |
| **Push Notifications** | OneSignal |
| **Llamadas Automáticas** | Twilio API |
| **Build** | Gradle + Android Studio |
| **CI/CD** | GitHub Actions |

### Configuración de build
- **Kotlin**: 2.1.0
- **Compose Multiplatform**: 1.7.3
- **minSdk**: 26 (Android 8.0 Oreo)
- **targetSdk**: 35 (Android 15)
- **JVM target**: 17
- **CameraX**: 1.4.1
- **ML Kit Face Detection**: 16.1.7
- **Media3 (ExoPlayer)**: 1.5.1
- **Multiplatform Settings**: 1.2.0

---

## 3. ESTRUCTURA DEL CÓDIGO

```
kmp/
├── build.gradle.kts                          # Root build (plugins)
├── settings.gradle.kts                       # Project settings
├── gradle.properties                         # Gradle config
├── gradle/libs.versions.toml                 # Version catalog
└── composeApp/
    ├── build.gradle.kts                      # App-level build
    ├── proguard-rules.pro                    # ProGuard rules
    └── src/
        ├── commonMain/kotlin/com/duovial/
        │   ├── App.kt                        # Root composable (Scaffold + bottom nav)
        │   ├── auth/
        │   │   ├── AuthService.kt            # Interface de autenticación
        │   │   ├── AuthState.kt              # AuthUser, AuthState, AuthStateManager
        │   │   └── AuthLocator.kt            # LocalAuthService CompositionLocal
        │   ├── components/
        │   │   ├── GlassSurface.kt           # Modifier.glass() glassmorphism
        │   │   └── PlatformBlur.kt           # expect fun platformBlur
        │   ├── platform/
        │   │   ├── Platform.kt               # expect funs: CameraPreview, FrontCameraPreview, IncidentPlayerScreen
        │   │   └── NumberFormat.kt           # Double.formatDecimal()
        │   ├── screens/
        │   │   ├── MonitorScreen.kt          # Dash cam principal (cámara trasera, G-force, velocidad)
        │   │   ├── EventsScreen.kt           # Lista de incidentes guardados
        │   │   ├── FatigueScreen.kt          # Cámara frontal + detección de fatiga
        │   │   ├── SettingsScreen.kt         # Configuración (umbral G-Force, permisos)
        │   │   ├── AccountScreen.kt          # Perfil de usuario
        │   │   └── LoginScreen.kt            # Login/Registro
        │   ├── state/
        │   │   ├── AppState.kt               # AppStateManager (StateFlow central)
        │   │   ├── CameraServiceManager.kt   # Interface del servicio de cámara
        │   │   ├── SettingsManager.kt        # Interface de settings
        │   │   └── ServiceLocator.kt         # LocalCameraServiceManager CompositionLocal
        │   └── theme/
        │       └── Theme.kt                  # DuoVialTheme (dark theme, neon colors)
        │
        └── androidMain/kotlin/com/duovial/
            ├── DuoVialApplication.kt         # Application class (notification channels)
            ├── DuoVialConfig.kt              # Config (AWS Cognito - migrando a Supabase)
            ├── MainActivity.kt               # Entry point
            ├── components/
            │   └── PlatformBlur.kt           # actual fun platformBlur (RenderEffect)
            ├── platform/
            │   ├── Platform.android.kt       # actual funs delegan a views
            │   ├── CameraPreview.kt          # CameraPreviewView (AndroidView + PreviewView)
            │   ├── FrontCameraPreview.kt     # AndroidView para cámara frontal
            │   ├── CameraServiceManagerAndroid.kt # Implementación Android del manager
            │   ├── SettingsManagerAndroid.kt # Implementación Android (russhwolf)
            │   ├── Permissions.android.kt    # Verificación de permisos
            │   ├── NotificationHelper.kt     # Canales de notificación
            │   ├── AuthServiceAndroid.kt     # Auth (demo mode → migrando a Supabase)
            │   ├── IncidentPlayerScreen.kt   # Reproductor ExoPlayer
            │   ├── IncidentRepository.kt     # Escaneo de incidentes en MediaStore
            │   ├── IncidentVideoView.kt      # ExoPlayer con videos concatenados
            │   └── MarkedProgressBar.kt      # Progress bar con marcadores
            └── services/
                ├── BackgroundCameraService.kt    # ⚡ CORE: 1189 líneas, Foreground Service
                ├── FatigueCameraManager.kt       # Cámara frontal CameraX + ImageAnalysis
                ├── FaceProcessor.kt              # ML Kit + EAR calculation
                └── FrontFaceDetector.kt          # ⚠️ LEGACY (Camera2, no usar)
```

### Archivos críticos (NO modificar sin revisión)
| Archivo | Razón |
|---------|-------|
| `BackgroundCameraService.kt` | Corazón de la app. Buffer circular, sensores, GPS, floating bubble |
| `AppState.kt` | Estado centralizado. Cualquier cambio afecta toda la UI |
| `CameraServiceManagerAndroid.kt` | Puente UI ↔ Servicio. Maneja intents y callbacks |
| `FatigueCameraManager.kt` | Pipeline de detección de fatiga. CameraX + ML Kit |
| `FaceProcessor.kt` | Cálculo de EAR. Lógica crítica de detección |

### Código legacy (NO usar, considerar eliminación)
| Archivo | Estado | Reemplazo |
|---------|--------|-----------|
| `FrontFaceDetector.kt` | ⚠️ Legacy (Camera2) | `FatigueCameraManager.kt` (CameraX) |
| `AuthServiceAndroid.kt` | ⚠️ Demo mode | Migrando a Supabase Auth |
| `DuoVialConfig.kt` | ⚠️ AWS Cognito | Migrando a Supabase |

---

## 4. PATRONES DE DISEÑO OBLIGATORIOS

### 4.1 Interface + Implementación (Platform Abstraction)
**SIEMPRE** usar interfaces en `commonMain` e implementaciones en `androidMain`.

```kotlin
// commonMain
interface CameraServiceManager {
    fun startStandby()
    fun startRecording()
    fun triggerPanic()
    // ...
}

// androidMain
class CameraServiceManagerAndroid(
    private val context: Context
) : CameraServiceManager {
    override fun startStandby() { /* Intent al servicio */ }
    // ...
}
```

### 4.2 expect/actual para componentes platform-specific
**SIEMPRE** usar `expect` en common y `actual` en android para previews y componentes nativos.

```kotlin
// commonMain/platform/Platform.kt
@Composable
expect fun CameraPreview(modifier: Modifier = Modifier)

// androidMain/platform/Platform.android.kt
@Composable
actual fun CameraPreview(modifier: Modifier) {
    CameraPreviewView(modifier)
}
```

### 4.3 CompositionLocal para inyección de dependencias
**SIEMPRE** usar CompositionLocal para servicios accesibles desde la UI.

```kotlin
val LocalCameraServiceManager = compositionLocalOf<CameraServiceManager> {
    error("No CameraServiceManager provided")
}

// En DuoVialApp:
CompositionLocalProvider(LocalCameraServiceManager provides manager) {
    // ...
}
```

### 4.4 Servicio como Fuente Única de Verdad
**REGLA DE ORO**: El `BackgroundCameraService` es la única fuente de verdad del estado de la cámara. La UI es un espejo que se sincroniza.

- La UI **NUNCA** mantiene estado de cámara independiente
- La UI **SIEMPRE** observa `AppStateManager` que refleja el estado del servicio
- El servicio **NUNCA** depende de la UI para su lógica

### 4.5 Singleton para StateManagers
`AppStateManager` y `AuthStateManager` son **objects** (singletons en Kotlin).

```kotlin
object AppStateManager {
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    // ...
}
```

---

## 5. CONVENCIONES DE CÓDIGO

### 5.1 Idioma
- **UI labels**: Español (Monitor, Eventos, Configurar, Cuenta)
- **Enums de estado**: Español (`INACTIVO`, `INICIANDO`, `ACTIVO`, `GUARDANDO`, `ERROR`)
- **Código (variables, funciones, clases)**: Inglés
- **Comentarios**: Español (para contexto del equipo)
- **Documentación técnica**: Español

### 5.2 Naming
| Tipo | Convención | Ejemplo |
|------|-----------|---------|
| Clases | PascalCase | `BackgroundCameraService`, `FatigueCameraManager` |
| Funciones | camelCase | `startRecording()`, `triggerPanic()` |
| Variables | camelCase | `gForceThreshold`, `isSavingEvent` |
| Constantes | UPPER_SNAKE_CASE | `MAX_RETRY_ATTEMPTS`, `DEFAULT_COOLDOWN_MS` |
| Enums | UPPER_SNAKE_CASE | `STANDBY`, `RECORDING`, `SAVING` |
| Composables | PascalCase | `MonitorScreen`, `GlassSurface` |
| Resources | snake_case | `ic_camera`, `string_monitor_title` |

### 5.3 Estructura de archivos
- **Un archivo por clase/función principal** (evitar archivos >500 líneas)
- `BackgroundCameraService.kt` es la excepción justificada (1189 líneas, pero es el core)
- **Imports ordenados**: Kotlin stdlib → Android → Third-party → Internal
- **Sin imports wildcard** (`import com.duovial.*` → prohibido)

### 5.4 Manejo de errores
```kotlin
// ✅ CORRECTO: Result type con manejo explícito
sealed class CameraResult {
    data class Success(val message: String) : CameraResult()
    data class Error(val exception: Exception) : CameraResult()
}

// ✅ CORRECTO: try-catch con logging
try {
    cameraProvider.unbindAll()
} catch (e: IllegalStateException) {
    Log.e(TAG, "Camera unbind failed", e)
    // Recovery action
}

// ❌ INCORRECTO: catch vacío
try {
    doSomething()
} catch (e: Exception) {}

// ❌ INCORRECTO: printStackTrace
e.printStackTrace() // Usar Log.e() en su lugar
```

### 5.5 Null safety
- **Evitar `!!`** siempre que sea posible
- Usar `?.let`, `?:`, `when` para manejo seguro
- Si `!!` es inevitable, documentar por qué es seguro

```kotlin
// ✅ Preferido
cameraProvider?.let { provider ->
    provider.unbindAll()
}

// ✅ Con default
val threshold = settings.gForceThreshold ?: 2.5f

// ❌ Evitar
cameraProvider!!.unbindAll()
```

---

## 6. GESTIÓN DE ESTADO

### 6.1 StateFlow para estado reactivo
**SIEMPRE** usar `StateFlow` para estado que la UI necesita observar.

```kotlin
// En AppStateManager
private val _cameraState = MutableStateFlow(CameraState())
val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

// En la UI
val state by appStateManager.cameraState.collectAsState()
```

### 6.2 SharedFlow para eventos one-shot
**SIEMPRE** usar `SharedFlow` para eventos que no deben repetirse al reconectar.

```kotlin
// En AppStateManager
private val _appEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 1)
val appEvents: SharedFlow<AppEvent> = _appEvents.asSharedFlow()

// Emitir evento
_appEvents.tryEmit(AppEvent.DrowsinessDetected(timestamp))
```

### 6.3 Data classes para estado
**SIEMPRE** usar `data class` inmutables para estado.

```kotlin
data class CameraState(
    val status: CameraStatus = CameraStatus.INACTIVO,
    val gForce: Double = 0.0,
    val speedKmh: Double = 0.0,
    val isRecording: Boolean = false,
    val segmentDuration: Long = 0L,
)
```

### 6.4 Estados de la cámara
```kotlin
enum class CameraStatus {
    INACTIVO,      // Servicio no iniciado
    INICIANDO,     // Inicializando cámara
    ACTIVO,        // Modo Vigilante (buffer circular)
    GUARDANDO,     // Guardando evento
    ERROR          // Error irrecuperable
}
```

### 6.5 Máquina de estados del servicio
```
STANDBY ──startRecording()──→ RECORDING
RECORDING ──saveEvent()──→ SAVING
SAVING ──onRecordingFinalized()──→ STANDBY
Cualquier estado ──forceResetToStandby()──→ STANDBY
```

---

## 7. COMUNICACIÓN UI ↔ SERVICIO

### 7.1 UI → Servicio (Intents)
La UI envía acciones al servicio vía `Intent` con action strings.

```kotlin
// CameraServiceManagerAndroid
fun startStandby() {
    val intent = Intent(context, BackgroundCameraService::class.java).apply {
        action = ACTION_START_STANDBY
    }
    context.startForegroundService(intent)
}

fun triggerPanic() {
    val intent = Intent(context, BackgroundCameraService::class.java).apply {
        action = ACTION_TRIGGER_PANIC
    }
    context.startService(intent)
}
```

### 7.2 Servicio → UI (CameraStatusListener)
El servicio notifica a la UI vía interface callback.

```kotlin
interface CameraStatusListener {
    fun onCameraStatusChanged(status: CameraStatus)
    fun onAccelChanged(x: Float, y: Float, z: Float, magnitude: Double)
    fun onSpeedChanged(speedKmh: Double)
    fun onFaceStatusChanged(status: FaceStatus)
    fun onDrowsinessDetected(timestamp: Long)
}
```

### 7.3 Pending values pattern
Si el servicio no está iniciado cuando la UI envía configuración, los valores se almacenan como `pending` y se aplican en `onCreate`.

```kotlin
// En BackgroundCameraService
companion object {
    @Volatile var pendingGForceThreshold: Float? = null
    @Volatile var pendingEarThreshold: Float? = null
}

override fun onCreate() {
    super.onCreate()
    pendingGForceThreshold?.let { gForceThreshold = it }
    pendingEarThreshold?.let { earThreshold = it }
}
```

### 7.4 Anti-race conditions
**SIEMPRE** usar flags de protección para evitar race conditions.

```kotlin
@Volatile private var isSavingEvent = false

fun saveEvent() {
    if (isSavingEvent) return // Anti-race: ignorar si ya guardando
    isSavingEvent = true
    // ... lógica de guardado
    isSavingEvent = false
}
```

---

## 8. REGLAS DE SEGURIDAD Y PRIVACIDAD

### 8.1 Cámara frontal
- ✅ **SOLO** activa cuando `FatigueScreen` está visible
- ✅ **NUNCA** guarda video (solo analiza en tiempo real)
- ✅ **NUNCA** envía frames a servidores
- ❌ **NUNCA** activa en background sin wearable
- ❌ **NUNCA** almacena fotos del conductor (solo embeddings en Supabase)

### 8.2 Grabación de video
- ✅ **SOLO** video (audio DESACTIVADO)
- ✅ Buffer en `context.cacheDir` (se borra automáticamente)
- ✅ Videos de incidente en `Downloads/DuoVial/`
- ❌ **NUNCA** sube videos automáticamente (excepto colisión grave >40km/h)
- ❌ **NUNCA** comparte videos sin consentimiento del usuario

### 8.3 Datos de ubicación
- ✅ GPS solo para velocímetro y geofencing
- ❌ **NO** guardar trayectoria continua
- ✅ Solo guardar ubicación en eventos (colisión, geofence cross)

### 8.4 Wearables (Health Connect)
- ✅ Datos solo en memoria (no se guardan)
- ✅ No se envían a servidores
- ✅ Solo leer: HeartRate, HRV, SleepSession, Steps

### 8.5 Reconocimiento facial
- ✅ **SOLO** alerta (nunca bloquea)
- ✅ Desactivado por defecto
- ✅ Embeddings en pgvector (Supabase)
- ✅ Foto temporal se borra en 24h
- ❌ **NUNCA** usar para acciones automáticas (bloqueo, multa)

### 8.6 Permisos requeridos
```xml
<!-- Declarados en AndroidManifest.xml -->
CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
SYSTEM_ALERT_WINDOW, VIBRATE, WAKE_LOCK, POST_NOTIFICATIONS,
FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_LOCATION,
FOREGROUND_SERVICE_MICROPHONE, FOREGROUND_SERVICE_DATA_SYNC
```

---

## 9. BUENAS PRÁCTICAS DE RENDIMIENTO

### 9.1 Cámara trasera (Modo Vigilante)
| Parámetro | Valor | Razón |
|-----------|-------|-------|
| Resolución | 1920×1080 | Full HD suficiente para evidencia |
| FPS | 30 | Estándar para video |
| Codec | H.264 | Compatible universal |
| Bitrate | 2 Mbps | 66% menos que 6 Mbps |
| Audio | DESACTIVADO | Ahorro CPU + legal |
| Buffer | 2-3 segmentos × 15s | Balance evidencia/almacenamiento |

### 9.2 Cámara frontal (Anti-somnolencia)
| Parámetro | Valor | Razón |
|-----------|-------|-------|
| Resolución | 640×480 | Suficiente para EAR |
| FPS | 10 | ML Kit no necesita más |
| Almacenamiento | 0 bytes | Solo análisis en tiempo real |
| CPU estimado | 4-6% | ML Kit optimizado |

### 9.3 Optimizaciones obligatorias
1. **Rate-limiting**: Emitir telemetría a UI cada 200ms máximo
2. **Handler.postDelayed**: Para rotación de segmentos (no coroutines en servicio)
3. **unbindAll()**: SIEMPRE liberar cámara al salir de pantalla/servicio
4. **LifecycleOwner personalizado**: Para CameraX en servicio
5. **START_STICKY**: Para supervivencia del foreground service
6. **WorkManager Watchdog**: Cada 15 minutos para revivir servicio

### 9.4 Consumo objetivo
| Componente | Batería/hora | Temperatura |
|------------|-------------|-------------|
| Vigilante (trasera) | ~6% | +4-6°C |
| FatigueScreen (frontal) | ~4-5% | +2-3°C |
| Wearable (Health Connect) | ~1-2% | negligible |

### 9.5 Anti-sobrecalentamiento
```kotlin
// Monitorear temperatura del dispositivo
if (temperature > SAFE_THRESHOLD) {
    // Modo reducido: bajar bitrate, reducir FPS, o pausar
    reduceBitrate()
    if (temperature > CRITICAL_THRESHOLD) {
        pauseRecording()
    }
}
```

---

## 10. TESTING

### 10.1 Estrategia de testing
| Capa | Herramienta | Cobertura | Ejecución |
|------|-------------|-----------|-----------|
| Unit Tests | JUnit5 + MockK | >80% código nativo | Cada PR |
| Integration Tests | Supabase Local + Testcontainers | Edge Functions, RLS | Semanal |
| UI Tests | Compose Testing + Espresso | Flujos críticos | Pre-release |
| E2E Tests | Maestro (mobile) + Playwright (web) | Flujo completo | Pre-release |
| Device Tests | Firebase Test Lab | Compatibilidad | Cada release |

### 10.2 Tests críticos (bloquean release)
- [ ] Buffer circular: guardado de video tras evento
- [ ] Foreground Service: supervivencia 2h+ en background
- [ ] Supabase Auth: login, roles, RLS
- [ ] Stripe: checkout flow, webhook processing

### 10.3 Tests importantes (antes de beta)
- [ ] Health Connect: lectura FC/HRV con 3+ wearables
- [ ] CameraX: preview estable, concurrent cameras
- [ ] Geofencing: trigger al cruzar zona
- [ ] OneSignal: push notification delivery

### 10.4 Escribir tests
```kotlin
// ✅ Unit test ejemplo
@Test
fun `saveEvent should not trigger when already saving`() = runTest {
    val service = createService()
    service.isSavingEvent = true
    
    service.saveEvent()
    
    assertFalse(service.eventSaved) // No debe guardar
}
```

---

## 11. GIT Y COMMITS

### 11.1 Convención de commits
```
<tipo>(<scope>): <descripción>

[opcional: cuerpo detallado]

[opcional: footer]
```

| Tipo | Uso |
|------|-----|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `refactor` | Refactorización sin cambio de comportamiento |
| `perf` | Mejora de rendimiento |
| `test` | Agregar o modificar tests |
| `docs` | Documentación |
| `chore` | Tareas de mantenimiento |
| `ci` | Cambios en CI/CD |

### 11.2 Ejemplos
```
feat(camera): agregar rotación de 3 segmentos en buffer circular
fix(fatigue): corregir cálculo de EAR para rostros con lentes
refactor(auth): migrar de AWS Cognito a Supabase Auth
perf(service): reducir emisión de telemetría de 100ms a 200ms
docs(arch): actualizar AGENT.md con nuevas convenciones
```

### 11.3 Branch naming
```
feature/<descripcion>     # Nueva funcionalidad
fix/<descripcion>         # Corrección de bug
refactor/<descripcion>    # Refactorización
chore/<descripcion>       # Tareas de mantenimiento
```

### 11.4 Reglas de branch
- `main` → producción (solo merges desde develop)
- `develop` → integración (solo merges desde feature branches)
- Feature branches → desde develop, merge de vuelta a develop
- **NUNCA** hacer push directo a main o develop

---

## 12. DELEGACIÓN DE TAREAS

### 12.1 Cuándo delegar al mid-level-developer
Delegar cuando:
- ✅ La implementación es repetitiva (CRUD screens, UI components)
- ✅ Hay instrucciones claras y archivos bien definidos
- ✅ No requiere decisiones de arquitectura
- ✅ Es código aislado (no afecta el core service)

### 12.2 Cuándo NO delegar
No delegar cuando:
- ❌ Cambios en `BackgroundCameraService.kt`
- ❌ Cambios en `AppState.kt` o gestión de estado central
- ❌ Decisiones de arquitectura o patrones de diseño
- ❌ Cambios que afectan múltiples módulos
- ❌ Migraciones de base de datos
- ❌ Configuración de seguridad o permisos

### 12.3 Formato de delegación
Al delegar, proporcionar:
1. **Contexto**: Qué se necesita y por qué
2. **Archivos**: Lista exacta de archivos a modificar/crear
3. **Especificaciones**: Requisitos técnicos detallados
4. **Criterios de aceptación**: Cómo verificar que está correcto
5. **Restricciones**: Qué NO hacer

---

## 13. CHECKLIST ANTES DE ENTREGAR

### 13.1 Checklist general
- [ ] El código compila sin warnings
- [ ] No hay imports no utilizados
- [ ] No hay `TODO` o `FIXME` sin ticket asociado
- [ ] Los nombres siguen las convenciones del proyecto
- [ ] Los comentarios están en español
- [ ] No hay código legacy referenciado (`FrontFaceDetector`)

### 13.2 Checklist de rendimiento
- [ ] No hay operaciones bloqueantes en el main thread
- [ ] Las cámaras se liberan correctamente (`unbindAll()`)
- [ ] No hay memory leaks (listeners no removidos)
- [ ] Rate-limiting aplicado en emisiones de telemetría
- [ ] No hay creación de objetos en loops de cámara

### 13.3 Checklist de seguridad
- [ ] No se guarda video de cámara frontal
- [ ] No se envían datos biométricos sin consentimiento
- [ ] Los permisos se solicitan de forma granular
- [ ] No hay hardcoded secrets o API keys
- [ ] RLS policies consideradas para queries de Supabase

### 13.4 Checklist de UX
- [ ] Los textos de UI están en español
- [ ] Los errores tienen mensajes claros para el usuario
- [ ] Loading states considerados
- [ ] Edge cases manejados (sin cámara, sin GPS, sin permisos)
- [ ] Compatible con minSdk 26 (Android 8.0)

---

## 14. REFERENCIAS

### 14.1 Documentos del proyecto
| Documento | Ubicación | Propósito |
|-----------|-----------|-----------|
| `CONTEXT.md` | Raíz del repo | Fuente única de verdad del proyecto |
| `TICKETS_DESARROLLO.md` | Raíz del repo | Tickets y roadmap de desarrollo |
| `AGENT.md` | Raíz del repo | Este documento (guía para agentes) |
| `anti-drowsiness-implementation.md` | Raíz del repo | Detalles de implementación anti-somnolencia |

### 14.2 Recursos externos
| Recurso | URL |
|---------|-----|
| CameraX Docs | https://developer.android.com/jetpack/androidx/releases/camera |
| ML Kit Face Detection | https://developers.google.com/ml-kit/vision/face-detection/android |
| Compose Multiplatform | https://www.jetbrains.com/lp/compose-multiplatform/ |
| Supabase Docs | https://supabase.com/docs |
| Stripe Docs | https://stripe.com/docs |
| Mux Docs | https://docs.mux.com/ |

### 14.3 Decisiones técnicas clave (resumen)
| Decisión | Qué se eligió | Por qué |
|----------|--------------|---------|
| Buffer circular vs grabación continua | Buffer circular | 40% menos batería, menos calor |
| Camera2 vs CameraX (frontal) | CameraX | Robustez, menos bugs de Surface |
| Cámara frontal siempre vs solo en pantalla | Solo en FatigueScreen | Batería + privacidad |
| Health Connect vs SDK fabricante | Health Connect | Estándar abierto, sin acoplamiento |
| Servicio como fuente de verdad | BackgroundCameraService | Evita desincronización UI/servicio |
| Supabase vs Firebase | Supabase | PostgreSQL open source, realtime nativo, sin vendor lock-in |
| Stripe vs Wompi/PayU | Stripe | Sync Engine, Customer Portal, soporte Colombia |
| Mux vs procesamiento propio | Mux | Partner Supabase, CDN global, free tier |

---

## ⚠️ REGLAS DE ORO (NO ROMPER BAJO NINGUNA CIRCUNSTANCIA)

1. **NUNCA** grabar audio en el modo Vigilante
2. **NUNCA** guardar video de la cámara frontal
3. **NUNCA** hacer push directo a `main` o `develop`
4. **NUNCA** usar `FrontFaceDetector.kt` (legacy)
5. **NUNCA** hardcodear API keys o secrets
6. **NUNCA** bloquear el main thread con operaciones de I/O
7. **NUNCA** liberar cámaras sin `unbindAll()`
8. **NUNCA** modificar `BackgroundCameraService.kt` sin revisión del Tech Lead
9. **NUNCA** subir videos automáticamente (excepto colisión >40km/h)
10. **NUNCA** usar reconocimiento facial para bloquear vehículos

---

*Este documento es la fuente de verdad para agentes de desarrollo. Cualquier desviación debe ser aprobada por el Tech Lead y documentada aquí.*

**Última revisión**: Junio 30, 2026
**Próxima revisión**: Julio 2026
