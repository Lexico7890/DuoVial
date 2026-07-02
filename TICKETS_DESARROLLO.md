# TICKETS DE DESARROLLO — DuoVial MVP

**Versión**: 3.1 | **Fecha**: Julio 2, 2026
**Repositorio**: `C:\Users\camip\Desktop\ocdev\DuoVial`
**Convención**: `[FASE]-[ID]` — Ej: `A-01`, `C-03`, `G-01`

---

## ANÁLISIS DE BRECHAS: CONTEXT.md vs Tickets Existentes

### Funcionalidades NUEVAS en CONTEXT.md sin ticket

| # | Funcionalidad | Sección CONTEXT.md | Estado |
|---|--------------|-------------------|--------|
| 1 | Multi-tenancy (Organizations + RLS) | Arquitectura Multi-tenancy | **SIN TICKET** |
| 2 | Google Play Billing + Wompi (híbrido app/web) | Payments Architecture | **SIN TICKET** (E-01 necesita reescribirse) |
| 3 | Mux video processing (transcoding HLS/DASH) | Video Processing Architecture | **SIN TICKET** |
| 4 | OneSignal push notifications | Stack tecnológico | **SIN TICKET** |
| 5 | Twilio IVR para colisiones | Colisión + Llamada Automática | **SIN TICKET** |
| 6 | Detección de colisión + llamada automática (6.11) | Sistema de Detección | **SIN TICKET** |
| 7 | Integración OBD II (ELM327) | Módulo 6.10 | **SIN TICKET** |
| 8 | Fleet onboarding flow | Dashboard Web | **SIN TICKET** |
| 9 | Billing Dashboard | Dashboard Web | **SIN TICKET** |
| 10 | Mantenimiento predictivo (6.5) | Módulo 6.5 | **SIN TICKET** |
| 11 | Anti-somnolencia 3 niveles (escalada completa) | Módulo 6.8 | **PARCIAL** (D-01/D-02 solo detección básica) |
| 12 | Reconocimiento facial (6.3) | Módulo 6.3 | **SIN TICKET** |
| 13 | Offline sync strategy (6.9) | Módulo 6.9 | **SIN TICKET** |
| 14 | Legal & Compliance | Sección Legal | **SIN TICKET** |
| 15 | Monitoring & Observability | Sección Monitoring | **SIN TICKET** |
| 16 | Auto-Inicio por Actividad (6.6) | Módulo 6.6 | **SIN TICKET** (H-04 — diferente a A-10) |
| 17 | Supabase Edge Functions | Arquitectura | **SIN TICKET** |
| 18 | Supabase Realtime | Arquitectura | **SIN TICKET** |
| 19 | Configurar umbral G-Force desde UI | Especificaciones | **SIN TICKET** |
| 20 | Configurar umbrales de fatiga desde UI | Especificaciones | **SIN TICKET** |
| 21 | Auto-inicio del Vigilante a 30 km/h | FASE_A (sección G) | **CON TICKET** (A-10) |
| 22 | Borrado automático de videos >72 horas | FASE_A (sección H) | **CON TICKET** (A-11) |

### Tickets existentes que necesitan MODIFICACIÓN

| Ticket | Cambio requerido | Razón |
|--------|-----------------|-------|
| E-01 | Cambiar Stripe por **Google Play Billing + Wompi** | Stripe no funciona bien en Colombia; se usa esquema híbrido |
| G-04 | Cambiar Stripe + Sync Engine por **Google Play Billing + Wompi** | Mismo motivo que E-01 |
| C-04 | Agregar tablas: `organizations`, `organization_members`, `vehicles`, `drivers`, `maintenance_rules`, `odometer_logs`, `obd_readings`, `maintenance_alerts`, `geofence_events`, `drivers.face_embedding` | Multi-tenancy + Fleet features |
| F-01 | Mover de `fase:post-mvp` a `fase:fleet` (MVP Fleet) | CONTEXT.md lo marca como MVP Fleet |
| F-03 | Mover de `fase:post-mvp` a `fase:fleet` (MVP Fleet) | CONTEXT.md lo marca como MVP Fleet |
| D-02 | Expandir para soportar 3 niveles de escalada | CONTEXT.md define Nivel 1/2/3 |

---

## ORDEN DE EJECUCIÓN RECOMENDADO

### Fase 0: Infraestructura Base (Semana 1-2)
> **Sin esto, nada funciona.** Configuración de servicios en la nube.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 1 | **G-01** | Setup Supabase Edge Functions | Ninguna |
| 2 | **G-02** | Setup OneSignal push notifications | Ninguna |
| 3 | **G-03** | Setup Mux video processing | Ninguna |
| 4 | **G-04** | Setup Google Play Billing + Wompi | Ninguna |
| 5 | **C-04** | Schema de BD completo (actualizado) | G-01 |

### Fase A: Estabilidad del Vigilante (Semana 3-6)
> **Core de la app.** Sin esto, la dash cam no funciona.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 6 | A-01 | Migrar cámara frontal a CameraX | Ninguna |
| 7 | A-02 | Refactorizar buffer circular a 3 videos | Ninguna |
| 8 | A-09 | Detener NO guarda buffer | Ninguna |
| 9 | A-06 | Reducir cooldown 12s → 5s | Ninguna |
| 10 | A-04 | Verificar Foreground Service Android 14/15 | A-02 |
| 11 | A-08 | Configurar duración post-evento | A-02 |
| 12 | A-07 | Botón EVENTO en notificación | Ninguna |
| 13 | **G-05** | Configurar umbral G-Force desde UI | A-02 |
| 14 | A-03 | Stress test buffer circular >4h | A-02, A-04 |
| 15 | A-05 | Reparar EventsScreen con lista real | A-02 |
| 16 | **A-10** | Auto-inicio del Vigilante a 30 km/h | Ninguna |
| 17 | **A-11** | Borrado automático de videos >72 horas | Ninguna |

### Fase B: Onboarding y UX (Semana 7-8)
> **Primera impresión del usuario.** Permisos y onboarding.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 16 | B-01 | Pantalla de Onboarding con disclaimer OIS | Ninguna |
| 17 | B-02 | Solicitud granular de permisos | B-01 |
| 18 | B-03 | Reproductor de video integrado | A-05 |

### Fase C: Supabase y Backend (Semana 9-12)
> **Conectividad con la nube.** Auth, storage, multi-tenancy.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 19 | C-01 | Reemplazar AWS Cognito por Supabase Auth | Ninguna |
| 20 | C-02 | Login/Registro opcional | C-01 |
| 21 | C-03 | Supabase Storage para videos | C-01, C-04 |
| 22 | **C-05** | Multi-tenancy (Organizations + RLS) | C-01, C-04 |
| 23 | **C-06** | Supabase Realtime para telemetría | C-04 |

### Fase D: Anti-Somnolencia Avanzada (Semana 13-16)
> **Prevención de accidentes.** Detección + escalada de 3 niveles.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 24 | D-01 | Health Connect: lectura de wearables | A-01 |
| 25 | D-02 | Lógica de detección de fatiga por wearable | D-01 |
| 26 | D-03 | Detección de cámaras concurrentes | A-01 |
| 27 | D-04 | Pantalla de sugerencia de wearables | D-01 |
| 28 | **G-06** | Configurar umbrales de fatiga desde UI | D-02 |
| 29 | **D-05** | Sistema de escalada 3 niveles | D-01, D-02, G-02 |
| 30 | **D-06** | FatigueScreen con CameraX integrado | A-01, D-01 |

