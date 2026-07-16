# FASE C - Cambios Implementados

**Fecha**: Julio 16, 2026  
**Rama**: `feature/fase-c-supabase-integration`  
**Estado**: Implementación completa, pendiente de testing

---

## Resumen

Implementación completa de la Fase C del documento `FASE_C_Implementation.md`. Se reemplazó AWS Cognito por Supabase Auth y se crearon los módulos de Storage, Organizations y Realtime.

---

## Archivos Modificados (10)

| Archivo | Cambio |
|---------|--------|
| `kmp/gradle/libs.versions.toml` | Eliminadas dependencias AWS, agregadas dependencias Supabase |
| `kmp/composeApp/build.gradle.kts` | Reemplazado aws-cognito por supabase-bom + módulos |
| `AndroidManifest.xml` | Agregado deep link `duovial://callback/auth` para OAuth |
| `DuoVialConfig.kt` | Reemplazado cognitoUserPoolId por supabaseUrl/supabaseAnonKey |
| `MainActivity.kt` | Integrado SupabaseAuthService + Google Sign-In launcher |
| `App.kt` | Agregado flujo de auth (needsLogin), AuthViewModel, onGoogleSignIn |
| `AuthLocator.kt` | Agregado LocalAuthViewModel CompositionLocal |
| `AuthService.kt` | Expandida interfaz con Google, Anonymous, ResetPassword, etc. |
| `AuthState.kt` | Agregado AuthFlowState sealed class + AuthMode.FORGOT_PASSWORD |
| `LoginScreen.kt` | Agregado Google button, modo anónimo, forgot password |

---

## Archivos Nuevos (10)

### commonMain

| Archivo | Propósito |
|---------|-----------|
| `supabase/SupabaseClientProvider.kt` | Singleton para cliente Supabase |
| `supabase/SupabaseErrorHandler.kt` | Mapeo de errores a mensajes amigables |
| `auth/AuthViewModel.kt` | ViewModel para formularios de auth |
| `storage/VideoStorageRepository.kt` | Interface para subida de videos |
| `organizations/OrganizationRepository.kt` | Interface para multi-tenancy |
| `realtime/TelemetryRepository.kt` | Interface para telemetría en tiempo real |

### androidMain

| Archivo | Propósito |
|---------|-----------|
| `platform/SupabaseAuthService.kt` | Implementación de AuthService con Supabase |
| `platform/GoogleSignInHelper.kt` | Helper para Google OAuth |
| `platform/SupabaseVideoStorage.kt` | Implementación de VideoStorageRepository |
| `platform/SupabaseOrganizationRepository.kt` | Implementación de OrganizationRepository |
| `platform/SupabaseRealtimeManager.kt` | Implementación de TelemetryRepository |

---

## Tickets Implementados

### C-01: Reemplazar Auth por Supabase Auth ✅

**Archivos creados/modificados:**
- `SupabaseClientProvider.kt` - Cliente singleton
- `SupabaseAuthService.kt` - Implementación completa
- `GoogleSignInHelper.kt` - Google OAuth
- `AuthViewModel.kt` - ViewModel para UI
- `LoginScreen.kt` - UI actualizada
- `build.gradle.kts` - Dependencias
- `libs.versions.toml` - Versiones

**Funcionalidades:**
- Login con email + contraseña
- Registro con email
- Confirmación de email (OTP)
- Google Sign-In
- Recuperación de contraseña
- Persistencia de sesión (refresh automático)
- Manejo de errores amigable

### C-02: Login/Registro Opcional ✅

**Integrado en:**
- `SupabaseAuthService.kt` - `signInAnonymously()`, `linkToEmail()`
- `LoginScreen.kt` - Botón "Usar sin cuenta"
- `AuthState.kt` - `isAnonymous` en AuthUser

**Funcionalidades:**
- Sesión anónima sin email
- Conversión de anónimo a registrado
- Banner "Crea una cuenta" (pendiente UI)

### C-03: Supabase Storage para Videos ✅

**Archivos creados:**
- `VideoStorageRepository.kt` - Interface
- `SupabaseVideoStorage.kt` - Implementación

