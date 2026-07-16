# 📋 FASE C: SUPABASE Y BACKEND — Documento de Implementación

**Versión**: 1.1
**Fecha**: Julio 15, 2026
**Sprint**: 9-12 (4 semanas)
**Puntos totales**: 30
**Estado del Schema**: ✅ COMPLETADO (17 tablas verificadas via MCP)
**Edge Functions**: ✅ 7 functions desplegadas

---

## 📌 TABLA DE CONTENIDO

1. [Visión General](#visión-general)
2. [Estado Actual del Schema](#estado-actual-del-schema)
3. [Issues de Seguridad a Resolver](#issues-de-seguridad-a-resolver)
4. [Orden de Ejecución](#orden-de-ejecución)
5. [Tickets Detallados](#tickets-detallados)
   - 5.1 [C-01: Reemplazar Auth por Supabase Auth](#c-01)
   - 5.2 [C-02: Login/Registro Opcional](#c-02)
   - 5.3 [C-03: Supabase Storage para Videos](#c-03)
   - 5.4 [C-05: Multi-tenancy (Organizations + RLS)](#c-05)
   - 5.5 [C-06: Supabase Realtime para Telemetría](#c-06)
6. [Consideraciones Técnicas Transversales](#consideraciones-técnicas-transversales)
7. [Criterios de Validación Global](#criterios-de-validación-global)
8. [Riesgos y Mitigaciones](#riesgos-y-mitigaciones)

---

## 🎯 VISIÓN GENERAL

### ¿Qué se construye?

La Fase C conecta la app Android nativa (Kotlin + Compose) con el backend Supabase para:

1. **Autenticación**: Login/registro con email, Google, Apple (Supabase Auth)
2. **Almacenamiento**: Subida de videos de incidentes a Supabase Storage
3. **Multi-tenancy**: Modelo Organizations con RLS para aislamiento de datos
4. **Telemetría**: Envío de datos en tiempo real (GPS, G-Force, estado) via Realtime

### ¿Por qué es importante?

- **Sin Auth**: No hay usuarios, no hay facturación, no hay Fleet
- **Sin Storage**: Videos se pierden al cambiar de dispositivo
- **Sin Multi-tenancy**: No se puede vender a empresas (Fleet)
- **Sin Realtime**: Dashboard Web no puede mostrar flota en vivo

### Stack técnico

| Componente | Tecnología | Propósito |
|------------|-----------|----------|
| Auth | Supabase Auth (GoTrue) | Login, registro, JWT, MFA |
| Database | Supabase PostgreSQL | Datos relacionales + RLS |
| Storage | Supabase Storage (S3-compatible) | Videos de incidentes |
| Realtime | Supabase Realtime (WebSockets) | Telemetría en vivo |
| App Android | Supabase Kotlin SDK | Cliente nativo |

---

## 📊 ESTADO ACTUAL DEL SCHEMA

### ✅ Tablas existentes (verificadas via MCP)

| Tabla | RLS | Filas | Propósito |
|-------|-----|-------|-----------|
| `profiles` | ✅ | 0 | Perfiles de usuario (extiende auth.users) |
| `organizations` | ✅ | 0 | Organizaciones/empresas (multi-tenancy) |
| `organization_members` | ✅ | 0 | Miembros con roles (owner, admin, supervisor, driver) |
| `vehicles` | ✅ | 0 | Vehículos vinculados a org |
| `drivers` | ✅ | 0 | Conductores con face_embedding (pgvector) |
| `incidents` | ✅ | 0 | Incidentes con video, ubicación, G-Force |
| `geofence_events` | ✅ | 0 | Eventos de cruce de geofence |
| `vehicle_telemetry` | ✅ | 0 | Telemetría en tiempo real (GPS, sensores) |
| `maintenance_rules` | ✅ | 0 | Reglas de mantenimiento por modelo |
| `odometer_logs` | ✅ | 0 | Logs de kilometraje |
| `obd_readings` | ✅ | 0 | Lecturas OBD II (RPM, temp, voltaje) |
| `maintenance_alerts` | ✅ | 0 | Alertas de mantenimiento |
| `products` | ✅ | 0 | Catálogo de productos (suscripciones, one-time) |
| `purchases` | ✅ | 0 | Transacciones individuales |
| `subscriptions` | ✅ | 0 | Suscripciones activas |
| `billing_events` | ✅ | 0 | Audit log de webhooks |
| `wompi_card_tokens` | ✅ | 0 | Tokens de tarjeta para cobros recurrentes |

### ✅ Funciones SQL existentes

| Función | Propósito | Seguridad |
|---------|-----------|-----------|
| `current_user_id()` | Retorna auth.uid() actual | ⚠️ SECURITY DEFINER |
| `current_org_id()` | Retorna org_id de la sesión | ⚠️ SECURITY DEFINER |
| `handle_new_user()` | Trigger: crea profile al registrar usuario | ⚠️ SECURITY DEFINER |
| `handle_new_organization()` | Trigger: crea org personal al registrar | ⚠️ SECURITY DEFINER |
| `handle_updated_at()` | Trigger: actualiza updated_at automáticamente | ⚠️ SECURITY DEFINER |
| `rls_auto_enable()` | Helper: habilita RLS en tablas | ⚠️ SECURITY DEFINER |

### ✅ Edge Functions desplegadas (7 functions)

| Function | verify_jwt | Propósito |
|----------|------------|-----------|
| `trigger-mux-transcode` | ❌ | Dispara transcodificación Mux al subir video |
| `mux-webhook-handler` | ❌ | Recibe webhook de Mux (asset ready) |
| `send-push-notification` | ✅ | Envía push vía OneSignal |
| `wompi-webhook` | ❌ | Recibe webhooks de Wompi (pagos) |
| `verify-google-purchase` | ✅ | Verifica compras de Google Play |
| `create-wompi-link` | ✅ | Crea links de pago Wompi |
| `process-recurring-billing` | ❌ | Renovaciones recurrentes (pg_cron) |

---

## ⚠️ ISSUES DE SEGURIDAD A RESOLVER

### 🔴 Problemas críticos encontrados (Security Advisors)

#### 1. RLS Policies Demasiado Permisivas (12 policies)

**Problema**: Políticas con `USING (true)` o `WITH CHECK (true)` que bypass RLS.

| Tabla | Policy | Comando | Riesgo |
|-------|--------|---------|--------|
| `billing_events` | `service_all_billing_events` | ALL | 🔴 Alto |
| `geofence_events` | `service_insert_geofence_events` | INSERT | 🟡 Medio |
| `incidents` | `service_insert_incidents` | INSERT | 🟡 Medio |
| `incidents` | `service_update_incidents` | UPDATE | 🟡 Medio |
| `maintenance_alerts` | `service_insert_maintenance_alerts` | INSERT | 🟡 Medio |
| `maintenance_alerts` | `service_update_maintenance_alerts` | UPDATE | 🟡 Medio |
| `obd_readings` | `service_insert_obd` | INSERT | 🟡 Medio |
| `odometer_logs` | `service_insert_odometer` | INSERT | 🟡 Medio |
| `purchases` | `service_insert_purchases` | INSERT | 🟡 Medio |
| `purchases` | `service_update_purchases` | UPDATE | 🟡 Medio |
| `subscriptions` | `service_insert_subscriptions` | INSERT | 🟡 Medio |
| `subscriptions` | `service_update_subscriptions` | UPDATE | 🟡 Medio |
| `vehicle_telemetry` | `service_insert_telemetry` | INSERT | 🟡 Medio |

**Solución recomendada**: Estas policies son para Edge Functions (service role). Verificar que solo se usan desde el backend y NO desde la app Android.

```sql
-- Ejemplo: Verificar que la policy es para service_role
CREATE POLICY "service_insert_incidents" ON incidents
  FOR INSERT
  TO service_role  -- ← Solo para service_role, NO anon/authenticated
  WITH CHECK (true);
```

#### 2. SECURITY DEFINER Functions Ejecutables por Anon (5 functions)

**Problema**: Funciones que pueden ejecutarse sin autenticación.

| Función | Riesgo |
|---------|--------|
| `current_org_id()` | 🔴 Alto |
| `current_user_id()` | 🔴 Alto |
| `handle_new_organization()` | 🟡 Medio |
| `handle_new_user()` | 🟡 Medio |
| `rls_auto_enable()` | 🟡 Medio |

**Solución recomendada**:

```sql
-- Opción 1: Revocar EXECUTE para anon
REVOKE EXECUTE ON FUNCTION current_org_id() FROM anon;
REVOKE EXECUTE ON FUNCTION current_user_id() FROM anon;

-- Opción 2: Cambiar a SECURITY INVOKER (si no necesitan SECURITY DEFINER)
ALTER FUNCTION current_org_id() SECURITY INVOKER;
ALTER FUNCTION current_user_id() SECURITY INVOKER;

-- Opción 3: Mover a schema privado (no expuesto via API)
ALTER FUNCTION current_org_id() SET search_path = '';
```

#### 3. Function Search Path Mutable (6 functions)

**Problema**: Funciones sin `search_path` fijo pueden ser vulnerables a inyección.

**Solución recomendada**:

```sql
-- Agregar search_path fijo a todas las funciones
ALTER FUNCTION current_org_id() SET search_path = 'public';
ALTER FUNCTION current_user_id() SET search_path = 'public';
ALTER FUNCTION handle_new_user() SET search_path = 'public';
ALTER FUNCTION handle_new_organization() SET search_path = 'public';
ALTER FUNCTION handle_updated_at() SET search_path = 'public';
ALTER FUNCTION rls_auto_enable() SET search_path = 'public';
```

---

## ✅ PREREQUISITOS (Estado Real)

### ✅ Ya completado

| Ticket | Fase | Descripción | Estado |
|--------|------|-------------|--------|
| **C-04** | G (Infraestructura) | Schema de BD completo (17 tablas) | ✅ COMPLETADO |
| **G-01** | G (Infraestructura) | Setup Supabase Edge Functions (7 functions) | ✅ COMPLETADO |

### ⚠️ Pendiente de verificar

| Ticket | Fase | Descripción | Estado |
|--------|------|-------------|--------|
| **A-02** | A (Estabilidad) | Buffer circular refactorizado a 3 videos | ⚠️ Verificar en código |

---

## 📅 ORDEN DE EJECUCIÓN

### Estado actual: Schema y Edge Functions completos

```
✅ COMPLETADO: C-04 (Schema) + G-01 (Edge Functions)
     ↓
Semana 9-10: C-01 (Auth) + C-02 (Login/Registro)
     ↓
Semana 10-11: C-03 (Storage) + C-05 (Multi-tenancy)
     ↓
Semana 11-12: C-06 (Realtime) + Correcciones de seguridad
```

### Diagrama de dependencias (actualizado)

```
C-04 (Schema) ✅ ─────┬──────→ C-05 (Multi-tenancy) ─────→ C-06 (Realtime)
                       │              ↑
                       │              │
C-01 (Auth) ──────────┴──────→ C-02 (Login/Registro)
     │
     └──────────────────────────→ C-03 (Storage)

G-01 (Edge Functions) ✅ ──────→ C-03 (Storage) + C-06 (Realtime)
```

### Paralelismo posible

- **C-01** y **C-03** pueden desarrollarse en paralelo (diferentes módulos)
- **C-05** requiere C-01 completado (para auth de usuarios)
- **C-06** requiere C-05 completado (para org_id en telemetría)
- **Correcciones de seguridad** pueden hacerse en paralelo con C-01

### Prioridad de seguridad

**ANTES de implementar C-01**, corregir los issues de seguridad:

1. ✅ Verificar que RLS policies son para `service_role` (no `anon`)
2. ✅ Revocar EXECUTE de funciones SECURITY DEFINER para `anon`
3. ✅ Agregar `search_path` fijo a todas las funciones

---

## 📦 TICKETS DETALLADOS

---

<a name="c-01"></a>
### 5.1 C-01 — Reemplazar Auth por Supabase Auth

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature / Infra |
| **Prioridad** | P1 |
| **Puntos** | 8 |
| **Dependencias** | Ninguna (schema profiles ya existe) |
| **Sprint** | 9-10 |

#### Descripción

Implementar autenticación completa con Supabase Auth en la app Android. **REEMPLAZAR AWS Cognito** (que no se usará) por Supabase Auth con soporte para:
- Email + Password
- Google Sign-In (OAuth 2.0)
- Magic Link (opcional)
- Recuperación de contraseña

**Nota importante**: El schema de `profiles` ya existe con los campos necesarios (id, email, full_name, phone, plan, plan_source, plan_expires_at, push_token). Solo se necesita conectar la app a Supabase Auth.

#### Archivos a crear/modificar

```
📁 kmp/composeApp/src/
├── commonMain/
│   ├── kotlin/com/duovial/
│   │   ├── auth/
│   │   │   ├── AuthRepository.kt          ← NUEVO: Interface común
│   │   │   ├── AuthState.kt               ← NUEVO: Sealed class estados
│   │   │   └── AuthEvent.kt               ← NUEVO: Eventos de auth
│   │   └── di/
│   │       └── AuthModule.kt              ← NUEVO: DI para auth
│   └── composeApp/
│       ├── screens/
│       │   ├── LoginScreen.kt             ← MODIFICAR: Integrar Supabase
│       │   ├── RegisterScreen.kt          ← NUEVO: Registro
│       │   └── ForgotPasswordScreen.kt    ← NUEVO: Recuperación
│       └── viewmodels/
│           └── AuthViewModel.kt           ← NUEVO: ViewModel auth
│
├── androidMain/
│   ├── kotlin/com/duovial/
│   │   ├── auth/
│   │   │   ├── SupabaseAuthProvider.kt    ← NUEVO: Implementación Android
│   │   │   └── GoogleSignInHelper.kt      ← NUEVO: Google OAuth
│   │   └── di/
│   │       └── AuthModuleAndroid.kt       ← NUEVO: DI Android
│   └── AndroidManifest.xml               ← MODIFICAR: Deep links
│
└── build.gradle.kts                       ← MODIFICAR: Dependencias Supabase
```

#### Dependencias a agregar

```kotlin
// build.gradle.kts (composeApp)
implementation("io.github.jan-tennert.supabase:auth-kt:2.5.0")
implementation("io.github.jan-tennert.supabase:auth-kt-google:2.5.0") // Google Sign-In
implementation("io.github.jan-tennert.supabase:realtime-kt:2.5.0")    // Para Realtime después
implementation("io.github.jan-tennert.supabase:storage-kt:2.5.0")     // Para Storage después
```

#### Configuración Supabase

```kotlin
// SupabaseClient.kt (singleton)
val supabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Auth) {
        scheme = "duovial"
        host = "callback"
    }
    install(Realtime)
    install(Storage)
}
```

#### Criterios de Aceptación

**CA-1: Registro con email**
```
DADO que un usuario nuevo quiere registrarse
CUANDO ingresa email + contraseña y toca "Registrarse"
ENTONCES:
  - Se crea usuario en Supabase Auth (auth.users)
  - Se crea perfil en tabla profiles (trigger on_auth_user_created)
  - Se envía email de confirmación
  - Se muestra pantalla "Revisa tu email"
```

**CA-2: Login con email**
```
DADO que un usuario registrado quiere iniciar sesión
CUANDO ingresa email + contraseña y toca "Iniciar sesión"
ENTONCES:
  - Se autentica con Supabase Auth
  - Se obtiene JWT (access_token + refresh_token)
  - Se almacenan tokens en EncryptedSharedPreferences
  - Se navega a MonitorScreen
```

**CA-3: Google Sign-In**
```
DADO que un usuario quiere usar Google
CUANDO toca "Continuar con Google"
ENTONCES:
  - Se abre selector de cuentas Google
  - Se obtiene ID token
  - Se intercambia por Supabase session
  - Se crea/actualiza perfil
  - Se navega a MonitorScreen
```

**CA-4: Recuperación de contraseña**
```
DADO que un usuario olvidó su contraseña
CUANDO ingresa email y toca "Recuperar contraseña"
ENTONCES:
  - Se envía email con link de recuperación
  - El link abre la app (deep link)
  - Se muestra pantalla para nueva contraseña
  - Se actualiza contraseña en Supabase Auth
```

**CA-5: Persistencia de sesión**
```
DADO que el usuario cerró la app
CUANDO la reabre
ENTONCES:
  - Se verifica si hay refresh_token válido
  - Si es válido, se refresca el access_token automáticamente
  - Se navega directamente a MonitorScreen (sin pedir login)
  - Si expiró, se muestra LoginScreen
```

**CA-6: Logout**
```
DADO que el usuario quiere cerrar sesión
CUANDO toca "Cerrar sesión" en Settings
ENTONCES:
  - Se revoca el refresh_token en Supabase
  - Se limpian tokens locales
  - Se navega a LoginScreen
  - Se detiene el Vigilante (si estaba activo)
```

#### Implementación detallada

**Paso 1: Configurar Supabase Client**

```kotlin
// commonMain: SupabaseClientProvider.kt
object SupabaseClientProvider {
    private lateinit var client: SupabaseClient
    
    fun initialize(url: String, key: String) {
        client = createSupabaseClient(url, key) {
            install(Auth) {
                scheme = "duovial"
                host = "callback"
            }
        }
    }
    
    fun getClient(): SupabaseClient = client
}
```

**Paso 2: AuthRepository (Interface común)**

```kotlin
// commonMain: auth/AuthRepository.kt
interface AuthRepository {
    val currentUser: StateFlow<User?>
    val authState: StateFlow<AuthState>
    
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun refreshSession(): Result<Unit>
}

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

data class User(
    val id: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?
)
```

**Paso 3: Implementación Android**

```kotlin
// androidMain: auth/SupabaseAuthProvider.kt
class SupabaseAuthProvider(
    private val supabase: SupabaseClient,
    private val context: Context
) : AuthRepository {
    
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    init {
        // Verificar sesión existente al inicializar
        checkExistingSession()
    }
    
    private fun checkExistingSession() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null && !session.isExpired()) {
                    _currentUser.value = session.user?.toUser()
                    _authState.value = AuthState.Authenticated(_currentUser.value!!)
                } else {
                    // Intentar refrescar
                    supabase.auth.refreshCurrentSession()
                    val refreshedSession = supabase.auth.currentSessionOrNull()
                    if (refreshedSession != null) {
                        _currentUser.value = refreshedSession.user?.toUser()
                        _authState.value = AuthState.Authenticated(_currentUser.value!!)
                    } else {
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }
    
    override suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()?.toUser()
            _currentUser.value = user
            _authState.value = AuthState.Authenticated(user!!)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }
            val user = supabase.auth.currentUserOrNull()?.toUser()
            _currentUser.value = user
            _authState.value = AuthState.Authenticated(user!!)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ... otros métodos
}
```

**Paso 4: Trigger para crear perfil automáticamente**

**Nota**: El trigger `handle_new_user()` ya existe en la BD. Verificar que funciona correctamente.

```sql
-- Trigger existente (verificar que está activo)
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Si no existe, crear:
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, email, full_name)
  VALUES (
    NEW.id,
    NEW.email,
    NEW.raw_user_meta_data->>'full_name'
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Agregar search_path para seguridad
ALTER FUNCTION public.handle_new_user() SET search_path = 'public';
```

**Paso 5: Deep Links para OAuth**

```xml
<!-- AndroidManifest.xml -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="duovial"
        android:host="callback"
        android:pathPrefix="/auth" />
</intent-filter>
```

#### Testing

```kotlin
// Unit Test: AuthRepositoryTest.kt
class AuthRepositoryTest {
    @Test
    fun `signUp with valid email creates user`() = runTest {
        // Given
        val repository = createTestRepository()
        
        // When
        val result = repository.signUp("test@example.com", "password123")
        
        // Then
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `signIn with wrong password returns error`() = runTest {
        // Given
        val repository = createTestRepository()
        
        // When
        val result = repository.signIn("test@example.com", "wrongpassword")
        
        // Then
        assertTrue(result.isFailure)
    }
}
```

#### Notas técnicas

1. **Tokens**: Almacenar en `EncryptedSharedPreferences` (no SharedPreferences plano)
2. **Refresh automático**: Supabase Kotlin SDK maneja refresh automático del token
3. **Error handling**: Mapear errores de Supabase a mensajes amigables en español
4. **Google Sign-In**: Requiere configurar SHA-1 en Google Cloud Console y Supabase Dashboard
5. **Rate limiting**: Supabase Auth tiene rate limiting por IP (implementar backoff)

---

<a name="c-02"></a>
### 5.2 C-02 — Login/Registro Opcional

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Puntos** | 3 |
| **Dependencias** | C-01 |
| **Sprint** | 10 |

#### Descripción

Permitir que la app funcione SIN autenticación (modo anónimo). El usuario puede usar el Vigilante, guardar videos localmente, y sincronizar después cuando se registre. Esto es crítico para la primera experiencia (no pedir login de inmediato).

#### Archivos a modificar

```
📁 kmp/composeApp/src/
├── commonMain/
│   └── composeApp/
│       ├── App.kt                         ← MODIFICAR: Flujo condicional
│       └── screens/
│           └── MonitorScreen.kt           ← MODIFICAR: Modo anónimo
│
└── androidMain/
    └── kotlin/com/duovial/
        └── auth/
            └── AnonymousAuth.kt           ← NUEVO: Sesión anónima
```

#### Criterios de Aceptación

**CA-1: Modo anónimo**
```
DADO que un usuario abre la app por primera vez
CUANDO toca "Usar sin cuenta"
ENTONCES:
  - Se crea sesión anónima en Supabase (auth.sign_in_anonymously)
  - Se genera UUID local para identificar datos
  - El Vigilante funciona normalmente
  - Se muestra banner: "Crea una cuenta para sincronizar tus datos"
```

**CA-2: Conversión de anónimo a registrado**
```
DADO que un usuario anónimo quiere registrarse
CUANDO ingresa email + contraseña
ENTONCES:
  - Se vincula la sesión anónima a la cuenta real (linkIdentity)
  - Los datos existentes (incidents, settings) se mantienen
  - Se actualiza el perfil con email
```

**CA-3: Restricciones de anónimo**
```
DADO que un usuario está en modo anónimo
CUANDO intenta acceder a funciones que requieren auth
ENTONCES:
  - Se muestra modal: "Crea una cuenta para usar esta función"
  - Las funciones restringidas son: Storage upload, Realtime sync, Fleet
```

#### Implementación

```kotlin
// AnonymousAuth.kt
class AnonymousAuth(private val supabase: SupabaseClient) {
    
    suspend fun signInAnonymously(): Result<String> {
        return try {
            val result = supabase.auth.signInAnonymously()
            val userId = result.user?.id ?: throw Exception("No user ID")
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun linkToEmail(email: String, password: String): Result<Unit> {
        return try {
            supabase.auth.linkIdentity(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun isAnonymous(): Boolean {
        val user = supabase.auth.currentUserOrNull()
        return user?.isAnonymous == true
    }
}
```

#### Notas técnicas

1. **Sesión anónima**: Supabase crea un usuario real pero sin email
2. **Link identity**: Vincula datos del anónimo al usuario registrado
3. **UUID local**: Para datos que no se sincronizan (settings locales)
4. **Banner persistente**: Mostrar hasta que el usuario se registre o lo descarte (7 días)

---

<a name="c-03"></a>
### 5.3 C-03 — Supabase Storage para Videos

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Puntos** | 5 |
| **Dependencias** | C-01, C-04 |
| **Sprint** | 10-11 |

#### Descripción

Implementar subida de videos de incidentes a Supabase Storage. Los videos se suben SOLO cuando:
1. El usuario toca "Subir" manualmente
2. Ocurre una colisión grave (F-08, futuro)

Los videos NO se suben automáticamente para ahorrar datos móviles.

#### Archivos a crear/modificar

```
📁 kmp/composeApp/src/
├── commonMain/
│   ├── kotlin/com/duovial/
│   │   ├── storage/
│   │   │   ├── VideoStorageRepository.kt  ← NUEVO: Interface
│   │   │   └── StorageState.kt            ← NUEVO: Estados de subida
│   │   └── incidents/
│   │       └── IncidentRepository.kt      ← MODIFICAR: Agregar upload
│   └── composeApp/
│       └── screens/
│           └── IncidentPlayerScreen.kt    ← MODIFICAR: Botón subir
│
└── androidMain/
    └── kotlin/com/duovial/
        └── storage/
            └── SupabaseVideoStorage.kt    ← NUEVO: Implementación
```

#### Configuración Storage

```sql
-- Bucket para videos de incidentes
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'incident-videos',
  'incident-videos',
  false,  -- Privado
  52428800,  -- 50MB max
  ARRAY['video/mp4', 'video/quicktime']
);

-- RLS Policy: Usuario solo ve sus propios videos
CREATE POLICY "Users can view own videos"
ON storage.objects FOR SELECT
USING (
  bucket_id = 'incident-videos' 
  AND (storage.foldername(name))[1] = auth.uid()::text
);

CREATE POLICY "Users can upload own videos"
ON storage.objects FOR INSERT
WITH CHECK (
  bucket_id = 'incident-videos' 
  AND (storage.foldername(name))[1] = auth.uid()::text
);
```

#### Criterios de Aceptación

**CA-1: Subida manual**
```
DADO que un incidente tiene video guardado localmente
CUANDO el usuario toca "Subir a la nube"
ENTONCES:
  - Se muestra indicador de progreso (0-100%)
  - El video se sube a Supabase Storage
  - Se genera signed URL con expiración de 7 días
  - Se actualiza el incidente con la URL en la BD
```

**CA-2: Visualización remota**
```
DADO que un video fue subido exitosamente
CUANDO el usuario toca "Ver en la nube"
ENTONCES:
  - Se obtiene signed URL del Storage
  - Se reproduce en el reproductor integrado
  - Funciona en cualquier dispositivo (al estar en la nube)
```

**CA-3: Manejo de errores**
```
DADO que la subida falla (sin internet, timeout)
CUANDO ocurre el error
ENTONCES:
  - Se muestra mensaje claro: "Sin conexión. Se subirá cuando haya internet."
  - Se encola para reintento automático
  - El video local NO se elimina
```

**CA-4: Límites de plan**
```
DADO que un usuario Free intenta subir un video
CUANDO toca "Subir"
ENTONCES:
  - Se muestra: "Crea una cuenta para sincronizar videos"
  - Si ya tiene cuenta Free: "Actualiza a Premium para subir videos ilimitados"
```

#### Implementación

**Paso 1: VideoStorageRepository**

```kotlin
// commonMain: storage/VideoStorageRepository.kt
interface VideoStorageRepository {
    sealed class UploadState {
        object Idle : UploadState()
        data class Uploading(val progress: Float) : UploadState()
        data class Success(val url: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }
    
    val uploadState: StateFlow<UploadState>
    
    suspend fun uploadVideo(
        localUri: String,
        incidentId: String,
        userId: String
    ): Result<String>
    
    suspend fun getSignedUrl(path: String, expiresIn: Long = 604800): Result<String>
    
    suspend fun deleteVideo(path: String): Result<Unit>
}
```

**Paso 2: Implementación Android**

```kotlin
// androidMain: storage/SupabaseVideoStorage.kt
class SupabaseVideoStorage(
    private val supabase: SupabaseClient,
    private val context: Context
) : VideoStorageRepository {
    
    private val _uploadState = MutableStateFlow<VideoStorageRepository.UploadState>(
        VideoStorageRepository.UploadState.Idle
    )
    override val uploadState: StateFlow<VideoStorageRepository.UploadState> = 
        _uploadState.asStateFlow()
    
    override suspend fun uploadVideo(
        localUri: String,
        incidentId: String,
        userId: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                _uploadState.value = VideoStorageRepository.UploadState.Uploading(0f)
                
                val file = File(localUri)
                val path = "$userId/$incidentId/${file.name}"
                
                supabase.storage
                    .from("incident-videos")
                    .upload(path, file.readBytes()) {
                        upsert = false
                        contentType = ContentType.Video.MP4
                    }
                
                val url = supabase.storage
                    .from("incident-videos")
                    .createSignedUrl(path, expiresIn = 604800) // 7 días
                
                _uploadState.value = VideoStorageRepository.UploadState.Success(url)
                Result.success(url)
            } catch (e: Exception) {
                _uploadState.value = VideoStorageRepository.UploadState.Error(e.message ?: "Error")
                Result.failure(e)
            }
        }
    }
    
    // ... otros métodos
}
```

**Paso 3: Actualizar IncidentRepository**

```kotlin
// Modificar IncidentRepository para incluir upload
class IncidentRepository(
    private val storage: VideoStorageRepository,
    private val supabase: SupabaseClient
) {
    suspend fun uploadIncidentVideo(incidentId: String): Result<String> {
        val incident = getIncident(incidentId) ?: return Result.failure(Exception("No encontrado"))
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.failure(Exception("No auth"))
        
        return storage.uploadVideo(
            localUri = incident.localVideoPath,
            incidentId = incidentId,
            userId = userId
        ).also { result ->
            if (result.isSuccess) {
                // Actualizar incidente con URL remota
                updateIncident(incidentId, mapOf(
                    "remote_video_url" to result.getOrNull(),
                    "sync_status" to "synced"
                ))
            }
        }
    }
}
```

#### Notas técnicas

1. **TUS Upload**: Supabase Storage soporta TUS (resumable uploads) para videos grandes
2. **Compresión**: Considerar comprimir video antes de subir (FFmpeg)
3. **Signed URLs**: Expiran en 7 días por defecto (renovables)
4. **Costos**: Supabase Storage cobra por GB almacenado y transferencia
5. **Offline**: Encolar subidas y ejecutar cuando haya conexión

---

<a name="c-05"></a>
### 5.4 C-05 — Multi-tenancy: Organizations + RLS

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature / Infra |
| **Prioridad** | P1 |
| **Puntos** | 8 |
| **Dependencias** | C-01, C-04 |
| **Sprint** | 10-11 |

#### Descripción

Implementar el modelo de multi-tenancy completo con Organizations. Cada empresa es una "organización" con sus propios vehículos, conductores, incidentes y datos. RLS (Row Level Security) garantiza que cada organización solo vea sus propios datos.

**Nota importante**: Las tablas `organizations`, `organization_members`, `vehicles`, `drivers` ya existen con RLS habilitado. El trabajo se enfoca en:
1. Verificar y corregir las RLS policies existentes
2. Implementar la lógica de cambio de organización en la app
3. Conectar la app con las tablas existentes

#### Archivos a crear/modificar

```
📁 kmp/composeApp/src/
├── commonMain/
│   ├── kotlin/com/duovial/
│   │   ├── organizations/
│   │   │   ├── OrganizationRepository.kt  ← NUEVO: Interface
│   │   │   ├── Organization.kt            ← NUEVO: Data class
│   │   │   └── MemberRole.kt              ← NUEVO: Enum roles
│   │   └── di/
│   │       └── OrganizationModule.kt      ← NUEVO: DI
│   └── composeApp/
│       └── screens/
│           └── OrganizationScreen.kt      ← NUEVO: Gestión org
│
└── supabase/
    └── migrations/
        └── XXX_multi_tenancy.sql          ← NUEVO: Schema completo
```

#### Schema de Base de Datos

**Las tablas ya existen**. Solo se necesitan verificar y corregir las RLS policies:

```sql
-- Verificar RLS policies existentes
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
FROM pg_policies
WHERE schemaname = 'public'
AND tablename IN ('organizations', 'organization_members', 'vehicles', 'drivers');

-- Si las policies son demasiado permisivas, corregir:
-- Ejemplo: Cambiar policy para que solo service_role pueda hacer INSERT
DROP POLICY IF EXISTS "service_insert_vehicles" ON vehicles;
CREATE POLICY "service_insert_vehicles" ON vehicles
  FOR INSERT
  TO service_role
  WITH CHECK (true);

-- Ejemplo: Policy para que usuarios autenticados solo vean su org
CREATE POLICY "users_view_own_org_vehicles" ON vehicles
  FOR SELECT
  TO authenticated
  USING (org_id = current_org_id());
```

**Función helper existente**:
```sql
-- current_org_id() ya existe, verificar que funciona
SELECT current_org_id();  -- Debe retornar el org_id de la sesión

-- Si no existe, crear:
CREATE OR REPLACE FUNCTION current_org_id() RETURNS UUID AS $$
  SELECT current_setting('app.current_org_id', true)::UUID;
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- Agregar search_path para seguridad
ALTER FUNCTION current_org_id() SET search_path = 'public';
```

#### Criterios de Aceptación

**CA-1: Crear organización**
```
DADO que un admin quiere crear una empresa
CUANDO ingresa nombre y toca "Crear organización"
ENTONCES:
  - Se crea registro en organizations
  - Se crea organization_members con rol 'owner'
  - Se genera slug único (URL-friendly)
  - Se redirige al Dashboard de la org
```

**CA-2: Invitar conductor**
```
DADO que un admin invita a un conductor por email
CUANDO envía la invitación
ENTONCES:
  - Se crea organization_members con rol 'driver' y accepted_at NULL
  - Se envía email con link de invitación
  - El conductor acepta → accepted_at se actualiza
  - El conductor puede ver vehículos asignados
```

**CA-3: Aislamiento de datos (RLS)**
```
DADO que existen Org A y Org B con incidentes
CUANDO un usuario de Org A consulta incidentes
ENTONCES:
  - SOLO ve incidentes de Org A (nunca de Org B)
  - La consulta usa RLS automáticamente
  - No es necesario filtrar en el código
```

**CA-4: Cambiar de organización**
```
DADO que un usuario pertenece a múltiples organizaciones
CUANDO selecciona otra org en el selector
ENTONCES:
  - Se establece app.current_org_id
  - Todos los datos se filtran por la nueva org
  - El mapa muestra vehículos de la nueva org
```

#### Implementación

**Paso 1: OrganizationRepository**

```kotlin
// commonMain: organizations/OrganizationRepository.kt
interface OrganizationRepository {
    val currentOrg: StateFlow<Organization?>
    val userOrganizations: StateFlow<List<Organization>>
    
    suspend fun createOrganization(name: String): Result<Organization>
    suspend fun inviteMember(email: String, role: MemberRole): Result<Unit>
    suspend fun acceptInvitation(invitationId: String): Result<Unit>
    suspend fun switchOrganization(orgId: String): Result<Unit>
    suspend fun getMembers(orgId: String): Result<List<OrganizationMember>>
    suspend fun updateMemberRole(userId: String, role: MemberRole): Result<Unit>
    suspend fun removeMember(userId: String): Result<Unit>
}

data class Organization(
    val id: String,
    val name: String,
    val slug: String,
    val plan: String,
    val createdAt: String
)

enum class MemberRole {
    OWNER, ADMIN, SUPERVISOR, DRIVER
}

data class OrganizationMember(
    val userId: String,
    val email: String,
    val role: MemberRole,
    val acceptedAt: String?
)
```

**Paso 2: Establecer contexto de org en Supabase**

```kotlin
// androidMain: organizations/SupabaseOrganizationRepository.kt
class SupabaseOrganizationRepository(
    private val supabase: SupabaseClient
) : OrganizationRepository {
    
    private val _currentOrg = MutableStateFlow<Organization?>(null)
    override val currentOrg: StateFlow<Organization?> = _currentOrg.asStateFlow()
    
    override suspend fun switchOrganization(orgId: String): Result<Unit> {
        return try {
            // Establecer session variable para RLS
            supabase.postgrest.rpc(
                "set_config",
                mapOf(
                    "parameter" to "app.current_org_id",
                    "value" to orgId
                )
            )
            
            // Cargar datos de la org
            val org = supabase.from("organizations")
                .select { filter { eq("id", orgId) } }
                .decodeSingle<Organization>()
            
            _currentOrg.value = org
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Paso 3: SQL para establecer configuración**

```sql
-- Función para establecer configuración de sesión
CREATE OR REPLACE FUNCTION set_config(parameter TEXT, value TEXT)
RETURNS VOID AS $$
  SELECT set_config(parameter, value, false);
$$ LANGUAGE sql;
```

#### Notas técnicas

1. **RLS automático**: Una vez configurado, TODAS las consultas filtran automáticamente por org
2. **Performance**: RLS agrega overhead mínimo (<1ms por query)
3. **Slug**: Generar automáticamente desde el nombre (ej: "Transportes ABC" → "transportes-abc")
4. **Migración**: Agregar org_id a incidents existentes (puede ser NULL para datos legacy)
5. **Testing**: Crear orgs de prueba y verificar aislamiento

---

<a name="c-06"></a>
### 5.5 C-06 — Supabase Realtime para Telemetría

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Puntos** | 5 |
| **Dependencias** | C-04, C-05 |
| **Sprint** | 11-12 |

#### Descripción

Configurar Supabase Realtime para que la app Android envíe telemetría (ubicación, estado, G-Force, batería) cada 30 segundos y el Dashboard Web la reciba en tiempo real via WebSockets.

#### Archivos a crear/modificar

```
📁 kmp/composeApp/src/
├── commonMain/
│   ├── kotlin/com/duovial/
│   │   ├── realtime/
│   │   │   ├── TelemetryRepository.kt    ← NUEVO: Interface
│   │   │   ├── TelemetryData.kt          ← NUEVO: Data class
│   │   │   └── RealtimeManager.kt        ← NUEVO: Gestión conexión
│   │   └── di/
│   │       └── RealtimeModule.kt         ← NUEVO: DI
│   └── composeApp/
│       └── viewmodels/
│           └── TelemetryViewModel.kt     ← NUEVO: ViewModel
│
└── androidMain/
    └── kotlin/com/duovial/
        └── realtime/
            └── SupabaseRealtimeManager.kt ← NUEVO: Implementación
```

#### Schema de Base de Datos

**La tabla `vehicle_telemetry` ya existe** con los siguientes campos:
- id, org_id, vehicle_id, driver_id
- latitude, longitude, location (geography), speed_kmh, g_force, heading
- battery_level, storage_free_mb, device_temp_celsius
- created_at

**Verificar que Realtime está habilitado**:

```sql
-- Verificar si la tabla está en la publicación de Realtime
SELECT * FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'vehicle_telemetry';

-- Si no está, agregar:
ALTER PUBLICATION supabase_realtime ADD TABLE vehicle_telemetry;

-- Verificar RLS policies
SELECT * FROM pg_policies WHERE tablename = 'vehicle_telemetry';

-- Si las policies son demasiado permisivas, corregir:
DROP POLICY IF EXISTS "service_insert_telemetry" ON vehicle_telemetry;
CREATE POLICY "service_insert_telemetry" ON vehicle_telemetry
  FOR INSERT
  TO service_role
  WITH CHECK (true);

-- Policy para que usuarios autenticados solo vean telemetría de su org
CREATE POLICY "users_view_own_org_telemetry" ON vehicle_telemetry
  FOR SELECT
  TO authenticated
  USING (org_id = current_org_id());
```

**TTL opcional** (limpiar datos antiguos):
```sql
-- Función para limpiar telemetría mayor a 24 horas
CREATE OR REPLACE FUNCTION cleanup_old_telemetry()
RETURNS VOID AS $$
  DELETE FROM vehicle_telemetry 
  WHERE created_at < now() - INTERVAL '24 hours';
$$ LANGUAGE sql SECURITY DEFINER;

-- Programar limpieza cada hora (pg_cron)
SELECT cron.schedule('cleanup-telemetry', '0 * * * *', 'SELECT cleanup_old_telemetry()');
```

#### Criterios de Aceptación

**CA-1: Envío de telemetría**
```
DADO que el Vigilante está activo
CUANDO han pasado 30 segundos
ENTONCES:
  - Se envía telemetría a Supabase: {lat, lon, speed, gforce, status, battery}
  - El registro se asocia al vehicle_id y org_id correctos
  - No se envía si no hay conexión (se encola)
```

**CA-2: Recepción en Dashboard**
```
DADO que el Dashboard Web está abierto
CUANDO un vehículo envía telemetría
ENTONCES:
  - El marcador en el mapa se actualiza en <2 segundos
  - Se muestra: velocidad, G-Force, estado, batería
  - La trayectoria se dibuja en el mapa
```

**CA-3: Múltiples vehículos**
```
DADO que una flota tiene 10 vehículos activos
CUANDO todos envían telemetría simultáneamente
ENTONCES:
  - El Dashboard recibe los 10 streams sin lag
  - Cada vehículo tiene su propio marcador
  - No hay mezcla de datos entre vehículos
```

**CA-4: Reconexión automática**
```
DADO que la conexión WebSocket se pierde
CUANDO se recupera la conexión
ENTONCES:
  - Se reconecta automáticamente
  - Se sincroniza el estado actual
  - No se pierden datos críticos
```

#### Implementación

**Paso 1: TelemetryData**

```kotlin
// commonMain: realtime/TelemetryData.kt
@Serializable
data class TelemetryData(
    val vehicleId: String,
    val orgId: String,
    val driverId: String?,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double?,
    val heading: Double?,
    val altitude: Double?,
    val gForce: Double?,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val serviceStatus: String,
    val recordedAt: String = Clock.System.now().toString()
)
```

**Paso 2: TelemetryRepository**

```kotlin
// commonMain: realtime/TelemetryRepository.kt
interface TelemetryRepository {
    val isConnected: StateFlow<Boolean>
    val lastTelemetry: StateFlow<TelemetryData?>
    
    suspend fun sendTelemetry(data: TelemetryData): Result<Unit>
    suspend fun startListening(vehicleId: String): Flow<TelemetryData>
    suspend fun stopListening()
    suspend fun reconnect()
}
```

**Paso 3: Implementación Android**

```kotlin
// androidMain: realtime/SupabaseRealtimeManager.kt
class SupabaseRealtimeManager(
    private val supabase: SupabaseClient,
    private val repository: OrganizationRepository
) : TelemetryRepository {
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _lastTelemetry = MutableStateFlow<TelemetryData?>(null)
    override val lastTelemetry: StateFlow<TelemetryData?> = _lastTelemetry.asStateFlow()
    
    private var realtimeChannel: RealtimeChannel? = null
    
    override suspend fun sendTelemetry(data: TelemetryData): Result<Unit> {
        return try {
            supabase.from("vehicle_telemetry")
                .insert(data)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun startListening(vehicleId: String): Flow<TelemetryData> {
        realtimeChannel = supabase.channel("telemetry-$vehicleId")
        
        val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(
            schema = "public",
            table = "vehicle_telemetry",
            filter = "vehicle_id=eq.$vehicleId"
        )
        
        realtimeChannel!!.subscribe()
        _isConnected.value = true
        
        return changeFlow.map { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    action.decodeRecord<TelemetryData>()
                }
                else -> null
            }
        }.filterNotNull()
    }
    
    override suspend fun stopListening() {
        realtimeChannel?.unsubscribe()
        realtimeChannel = null
        _isConnected.value = false
    }
    
    override suspend fun reconnect() {
        stopListening()
        // Re-suscribirse al canal
        repository.currentOrg.value?.let { org ->
            // Lógica de reconexión
        }
    }
}
```

**Paso 4: Integración con BackgroundCameraService**

```kotlin
// Modificar BackgroundCameraService para enviar telemetría
class BackgroundCameraService : LifecycleService() {
    private lateinit var telemetryRepository: TelemetryRepository
    private var telemetryJob: Job? = null
    
    private fun startTelemetrySync() {
        telemetryJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val telemetry = TelemetryData(
                        vehicleId = currentVehicleId,
                        orgId = currentOrgId,
                        driverId = currentDriverId,
                        latitude = lastLatitude,
                        longitude = lastLongitude,
                        speedKmh = lastSpeed,
                        gForce = lastGForce,
                        batteryLevel = getBatteryLevel(),
                        isCharging = isCharging(),
                        serviceStatus = currentState.name
                    )
                    
                    telemetryRepository.sendTelemetry(telemetry)
                } catch (e: Exception) {
                    Log.e("Telemetry", "Error sending", e)
                }
                
                delay(30_000) // 30 segundos
            }
        }
    }
}
```

**Paso 5: Dashboard Web (React ejemplo)**

```javascript
// Dashboard: useTelemetry.js
import { createClient } from '@supabase/supabase-js'
import { useEffect, useState } from 'react'

export function useTelemetry(vehicleId) {
  const [telemetry, setTelemetry] = useState(null)
  
  useEffect(() => {
    const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)
    
    const channel = supabase
      .channel(`telemetry-${vehicleId}`)
      .on('postgres_changes', {
        event: 'INSERT',
        schema: 'public',
        table: 'vehicle_telemetry',
        filter: `vehicle_id=eq.${vehicleId}`
      }, (payload) => {
        setTelemetry(payload.new)
      })
      .subscribe()
    
    return () => {
      supabase.removeChannel(channel)
    }
  }, [vehicleId])
  
  return telemetry
}
```

#### Notas técnicas

1. **Frecuencia**: 30 segundos es un buen balance entre latencia y costo
2. **Batching**: Considerar batch inserts si hay muchos vehículos
3. **TTL**: Implementar limpieza automática para no acumular datos
4. **Costos**: Supabase cobra por mensajes Realtime (optimizar frecuencia)
5. **Offline**: Encolar telemetría y enviar cuando haya conexión

---

## 🔧 CONSIDERACIONES TÉCNICAS TRANSVERSALES

### 1. Manejo de errores global

```kotlin
// commonMain: core/SupabaseErrorHandler.kt
object SupabaseErrorHandler {
    fun mapError(e: Exception): String {
        return when {
            e is HttpRequestException -> "Sin conexión a internet"
            e.message?.contains("JWT expired") == true -> "Sesión expirada, inicia sesión nuevamente"
            e.message?.contains("duplicate key") == true -> "El registro ya existe"
            e.message?.contains("foreign key") == true -> "Referencia no válida"
            else -> "Error inesperado: ${e.message}"
        }
    }
}
```

### 2. Logging y observabilidad

```kotlin
// Agregar logging en puntos críticos
class SupabaseLogger {
    fun logAuth(event: String, userId: String?) {
        println("[AUTH] $event | user=$userId | time=${Instant.now()}")
    }
    
    fun logRealtime(event: String, vehicleId: String) {
        println("[REALTIME] $event | vehicle=$vehicleId | time=${Instant.now()}")
    }
    
    fun logStorage(event: String, path: String) {
        println("[STORAGE] $event | path=$path | time=${Instant.now()}")
    }
}
```

### 3. Testing strategy

```kotlin
// Tests unitarios para cada repositorio
class AuthRepositoryTest { /* ... */ }
class OrganizationRepositoryTest { /* ... */ }
class TelemetryRepositoryTest { /* ... */ }
class VideoStorageRepositoryTest { /* ... */ }

// Tests de integración con Supabase
class SupabaseIntegrationTest {
    @Test
    fun `full flow: register, create org, send telemetry`() = runTest {
        // 1. Registrar usuario
        // 2. Crear organización
        // 3. Enviar telemetría
        // 4. Verificar datos en BD
    }
}
```

### 4. Seguridad

```kotlin
// NUNCA exponer service role key en la app
// Usar solo anon key + RLS

// Verificar JWT en cada request (Supabase SDK lo hace automáticamente)
// Usar EncryptedSharedPreferences para tokens
// Implementar certificate pinning para producción
```

---

## ✅ CRITERIOS DE VALIDACIÓN GLOBAL

### Al completar Fase C, verificar:

1. **Auth funciona**
   - [ ] Registro con email
   - [ ] Login con email
   - [ ] Google Sign-In
   - [ ] Recuperación de contraseña
   - [ ] Persistencia de sesión
   - [ ] Logout

2. **Storage funciona**
   - [ ] Subida de video manual
   - [ ] Visualización remota
   - [ ] Manejo de errores

3. **Multi-tenancy funciona**
   - [ ] Crear organización
   - [ ] Invitar miembros
   - [ ] RLS aísla datos
   - [ ] Cambiar de organización

4. **Realtime funciona**
   - [ ] Envío de telemetría cada 30s
   - [ ] Recepción en Dashboard
   - [ ] Reconexión automática

5. **Integración completa**
   - [ ] Login → Crear org → Invitar → Login conductor → Enviar telemetría
   - [ ] Todo el flujo funciona sin crashes

---

## ⚠️ RIESGOS Y MITIGACIONES

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|--------|-----------|
| **RLS mal configurado** | Media | Crítico | Tests exhaustivos de aislamiento |
| **Tokens expiran** | Alta | Medio | Refresh automático (SDK lo maneja) |
| **Realtime latency** | Media | Medio | Health checks + reconexión |
| **Storage costs** | Baja | Medio | Límites por plan + monitoreo |
| **Google Sign-In config** | Media | Alto | Documentar SHA-1 + Cloud Console |
| **Deep links no funcionan** | Media | Medio | Testing en múltiples dispositivos |
| **Offline sync conflicts** | Baja | Medio | Server-wins strategy |

---

## 📋 RESUMEN DE CAMBIOS REQUERIDOS

### 🔴 Cambios críticos (ANTES de implementar C-01)

1. **Corregir RLS policies** (12 policies demasiado permisivas)
   - Verificar que son para `service_role` (no `anon`/`authenticated`)
   - Agregar policies para usuarios autenticados con `current_org_id()`

2. **Corregir funciones SECURITY DEFINER** (5 functions)
   - Revocar EXECUTE para `anon`
   - Agregar `search_path` fijo

3. **Verificar triggers existentes**
   - `handle_new_user()` - crear profile al registrar usuario
   - `handle_new_organization()` - crear org personal al registrar

### 🟡 Cambios para C-01 (Auth)

1. **Eliminar dependencias de AWS Cognito** del código Android
2. **Agregar dependencias de Supabase Kotlin SDK** en `build.gradle.kts`
3. **Crear módulo de auth** con:
   - `AuthRepository` (interface común)
   - `SupabaseAuthProvider` (implementación Android)
   - `GoogleSignInHelper` (OAuth)
4. **Configurar deep links** para OAuth callback
5. **Modificar LoginScreen** para usar Supabase Auth
6. **Crear RegisterScreen** y **ForgotPasswordScreen**

### 🟢 Cambios para C-02 (Login/Registro Opcional)

1. **Implementar sesión anónima** con `signInAnonymously()`
2. **Agregar lógica de conversión** de anónimo a registrado
3. **Mostrar banner** "Crea una cuenta para sincronizar"

### 🟢 Cambios para C-03 (Storage)

1. **Verificar bucket** `incident-videos` existe en Supabase Storage
2. **Crear policies** para que usuarios solo vean sus videos
3. **Implementar `VideoStorageRepository`** con:
   - `uploadVideo()` - subida manual
   - `getSignedUrl()` - URL con expiración
   - `deleteVideo()` - eliminación
4. **Agregar botón "Subir"** en IncidentPlayerScreen

### 🟢 Cambios para C-05 (Multi-tenancy)

1. **Verificar RLS policies** en organizations, vehicles, drivers
2. **Implementar `OrganizationRepository`** con:
   - `switchOrganization()` - cambiar contexto
   - `inviteMember()` - invitar conductores
   - `getMembers()` - listar miembros
3. **Agregar Organization Selector** en la UI
4. **Establecer `app.current_org_id`** en cada sesión

### 🟢 Cambios para C-06 (Realtime)

1. **Verificar que Realtime está habilitado** en `vehicle_telemetry`
2. **Implementar `TelemetryRepository`** con:
   - `sendTelemetry()` - enviar datos cada 30s
   - `startListening()` - suscribirse a cambios
3. **Integrar con BackgroundCameraService** para envío automático
4. **Implementar reconexión automática**

---

## 📚 REFERENCIAS

- [Supabase Auth Kotlin SDK](https://github.com/supabase-community/supabase-kt)
- [Supabase Realtime](https://supabase.com/docs/guides/realtime)
- [Supabase Storage](https://supabase.com/docs/guides/storage)
- [Row Level Security](https://supabase.com/docs/guides/auth/row-level-security)
- [Google Sign-In Android](https://developers.google.com/identity/sign-in/android)

---

**Documento generado por**: Tech Lead Agent
**Fecha**: Julio 15, 2026
**Versión**: 1.1
**Estado**: Listo para implementación
**Schema verificado**: ✅ 17 tablas + 7 Edge Functions