### Fase E: Monetización (Semana 17-20)
> **Revenue.** Pagos, informes, dashboards de métricas.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 31 | E-01 | Integrar Stripe (pagos Colombia) | C-01, G-04 |
| 32 | E-02 | Generación de informe PDF | E-01, C-03 |
| 33 | E-03 | Flujo de pago por evento | E-01, E-02, C-03 |
| 34 | E-04 | Flujo de pago por día anti-somnolencia | E-01, D-02, C-04 |
| 35 | E-05 | Dashboard de métricas de fatiga (móvil) | E-04, D-02 |
| 36 | **E-06** | Customer Portal (self-service) | E-01, G-04 |

### Fase F: Fleet — MVP Empresarial (Semana 21-28)
> **El foco de revenue.** Dashboard web, geofencing, facial, OBD, colisión.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 37 | F-01 | Geo-fencing: alertas de perímetro | C-05, G-02 |
| 38 | **F-04** | Reconocimiento facial (solo alerta) | C-05, C-04 |
| 39 | **F-05** | Fleet onboarding flow | C-05, C-01 |
| 40 | F-03 | Dashboard Enterprise (web) | C-05, C-06, E-01 |
| 41 | **F-06** | Billing Dashboard | F-03, E-01 |
| 42 | **F-07** | Integración OBD II (ELM327) | C-05, C-04 |
| 43 | **F-08** | Colisión + Llamada Twilio automática | G-03, G-02, A-02 |
| 44 | **F-09** | Mantenimiento predictivo | C-05, F-07 |

### Fase H: Operaciones y Compliance (Semana 29-30)
> **Robustez y legalidad.** Offline, monitoring, compliance.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 45 | **H-01** | Offline sync strategy | C-04 |
| 46 | **H-02** | Legal & Compliance (Privacy Policy, consent) | Ninguna |
| 47 | **H-03** | Monitoring & Observability | G-01 |
| 48 | **H-04** | Auto-Inicio por Actividad | Ninguna |

### Fase I: Post-MVP (Semana 31+)
> **Futuro.** Solo si hay demanda.

| Orden | Ticket | Descripción | Dependencias |
|-------|--------|-------------|--------------|
| 49 | F-02 | Detección de salud de alto riesgo | D-01, D-02 |

---

## RESUMEN POR SPRINT

| Sprint | Fase | Tickets | Prioridad | Puntos |
|--------|------|---------|-----------|--------|
| 1-2 | G: Infraestructura | G-01 → G-06 | P0 | 21 |
| 3-6 | A: Estabilidad | A-01 → A-11 + G-05 | P0-P1 | 42 |
| 7-8 | B: Onboarding/UX | B-01 → B-03 | P1 | 13 |
| 9-12 | C: Supabase | C-01 → C-06 | P1 | 30 |
| 13-16 | D: Anti-Somnolencia | D-01 → D-06 + G-06 | P1-P2 | 33 |
| 17-20 | E: Monetización | E-01 → E-06 | P2 | 37 |
| 21-28 | F: Fleet | F-01, F-03 → F-09 | P2 | 56 |
| 29-30 | H: Operaciones | H-01 → H-04 | P2 | 16 |
| 31+ | I: Post-MVP | F-02 | P3 | 8 |

---

# FASE G: INFRAESTRUCTURA BASE (Sprint 1-2)

> **CRÍTICO**: Estos tickets deben completarse ANTES de cualquier funcionalidad que dependa de servicios en la nube.

---

## G-01 — Setup Supabase Edge Functions

| Campo | Valor |
|-------|-------|
| **Tipo** | Chore / Infra |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | Proyecto Supabase (Edge Functions) |
| **Estimación** | 3 puntos |

### Descripción

Configurar el proyecto local de Supabase con Edge Functions para manejar: webhooks de Mux (transcoding completo), webhooks de Stripe (sync de pagos), y triggers de notificaciones push (OneSignal).

### Criterios de Aceptación

- **Dado** que Supabase está configurado localmente
  **Cuando** se ejecuta `supabase start`
  **Entonces** todas las Edge Functions están desplegadas y responden correctamente.

- **Dado** que Mux envía un webhook `asset.ready`
  **Cuando** la Edge Function lo recibe
  **Entonces** actualiza el estado del incidente en la BD y notifica al Dashboard via Realtime.

- **Dado** que Stripe envía un webhook `checkout.session.completed`
  **Cuando** la Edge Function lo recibe
  **Entonces** sincroniza el pago a la tabla `payments` y actualiza el plan del usuario/organización.

### Notas Técnicas

- Functions a crear: `trigger-mux-transcode`, `mux-webhook-handler`, `stripe-webhook`, `send-push-notification`.
- Usar Deno runtime (estándar de Supabase Edge Functions).
- Variables de entorno: `MUX_TOKEN_ID`, `MUX_TOKEN_SECRET`, `STRIPE_SECRET_KEY`, `ONESIGNAL_APP_ID`.

---

## G-02 — Setup OneSignal push notifications

| Campo | Valor |
|-------|-------|
| **Tipo** | Chore / Infra |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | OneSignal Dashboard, `build.gradle.kts`, `AndroidManifest.xml` |
| **Estimación** | 3 puntos |

### Descripción

Configurar OneSignal para notificaciones push cross-platform. Crear la app en OneSignal, configurar FCM, e integrar el SDK en la app Android.

### Criterios de Aceptación

- **Dado** que OneSignal está configurado
  **Cuando** se envía una notificación de prueba desde el dashboard
  **Entonces** el dispositivo Android la recibe.

- **Dado** que el usuario tiene la app instalada
  **Cuando** la primera vez que se registra
  **Entonces** se envía el playerId a Supabase y se almacena en `profiles.push_token`.

- **Dado** que un admin envía una alerta de geofencing
  **Cuando** se ejecuta la Edge Function
  **Entonces** el conductor recibe la notificación push en tiempo real.

### Notas Técnicas

- Crear app en OneSignal Dashboard.
- Configurar FCM Server Key en OneSignal.
- Integrar `OneSignal Kotlin SDK` en `build.gradle.kts`.
- Crear segmentos: "drivers", "admins", "all_users".
- Para MVP, usar OneSignal免费 tier (hasta 10k usuarios).

---

## G-03 — Setup Mux video processing

| Campo | Valor |
|-------|-------|
| **Tipo** | Chore / Infra |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | Mux Dashboard, Supabase Edge Functions |
| **Estimación** | 3 puntos |

### Descripción

Configurar Mux para transcodificación de videos de incidentes. Crear cuenta, configurar webhook con Supabase, y definir políticas de encoding (HLS/DASH, bitrate adaptivo).

### Criterios de Aceptación

- **Dado** que un video se sube a Supabase Storage
  **Cuando** se dispara el webhook `object.created`
  **Entonces** la Edge Function crea un Mux Asset y retorna `mux_asset_id`.

- **Dado** que Mux completa la transcodificación
  **Cuando** envía webhook `asset.ready`
  **Entonces** el incidente tiene `streaming_url` válido y `status = 'ready'`.

- **Dado** que el Dashboard solicita reproducir un video
  **Cuando** usa el `mux_playback_id`
  **Entonces** el video carga via Mux Player (HLS) sin buffering visible.

### Notas Técnicas

- Free Tier: 100 min encoding/mes, 500 min streaming/mes.
- Crear Mux Video playground para pruebas.
- Configurar encoding: H.264 → HLS (1080p, 720p, 480p, 360p).
- Webhook URL: `https://{project-ref}.supabase.co/functions/v1/mux-webhook-handler`.

---

## G-04 — Setup Google Play Billing + Wompi