**Funcionalidades:**
- Subida manual de videos
- URLs firmadas (expiran en 7 días)
- Eliminación de videos
- Límite de 50MB
- Bucket privado con RLS

### C-05: Multi-tenancy (Organizations + RLS) ✅

**Archivos creados:**
- `OrganizationRepository.kt` - Interface + modelos
- `SupabaseOrganizationRepository.kt` - Implementación

**Funcionalidades:**
- Crear organización
- Invitar miembros por email
- Aceptar invitaciones
- Cambiar organización (switchOrganization)
- Gestión de roles (owner, admin, supervisor, driver)
- Aislamiento de datos via RLS

### C-06: Supabase Realtime para Telemetría ✅

**Archivos creados:**
- `TelemetryRepository.kt` - Interface + modelo TelemetryData
- `SupabaseRealtimeManager.kt` - Implementación

**Funcionalidades:**
- Envío de telemetría cada 30 segundos
- Suscripción a cambios via WebSocket
- Filtrado por vehicle_id
- Reconexión automática
- Datos: GPS, velocidad, G-Force, batería, estado

---

## Dependencias Agregadas

```toml
# libs.versions.toml
supabase = "3.1.1"
google-id = "1.1.1"

# Libraries
supabase-bom = { module = "io.github.jan-tennert.supabase:bom", version.ref = "supabase" }
supabase-auth = { module = "io.github.jan-tennert.supabase:auth-kt" }
supabase-realtime = { module = "io.github.jan-tennert.supabase:realtime-kt" }
supabase-storage = { module = "io.github.jan-tennert.supabase:storage-kt" }
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt" }
supabase-functions = { module = "io.github.jan-tennert.supabase:functions-kt" }
google-id = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "google-id" }
```

**Eliminadas:**
- `aws-cognitoidentityprovider`
- `aws-cognitoidentity`

---

## Configuración Requerida

### 1. Variables de Entorno (local.properties)

Los secretos se almacenan en `kmp/local.properties` (gitignored), NO en el código fuente.

```bash
# Copiar el ejemplo
cp kmp/local.properties.example kmp/local.properties
```

Editar `kmp/local.properties` con tus valores:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_ANON_KEY=TU-ANON-KEY
GOOGLE_WEB_CLIENT_ID=TU-WEB-CLIENT-ID.apps.googleusercontent.com
```

**NUNCA** subas `local.properties` al repositorio.

### 2. Supabase Dashboard

```sql
-- Verificar bucket incident-videos existe
SELECT * FROM storage.buckets WHERE id = 'incident-videos';

-- Verificar RLS policies
SELECT * FROM pg_policies WHERE tablename IN 
  ('organizations', 'organization_members', 'vehicle_telemetry');

-- Verificar Realtime habilitado
SELECT * FROM pg_publication_tables 
WHERE pubname = 'supabase_realtime' AND tablename = 'vehicle_telemetry';
```

### 3. Google Cloud Console

- Crear credencial de tipo "Web application"
- Agregar URI de autorización: `https://TU-PROYECTO.supabase.co/auth/v1/callback`
- Copiar el Client ID a `GOOGLE_WEB_CLIENT_ID` en `local.properties`

### 4. Supabase Dashboard > Auth > URL Configuration

- Site URL: `duovial://callback/auth`
- Redirect URLs: `duovial://callback/auth`

---

## Pendiente de Testing

1. **Compilar la app** - El usuario debe ejecutar `./gradlew assembleDebug`
2. **Probar flujo de auth completo** - Login, registro, Google, anónimo
3. **Probar subida de videos** - Verificar bucket y RLS
4. **Probar telemetría** - Verificar Realtime habilitado
5. **Probar multi-tenancy** - Crear org, invitar, verificar RLS

---

## Notas Importantes

1. **Secretos**: Se leen de `local.properties` via `BuildConfig` (nunca hardcodeados)
2. **Audio**: Nunca se graba audio (por ley y batería)
3. **Front Camera**: Solo analiza frames, nunca graba video
4. **Facial Recognition**: Solo alerta, nunca bloquea al conductor
