# 🚀 FASE 0 — GUÍA DE IMPLEMENTACIÓN: INFRAESTRUCTURA BASE

**Versión**: 3.0
**Última actualización**: Junio 30, 2026
**Audiencia**: Mid-level Developer (ejecución) + Tech Lead (revisión)
**Duración estimada**: 1-2 semanas
**Prioridad**: P0 (CRÍTICO — sin esto, nada funciona)
**Enfoque**: 100% Supabase Cloud (vía MCP) — NO desarrollo local

---

## 📌 TABLA DE CONTENIDOS

1. [Contexto y Objetivo](#1-contexto-y-objetivo)
2. [Orden de Ejecución](#2-orden-de-ejecución)
3. [Prerrequisitos](#3-prerrequisitos)
4. [G-01: Setup Supabase Edge Functions](#4-g-01-setup-supabase-edge-functions)
5. [G-02: Setup OneSignal Push Notifications](#5-g-02-setup-onesignal-push-notifications)
6. [G-03: Setup Mux Video Processing](#6-g-03-setup-mux-video-processing)
7. [G-04: Setup Google Play Billing + Wompi](#7-g-04-setup-google-play-billing--wompi)
8. [C-04: Schema de Base de Datos Completo](#8-c-04-schema-de-base-de-datos-completo)
9. [Variables de Entorno](#9-variables-de-entorno)
10. [Posibles Problemas y Soluciones](#10-posibles-problemas-y-soluciones)
11. [Checklist de Validación](#11-checklist-de-validación)
12. [Entregables](#12-entregables)

---

## 1. CONTEXTO Y OBJETIVO

### Por qué existe esta fase
La Fase 0 configura **toda la infraestructura en la nube** que el resto del proyecto necesita. Sin estos servicios:
- ❌ No hay autenticación (Supabase Auth)
- ❌ No hay base de datos (Supabase Postgres)
- ❌ No hay almacenamiento de videos (Supabase Storage)
- ❌ No hay notificaciones push (OneSignal)
- ❌ No hay procesamiento de video (Mux)
- ❌ No hay sistema de pagos (Google Play Billing + Wompi)
- ❌ No hay lógica servidor (Edge Functions)

### Qué NO hace esta fase
- ❌ No implementa UI de la app
- ❌ No conecta la app a los servicios (eso viene en Fase C)
- ❌ No implementa lógica de negocio
- ❌ No crea el Dashboard web

### Enfoque de trabajo
- **TODO se ejecuta directamente en Supabase Cloud** vía MCP
- **NO se usa desarrollo local** (Supabase tiene todo lo necesario en la nube)
- **El código de Edge Functions y migraciones lo escribe el agente desarrollador** asignado a cada ticket
- **Este documento solo describe QUÉ hacer, CÓMO hacerlo y CONSIDERACIONES**

### Resultado esperado
Al finalizar esta fase, todos los servicios externos están **configurados, probados y documentados** con sus credenciales almacenadas de forma segura en Supabase.

---

## 2. ORDEN DE EJECUCIÓN

| Orden | Ticket | Descripción | Tiempo estimado | Dependencias |
|-------|--------|-------------|-----------------|--------------|
| 1 | **G-01** | Setup Supabase Edge Functions | 2-3 horas | Ninguna |
| 2 | **G-02** | Setup OneSignal push notifications | 1-2 horas | Ninguna |
| 3 | **G-03** | Setup Mux video processing | 1-2 horas | Ninguna |
| 4 | **G-04** | Setup Google Play Billing + Wompi | 4-5 horas | Ninguna |
| 5 | **C-04** | Schema de BD completo (actualizado) | 3-4 horas | G-01 |

**Total estimado**: 10-15 horas de trabajo

---

## 3. PRERREQUISITOS

### Cuentas necesarias (crear antes de empezar)
| Servicio | URL | Plan | Costo |
|----------|-----|------|-------|
| Supabase | https://supabase.com | Free tier | $0 (hasta 500MB DB, 1GB Storage) |
| OneSignal | https://onesignal.com | Free tier | $0 (hasta 10k suscriptores) |
| Mux | https://mux.com | Free tier | $0 (100 min encoding/mes, 500 min streaming/mes) |
| Google Play Console | https://play.google.com/console | Developer account | $25 USD (fee único) |
| Google Cloud Console | https://console.cloud.google.com | Free tier | $0 (Pub/Sub para RTDN) |
| Wompi | https://comercios.wompi.co | Sandbox gratis | Sin costo (sandbox); ~2.95% en producción |
| Firebase | https://console.firebase.google.com | Spark (free) | $0 (para FCM) |

### Herramientas necesarias
| Herramienta | Propósito |
|-------------|-----------|
| MCP de Supabase | Ejecutar migraciones, crear Edge Functions, verificar tablas |
| Dashboards externos | Configurar OneSignal, Mux, Google Play Console, Wompi, Firebase |

### Estructura de archivos a crear en el repositorio
```
supabase/
├── migrations/                    # Migraciones SQL (6 archivos)
├── functions/                     # Edge Functions (7 carpetas con index.ts)
├── config.toml                    # Configuración del proyecto
└── seed.sql                       # Datos de prueba (opcional)
```

---

## 4. G-01: SETUP SUPABASE EDGE FUNCTIONS

### 4.1 Descripción
Crear y desplegar 7 Edge Functions en Supabase Cloud para manejar webhooks de Mux, Wompi, verificación de compras de Google Play, y triggers de notificaciones push.

### 4.2 Edge Functions requeridas

| Función | Propósito | verify_jwt | Trigger |
|---------|-----------|------------|---------|
| `trigger-mux-transcode` | Cuando un video se sube a Storage, crea un Mux Asset para transcodificación | `false` | Webhook de Supabase Storage (`object.created`) |
| `mux-webhook-handler` | Recibe webhook de Mux cuando transcodificación está lista; actualiza incidente con `streaming_url` | `false` | Webhook de Mux (`video.asset.ready`) |
| `wompi-webhook` | Recibe webhook de Wompi, verifica firma SHA256, actualiza estado de pago en DB | `false` | Webhook de Wompi (`transaction.updated`) |
| `verify-google-purchase` | Recibe `purchaseToken` de la app, verifica con Google Play Developer API, actualiza suscripción | `true` | Llamada desde app Android |
| `create-wompi-link` | Crea payment link de Wompi para pagos desde web (Fleet, instalación OBD) | `true` | Llamada desde Dashboard web |
| `send-push-notification` | Envía notificaciones push via OneSignal desde el servidor | `true` | Llamada desde app o Edge Function |
| `process-recurring-billing` | Scheduled function (pg_cron) para cobros recurrentes de suscripciones Wompi | N/A (scheduled) | Ejecución programada mensual |

### 4.3 Proceso de implementación

#### Paso 1: Crear archivos de Edge Functions en el repositorio
- Crear la carpeta `supabase/functions/` con una subcarpeta por función
- Cada función debe tener su archivo `index.ts` con la lógica correspondiente
- El agente desarrollador asignado a este ticket escribirá el código TypeScript/Deno

#### Paso 2: Configurar `supabase/config.toml`
- Definir el `project_id` del proyecto Supabase
- Configurar `verify_jwt` para cada función según la tabla anterior
- Las funciones que reciben webhooks externos deben tener `verify_jwt = false`
- Las funciones que reciben llamadas de la app deben tener `verify_jwt = true`

#### Paso 3: Desplegar Edge Functions vía MCP
- Usar la herramienta `supabase_deploy_edge_function` para cada función
- Verificar el despliegue con `supabase_list_edge_functions`
- Cada función debe quedar accesible en `https://<project-ref>.supabase.co/functions/v1/<function-name>`

#### Paso 4: Configurar secretos en Supabase
- Ir a Supabase Dashboard > Project Settings > Edge Functions > Secrets
- Agregar las siguientes variables como secretos (NO en código):
  - `MUX_TOKEN_ID`
  - `MUX_TOKEN_SECRET`
  - `WOMPI_PUBLIC_KEY`
  - `WOMPI_PRIVATE_KEY`
  - `WOMPI_EVENT_SECRET`
  - `GOOGLE_PLAY_PACKAGE_NAME`
  - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
  - `ONESIGNAL_APP_ID`
  - `ONESIGNAL_REST_API_KEY`
  - `SUPABASE_URL`
  - `SUPABASE_SERVICE_ROLE_KEY`
  - `SUPABASE_PROJECT_REF`
  - `APP_URL`

#### Paso 5: Verificar despliegue
- Usar `supabase_get_edge_function` para verificar que cada función está desplegada
- Probar cada función haciendo una petición HTTP a su URL pública
- Verificar logs con `supabase_get_logs` (service: `edge-function`)

### 4.4 Consideraciones críticas

- **Webhooks externos NO envían JWT**: Las funciones `trigger-mux-transcode`, `mux-webhook-handler` y `wompi-webhook` deben tener `verify_jwt = false` porque los servicios externos no autentican con JWT de Supabase
- **Seguridad de webhooks**: Aunque `verify_jwt = false`, cada función debe verificar la firma del webhook (Mux y Wompi proporcionan signing secrets)
- **Wompi signature verification**: La firma de Wompi usa SHA256 con concatenación de propiedades del evento + timestamp + Event Secret. El Event Secret es DIFERENTE de las API keys.
- **Google Play RTDN**: Usa Google Cloud Pub/Sub (no es un webhook directo). Requiere tópico Pub/Sub y service account.
- **Service Role Key**: Las funciones que necesitan acceder a la base de datos deben usar `SUPABASE_SERVICE_ROLE_KEY` (bypassea RLS)
- **Timeout**: Edge Functions tienen un timeout de 60 segundos en el plan free; la lógica debe ser eficiente
- **CORS**: Las funciones que reciben llamadas del navegador deben incluir headers CORS

### 4.5 Posibles problemas y soluciones

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| Función no responde | Secretos no configurados | Verificar en Supabase Dashboard > Secrets |
| Error 401 en función con verify_jwt=true | Token JWT inválido o expirado | Verificar que la app envía token válido |
| Webhook no llega | URL incorrecta en el servicio externo | Verificar URL en dashboard del servicio |
| Función timeout (>60s) | Lógica muy pesada | Optimizar o usar async/await correctamente |
| Error de CORS | Headers no configurados | Agregar `Access-Control-Allow-Origin` en la respuesta |

### 4.6 Criterios de aceptación
- [ ] 7 Edge Functions creadas en el repositorio
- [ ] 7 Edge Functions desplegadas en Supabase Cloud
- [ ] `verify_jwt` configurado correctamente por función
- [ ] 13 secretos configurados en Supabase Dashboard
- [ ] Cada función responde correctamente a peticiones HTTP
- [ ] Logs de funciones accesibles y sin errores

---

## 5. G-02: SETUP ONESIGNAL PUSH NOTIFICATIONS

### 5.1 Descripción
Configurar OneSignal para notificaciones push cross-platform. Esto incluye crear la app en OneSignal, configurar Firebase Cloud Messaging (FCM), y preparar la integración con la app Android.

### 5.2 Proceso de implementación

#### Paso 1: Crear proyecto Firebase
- Ir a https://console.firebase.google.com
- Crear proyecto: "DuoVial"
- Ir a Project Settings > Cloud Messaging
- Copiar **Server Key** y **Sender ID**
- Descargar `google-services.json` (se usará en la app Android en fases posteriores)

#### Paso 2: Crear app en OneSignal
- Ir a https://dashboard.onesignal.com
- Crear nueva app: "DuoVial"
- Seleccionar plataforma: Android
- En configuración de Android, pegar:
  - Firebase Server Key (obtenido en Paso 1)
  - Firebase Sender ID (obtenido en Paso 1)
- Anotar el **App ID** de OneSignal

#### Paso 3: Configurar segmentos en OneSignal Dashboard
Crear los siguientes segmentos para enviar notificaciones dirigidas:

| Segmento | Filtro | Propósito |
|----------|--------|-----------|
| `drivers` | Tag `role` = `driver` | Notificaciones a conductores |
| `admins` | Tag `role` = `admin` | Notificaciones a administradores |
| `all_users` | Sin filtro | Notificaciones generales |

#### Paso 4: Configurar secretos en Supabase
- Agregar en Supabase Dashboard > Secrets:
  - `ONESIGNAL_APP_ID`
  - `ONESIGNAL_REST_API_KEY` (se obtiene en OneSignal Dashboard > Settings > Keys & IDs)

#### Paso 5: Preparar integración en la app (documentación para Fase C)
- El SDK de OneSignal se integrará en la app Android en una fase posterior
- La dependencia a agregar es `com.onesignal:OneSignal:5.1.29`
- Se debe crear un `OneSignalManager.kt` que inicialice el SDK en `MainActivity`
- El `playerId` obtenido debe enviarse a Supabase para almacenarlo en `profiles.push_token`

### 5.3 Consideraciones críticas

- **FCM es obligatorio**: OneSignal usa FCM como transporte para Android; sin Firebase configurado, las notificaciones no llegan
- **Permisos de notificación**: Android 13+ requiere pedir permiso de notificaciones explícitamente
- **Free Tier**: OneSignal es gratis hasta 10,000 suscriptores; suficiente para MVP
- **playerId**: Es el identificador único del dispositivo en OneSignal; se necesita para enviar notificaciones individuales
- **Tags**: Los segmentos se basan en tags; la app debe enviar el tag `role` al registrarse

### 5.4 Posibles problemas y soluciones

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| Notificaciones no llegan | FCM Server Key incorrecto | Verificar en Firebase Console > Cloud Messaging |
| playerId es null | Usuario no aceptó permisos | Verificar que se pidió permiso de notificación |
| Segmentos vacíos | Tags no configurados | La app debe enviar tags al inicializar OneSignal |
| Notificaciones duplicadas | Múltiples inicializaciones del SDK | Llamar `initWithContext` solo una vez en el ciclo de vida |

### 5.5 Criterios de aceptación
- [ ] Proyecto Firebase creado con FCM habilitado
- [ ] App OneSignal creada y vinculada a Firebase
- [ ] Server Key y Sender ID configurados en OneSignal
- [ ] 3 segmentos creados (drivers, admins, all_users)
- [ ] Secretos de OneSignal configurados en Supabase
- [ ] Notificación de prueba desde OneSignal Dashboard llega al dispositivo

---

## 6. G-03: SETUP MUX VIDEO PROCESSING

### 6.1 Descripción
Configurar Mux para transcodificación de videos de incidentes. Mux convierte los videos subidos a formato HLS adaptivo para streaming eficiente.

### 6.2 Proceso de implementación

#### Paso 1: Crear cuenta en Mux
- Ir a https://dashboard.mux.com
- Crear cuenta (puede ser con GitHub)
- Ir a Settings > API Keys
- Crear nueva API Key
- Anotar **Token ID** y **Token Secret**

#### Paso 2: Configurar webhook en Mux
- Ir a Settings > Webhooks en Mux Dashboard
- Crear nuevo webhook con la URL de la Edge Function `mux-webhook-handler`
- La URL será: `https://<project-ref>.supabase.co/functions/v1/mux-webhook-handler`
- Suscribirse a los siguientes eventos:
  - `video.asset.ready` (transcodificación completada)
  - `video.asset.errored` (error en transcodificación)
  - `video.upload.error` (error en subida)
- Anotar el **Signing Secret** del webhook (para verificar la autenticidad)

#### Paso 3: Configurar secretos en Supabase
- Agregar en Supabase Dashboard > Secrets:
  - `MUX_TOKEN_ID`
  - `MUX_TOKEN_SECRET`

#### Paso 4: Configurar encoding
- Mux usa encoding adaptivo por defecto (no requiere configuración adicional)
- El output será HLS con múltiples calidades: 1080p, 720p, 480p, 360p
- El input esperado es H.264 MP4 (el formato que produce la app)

#### Paso 5: Configurar monitoreo de Free Tier
- Free Tier incluye: 100 minutos de encoding/mes, 500 minutos de streaming/mes
- Configurar alerta en Mux Dashboard al alcanzar 80% del límite
- Para el MVP: esto cubre aproximadamente 50 incidentes/mes

#### Paso 6: Probar con un video
- Subir un video de prueba a Supabase Storage (bucket `incident-videos`)
- Verificar que la Edge Function `trigger-mux-transcode` se ejecuta
- Verificar que Mux recibe el video y comienza la transcodificación
- Verificar que el webhook `mux-webhook-handler` recibe la notificación de `asset.ready`
- Verificar que el incidente se actualiza con `streaming_url`

### 6.3 Consideraciones críticas

- **Solo se sube video automáticamente en colisiones graves**: Para otros eventos (botón pánico, G-Force normal), el video se guarda localmente y el usuario decide si subirlo
- **Free Tier limitado**: 100 min encoding/mes; monitorear uso para no exceder
- **Latencia de transcodificación**: Típicamente 2-5 minutos para videos de 30 segundos
- **CDN global**: Mux distribuye el video via CDN (200+ PoPs), no hay que configurar nada adicional
- **Playback URL**: El formato es `https://stream.mux.com/<playback_id>.m3u8`

### 6.4 Posibles problemas y soluciones

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| Webhook no llega | URL incorrecta o función no desplegada | Verificar URL en Mux Dashboard y que la función existe |
| Asset en estado `errored` | Video corrupto o formato no soportado | Verificar que el video es H.264 MP4 válido |
| Playback URL no funciona | Asset aún no está `ready` | Esperar 2-5 minutos para transcodificación |
| Límite de Free Tier excedido | Demasiados videos procesados | Monitorear usage en Mux Dashboard; considerar upgrade |
| Signing Secret no verificado | Webhook no autenticado | Verificar firma del webhook en la Edge Function |

### 6.5 Criterios de aceptación
- [ ] Cuenta Mux creada con API Keys generadas
- [ ] Webhook configurado apuntando a Edge Function `mux-webhook-handler`
- [ ] Secretos de Mux configurados en Supabase
- [ ] Video de prueba transcoded exitosamente
- [ ] Playback URL funciona en navegador
- [ ] Alerta de Free Tier configurada al 80%

---

## 7. G-04: SETUP GOOGLE PLAY BILLING + WOMPI

### 7.1 Descripción
Configurar un esquema híbrido de pagos: **Google Play Billing** para compras dentro de la app Android (obligatorio por política de Google para bienes digitales) y **Wompi** para pagos desde la web Dashboard (PSE, Nequi, tarjetas colombianas). **NO se usa Stripe**.

### 7.2 Arquitectura de pagos por canal

| Canal | Método de pago | Qué se puede comprar |
|-------|---------------|---------------------|
| App Android | Google Play Billing | Suscripciones (Premium, Fleet), pago por evento |
| Web Dashboard | Wompi | Suscripciones Fleet, pagos únicos, instalación OBD |
| App Android | Wompi | ❌ NO disponible (Google obliga a usar Play Billing para bienes digitales) |
| Web Dashboard | Google Play Billing | ❌ NO disponible (solo Android) |

### 7.3 Proceso de implementación — Google Play Billing

#### Paso 1: Crear cuenta en Google Play Console
- Ir a https://play.google.com/console
- Crear cuenta de developer (fee único de $25 USD)
- Crear la app DuoVial en la consola

#### Paso 2: Crear productos de suscripción en Play Console
- Ir a Monetization > Subscriptions
- Crear los siguientes productos:

| ID de producto | Nombre | Tipo | Precio COP |
|---------------|--------|------|-----------|
| `premium_monthly` | DuoVial Premium | Suscripción mensual | $10,900 |
| `fleet_monthly` | DuoVial Fleet (por vehículo) | Suscripción mensual | $9,900 |

- Cada suscripción debe tener al menos un **Base Plan** mensual
- Para MVP: sin ofertas ni trials

#### Paso 3: Crear producto one-time en Play Console
- Ir a Monetization > In-app products
- Crear producto:

| ID de producto | Nombre | Tipo | Precio COP |
|---------------|--------|------|-----------|
| `per_event` | Pago por evento | One-time (no consumible) | $19,900 |

#### Paso 4: Configurar precios en COP
- En Play Console, configurar precios por región
- Colombia usa COP sin decimales
- Usar el price converter tool para precios equivalentes en otras regiones

#### Paso 5: Configurar Real-Time Developer Notifications (RTDN)
- Ir a Google Cloud Console > crear proyecto
- Habilitar Pub/Sub API
- Crear un tópico Pub/Sub (ej: `duovial-billing-notifications`)
- En Play Console > App settings > Real-time developer notifications, vincular el tópico
- Crear una suscripción al tópico para que la Edge Function reciba eventos

#### Paso 6: Crear Service Account para verificación server-side
- Google Cloud Console > IAM > Service Accounts > Crear
- Otorgar rol "Finance" en Google Play Console > Settings > API access
- Descargar JSON key (se usará en Edge Function `verify-google-purchase` para autenticar con Google Play Developer API)

### 7.4 Proceso de implementación — Wompi

#### Paso 1: Crear cuenta en Wompi
- Ir a https://comercios.wompi.co
- Registrarse como comercio (requiere RUT, cuenta bancaria colombiana)
- Usar modo **sandbox** para desarrollo

#### Paso 2: Obtener credenciales
- Dashboard Wompi > Mi cuenta > Integración técnica
- Obtener:
  - **Public Key** (`pub_test_...`)
  - **Private Key** (`prv_test_...`)
  - **Event Secret** (para verificar firma de webhooks — es DIFERENTE de las API keys)

#### Paso 3: Configurar webhook en Wompi
- Dashboard Wompi > Mi cuenta > Webhooks
- Agregar URL: `https://<project-ref>.supabase.co/functions/v1/wompi-webhook`
- URL diferente para sandbox vs producción
- Wompi reintenta hasta 3 veces en 24 horas si no recibe HTTP 200

#### Paso 4: Configurar métodos de pago
- Wompi soporta nativamente: tarjetas (Visa, MC, Amex), PSE, Nequi, Botón Bancolombia, DaviPlata, corresponsales
- No requiere configuración adicional; todos están habilitados por defecto

### 7.5 Consideraciones críticas

- **Google Play Billing es OBLIGATORIO** para bienes digitales dentro de la app — es política de Google, no es opcional
- **Wompi NO tiene suscripciones nativas** — solo procesa pagos únicos. Para cobros recurrentes (suscripciones Fleet desde web), hay que construir lógica propia con scheduled function (pg_cron) y tokenización de tarjetas
- **Comisión Google**: 15% para los primeros $1M USD/año, luego 30%
- **Comisión Wompi**: ~2.95% + fee fijo (varía por plan)
- **Acknowledge obligatorio**: Toda compra de Google Play debe reconocerse dentro de 3 días o Google hace reembolso automático
- **Wompi signature verification**: SHA256 con concatenación de propiedades del evento + timestamp + Event Secret. El Event Secret es diferente de las API keys.
- **Moneda Wompi**: Solo COP. Montos en **centavos** (1 COP = 100 centavos). Ejemplo: $10,900 COP = `1090000` centavos
- **Bienes físicos**: La instalación OBD ($39,900 COP) es un servicio físico, por lo que SÍ se cobra con Wompi incluso desde la app

### 7.6 Posibles problemas y soluciones

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| Compra Google Play no se activa | No se llamó `acknowledgePurchase` en 3 días | Google hace reembolso automático; implementar acknowledge inmediato tras verificación |
| RTDN de Google no llega | Pub/Sub no configurado correctamente | Verificar tópico, suscripción y permisos del service account |
| Webhook Wompi no llega | URL incorrecta o Edge Function no responde | Verificar URL en Wompi Dashboard; asegurar que función retorna HTTP 200 |
| Firma Wompi no verifica | Event Secret incorrecto o algoritmo mal implementado | Verificar Event Secret en Dashboard; seguir exactamente el algoritmo de concatenación |
| Cobro recurrente Wompi falla | Token de tarjeta expirado o sin fondos | Reintentar; notificar al usuario para actualizar método de pago |
| Transacción Wompi en PENDING | PSE o Nequi tarda en confirmar | Polling cada 30 segundos hasta APPROVED o DECLINED (máximo 5 minutos) |
| Doble cobro Wompi | Webhook recibido dos veces | Usar `external_id` como unique constraint en tabla `purchases` |

### 7.7 Criterios de aceptación
- [ ] Cuenta Google Play Console creada con productos configurados
- [ ] RTDN configurado con Pub/Sub (tópico + suscripción)
- [ ] Service Account creado con rol Finance y JSON key descargado
- [ ] Cuenta Wompi creada en sandbox
- [ ] Credenciales Wompi obtenidas (Public Key, Private Key, Event Secret)
- [ ] Webhook Wompi configurado apuntando a Edge Function
- [ ] Precios en COP configurados en Play Console
- [ ] Secretos de Google Play y Wompi configurados en Supabase

---

## 8. C-04: SCHEMA DE BASE DE DATOS COMPLETO

### 8.1 Descripción
Crear todas las tablas necesarias en Supabase Postgres mediante migraciones SQL. Este schema es la base para todas las funcionalidades futuras.

### 8.2 Extensiones requeridas

Antes de crear las tablas, habilitar las siguientes extensiones en Supabase:

| Extensión | Propósito | Cómo habilitar |
|-----------|-----------|----------------|
| `uuid-ossp` | Generación de UUIDs | Supabase Dashboard > Database > Extensions |
| `vector` (pgvector) | Embeddings faciales para reconocimiento | Supabase Dashboard > Database > Extensions |
| `postgis` | Datos geográficos para geofencing | Supabase Dashboard > Database > Extensions |

### 8.3 Orden de migraciones

Las migraciones deben ejecutarse en este orden exacto:

| Migración | Tablas creadas | Dependencias |
|-----------|---------------|--------------|
| `001_initial_schema` | `profiles` | Ninguna |
| `002_organizations` | `organizations`, `organization_members`, `vehicles`, `drivers` | 001 |
| `003_incidents` | `incidents`, `geofence_events`, `vehicle_telemetry` | 002 |
| `004_maintenance` | `maintenance_rules`, `odometer_logs`, `obd_readings`, `maintenance_alerts` | 002 |
| `005_storage` | Bucket `incident-videos` + policies | Ninguna |
| `006_billing` | `products`, `purchases`, `subscriptions`, `billing_events`, `wompi_card_tokens` | 001, 002 |

### 8.4 Detalle de cada migración

#### Migración 001: Schema inicial
**Tablas**: `profiles`

**Descripción**:
- Tabla `profiles` extiende `auth.users` con datos adicionales del usuario
- Incluye: email, full_name, phone, plan, plan_source, plan_expires_at, push_token
- Trigger automático: al crear un usuario en Supabase Auth, se crea su perfil automáticamente
- RLS: usuarios solo pueden ver y actualizar su propio perfil
- Función helper: `current_user_id()` retorna el ID del usuario autenticado

**Consideraciones**:
- El trigger `on_auth_user_created` es SECURITY DEFINER (se ejecuta con permisos de admin)
- La columna `plan` tiene un CHECK constraint con los valores válidos: `free`, `per_event`, `premium`, `fleet`

#### Migración 002: Organizations + Multi-tenancy
**Tablas**: `organizations`, `organization_members`, `vehicles`, `drivers`

**Descripción**:
- `organizations`: empresas/flotas con plan, slug único, settings JSONB
- `organization_members`: relación usuario-organización con roles (owner, admin, supervisor, driver)
- `vehicles`: vehículos pertenecientes a una organización
- `drivers`: conductores vinculados a una organización (con embedding facial para reconocimiento)
- Trigger automático: al crear una organización, el creador se registra como `owner`
- Función helper: `current_org_id()` retorna la organización activa de la sesión
- RLS: aislamiento total por organización (un usuario solo ve datos de sus organizaciones)

**Consideraciones**:
- La columna `drivers.face_embedding` usa tipo `VECTOR(512)` (requiere extensión `vector`)
- `organization_members` tiene primary key compuesta (org_id, user_id)
- Los roles tienen un CHECK constraint: `owner`, `admin`, `supervisor`, `driver`
- RLS en `organizations` y `organization_members` permite SELECT basado en membresía
- RLS en `vehicles` y `drivers` usa `current_org_id()` para aislamiento

#### Migración 003: Incidents + Geofencing + Telemetría
**Tablas**: `incidents`, `geofence_events`, `vehicle_telemetry`

**Descripción**:
- `incidents`: eventos de video con trigger_type, estado de procesamiento Mux, ubicación geográfica
- `geofence_events`: registros de entrada/salida de zonas geofenceadas
- `vehicle_telemetry`: datos en tiempo real (lat, lon, velocidad, G-force, batería)
- Realtime habilitado en `vehicle_telemetry` para actualizaciones en vivo
- RLS: aislamiento por organización en todas las tablas

**Consideraciones**:
- La columna `location` usa tipo `GEOGRAPHY(POINT, 4326)` (requiere extensión `postgis`)
- `incidents.status` tiene CHECK constraint: `uploading`, `processing`, `ready`, `error`
- `incidents.trigger_type` tiene CHECK constraint: `panic`, `accel`, `collision`, `geofence`
- `geofence_events.event_type` tiene CHECK constraint: `enter`, `exit`
- `vehicle_telemetry` debe estar en la publicación `supabase_realtime` para funcionar con WebSockets

#### Migración 004: Mantenimiento predictivo
**Tablas**: `maintenance_rules`, `odometer_logs`, `obd_readings`, `maintenance_alerts`

**Descripción**:
- `maintenance_rules`: reglas de mantenimiento por marca/modelo/año (aceite, filtros, bujías, frenos)
- `odometer_logs`: registros de kilometraje por vehículo (fuente: gps, obd, manual)
- `obd_readings`: lecturas del dongle OBD II (RPM, temperatura, voltaje, códigos de error)
- `maintenance_alerts`: alertas generadas por el sistema de mantenimiento
- RLS: aislamiento por organización en `maintenance_alerts`

**Consideraciones**:
- `maintenance_alerts.severity` tiene CHECK constraint: `info`, `warning`, `critical`
- `maintenance_alerts.status` tiene CHECK constraint: `pending`, `acknowledged`, `resolved`
- `odometer_logs.source` tiene CHECK constraint: `gps`, `obd`, `manual`

#### Migración 005: Storage Bucket
**Recursos**: Bucket `incident-videos` + policies

**Descripción**:
- Bucket privado para almacenar videos de incidentes
- Policies: usuarios autenticados pueden subir, ver y eliminar sus propios videos
- El bucket NO es público (los videos se acceden via signed URLs o Mux streaming)

**Consideraciones**:
- Las policies de Storage usan `auth.uid()` para verificar el usuario
- Los videos se suben directamente desde la app (TUS resumable upload)
- La Edge Function `trigger-mux-transcode` se dispara automáticamente al subir un video

#### Migración 006: Billing (Google Play + Wompi)
**Tablas**: `products`, `purchases`, `subscriptions`, `billing_events`, `wompi_card_tokens`

**Descripción**:
- `products`: catálogo de productos disponibles (reemplaza `stripe.products` y `stripe.prices`). Incluye nombre, tipo (subscription/one_time), canal (google_play/wompi), precio COP, y IDs externos
- `purchases`: registro de todas las compras (tanto Google Play como Wompi). Incluye user_id, org_id, product_id, channel, external_id (purchaseToken o transaction_id), status, amount_cop
- `subscriptions`: estado actual de suscripciones activas (reemplaza `stripe.subscriptions`). Incluye status, current_period_start/end, next_billing_date, cancel_at_period_end, wompi_card_token para cobros recurrentes
- `billing_events`: log de todos los webhooks y notificaciones recibidos (Google RTDN + Wompi webhooks). Incluye source, event_type, payload JSONB, processed flag
- `wompi_card_tokens`: tokens de tarjetas guardados para cobros recurrentes de Wompi. Incluye wompi_token, last_four, brand, is_default

**Consideraciones**:
- `subscriptions.status` tiene CHECK constraint: `active`, `expired`, `cancelled`, `on_hold`, `grace_period`
- `purchases.status` tiene CHECK constraint: `pending`, `approved`, `declined`, `refunded`, `expired`
- `billing_events` NO tiene acceso directo desde la app; solo Edge Functions pueden escribir
- `wompi_card_tokens` solo almacena el token de Wompi, NO datos de tarjeta (Wompi es PCI compliant)
- Datos iniciales en `products`: Premium ($10,900/mes), Fleet ($9,900/mes), Por Evento ($19,900), Instalación OBD ($39,900)

### 8.5 Proceso de ejecución vía MCP

Para cada migración:

1. **Crear el archivo SQL** en `supabase/migrations/` con el nombre correspondiente
2. **Ejecutar la migración** usando `supabase_apply_migration` con el nombre y query
3. **Verificar** usando `supabase_list_tables` que las tablas se crearon correctamente
4. **Verificar RLS** usando `supabase_get_advisors` (type: `security`) para detectar políticas faltantes

### 8.6 Seed data (datos de prueba)

Crear un archivo `supabase/seed.sql` con reglas de mantenimiento genéricas para vehículos comunes en Colombia:
- Chevrolet Spark (aceite 5,000km, filtros 10,000km, bujías 20,000km, frenos 30,000km)
- Renault Logan (mismos intervalos)
- Toyota Corolla (aceite 10,000km, filtros 20,000km)

### 8.7 Consideraciones críticas

- **Orden de migraciones es obligatorio**: Las foreign keys dependen de que las tablas referenciadas existan
- **Extensiones antes que tablas**: `vector` y `postgis` deben habilitarse antes de crear tablas que usan esos tipos
- **RLS en TODAS las tablas**: Sin RLS, cualquier usuario puede ver datos de otros (fuga de datos multi-tenancy)
- **Funciones helper**: `current_user_id()` y `current_org_id()` son esenciales para las policies
- **Triggers automáticos**: `handle_new_user` y `handle_new_organization` simplifican el onboarding

### 8.8 Posibles problemas y soluciones

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| Migración falla | Tabla ya existe | Verificar que no se ejecutó antes; usar `supabase_list_migrations` |
| RLS bloquea todo | Policy incorrecta o `current_org_id()` retorna null | Verificar que la session variable `app.current_org_id` se establece |
| `vector` no disponible | Extensión no habilitada | Habilitar en Supabase Dashboard > Database > Extensions |
| `postgis` no disponible | Extensión no habilitada | Habilitar en Supabase Dashboard > Database > Extensions |
| Foreign key falla | Tabla referenciada no existe | Verificar orden de migraciones |
| Trigger no se ejecuta | Función no creada o permisos insuficientes | Verificar que la función es SECURITY DEFINER |

### 8.9 Criterios de aceptación
- [ ] 3 extensiones habilitadas: `uuid-ossp`, `vector`, `postgis`
- [ ] 6 migraciones ejecutadas sin errores
- [ ] 12 tablas creadas: `profiles`, `organizations`, `organization_members`, `vehicles`, `drivers`, `incidents`, `geofence_events`, `vehicle_telemetry`, `maintenance_rules`, `odometer_logs`, `obd_readings`, `maintenance_alerts`
- [ ] 5 tablas de billing creadas: `products`, `purchases`, `subscriptions`, `billing_events`, `wompi_card_tokens`
- [ ] RLS policies activas en todas las tablas de usuario
- [ ] Funciones helper creadas: `current_user_id()`, `current_org_id()`
- [ ] Triggers automáticos creados: `handle_new_user`, `handle_new_organization`
- [ ] Bucket `incident-videos` creado con policies
- [ ] Realtime habilitado en `vehicle_telemetry`
- [ ] Seed data insertado correctamente
- [ ] `supabase_get_advisors` (security) no reporta RLS faltantes

---

## 9. VARIABLES DE ENTORNO

### 9.1 Variables requeridas

| Variable | Servicio | Dónde se configura | Propósito |
|----------|----------|-------------------|-----------|
| `SUPABASE_URL` | Supabase | Supabase Dashboard > Secrets | URL del proyecto |
| `SUPABASE_ANON_KEY` | Supabase | Supabase Dashboard > Secrets | Key pública para la app |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase | Supabase Dashboard > Secrets | Key de servicio (bypassea RLS) |
| `SUPABASE_PROJECT_REF` | Supabase | Supabase Dashboard > Secrets | Referencia del proyecto |
| `MUX_TOKEN_ID` | Mux | Supabase Dashboard > Secrets | API Key de Mux |
| `MUX_TOKEN_SECRET` | Mux | Supabase Dashboard > Secrets | API Secret de Mux |
| `WOMPI_PUBLIC_KEY` | Wompi | Supabase Dashboard > Secrets | Public Key de Wompi |
| `WOMPI_PRIVATE_KEY` | Wompi | Supabase Dashboard > Secrets | Private Key de Wompi |
| `WOMPI_EVENT_SECRET` | Wompi | Supabase Dashboard > Secrets | Event Secret para verificar webhooks |
| `WOMPI_BASE_URL` | Wompi | Supabase Dashboard > Secrets | `https://sandbox.wompi.co` o `https://production.wompi.co` |
| `GOOGLE_PLAY_PACKAGE_NAME` | Google Play | Supabase Dashboard > Secrets | Package name de la app (com.duovial) |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Google Play | Supabase Dashboard > Secrets | JSON del service account para Developer API |
| `ONESIGNAL_APP_ID` | OneSignal | Supabase Dashboard > Secrets | App ID de OneSignal |
| `ONESIGNAL_REST_API_KEY` | OneSignal | Supabase Dashboard > Secrets | REST API Key de OneSignal |
| `APP_URL` | App | Supabase Dashboard > Secrets | URL base de la app |

### 9.2 Variables para la app Android (BuildConfig)

Estas variables se configurarán en `gradle.properties` (NO commitear) y se inyectarán en `BuildConfig`:

| Variable | Propósito |
|----------|-----------|
| `onesignal.appId` | App ID de OneSignal para inicializar SDK |
| `supabase.url` | URL de Supabase para el cliente |
| `supabase.anonKey` | Key pública de Supabase para el cliente |

### 9.3 Archivo `.env.example`

Crear un archivo `.env.example` en la raíz del repositorio con todas las variables documentadas (sin valores reales). Este archivo SÍ se commitea como referencia.

### 9.4 Consideraciones críticas

- **NUNCA commitear valores reales**: Los archivos con valores reales (`.env.local`, `gradle.properties` con keys) deben estar en `.gitignore`
- **Secrets en Supabase Dashboard**: Las Edge Functions leen variables de entorno desde Supabase Dashboard > Secrets, NO desde archivos
- **Rotación de keys**: Si una key se compromete, rotarla inmediatamente en el servicio correspondiente y actualizar en Supabase

---

## 10. POSIBLES PROBLEMAS Y SOLUCIONES

### 10.1 Problemas generales

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| Servicios no se comunican | URLs incorrectas | Verificar todas las URLs de webhook en cada dashboard |
| Variables de entorno no se leen | No configuradas en Supabase Secrets | Verificar en Supabase Dashboard > Edge Functions > Secrets |
| CORS errors | Headers no configurados en Edge Functions | Agregar `Access-Control-Allow-Origin` en la respuesta |
| Rate limiting | Demasiadas requests a un servicio | Implementar backoff exponencial |
| Timeouts | Servicio externo lento o caído | Verificar status pages de cada servicio |

### 10.2 Problemas por servicio

#### Supabase
| Problema | Solución |
|----------|----------|
| Connection pool lleno | Escalar plan o optimizar queries con connection pooling |
| RLS bloquea queries legítimos | Verificar policies con `EXPLAIN ANALYZE` |
| Migraciones fallan | Verificar orden y que no hay conflictos con migraciones existentes |

#### OneSignal
| Problema | Solución |
|----------|----------|
| Notificaciones no llegan | Verificar FCM Server Key en OneSignal Dashboard |
| playerId null | Usuario no aceptó permisos de notificación |
| Segmentos vacíos | Tags no configurados correctamente en la app |

#### Mux
| Problema | Solución |
|----------|----------|
| Asset en error | Verificar formato de video (debe ser H.264 MP4) |
| Webhook no llega | Verificar URL y que Edge Function está desplegada |
| Límite excedido | Monitorear usage en Mux Dashboard |

#### Google Play Billing
| Problema | Solución |
|----------|----------|
| Compra no se activa | Verificar que `acknowledgePurchase` se llama tras verificación exitosa |
| RTDN no llega | Verificar tópico Pub/Sub, suscripción y permisos del service account |
| Token inválido | Verificar `packageName` en la llamada a la API |

#### Wompi
| Problema | Solución |
|----------|----------|
| Webhook no llega | Verificar URL en Wompi Dashboard; asegurar que Edge Function retorna HTTP 200 |
| Firma no verifica | Verificar Event Secret (diferente de API keys); seguir algoritmo de concatenación exacto |
| Cobro recurrente falla | Token de tarjeta expirado; notificar usuario para actualizar método de pago |
| Transacción en PENDING | PSE/Nequi tarda; polling cada 30s hasta APPROVED o DECLINED (máx 5 min) |

---

## 11. CHECKLIST DE VALIDACIÓN

### 11.1 Checklist general
- [ ] Todas las cuentas creadas (Supabase, OneSignal, Mux, Google Play Console, Wompi, Firebase, Google Cloud)
- [ ] Variables de entorno configuradas en Supabase Dashboard > Secrets
- [ ] `.env.example` creado y commiteado
- [ ] No hay secrets commiteados en el repositorio

### 11.2 Checklist G-01 (Supabase Edge Functions)
- [ ] 7 Edge Functions creadas en el repositorio
- [ ] 7 Edge Functions desplegadas en Supabase Cloud
- [ ] `verify_jwt` configurado correctamente por función
- [ ] 13 secretos configurados en Supabase Dashboard
- [ ] Cada función responde correctamente a peticiones HTTP

### 11.3 Checklist G-02 (OneSignal)
- [ ] Proyecto Firebase creado con FCM habilitado
- [ ] App OneSignal creada y vinculada a Firebase
- [ ] Server Key y Sender ID configurados en OneSignal
- [ ] 3 segmentos creados (drivers, admins, all_users)
- [ ] Secretos de OneSignal configurados en Supabase
- [ ] Notificación de prueba recibida en dispositivo

### 11.4 Checklist G-03 (Mux)
- [ ] Cuenta Mux creada con API Keys generadas
- [ ] Webhook configurado apuntando a Edge Function
- [ ] Secretos de Mux configurados en Supabase
- [ ] Video de prueba transcoded exitosamente
- [ ] Playback URL funciona en navegador
- [ ] Alerta de Free Tier configurada al 80%

### 11.5 Checklist G-04 (Google Play Billing + Wompi)
- [ ] Cuenta Google Play Console creada con productos configurados (premium_monthly, fleet_monthly, per_event)
- [ ] RTDN configurado con Pub/Sub (tópico + suscripción)
- [ ] Service Account creado con rol Finance y JSON key descargado
- [ ] Cuenta Wompi creada en sandbox
- [ ] Credenciales Wompi obtenidas (Public Key, Private Key, Event Secret)
- [ ] Webhook Wompi configurado apuntando a Edge Function
- [ ] Precios en COP configurados en Play Console
- [ ] Secretos de Google Play y Wompi configurados en Supabase

### 11.6 Checklist C-04 (Schema)
- [ ] 3 extensiones habilitadas: `uuid-ossp`, `vector`, `postgis`
- [ ] 6 migraciones ejecutadas sin errores
- [ ] 12 tablas de usuario creadas
- [ ] 5 tablas de billing creadas: `products`, `purchases`, `subscriptions`, `billing_events`, `wompi_card_tokens`
- [ ] RLS policies activas en todas las tablas
- [ ] Funciones helper creadas
- [ ] Triggers automáticos creados
- [ ] Bucket `incident-videos` creado con policies
- [ ] Realtime habilitado en `vehicle_telemetry`
- [ ] Seed data insertado
- [ ] `supabase_get_advisors` (security) sin reportes críticos

---

## 12. ENTREGABLES

### 12.1 Archivos a crear en el repositorio

| Archivo | Descripción |
|---------|-------------|
| `supabase/config.toml` | Configuración del proyecto Supabase |
| `supabase/migrations/001_initial_schema.sql` | Migración de schema inicial |
| `supabase/migrations/002_organizations.sql` | Migración de multi-tenancy |
| `supabase/migrations/003_incidents.sql` | Migración de incidents + telemetría |
| `supabase/migrations/004_maintenance.sql` | Migración de mantenimiento |
| `supabase/migrations/005_storage.sql` | Migración de storage buckets |
| `supabase/migrations/006_billing.sql` | Migración de billing (products, purchases, subscriptions, etc.) |
| `supabase/seed.sql` | Datos de prueba |
| `supabase/functions/trigger-mux-transcode/index.ts` | Edge Function Mux trigger |
| `supabase/functions/mux-webhook-handler/index.ts` | Edge Function Mux webhook |
| `supabase/functions/wompi-webhook/index.ts` | Edge Function Wompi webhook |
| `supabase/functions/verify-google-purchase/index.ts` | Edge Function verificación Google Play |
| `supabase/functions/create-wompi-link/index.ts` | Edge Function crear payment link Wompi |
| `supabase/functions/send-push-notification/index.ts` | Edge Function OneSignal |
| `supabase/functions/process-recurring-billing/index.ts` | Edge Function cobros recurrentes Wompi |
| `.env.example` | Template de variables de entorno |

### 12.2 Configuraciones externas a completar

| Servicio | Configuración | Verificación |
|----------|--------------|--------------|
| Supabase | Edge Functions desplegadas, migraciones aplicadas, secrets configurados | `supabase_list_edge_functions`, `supabase_list_tables` |
| OneSignal | App creada, FCM vinculado, segmentos creados | Notificación de prueba recibida |
| Mux | Cuenta creada, webhook configurado, encoding probado | Playback URL funciona |
| Google Play Console | Productos creados, RTDN configurado, Service Account creado | Productos visibles en Play Console |
| Wompi | Cuenta sandbox, webhook configurado, credenciales obtenidas | Webhook recibido correctamente |
| Google Cloud | Proyecto creado, Pub/Sub tópico configurado | Tópico activo |
| Firebase | Proyecto creado, FCM habilitado | Server Key y Sender ID obtenidos |

### 12.3 Documentación a entregar
- [ ] Este documento actualizado con cualquier desviación encontrada
- [ ] Webhook URLs documentadas (para referencia futura)
- [ ] Product IDs de Google Play documentados (se usarán en la app)
- [ ] Lista de secretos configurados en Supabase

---

## ⚠️ NOTAS IMPORTANTES PARA EL MID-LEVEL DEVELOPER

1. **TODO va a Supabase Cloud**: No usar desarrollo local. Usar el MCP de Supabase para ejecutar migraciones y desplegar funciones directamente en la nube.
2. **NO commitear secrets**: Nunca commitear archivos con credenciales reales. Usar `.env.example` como template.
3. **Usar modo sandbox**: Wompi y Google Play deben configurarse en modo sandbox/test primero.
4. **Google Play Billing es obligatorio** para bienes digitales dentro de la app — no es opcional, es política de Google.
5. **Wompi NO tiene suscripciones nativas** — hay que construir lógica propia de cobro recurrente con scheduled function.
4. **El código lo escribe el agente desarrollador**: Este documento solo describe QUÉ hacer y CÓMO. El código TypeScript/Deno de las Edge Functions y SQL de las migraciones lo escribe el agente asignado a cada ticket.
5. **Documentar desviaciones**: Si algo no funciona como se describe, documentar el problema y la solución aplicada.
6. **Consultar antes de cambiar schema**: Cualquier cambio en las migraciones debe ser revisado por el Tech Lead.
7. **Verificar RLS**: Después de crear cada tabla, usar `supabase_get_advisors` (type: `security`) para detectar políticas faltantes.
8. **Free Tier monitoring**: Configurar alertas en todos los servicios para no exceder los límites gratuitos.
9. **Orden de ejecución es crítico**: Seguir el orden G-01 → G-02 → G-03 → G-04 → C-04. Las dependencias son reales.
10. **Verificar con herramientas MCP**: Después de cada paso, usar las herramientas MCP correspondientes para verificar que todo quedó correcto.

---

*Documento v3.0 — Guía de implementación para Fase 0. Pagos: Google Play Billing (app) + Wompi (web). Enfoque 100% Supabase Cloud vía MCP. Cualquier desviación debe ser documentada y comunicada al Tech Lead.*

**Creado**: Junio 30, 2026
**Revisado por**: Tech Lead
**Aprobado para ejecución**: ☐ Pendiente