| Campo | Valor |
|-------|-------|
| **Tipo** | Chore / Infra |
| **Prioridad** | P0 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | Google Play Console, Wompi Dashboard, Google Cloud Console, Supabase (Edge Functions, migraciones) |
| **Estimación** | 5 puntos |

### Descripción

Configurar esquema híbrido de pagos: **Google Play Billing** para compras dentro de la app Android (obligatorio por política de Google para bienes digitales) y **Wompi** para pagos desde la web Dashboard (PSE, Nequi, tarjetas colombianas). NO se usa Stripe.

### Criterios de Aceptación

- **Dado** que Google Play Console está configurado
  **Cuando** se crean los productos `premium_monthly`, `fleet_monthly`, `per_event`
  **Entonces** están disponibles para compra con precios en COP.

- **Dado** que RTDN está configurado con Pub/Sub
  **Cuando** ocurre un evento de suscripción (compra, renovación, cancelación)
  **Entonces** la Edge Function recibe la notificación y actualiza la BD.

- **Dado** que Wompi está configurado en sandbox
  **Cuando** se crea un payment link desde la Edge Function
  **Entonces** el usuario puede pagar con PSE, Nequi o tarjeta.

- **Dado** que Wompi envía webhook `transaction.updated`
  **Cuando** la Edge Function lo recibe
  **Entonces** verifica firma SHA256, consulta estado en API, y actualiza la BD.

### Notas Técnicas

- **Google Play Console**: Crear cuenta developer ($25 USD), crear productos de suscripción y one-time, configurar RTDN con Pub/Sub, crear Service Account con rol Finance.
- **Google Cloud Console**: Crear proyecto, habilitar Pub/Sub API, crear tópico y suscripción para RTDN.
- **Wompi**: Crear cuenta sandbox, obtener Public Key, Private Key, Event Secret, configurar webhook URL.
- **Edge Functions nuevas**: `verify-google-purchase`, `wompi-webhook`, `create-wompi-link`, `process-recurring-billing`.
- **Edge Functions eliminadas**: `stripe-webhook`, `create-checkout-session`, `create-portal-session`.
- **Migración nueva**: `006_billing.sql` con tablas `products`, `purchases`, `subscriptions`, `billing_events`, `wompi_card_tokens`.
- **Wompi NO tiene suscripciones nativas**: Para cobros recurrentes Fleet, usar scheduled function (pg_cron) con tokens de tarjeta guardados.
- **Precios COP**: Premium $10,900/mes, Fleet $9,900/mes/vehículo, Por Evento $19,900, Instalación OBD $39,900.

---

## G-05 — Configurar umbral G-Force desde UI

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | A-02 |
| **Archivos afectados** | `SettingsScreen.kt`, `SettingsManager.kt`, `BackgroundCameraService.kt` |
| **Estimación** | 3 puntos |

### Descripción

Agregar un slider en Settings para que el usuario pueda configurar el umbral de G-Force que dispara un evento (rango: 1.5G a 5.0G, default: 2.5G). El valor se persiste y se usa por el acelerómetro.

### Criterios de Aceptación

- **Dado** que el usuario está en Settings
  **Cuando** ajusta "Umbral G-Force" a 3.0G
  **Entonces** los próximos eventos se disparan solo si el acelerómetro detecta ≥3.0G.

- **Dado** que el usuario configuró 2.0G
  **Cuando** ocurre una frenada brusca que genera 2.1G
  **Entonces** se dispara un evento (estaba por encima del umbral).

- **Dado** que el usuario cierra y reabre la app
  **Cuando** revisa Settings
  **Entonces** el umbral conserva el último valor configurado.

- **Dado** que el usuario intenta poner <1.5G o >5.0G
  **Cuando** ajusta el slider
  **Entonces** el valor se fuerza dentro del rango 1.5-5.0G.

### Notas Técnicas

- Agregar `gForceThreshold` a `SettingsManager` (Multiplatform Settings).
- Default: 2.5G.
- El slider muestra valores con 1 decimal (1.5, 2.0, 2.5, 3.0, ...).

---

## G-06 — Configurar umbrales de fatiga desde UI

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | D-02 |
| **Archivos afectados** | `SettingsScreen.kt`, `SettingsManager.kt`, `FatigueScreen.kt` |
| **Estimación** | 3 puntos |

### Descripción

Agregar controles en Settings/FatigueScreen para configurar: closed eye threshold (0.1-0.4, default 0.2), duration (1-5s, default 2s), snooze (1-30min, default 5min), max alerts per hour (1-5, default 3).

### Criterios de Aceptación

- **Dado** que el usuario está en FatigueScreen > Ajustes
  **Cuando** cambia "Closed eye threshold" a 0.3
  **Entonces** la cámara frontal solo alerta si EAR < 0.3.

- **Dado** que el usuario cambia "Duration" a 3 segundos
  **Cuando** los ojos están cerrados por 2.5 segundos
  **Entonces** NO se alerta (no alcanzó el umbral de 3s).

- **Dado** que el usuario configuró "Snooze" a 10 minutos
  **Cuando** se emite una alerta
  **Entonces** las siguientes 10 minutos no se emiten más alertas.

### Notas Técnicas

- Persistir en `SettingsManager` con Multiplatform Settings.
- Mostrar valores actuales en FatigueScreen (panel de configuración).

---

# FASE A: ESTABILIDAD DEL VIGILANTE (Sprint 3-6)

> Los tickets A-01 a A-09 ya están documentados arriba. Aquí solo se documentan las actualizaciones.

### Notas de actualización para Fase A

- **A-02**: Se mantiene tal cual. La refactorización a 3 videos es crítica.
- **A-08**: Se mantiene. La configuración de post-evento es necesaria antes de G-05.
- **A-05**: Después de completar, integrar con B-03 (reproductor de video).
- **Nuevo orden**: A-01 → A-02 → A-09 → A-06 → A-04 → A-08 → A-07 → G-05 → A-03 → A-05 → A-10 → A-11.

---

## A-10 — Auto-inicio del Vigilante a 30 km/h

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `BackgroundCameraService.kt`, `SettingsScreen.kt`, `SettingsManager.kt`, `SettingsManagerAndroid.kt`, `CameraServiceManager.kt`, `CameraServiceManagerAndroid.kt`, `NotificationHelper.kt` |
| **Estimación** | 5 puntos |

### Descripción

Implementar auto-inicio del Vigilante cuando el velocímetro alcanza 30 km/h. La funcionalidad debe ser configurable desde SettingsScreen (toggle, deshabilitado por defecto). Cuando se activa, el servicio envía una notificación de advertencia con 5 segundos de delay y un botón para cancelar. Si el usuario no cancela, el Vigilante cambia a modo RECORDING automáticamente.

### Criterios de Aceptación

- **Dado** que el usuario habilitó "Auto-inicio" en Settings
  **Cuando** el velocímetro marca 30 km/h y el servicio está en STANDBY
  **Entonces** aparece una notificación: "DuoVial se activará en 5 segundos. Toca para cancelar."

- **Dado** que la notificación de auto-inicio apareció
  **Cuando** el usuario toca "CANCELAR" antes de 5 segundos
  **Entonces** el auto-inicio se cancela y el servicio permanece en STANDBY.

- **Dado** que la notificación de auto-inicio apareció
  **Cuando** pasan 5 segundos sin que el usuario cancele
  **Entonces** el servicio cambia a modo RECORDING y la burbuja flotante aparece (si el permiso está concedido).

- **Dado** que el auto-inicio se activó
  **Cuando** el usuario detiene manualmente el Vigilante
  **Entonces** el auto-inicio se desactiva temporalmente hasta que la velocidad baje de 10 km/h.

- **Dado** que el usuario deshabilitó "Auto-inicio" en Settings
  **Cuando** el velocímetro marca 30 km/h
  **Entonces** no ocurre nada — el servicio permanece en STANDBY.

### Notas Técnicas

- Agregar `autoStartEnabled` a `SettingsManager` (SharedPreferences, default `false`).
- Agregar `ACTION_CANCEL_AUTO_START` al companion object de `BackgroundCameraService`.
- Usar `Handler.postDelayed` con 5 segundos de delay para el auto-inicio.
- Cooldown de 10 minutos entre auto-inicios para evitar activaciones repetidas en tráfico urbano.
- La notificación debe tener un botón "CANCELAR" con `PendingIntent` que envíe `ACTION_CANCEL_AUTO_START`.
- El GPS ya monitorea velocidad en `locationListener` — solo se agrega lógica de comparación.
- Impacto en rendimiento: negligible (una comparación numérica adicional).

### Documentación de Referencia

Ver `FASE_A_Implementation.md`, sección 9: "G: Auto-Inicio del Vigilante a 30 km/h".

---

## A-11 — Borrado automático de videos mayores a 72 horas

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature / Chore |
| **Prioridad** | P1 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | `IncidentRepository.kt`, `MainActivity.kt` |
| **Estimación** | 2 puntos |

### Descripción

Implementar borrado automático de videos de incidentes que tengan más de 72 horas de antigüedad. La limpieza se ejecuta silenciosamente al abrir la app (en `MainActivity.onCreate()`) y no es configurable por el usuario. Los videos se eliminan de MediaStore usando la columna `DATE_ADDED`.

### Criterios de Aceptación

- **Dado** que existen videos de incidentes en Downloads/DuoVial
  **Cuando** el usuario abre la app y hay videos con más de 72 horas de antigüedad
  **Entonces** los videos antiguos se eliminan automáticamente sin notificación al usuario.

- **Dado** que existen videos de incidentes con menos de 72 horas
  **Cuando** el usuario abre la app
  **Entonces** los videos recientes NO se eliminan.

- **Dado** que no hay videos antiguos
  **Cuando** el usuario abre la app
  **Entonces** la limpieza se ejecuta sin errores (no hace nada si no hay nada que borrar).

- **Dado** que la limpieza eliminó archivos
  **Cuando** se revisa el log
  **Entonces** se registra cuántos archivos se eliminaron.

### Notas Técnicas

- Agregar constante `MAX_INCIDENT_AGE_MS = 72 * 60 * 60 * 1000L` en `IncidentRepository`.
- Agregar función `cleanupOldIncidents(context: Context): Int` en `IncidentRepository`.
- La función consulta MediaStore con la misma query que `scanIncidents()` pero seleccionando `DATE_ADDED`.
- Para cada resultado, calcula antigüedad: `System.currentTimeMillis() - (DATE_ADDED * 1000L)`.
- Si antigüedad > 72 horas, elimina con `contentResolver.delete(uri, null, null)`.
- Ejecutar en `MainActivity.onCreate()` en un coroutine en `Dispatchers.IO`.
- No requiere permisos adicionales en Android 10+ (scoped storage permite eliminar archivos propios).
- Impacto en rendimiento: negligible (consulta MediaStore + I/O de disco, una sola vez al abrir la app).

### Documentación de Referencia

Ver `FASE_A_Implementation.md`, sección 10: "H: Borrado Automático de Videos Mayores a 72 Horas".

---

# FASE C: SUPABASE Y BACKEND (Sprint 9-12)

---

## C-05 — Multi-tenancy: Organizations + RLS

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature / Infra |
| **Prioridad** | P1 |
| **Dependencias** | C-01, C-04 |
| **Archivos afectados** | Supabase SQL (migraciones), Edge Functions, app Android (settings) |
| **Estimación** | 8 puntos |

### Descripción

Implementar el modelo de multi-tenancy completo: tablas `organizations`, `organization_members`, `vehicles`, `drivers` con RLS policies que aíslan datos por organización. Crear función helper `current_org_id()` y policies de aislamiento.

### Criterios de Aceptación

- **Dado** que existen dos organizaciones (Org A y Org B)
  **Cuando** un usuario de Org A consulta incidentes
  **Entonces** SOLO ve incidentes de vehículos de Org A (nunca de Org B).

- **Dado** que un admin crea una organización
  **Cuando** se registra
  **Entonces** se crea automáticamente como `owner` en `organization_members`.

- **Dado** que un admin invita a un conductor
  **Cuando** el conductor acepta
  **Entonces** se vincula a la organización con rol `driver`.

- **Dado** que un conductor tiene rol `driver`
  **Cuando** intenta acceder a datos de otra organización
  **Entonces** RLS bloquea la operación.

### Schema a crear

```sql
-- Organizaciones
CREATE TABLE organizations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  slug TEXT UNIQUE NOT NULL,
  plan TEXT DEFAULT 'free',
  stripe_customer_id TEXT,
  settings JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- Miembros
CREATE TABLE organization_members (
  org_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
  role TEXT CHECK (role IN ('owner', 'admin', 'supervisor', 'driver')),
  invited_by UUID REFERENCES auth.users(id),
  invited_at TIMESTAMPTZ DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  PRIMARY KEY (org_id, user_id)
);

-- Vehículos
CREATE TABLE vehicles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  plate TEXT NOT NULL,
  brand_model TEXT,
  year INTEGER,
  obd_dongle_id TEXT,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Conductores
CREATE TABLE drivers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  user_id UUID REFERENCES auth.users(id),
  full_name TEXT NOT NULL,
  license_number TEXT,
  license_expiry DATE,
  face_embedding VECTOR(512),
  phone TEXT,
  emergency_contact JSONB,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Helper function
CREATE OR REPLACE FUNCTION current_org_id() RETURNS UUID AS $$
  SELECT current_setting('app.current_org_id')::UUID;
$$ LANGUAGE sql STABLE;

-- RLS Policies
CREATE POLICY "org_isolation" ON incidents
  USING (org_id = current_org_id());

CREATE POLICY "org_isolation" ON vehicles
  USING (org_id = current_org_id());

CREATE POLICY "org_isolation" ON drivers
  USING (org_id = current_org_id());
```

### Notas Técnicas

- Ejecutar migraciones via `supabase db push`.
- La función `current_org_id()` se establece como session variable antes de cada query.
- El Dashboard Web usa el Organization Selector para cambiar el contexto.

---

## C-06 — Supabase Realtime para telemetría en tiempo real

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | C-04, C-05 |
| **Archivos afectados** | App Android (telemetría), Dashboard Web (suscripciones) |
| **Estimación** | 5 puntos |

### Descripción

Configurar Supabase Realtime para que la app Android envíe telemetría (ubicación, estado, G-Force) cada 30s y el Dashboard Web la reciba en tiempo real vía WebSockets.

### Criterios de Aceptación

- **Dado** que el Vigilante está activo
  **Cuando** han pasado 30 segundos
  **Entonces** se envía telemetría a Supabase: `{lat, lon, speed, gforce, status, battery}`.

- **Dado** que el Dashboard Web está abierto
  **Cuando** un vehículo se mueve
  **Entonces** el marcador en el mapa se actualiza en tiempo real (<2s de latencia).

- **Dado** que un vehículo genera un incidente
  **Cuando** el admin está en el dashboard
  **Entonces** recibe notificación inmediata con resumen.

### Notas Técnicas

- Tabla `vehicle_telemetry` para datos en tiempo real (TTL 24h, o partición por tiempo).
- Habilitar Realtime en la tabla: `ALTER PUBLICATION supabase_realtime ADD TABLE vehicle_telemetry;`.
- Android: enviar cada 30s via Supabase Client.
- Dashboard: suscribirse a cambios en la tabla.

---

# FASE D: ANTI-SOMNOLENCIA AVANZADA (Sprint 13-16)

---

## D-05 — Sistema de escalada 3 niveles

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | D-01, D-02, G-02 |
| **Archivos afectados** | Nuevo: `FatigueEscalationManager.kt`, `FatigueScreen.kt`, `SettingsScreen.kt` |
| **Estimación** | 8 puntos |

### Descripción

Implementar el sistema completo de escalada de 3 niveles para anti-somnolencia:

| Nivel | Detección | Acción |
|-------|-----------|--------|
| 🟢 Nivel 1 "El Acompañante" | FC elevada + HRV baja + conducción >2h | Sugerencia de ruta a gasolinera/descanso, bajar A/C 1.5°C, playlist energética 120+ BPM |
| 🟡 Nivel 2 "El Despertador" | EAR bajo + wearable sin movimiento | Alarma sonora fuerte + vibración + pantalla roja + voz "¡MANTÉN LOS OJOS ABIERTOS!" |
| 🔴 Nivel 3 "El Ángel Guardián" | No responde al Nivel 2 en 8s o ojos cerrados >3s | Push + SMS a contacto de emergencia con ubicación en Maps, reducir volumen música, instrucciones voz |

### Criterios de Aceptación

- **Dado** que el conductor lleva >2h manejando y FC elevada
  **Cuando** el wearable detecta la condición
  **Entonces** se muestra Nivel 1: "¿Necesitas una parada? Hay una gasolinera a 2km" + sugerencia de ruta.

- **Dado** que el Nivel 1 no fue atendido y los ojos se cierran
  **Cuando** EAR < umbral por >duration
  **Entonces** se activa Nivel 2: alarma sonora + vibración [0,500,200,500]ms + pantalla roja + voz.

- **Dado** que el Nivel 2 se activó y el conductor no responde en 8 segundos
  **Cuando** la condición persiste
  **Entonces** se activa Nivel 3: push + SMS al contacto de emergencia con ubicación Maps.

- **Dado** que el Nivel 3 se activó
  **Cuando** el contacto presiona "1" en el IVR de Twilio
  **Entonces** se notifica al Admin: "Conductor ileso".

- **Dado** que el Nivel 3 se activó
  **Cuando** el contacto presiona "2" o no responde en 30s
  **Entonces** se envía alerta prioritaria con ubicación exacta.

### Notas Técnicas

- Nivel 1: usar `MediaRouter` para rutas de Maps, `MediaPlayer` para playlist, `AudioManager` para A/C.
- Nivel 2: vibración con `Vibrator`, overlay rojo con `WindowManager`, TTS con `TextToSpeech`.
- Nivel 3: Twilio API para llamadas IVR, OneSignal para push, Supabase Edge Function para SMS.
- Los 3 niveles son configurables (on/off) en Settings por el usuario.
- Para Fleet, el Admin puede configurar umbrales globales desde el Dashboard.

---

## D-06 — FatigueScreen con CameraX integrado

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P1 |
| **Dependencias** | A-01, D-01 |
| **Archivos afectados** | `FatigueScreen.kt`, `FatigueCameraManager.kt` (nuevo) |
| **Estimación** | 5 puntos |

### Descripción

Integrar la cámara frontal migrada a CameraX (A-01) con la lógica de detección de fatiga (D-01/D-02) en FatigueScreen. La cámara solo está activa cuando FatigueScreen está visible.

### Criterios de Aceptación

- **Dado** que el usuario abre FatigueScreen
  **Cuando** la pantalla se muestra
  **Entonces** la cámara frontal inicia preview + ImageAnalysis con ML Kit Face Detection.

- **Dado** que la cámara frontal está activa
  **Cuando** el usuario sale de FatigueScreen
  **Entonces** la cámara se libera completamente (`unbindAll()`).

- **Dado** que ML Kit detecta un rostro
  **Cuando** calcula EAR
  **Entonces** el valor se muestra en la barra de EAR en tiempo real.

- **Dado** que EAR < umbral por >duration
  **Cuando** se detecta fatiga
  **Entonces** se emite alerta según el nivel configurado (D-05).

### Notas Técnicas

- Reutilizar `ProcessCameraProvider` de A-01.
- `ImageAnalysis` a 640×480 @ 10fps con `STRATEGY_KEEP_ONLY_LATEST`.
- ML Kit `FaceDetection` con `ContourOptions` para EAR.
- La cámara frontal NO guarda video (solo analiza en tiempo real).

---

# FASE E: MONETIZACIÓN (Sprint 17-20)

---

## E-01 — Integrar Google Play Billing + Wompi para pagos

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-01, G-04 |
| **Archivos afectados** | Nuevo: `PaymentService.kt`, `PaymentScreen.kt`, `BillingManager.kt`. `build.gradle.kts` |
| **Estimación** | 8 puntos |

### Descripción

**ESQUEMA HÍBRIDO**: Google Play Billing para compras dentro de la app Android (suscripciones + pago por evento) y Wompi para pagos desde la web Dashboard (Fleet, instalación OBD).

### Criterios de Aceptación

- **Dado** que un usuario quiere suscribirse a Premium desde la app
  **Cuando** toca "Suscribirse - $10,900/mes"
  **Entonces** se abre Google Play Billing UI nativa, paga, y la app verifica el purchaseToken con la Edge Function.

- **Dado** que un admin quiere suscribirse a Fleet desde el Dashboard web
  **Cuando** toca "Suscribirse - $9,900/mes/vehículo"
  **Entonces** se crea un payment link de Wompi, el admin paga con PSE/Nequi/tarjeta, y el webhook actualiza el plan.

- **Dado** que un usuario quiere procesar un video (pago por evento)
  **Cuando** toca "Procesar video - $19,900"
  **Entonces** se abre Google Play Billing, paga, y se desbloquea el procesamiento.

- **Dado** que el pago falla
  **Cuando** Google Play o Wompi retornan error
  **Entonces** se muestra mensaje claro y NO se desbloquea el servicio.

### Notas Técnicas

- **App Android**: Integrar Google Play Billing Library v6+. Usar `BillingClient` para query products, launch billing flow, handle purchases. Enviar purchaseToken a Edge Function `verify-google-purchase` para verificación server-side. Llamar `acknowledgePurchase()` tras verificación exitosa.
- **Web Dashboard**: Llamar Edge Function `create-wompi-link` para crear payment link. Redirigir a checkout.wompi.co. Recibir confirmación via webhook.
- **Dependencia Android**: `com.android.billingclient:billing:6.0.1`
- **Wompi**: Montos en centavos (1 COP = 100 centavos). Verificar firma SHA256 del webhook.
- **Google Play**: Precios en micros (1 COP = 1,000,000 micros). Acknowledge obligatorio en 3 días.

---

## E-06 — Gestión de suscripción (UI propia)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | E-01, G-04 |
| **Archivos afectados** | `SettingsScreen.kt`, `AccountScreen.kt`, `BillingScreen.kt` (nuevo) |
| **Estimación** | 5 puntos |

### Descripción

Construir UI propia para gestión de suscripciones (reemplaza Stripe Customer Portal). El usuario puede: ver plan actual, fecha de próximo cobro, método de pago, cancelar suscripción, cambiar plan.

### Criterios de Aceptación

- **Dado** que el usuario tiene una suscripción activa
  **Cuando** abre Settings > Mi Plan
  **Entonces** ve: plan actual, próximo cobro, método de pago, botón cancelar.

- **Dado** que el usuario cancela su suscripción
  **Cuando** confirma la cancelación
  **Entonces** se marca `cancel_at_period_end = true` y la suscripción sigue activa hasta fin del período.

- **Dado** que el usuario quiere cambiar de plan
  **Cuando** selecciona un plan diferente
  **Entonces** se inicia el flujo de compra del nuevo plan y se cancela el anterior al final del período.

### Notas Técnicas

- **Google Play Billing**: Para cancelar, usar `BillingClient` para obtener subscription status. La cancelación real se hace en Google Play (el usuario cancela desde la app de Google Play o desde la app DuoVial redirigiendo).
- **Wompi**: Para cancelar, actualizar `subscriptions.cancel_at_period_end = true` en la BD. El scheduled function dejará de cobrar.
- **UI**: Mostrar estado de suscripción desde tabla `subscriptions` de Supabase.

---

# FASE F: FLEET — MVP EMPRESARIAL (Sprint 21-28)

---

## F-01 — Geo-fencing: alertas de perímetro (ACTUALIZADO)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 (**movido de P3 a MVP Fleet**) |
| **Dependencias** | C-05, G-02 |
| **Archivos afectados** | Nuevo: `GeofenceManager.kt`, `GeofenceSyncService.kt`. Dashboard web. |
| **Estimación** | 8 puntos |

### Descripción

**ACTUALIZADO**: Implementar geofencing completo para Fleet. El admin define zonas (radio o polígono) desde el Dashboard. El dispositivo registra la zona y alerta cuando el GPS cruza el perímetro. Incluye grabación de video 5s como evidencia.

### Criterios de Aceptación

- **Dado** que un admin definió una zona "Zona Centro" de 2km radio
  **Cuando** el vehículo entra o sale de la zona
  **Entonces** el admin recibe notificación push con: vehículo, tipo (entrada/salida), timestamp, ubicación exacta.

- **Dado** que el vehículo cruza el geofence
  **Cuando** el admin revisa el Dashboard
  **Entonces** ve en el mapa el punto exacto de cruce y registro en `geofence_events`.

- **Dado** que no hay internet
  **Cuando** el vehículo cruza el geofence
  **Entonces** la alerta se encola y se envía al recuperar conexión.

- **Dado** que el vehículo cruza el geofence
  **Cuando** se graba video 5s
  **Entonces** el video se guarda como evidencia de quién manejaba.

### Notas Técnicas

- `GeofencingClient` de Google Play Services en el dispositivo.
- Zonas sincronizadas desde Supabase al iniciar la app.
- `geofence_events` table: `vehicle_id, org_id, fence_id, event_type, location, timestamp`.
- Edge Function `notify-geofence-cross` envía push via OneSignal.

---

## F-04 — Reconocimiento facial (solo alerta)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-05, C-04 |
| **Archivos afectados** | Nuevo: `FaceRecognitionManager.kt`, `FaceEnrollmentScreen.kt`. `drivers.face_embedding` en Supabase. |
| **Estimación** | 8 puntos |

### Descripción

Implementar reconocimiento facial para verificar que el conductor registrado sea quien maneja. **Desactivado por defecto**. El conductor se toma selfie → ML Kit extrae embedding → se compara con `drivers.face_embedding` (pgvector). Solo alerta, nunca bloquea.

### Criterios de Aceptación

- **Dado** que un conductor tiene embedding facial registrado
  **Cuando** inicia un viaje (>20 km/h)
  **Entonces** se captura 1 foto de la cámara frontal y se compara con el embedding almacenado.

- **Dado** que el rostro capturado coincide
  **Cuando** la similaridad es >0.85
  **Entonces** NO se genera alerta.

- **Dado** que el rostro capturado NO coincide
  **Cuando** la similaridad es <0.85
  **Entonces** el admin recibe notificación push: "Rostro no registrado conduciendo [Vehículo]."

- **Dado** que la funcionalidad está desactivada por defecto
  **Cuando** el admin la activa desde el Dashboard
  **Entonces** comienza a verificar en cada inicio de viaje.

- **Dado** que el conductor quiere registrar su rostro
  **Cuando** toma selfie en la app
  **Entonces** ML Kit extrae embedding → se guarda en `drivers.face_embedding` (pgvector).

### Notas Técnicas

- ML Kit Face Detection para extraer landmarks → embedding vectorial.
- pgvector en Supabase: `cosine_similarity(face_embedding <=> '[0.12, 0.45, ...]')`.
- La foto temporal se borra en 24h (nunca se sube a cloud).
- Para Fleet: el Admin ve "Conductores no autorizados" en el Dashboard.

---

## F-05 — Fleet onboarding flow

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-05, C-01 |
| **Archivos afectados** | Dashboard Web, app Android (login/vinculación) |
| **Estimación** | 5 puntos |

### Descripción

Flujo completo de onboarding para empresas Fleet:
1. Admin registra empresa → crea `organization` + `owner` member.
2. Admin invita conductores (email/SMS) → `organization_members` (pending).
3. Conductor acepta → descarga app → login → auto-vincula a org.
4. Conductor toma selfie → embedding facial → sync a `drivers.face_embedding`.
5. Admin registra vehículos → asigna conductor + dongle OBD (opcional).
6. Admin define geofences → configura toggles por vehículo.

### Criterios de Aceptación

- **Dado** que un admin registra su empresa
  **Cuando** completa el formulario
  **Entonces** se crea `organization` + `organization_members` con rol `owner`.

- **Dado** que el admin invita a un conductor por email
  **Cuando** el conductor recibe la invitación
  **Entonces** ve un link para descargar la app y aceptar la invitación.

- **Dado** que el conductor acepta la invitación
  **Cuando** hace login en la app
  **Entonces** se vincula automáticamente a la organización.

- **Dado** que el admin registra un vehículo con placa "ABC123"
  **Cuando** lo asigna a un conductor
  **Entonces** el conductor ve el vehículo en su lista de vehículos asignados.

### Notas Técnicas

- Dashboard Web: formularios de registro de empresa, invitación, gestión de vehículos.
- App Android: al hacer login, verificar si tiene invitaciones pendientes → auto-vincular.
- Email/SMS de invitación via Supabase Auth + OneSignal.

---

## F-06 — Billing Dashboard

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | F-03, E-01 |
| **Archivos afectados** | Dashboard Web (nueva sección) |
| **Estimación** | 5 puntos |

### Descripción

Dashboard de facturación para admins Fleet: ver plan actual, próximo cobro, método de pago, upgrade/downgrade, historial de facturas (descargar PDF), counter de eventos en plan "Por Evento".

### Criterios de Aceptación

- **Dado** que el admin abre Billing Dashboard
  **Cuando** consulta su plan
  **Entonces** ve: plan actual, próximo cobro, método de pago, número de vehículos.

- **Dado** que el admin quiere cambiar de plan
  **Cuando** toca "Upgrade/Downgrade"
  **Entonces** se abre Stripe Customer Portal.

- **Dado** que el admin tiene facturas anteriores
  **Cuando** ve el historial
  **Entonces** cada factura tiene link de descarga PDF (Stripe hosted invoice).

### Notas Técnicas

- Leer datos de `stripe.subscriptions`, `stripe.invoices`, `stripe.prices`.
- Supabase Realtime para actualización en vivo.
- Stripe Customer Portal para self-service.

---

## F-07 — Integración OBD II (ELM327)

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-05, C-04 |
| **Archivos afectados** | Nuevo: `ObdService.kt`, `ObdScreen.kt`. `obd_readings` table. |
| **Estimación** | 8 puntos |

### Descripción

Integrar dongle ELM327 Bluetooth para leer datos mecánicos del vehículo: RPM, temperatura del refrigerante, voltaje de batería, códigos de error (DTC). Los datos se envían al Dashboard y se usan para mantenimiento predictivo.

### Criterios de Aceptación

- **Dado** que un dongle ELM327 está conectado al puerto OBD
  **Cuando** el usuario abre la app y busca dispositivos Bluetooth
  **Entonces** aparece el dongle en la lista y puede emparejarse (PIN: 0000).

- **Dado** que el dongle está emparejado
  **Cuando** el Vigilante está activo
  **Entonces** se leen RPM, temperatura y voltaje cada 30 segundos.

- **Dado** que se detecta un código de error (DTC)
  **Cuando** se traduce con la base NHTSA
  **Entonces** el usuario ve el significado en español: "P0301 - Fallo de encendido en cilindro 1".

- **Dado** que el admin revisa el Dashboard
  **Cuando** ve un vehículo con dongle
  **Entonces** accede a: RPM actual, temperatura, voltaje, códigos de error recientes.

### Notas Técnicas

- Bluetooth LE + protocolo ELM327 (AT commands → OBD PIDs).
- PIDs estándar: `0x0C` (RPM), `0x05` (coolant temp), `0x42` (battery voltage).
- DTC codes: base de datos pública NHTSA para traducción.
- `obd_readings` table: `vehicle_id, rpm, coolant_temp, battery_voltage, dtc_code, recorded_at`.
- Servicio de instalación asistida: videollamada 15 min ($39,900 COP).

---

## F-08 — Colisión + Llamada Twilio automática

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | G-03, G-02, A-02 |
| **Archivos afectados** | `BackgroundCameraService.kt`, Edge Functions, Twilio Dashboard |
| **Estimación** | 8 puntos |

### Descripción

Cuando se detecta una colisión grave (G-Force > 3.5G AND velocidad > 40 km/h AND duración > 150ms): subir video automáticamente a Supabase Storage → Mux transcoding + llamar al usuario vía Twilio IVR para verificar su estado.

### Criterios de Aceptación

- **Dado** que el acelerómetro detecta >3.5G a >40 km/h
  **Cuando** la condición persiste >150ms
  **Entonces** se sube el video del buffer (pre + evento + post) a Supabase Storage automáticamente.

- **Dado** que el video se subió
  **Cuando** se confirma el upload
  **Entonces** Twilio llama al usuario con IVR: "DuoVial detectó un impacto. Presiona 1 si estás bien. Presiona 2 para ambulancia."

- **Dado** que el usuario presiona 1
  **Cuando** se confirma
  **Entonces** se notifica al Admin: "Conductor ileso" + ubicación.

- **Dado** que el usuario presiona 2 o no responde en 30s
  **Cuando** se detecta emergencia
  **Entonces** se envía alerta prioritaria a contacto de emergencia con ubicación en Maps.

- **Dado** que el usuario está a <40 km/h
  **Cuando** ocurre un impacto
  **Entonces** NO se sube video automáticamente (evitar falsos positivos en estacionamiento).

### Notas Técnicas

- Condición: `(gForce > 3.5) AND (speed > 40 km/h) AND (duration > 150ms)`.
- Twilio: crear flow IVR con webhook a Supabase Edge Function.
- La subida de video es automática SOLO en este caso excepcional (colisión grave).
- Para otros eventos (botón pánico, G-Force normal), el video se guarda localmente.

---

## F-09 — Mantenimiento predictivo

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-05, F-07 |
| **Archivos afectados** | Nuevo: `MaintenanceService.kt`, `MaintenanceScreen.kt`. Tablas en Supabase. |
| **Estimación** | 5 puntos |

### Descripción

Sistema de alertas preventivas basado en reglas de mantenimiento por modelo de vehículo + datos de OBD. Evalúa kilometraje, tiempo de motor, y códigos de error para generar alertas al conductor y admin.

### Criterios de Aceptación

- **Dado** que un vehículo tiene 4,800 km
  **Cuando** la regla de aceite es cada 5,000 km
  **Entonces** se genera alerta "Cambio de aceite en 200 km" al conductor y admin.

- **Dado** que el OBD reporta un DTC P0301
  **Cuando** se traduce con NHTSA
  **Entonces** se genera alerta "Fallo de encendido - requiere revisión" con severidad `critical`.

- **Dado** que el admin revisa el Dashboard
  **Cuando** ve la sección de mantenimiento
  **Entonces** ve: estado de cada vehículo, alertas pendientes, próximo mantenimiento.

- **Dado** que un conductor ack una alerta
  **Cuando** la confirma
  **Entonces** el estado cambia a `acknowledged` y se registra timestamp.

### Schema

```sql
CREATE TABLE maintenance_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  brand TEXT NOT NULL,
  model TEXT NOT NULL,
  year_from INTEGER,
  year_to INTEGER,
  component TEXT NOT NULL,
  interval_km INTEGER NOT NULL,
  interval_months INTEGER,
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE maintenance_alerts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_id UUID REFERENCES vehicles(id),
  org_id UUID REFERENCES organizations(id),
  rule_id UUID REFERENCES maintenance_rules(id),
  component TEXT NOT NULL,
  km_remaining NUMERIC,
  days_remaining INTEGER,
  severity TEXT DEFAULT 'info',
  status TEXT DEFAULT 'pending',
  created_at TIMESTAMPTZ DEFAULT now()
);
```

### Notas Técnicas

- Reglas genéricas: Aceite (5,000km), Filtros (10,000km), Bujías (20,000km), Pastillas (30,000km).
- Job programado (pg_cron): evalúa reglas cada 24h.
- Gemini API opcional para consultas específicas: "Intervalos de mantenimiento para Chevrolet Spark 2018".

---

# FASE H: OPERACIONES Y COMPLIANCE (Sprint 29-30)

---

## H-01 — Offline sync strategy

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | C-04 |
| **Archivos afectados** | Nuevo: `OfflineSyncManager.kt`, `SyncQueue.kt` |
| **Estimación** | 5 puntos |

### Descripción

Implementar sincronización offline inteligente: eventos, ubicaciones, acelerómetros y logs de viaje se guardan en SQLite local y sincronizan automáticamente al recuperar conexión. Prioridad de sync: (1) colisión, (2) geofencing, (3) fatiga, (4) logs, (5) telemetría.

### Criterios de Aceptación

- **Dado** que no hay conexión a internet
  **Cuando** ocurre un evento
  **Entonces** se guarda en SQLite local con timestamp y se encola para sync.

- **Dado** que hay eventos en cola
  **Cuando** se recupera la conexión
  **Entonces** se sincronizan en orden de prioridad (colisión primero).

- **Dado** que un evento ya existe en el servidor
  **Cuando** se intenta sincronizar
  **Entonces** se aplica server-wins (no se sobreescribe el dato del servidor).

- **Dado** que hay 50+ eventos en cola
  **Cuando** se sincronizan
  **Entonces** se agrupan en batches de 50 para reducir llamadas API.

### Notas Técnicas

- SQLite local para cola de sync: `{id, type, payload, priority, status, created_at}`.
- Backoff exponencial: 1s → 2s → 4s → 8s → 16s → 30s (máximo).
- Health check antes de sync masivo: `SELECT id FROM organizations LIMIT 1`.
- Métricas: tabla `sync_status` en SQLite.

---

## H-02 — Legal & Compliance

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature / Compliance |
| **Prioridad** | P2 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | App Android (onboarding consent), Edge Functions (right to erasure) |
| **Estimación** | 5 puntos |

### Descripción

Implementar cumplimiento de Ley 1581 de 2012 (Colombia): Privacy Policy, flujo de consentimiento en onboarding, endpoint "Eliminar mis datos" (right to erasure), disclaimer de reconocimiento facial.

### Criterios de Aceptación

- **Dado** que el usuario abre la app por primera vez
  **Cuando** completa el onboarding
  **Entonces** ve y acepta Privacy Policy y Términos de Uso.

- **Dado** que el usuario quiere eliminar sus datos
  **Cuando** toca "Eliminar mi cuenta y datos"
  **Entonces** se ejecuta un Edge Function que borra: perfil, incidents, face_embedding, ubicación histórica.

- **Dado** que la app usa reconocimiento facial
  **Cuando** el usuario lo activa
  **Entonces** ve disclaimer: "Los datos faciales se usan SOLO para verificar identidad. Se eliminan al terminar contrato."

- **Dado** que la app graba video
  **Cuando** el usuario lee el disclaimer
  **Entonces** ve: "NO se graba audio. Los videos son privados del usuario."

### Notas Técnicas

- Privacy Policy: página web estática (puede ser GitHub Pages).
- Consentimiento: checkbox en onboarding + modal antes de activar facial.
- Right to erasure: Edge Function `delete-user-data` que ejecuta DELETE en todas las tablas.
- Evaluar si se necesita DPO (Data Protection Officer).

---

## H-03 — Monitoring & Observability

| Campo | Valor |
|-------|-------|
| **Tipo** | Chore / Infra |
| **Prioridad** | P2 |
| **Dependencias** | G-01 |
| **Archivos afectados** | Firebase Console, PostHog, BetterStack |
| **Estimación** | 3 puntos |

### Descripción

Configurar stack de monitoreo: Firebase Crashlytics (crashes Android), Firebase Performance (startup, frames), PostHog (analytics Dashboard Web), BetterStack (uptime checks).

### Criterios de Aceptación

- **Dado** que la app tiene un crash
  **Cuando** se reporta vía Crashlytics
  **Entonces** aparece en Firebase Console con stack trace, dispositivo, y versión.

- **Dado** que el Dashboard Web tiene un error
  **Cuando** PostHog lo registra
  **Entonces** se ve en el dashboard de analytics con contexto del usuario.

- **Dado** que Supabase Edge Function falla >5% del tiempo
  **Cuando** BetterStack detecta la anomalía
  **Entonces** se envía alerta al equipo.

### Notas Técnicas

- Firebase: `google-services.json` ya en el proyecto. Habilitar Crashlytics y Performance.
- PostHog: self-host o cloud. Integrar SDK en Dashboard Web.
- BetterStack: health checks cada 5 min a endpoints críticos.
- Alertas críticas: crash rate >1%, Edge Function error >5%, DB connections >80%.

---

## H-04 — Auto-Inicio por Actividad

| Campo | Valor |
|-------|-------|
| **Tipo** | Feature |
| **Prioridad** | P2 |
| **Dependencias** | Ninguna |
| **Archivos afectados** | Nuevo: `ActivityRecognitionManager.kt`, `BackgroundCameraService.kt` |
| **Estimación** | 5 puntos |

### Descripción

Implementar auto-inicio del Vigilante cuando se detecta actividad de conducción. Usa Google Activity Recognition API + velocidad GPS para activar automáticamente sin intervención del usuario.

### Criterios de Aceptación

- **Dado** que el usuario sube al auto y arranca
  **Cuando** Activity Recognition detecta `ACTIVITY_IN_VEHICLE` + velocidad >15 km/h por 15s
  **Entonces** se muestra notificación: "🚗 DuoVial detectó que estás conduciendo. Modo Vigilante activado." (el usuario puede deslizar para detener).

- **Dado** que el Vigilante se auto-activó
  **Cuando** el usuario llega y apaga el auto (ACTIVITY_STILL por 2 min)
  **Entonces** el Vigilante se auto-detiene.

- **Dado** que el usuario desactivó el auto-inicio en Settings
  **Cuando** detecta actividad de conducción
  **Entonces** NO se auto-activa.

### Notas Técnicas

- Google Activity Recognition API: `ACTIVITY_IN_VEHICLE`, `ACTIVITY_STILL`, `ACTIVITY_WALKING`.
- Fused Location Provider para velocidad.
- La notificación usa `SYSTEM_ALERT_WINDOW` para mostrarse sobre otras apps.
- Los datos de actividad NO se guardan, solo se usan para trigger.

---

# FASE I: POST-MVP (Semana 31+)

---

## F-02 — Detección de estado de salud de alto riesgo (MANTENIDO POST-MVP)

> Se mantiene como post-MVP. Requiere calibración con datos reales y posiblemente colaboración con universidad.

---

## RESUMEN: ESTIMACIONES TOTALES POR FASE

| Fase | Tickets | Puntos totales | Prioridad |
|------|---------|---------------|-----------|
| G: Infraestructura | 6 | 21 puntos | P0 |
| A: Estabilidad | 9 + G-05 | 35 puntos | P0-P1 |
| B: Onboarding/UX | 3 | 13 puntos | P1 |
| C: Supabase | 6 | 30 puntos | P1 |
| D: Anti-Somnolencia | 6 | 33 puntos | P1-P2 |
| E: Monetización | 6 | 37 puntos | P2 |
| F: Fleet | 8 | 56 puntos | P2 |
| H: Operaciones | 4 | 16 puntos | P2 |
| I: Post-MVP | 1 | 8 puntos | P3 |
| **TOTAL MVP Core (G+A+B)** | **18** | **69 puntos** | |
| **TOTAL MVP Completo (G+A+B+C+D)** | **30** | **132 puntos** | |
| **TOTAL con Monetización** | **36** | **169 puntos** | |
| **TOTAL con Fleet** | **44** | **225 puntos** | |
| **TOTAL Completo** | **49** | **241 puntos** | |

---

## DEPENDENCRIAS CRÍTICAS (Diagrama)

```
G-01 (Edge Functions) ──┐
G-02 (OneSignal) ────────┤
G-03 (Mux) ──────────────┤
G-04 (Google Play+Wompi)─┤
                         ├── C-04 (Schema) ──┬── C-05 (Multi-tenancy) ──┬── F-01 (Geofencing)
                         │                    │                           ├── F-04 (Facial)
                         │                    │                           ├── F-05 (Fleet Onboarding)
                         │                    │                           ├── F-07 (OBD II)
                         │                    │                           └── F-09 (Maintenance)
                         │                    │
                         │                    └── C-06 (Realtime) ── F-03 (Dashboard Web)
                         │
A-01 (CameraX) ──────────┤── D-01 (Health Connect) ── D-02 (Fatigue Logic) ── D-05 (Escalada 3 niveles)
                         │                           └── D-06 (FatigueScreen)
                         │
A-02 (Buffer 3 videos) ──┤── A-03 (Stress test)
                         ├── A-04 (Foreground Service)
                         ├── A-08 (Post-event config)
                         ├── G-05 (G-Force UI)
                         └── F-08 (Collision + Twilio)

C-01 (Supabase Auth) ────┬── C-02 (Login opcional)
                         ├── C-03 (Storage) ── E-02 (PDF Report)
                         └── C-05 (Multi-tenancy)

E-01 (Google Play+Wompi)─┬── E-02 (PDF) ── E-03 (Pago evento)
                         ├── E-04 (Pago día) ── E-05 (Dashboard fatiga)
                         └── E-06 (Gestión suscripción UI propia)
```

---

*Documento v3.0 — Fuente única de verdad para tickets de desarrollo. Pagos: Google Play Billing (app) + Wompi (web). Cualquier cambio en alcance debe reflejarse aquí y comunicarse al equipo.*
