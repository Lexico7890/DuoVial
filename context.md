# 📋 CONTEXT.md — DUOVIAL: DASH CAM INTELIGENTE + PLATAFORMA DE FLOTAS

**Versión**: 2.0
**Última actualización**: Junio 25, 2026
**Estado**: MVP en desarrollo — Funcionalidades base completadas, capa empresarial Fleet en planeación
**Audiencia**: Agentes de IA, desarrolladores, stakeholders

---

## 📌 TABLA DE CONTENIDOS

1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Visión General del Proyecto](#visión-general-del-proyecto)
3. [Problema Identificado](#problema-identificado)
4. [Solución Propuesta](#solución-propuesta)
5. [Arquitectura Técnica](#arquitectura-técnica)
6. [Módulos Técnicos y Funcionales](#módulos-técnicos-y-funcionales)
   - 6.1 [Modo Vigilante (Buffer Circular)](#61-modo-vigilante-buffer-circular)
   - 6.2 [Geofencing (Perímetro de Operación)](#62-geofencing-perímetro-de-operación)
   - 6.3 [Reconocimiento Facial (Solo Alerta)](#63-reconocimiento-facial-solo-alerta)
   - 6.4 [Score de Conducción (Descartado MVP)](#64-score-de-conducción-descartado-mvp)
   - 6.5 [Mantenimiento Predictivo](#65-mantenimiento-predictivo)
   - 6.6 [Auto-Inicio por Actividad](#66-auto-inicio-por-actividad)
   - 6.7 [Dashboard Web (El Cerebro)](#67-dashboard-web-el-cerebro)
   - 6.8 [Anti-somnolencia + Wearables (El Copiloto)](#68-anti-somnolencia--wearables-el-copiloto)
   - 6.9 [Modo Offline + Gestión de Almacenamiento](#69-modo-offline--gestión-de-almacenamiento)
   - 6.10 [Integración OBD II (ELM327)](#610-integración-obd-ii-elm327)
   - 6.11 [Colisión + Llamada Automática](#611-colisión--llamada-automática)
7. [Sistema de Detección de Eventos](#sistema-de-detección-de-eventos)
8. [Flujos de Usuario](#flujos-de-usuario)
9. [Tabla de Funcionalidades Comerciales](#tabla-de-funcionalidades-comerciales)
10. [Consumo de Recursos y Optimizaciones](#consumo-de-recursos-y-optimizaciones)
11. [Segmentación de Usuarios](#segmentación-de-usuarios)
12. [Estrategia Go-to-Market](#estrategia-go-to-market)
13. [Riesgos y Mitigaciones](#riesgos-y-mitigaciones)
14. [Especificaciones Técnicas Detalladas](#especificaciones-técnicas-detalladas)
15. [Métricas de Éxito](#métricas-de-éxito)
16. [Wearables Recomendados](#wearables-recomendados)
17. [FAQ Técnico](#faq-técnico)

---

## 🎯 RESUMEN EJECUTIVO

DuoVial es una **app Android descargable desde Google Play Store** que convierte cualquier teléfono en una dash cam inteligente de bajo consumo. Está diseñada para cualquier persona que conduzca: particulares, Uber, taxi, reparto y flotas.

**DuoVial Fleet** es el plan/tier empresarial de la misma app, que añade una capa de administración centralizada vía Dashboard web con monitoreo de flotas, control de conductores, geofencing, OBD II y más. No es un producto separado: es la misma app, con funcionalidades premium para empresas.

**El problema**: Las dashcams tradicionales son caras ($100–$300) y no ofrecen monitoreo en vivo. Las soluciones GPS solo dan ubicación, no evidencia en video. Los accidentes sin evidencia cuestan a las empresas entre $5,000 y $50,000 por incidente.

**Nuestra solución**: Una app Android (Play Store) + Dashboard web que ofrece:

- Dashcam inteligente (buffer circular de bajo consumo)
- Monitoreo de fatiga (cámara frontal + wearables)
- Geofencing (control de zonas)
- Reconocimiento facial (control de conductores)
- Mantenimiento predictivo (alertas mecánicas)
- Detección de colisiones + llamada automática de verificación
- Integración OBD II (datos reales del motor)
- Auto-inicio sin intervención del usuario
- Modo offline con sincronización automática

**Modelo de negocio**: Suscripción mensual por vehículo (desde $19.99/mes para Fleet) + servicios de instalación OBD. Planes disponibles: Free, Por Evento, Premium, y Fleet (Empresas). Todo se construye sobre la misma app base DuoVial.

---

## 🎯 VISIÓN GENERAL DEL PROYECTO

### Qué es
Una **app Android descargable desde Google Play Store** que convierte el teléfono del usuario en una **dash cam inteligente de bajo consumo**. Para empresas, incluye un Dashboard web de administración de flotas (plan Fleet).

### Por qué existe
El creador ha vivido personalmente el problema de los accidentes sin evidencia. Conductores de Uber, taxi, particulares y empresas de flota necesitan:
- Registrar incidentes para defenderse legalmente
- Detectar fatiga al volante para evitar accidentes
- Controlar zonas de operación y conductores autorizados
- Hacer todo esto sin que el teléfono se sobrecaliente ni drene la batería

### Quién la va a usar
**Base**: Cualquier persona que conduzca (particulares, Uber, taxi, reparto). La app se instala desde la Play Store.
**Foco de revenue**: Empresas de flota mediante el plan Fleet (misma app + Dashboard web).
**Segmento secundario**: Padres monitoreando conductores jóvenes.

### Qué la hace diferente
- **Buffer circular en cache del OS**: No graba continuamente, solo guarda los 30 segundos alrededor de un evento
- **Detección multi-sensor**: No depende de un solo trigger (acelerómetro falló en pruebas reales)
- **Bajo consumo**: 40% menos batería que grabación continua
- **Anti-somnolencia integrada**: Prevención de accidentes con 3 niveles de escalada (cámara frontal + wearables + llamadas a contactos de emergencia)
- **Plataforma Fleet completa**: Dashboard web con mapa en vivo, gestión de conductores, toggles por vehículo, geofencing, facial, OBD II y reportes exportables

### Qué NO es
- ❌ No es una dash cam de hardware (es software en el teléfono)
- ❌ No funciona en iPhone (iOS bloquea cámara en background por política de privacidad)
- ❌ No graba continuamente 2+ horas (sobrecalienta el teléfono)
- ❌ No es una solución perfecta para todos los casos (ver sección de riesgos)
- ❌ No bloquea ni apaga vehículos (el reconocimiento facial es solo alerta)
- ❌ No reemplaza un sistema GPS dedicado de flota (complementa con video + sensores)

---

## 🔍 PROBLEMA IDENTIFICADO

### Experiencia del creador
El creador de este proyecto fue chocado mientras conducía Uber. El otro conductor negó la culpa. Sin video:
- El seguro no quiso cubrir
- Fue responsabilidad 50/50
- Perdió dinero en reparaciones

### Problema general en conductores
1. **Falta de evidencia**: Accidentes sin testigos = palabra contra palabra
2. **Costos de dash cams**: Hardware de calidad cuesta $100–$300 (caro para conductores de gig economy)
3. **Riesgo de fatiga**: Trayectos largos = riesgo de micro-sueños
4. **Soluciones inadecuadas**:
   - Apps tradicionales graban TODO (matan batería, sobrecalientan, llenan almacenamiento)
   - Sensores del teléfono no son confiables para detectar impactos
   - Mantener cámara frontal siempre activa para detectar somnolencia consume batería y genera problemas de privacidad

### Problema específico en empresas de flota
1. **Accidentes sin evidencia**: Un solo incidente cuesta entre $5,000 y $50,000 a la empresa (reparaciones, seguros, tiempo parado, demandas)
2. **Falta de control de zonas**: Vehículos que salen de la ciudad sin autorización
3. **Conductores no autorizados**: No hay forma de saber quién está manejando cada vehículo
4. **Mantenimiento reactivo**: Averías costosas que pudieron prevenirse
5. **Sin visibilidad centralizada**: El dueño de flota no tiene un tablero único para ver todos sus vehículos

### Datos de las pruebas
- **Prueba de acelerómetro**: En frenadas bruscas reales solo registró 1.3G (umbral no confiable)
- **Prueba de velocidad GPS**: La desaceleración medida por GPS tiene 1-2 seg de lag (demasiado lento)
- **Prueba de cámara frontal continua**: Alto consumo de batería y riesgo de sobre-calentamiento; no viable para detección en background
- **Conclusión**: No se puede depender de un único sensor para detección, y la cámara frontal no debe estar siempre activa

---

## 💡 SOLUCIÓN PROPUESTA

### La base: DuoVial (app para cualquier conductor)

#### 1. Modo Vigilante (Buffer Circular)
- Cámara trasera captura video continuamente
- **NO escribe a disco** (usa cache optimizada del OS)
- Mantiene solo los últimos 15 segundos en cache
- Cuando detecta un evento (impacto, botón de pánico), guarda esos 15 seg y los 15 segundos posteriores al evento creando dos videos de 15 segundos cada uno
- Los 15 seg más viejos se descartan automáticamente
- Funciona en background vía Foreground Service

#### 2. Anti-Somnolencia (Detección Facial + Wearables)
- **Cámara frontal**: activa SOLO cuando el usuario está en `FatigueScreen`. Analiza el rostro del conductor con ML Kit, mide parpadeo (Eye Aspect Ratio) y emite alerta si ojos cerrados > umbral configurable.
- **Wearables compatibles**: fuente primaria de detección de fatiga en background. Lee datos de salud (frecuencia cardíaca, HRV, actividad) vía Health Connect.
- **NO guarda video** de la cámara frontal (solo analiza en tiempo real y descarta)
- Si el dispositivo no soporta cámaras concurrentes, la app sugiere usar un wearable o pausa el modo Vigilante mientras se usa la cámara frontal

### La capa empresarial: DuoVial Fleet (plan/tier para empresas)

Misma app, con funcionalidades adicionales de administración centralizada:

| # | Funcionalidad | Incluida en MVP |
|---|--------------|-----------------|
| 1 | Geofencing (Perímetro de Operación) | ✅ Sí |
| 2 | Reconocimiento Facial (Solo alerta) | ✅ Sí (Off por defecto) |
| 3 | Score de Conducción | ❌ No (MVP) |
| 4 | Mantenimiento Predictivo | ✅ Sí (Reglas + LLM) |
| 5 | Auto-Inicio por Actividad | ✅ Sí |
| 6 | Dashboard Web | ✅ Sí |
| 7 | Anti-somnolencia 3 Niveles | ✅ Sí |
| 8 | Offline + Limpieza 7 días | ✅ Sí |
| 9 | OBD II + Instalación paga | ✅ Sí |
| 10 | Colisión + Llamada automática | ✅ Sí (Filtro >40km/h) |

### Gestión de Suscripciones
- **Plan Free**: Buffer circular, auto-inicio, guardado local, anti-somnolencia básico (solo alerta)
- **Plan Por Evento**: Mismo que Free + procesamiento y descarga de videos por evento específico (pago único por evento)
- **Plan Premium** ($X/mes): Ilimitado procesamiento de videos, anti-somnolencia avanzado (3 niveles), mantenimiento predictivo, OBD II, colisión + llamada
- **Plan Fleet** (desde $19.99/mes por vehículo): Todo lo de Premium + Dashboard web, geofencing, reconocimiento facial, métricas de flota, administración centralizada

---

## 🏗️ ARQUITECTURA TÉCNICA

### Stack tecnológico actual

> **Nota**: La implementación real divergió del plan original (React Native / Expo). El proyecto actual usa **Kotlin Multiplatform + Compose Multiplatform** para máximo control, rendimiento y mantenibilidad. Todo el acceso nativo está en Kotlin.

| Componente | Tecnología | Propósito |
|------------|-----------|----------|
| **App Móvil** | Kotlin Multiplatform + Compose Multiplatform | UI, lógica nativa |
| **Cámara Trasera** | CameraX (Preview) | Buffer circular, modo Vigilante |
| **Cámara Frontal** | CameraX (Preview + ImageAnalysis) | Anti-somnolencia en FatigueScreen |
| **Sensores** | SensorManager nativo (Kotlin) | Acelerómetro (G-Force) |
| **GPS** | LocationManager nativo (Kotlin) | Velocímetro (MPH/KPH) |
| **Background** | LifecycleService + Foreground Service (Kotlin) | Supervivencia del servicio de vigilancia |
| **Auto-Inicio** | Activity Recognition API + Foreground Service | Detección automática de conducción |
| **Persistencia local** | Multiplatform Settings (SharedPreferences) + SQLite | Configuración de usuario + datos offline |
| **Watchdog** | WorkManager (Kotlin) | Revive el servicio si Android lo mata |
| **Comunicación UI↔Servicio** | StateFlow / SharedFlow + callbacks nativos | Eventos de estado y telemetría en tiempo real |
| **IA Facial** | ML Kit Face Detection (Android nativo) | Detección de somnolencia (EAR) + Reconocimiento facial |
| **Wearables** | Health Connect (Android nativo) | Lectura de datos de salud para fatiga en background |
| **Geofencing** | Geofencing API (Google Play Services) | Zonas de operación |
| **OBD II** | Bluetooth LE + ELM327 protocol | Datos mecánicos (RPM, temp, voltaje) |
| **Dashboard Web** | Firebase Hosting + React/Vue | Monitoreo y gestión |
| **Backend** | Firebase (Auth, Firestore, Functions) + AWS S3 | Datos, almacenamiento, serverless |
| **Llamadas Automáticas** | Twilio API | Verificación de colisiones |
| **IA/LLM** | Gemini API (opcional) | Consultas de mantenimiento |
| **Build** | Gradle + Android Studio | APK firmado localmente |

### Flujo de Datos General

```
📱 APP MÓVIL (Conductor)
├── Auto-Inicio (Activity Recognition)
├── Vigilante (Cámara trasera + Buffer)
├── Copiloto (Wearable + Cámara frontal)
├── Geofencing (GPS + Zonas)
├── OBD II (Datos mecánicos)
└── Colisión (Acelerómetro + Velocímetro)
         │
         ▼ (Firestore / Realtime DB)
┌────────────────────────────────────────────┐
│            ☁️ NUBE (Backend)               │
│  Firestore (Datos en vivo)                 │
│  Firebase Auth (Roles)                     │
│  Cloud Functions (Lógica)                  │
│  Twilio (Llamadas/SMS)                     │
│  AWS S3 (Videos solo si usuario autoriza)  │
└────────────────────────────────────────────┘
         │
         ▼ (WebSocket / Realtime)
🖥️ DASHBOARD WEB (Admin)
├── Mapa en vivo
├── Toggles de funcionalidades
├── Reportes de incidentes
├── Gestión de conductores
└── Alertas en tiempo real
```

### Flujo de datos local (cámaras + sensores)

```
┌─────────────────────────────────────────────────────────────┐
│                    CÁMARA TRASERA (Vigilante)               │
│ 1080p @ 30fps → Encoder H.264 (2 Mbps) → Buffer Circular    │
│                                                              │
│ Almacenamiento: Cache del sistema Android                    │
│ Duración: Últimos 30 segundos (2 segmentos de 15 seg)       │
│ Escritura: Solo cuando hay evento (trigger)                  │
│ Background: Sí, vía Foreground Service                       │
└─────────────────────────────────────────────────────────────┘
                            ↓
        ┌───────────────────┼───────────────────┐
        ↓                                       ↓
     BOTÓN PÁNICO                           ACELERÓMETRO
     (Manual)                               (G-Force)
        ↓                                       ↓
        └───────────────────┼───────────────────┘
                            ↓
                  ¿EVENTO DETECTADO?
                        Sí → Guardar 15 seg previos y 15 seg posteriores a Downloads
                        No → Descartar y continuar

┌─────────────────────────────────────────────────────────────┐
│       ANTI-SOMNOLENCIA (múltiples fuentes)                   │
├─────────────────────────────────────────────────────────────┤
│ Nivel 1: Cámara frontal (ML Kit EAR)                        │
│   • Activa solo en FatigueScreen                             │
│   • 640×480 @ 10fps, NO guarda video                         │
│   • Alerta inmediata (vibración + sonido)                    │
├─────────────────────────────────────────────────────────────┤
│ Nivel 2: Wearable vía Health Connect                        │
│   • FC, HRV, movimiento                                      │
│   • Funciona en background                                   │
│   • Menor consumo que cámara frontal siempre activa          │
├─────────────────────────────────────────────────────────────┤
│ Fallback: Si no hay wearable ni cámaras concurrentes         │
│   • Sugerir comprar wearable compatible                      │
│   • Mostrar lista de dispositivos recomendados               │
└─────────────────────────────────────────────────────────────┘
```

### Directorios de almacenamiento

```
Android File System:
├── context.cacheDir/
│   ├── segment_0.mp4 (descarta después)
│   └── segment_1.mp4 (descarta después)
│
├── Downloads/DuoVial/
│   └── incident_[timestamp]_part0.mp4 (guardado permanente)
│   └── incident_[timestamp]_part1.mp4 (guardado permanente)
│
└── SQLite local (offline):
    ├── Eventos de ubicación
    ├── Lecturas de acelerómetro
    └── Logs de viaje (sincronizan al recuperar señal)
```

---

## 🔑 DECISIONES TÉCNICAS CLAVE

### Decisión 1: ¿Por qué Buffer Circular y no grabación continua?

**Opción A: Grabación Continua**
- ❌ 6 MB/seg × 3600 seg = 21 GB/hora
- ❌ Escritura continua al disco = calor extremo
- ❌ Chip de almacenamiento se desgasta rápido
- ❌ Batería muere en ~2 horas

**Opción B: Buffer Circular (elegida)**
- ✅ ~2 MB/seg durante grabación (bitrate 2 Mbps)
- ✅ Cache del OS agrupa escrituras
- ✅ Chip de almacenamiento inactivo = frío
- ✅ Batería dura más horas
- ✅ Solo guarda lo importante

**Justificación**: Un conductor necesita 6-12 horas de grabación sin agotar batería. La grabación continua es técnicamente inviable.

### Decisión 2: ¿Acelerómetro, GPS o múltiples sensores?

**Decisión Final**: **Multi-sensor** (botón + acelerómetro configurable + colisión con filtro de velocidad)
**Implementación**: MVP con botón + acelerómetro. Colisión con filtro >40 km/h para Fleet/Premium.

### Decisión 3: ¿React Native / Expo vs. Kotlin Multiplatform?

**Plan original**: React Native con Expo SDK 54

**Lo que realmente se implementó**: Kotlin Multiplatform + Compose Multiplatform, acceso nativo directo en Kotlin

**Razones del cambio**:
- ✅ Control total sobre el ciclo de vida de la cámara
- ✅ PreviewView nativo sin depender de abstracciones de RN
- ✅ SensorManager nativo permite rate-limiting y filtrado personalizado
- ✅ LocationManager nativo desacoplado del ciclo de grabación
- ✅ Comunicación UI↔Nativo vía StateFlow / callbacks sin puentes frágiles
- ✅ Un solo lenguaje (Kotlin) para UI y lógica nativa

**Tradeoff**: Mayor superficie de código nativo, pero sin dependencias npm ni puentes RN.

### Decisión 4: ¿Camera2 manual vs. CameraX para cámara frontal?

**Opción A: Camera2 manual (implementación actual v1)**
- ✅ Control total de formato y timing
- ❌ Manejo manual de `Surface`, sesiones, threads
- ❌ Bug actual: preview se congela tras ~1 segundo
- ❌ Código propenso a race conditions

**Opción B: CameraX (elegida para refactorización)**
- ✅ Manejo automático de `Surface` y lifecycle
- ✅ `Preview` + `ImageAnalysis` simplifica ML Kit
- ✅ Mismo consumo de recursos para 640×480@10fps
- ✅ Mucho menos propenso a bugs de preview congelado

**Decisión Final**: Migrar a **CameraX** para cámara frontal.

### Decisión 5: ¿Cámara frontal siempre activa vs. solo en FatigueScreen + wearables?

**Opción A: Cámara frontal siempre activa**
- ❌ Alto consumo de batería
- ❌ Problemas de privacidad y políticas de Play Store
- ❌ Android restringe cámara en background

**Opción B: Cámara frontal solo en FatigueScreen + wearables para background (elegida)**
- ✅ Bajo consumo: cámara frontal solo cuando el usuario la ve
- ✅ Wearable puede detectar fatiga en background sin cámara
- ✅ Cumple mejor políticas de privacidad
- ✅ Funciona en dispositivos que no soportan cámaras concurrentes

**Decisión Final**: **Cámara frontal solo en FatigueScreen; wearables como fuente principal de fatiga en background.**

### Decisión 6: Arquitectura de estado — Servicio como fuente única de verdad

**Problema encontrado**: La UI y el servicio nativo pueden desincronizarse.

**Solución implementada**:
- `CameraStatusListener`: interface para comunicación UI↔Servicio
- `resyncJsState()`: re-emite estado actual + última telemetría conocida al reconectar UI
- `forceResetToStandby()`: red de seguridad para recuperar control
- `onRecordingFinalized()`: guard anti-race que verifica estado antes de iniciar nuevo segmento
- `stopAndSave` guarda el buffer y vuelve a STANDBY, NO mata el servicio

**Definición**: El servicio nativo es la única fuente de verdad del estado. La UI es un espejo que se sincroniza en cada acción y reconexión.

### Decisión 7: Wearables — Health Connect como estándar principal

**Opciones evaluadas**:
- Health Connect: estandarizado, background, Android 14+
- SDK de fabricante: fuerte acoplamiento a marca
- Bluetooth LE directo: máximo control, máxima complejidad

**Decisión Final**: **Health Connect** para MVP. Permite soportar múltiples marcas sin acoplamiento. Fallback a SDK de fabricante si Health Connect no está disponible.

---

## 📦 MÓDULOS TÉCNICOS Y FUNCIONALES

### 6.1 Modo Vigilante (Buffer Circular)

**Valor**: Evidencia en video de incidentes sin llenar almacenamiento ni agotar batería.

| Especificación | Detalle |
|---------------|---------|
| Tecnología | CameraX (Preview) nativo |
| Resolución | 1920×1080 (Full HD) @ 30fps |
| Codec | H.264 |
| Bitrate | 2 Mbps |
| Audio | DESACTIVADO |
| Buffer | Circular, 30 segundos (2 segmentos × 15 seg) |
| Almacenamiento | context.cacheDir |
| Limpieza | Automática (máximo 2 segmentos en cache) |
| Triggers | Botón Pánico + Acelerómetro (G-Force > 2.5G configurable) + Colisión |
| Background | Sí, vía Foreground Service + WorkManager Watchdog |

### 6.2 Geofencing (Perímetro de Operación)

**Valor**: Evita que el vehículo salga de la ciudad/zona autorizada sin permiso.

**Plan**: Exclusivo Fleet.

| Especificación | Detalle |
|---------------|---------|
| Tecnología | Geofencing API (Google Play Services) |
| Precisión | Radio mínimo: 500 metros (configurable) |
| Consumo | Bajo (usa Fused Location Provider con prioridad balanceada) |
| Acción al cruzar | Notificación push al Admin + Guardado de ubicación + Video de 5s (evidencia de quién manejaba) |
| Excepciones | Admin puede desactivar temporalmente desde Dashboard |

### 6.3 Reconocimiento Facial (Solo Alerta)

**Valor**: Notifica al dueño si un conductor no registrado usa el vehículo. Desactivado por defecto.

**Plan**: Exclusivo Fleet.

| Especificación | Detalle |
|---------------|---------|
| Tecnología | ML Kit Face Detection + Comparación de embeddings |
| Activación | Manual (toggle en Settings) |
| Frecuencia | 1 foto al iniciar viaje (>20 km/h) |
| Almacenamiento | Local (borrado en 24h) |
| Acción | Notificación push: "Rostro no registrado conduciendo" |
| NO hace | Bloqueos, apagados, ni multas |

### 6.4 Score de Conducción (Descartado MVP)

❌ **DESCARTADO PARA MVP** (Decisión tomada en reunión del 24/06/2026). Se evaluará en Fase 3 si hay demanda explícita de clientes Fleet.

### 6.5 Mantenimiento Predictivo

**Valor**: Alertas preventivas para evitar averías costosas.

**Plan**: Premium y Fleet.

| Especificación | Detalle |
|---------------|---------|
| Datos base | Kilometraje (GPS), tiempo de motor encendido |
| Reglas genéricas | Aceite (5,000 km), Filtros (10,000 km), Bujías (20,000 km), Pastillas de freno (30,000 km) |
| Modelo de IA | NO entrenamos modelo. Usamos Gemini/OpenAI para consultas específicas: "Dame intervalos de mantenimiento para Chevrolet Spark 2018 en JSON" |
| Datos OBD (Futuro) | Códigos de error (DTC) se traducen a español con base de datos pública NHTSA |
| Acción | Notificación al conductor y Admin con kilometraje restante |

### 6.6 Auto-Inicio por Actividad

**Valor**: El usuario nunca olvida activar la app. La app se activa sola al detectar conducción.

**Plan**: Disponible en todos los planes.

| Especificación | Detalle |
|---------------|---------|
| Tecnología | Google Activity Recognition API |
| Condición de activación | ACTIVITY_IN_VEHICLE + Velocidad > 15 km/h (durante 15 segundos) |
| Condición de desactivación | ACTIVITY_STILL + WALKING por 2 minutos continuos |
| Notificación al usuario | "🚗 DuoVial detectó que estás conduciendo. Modo Vigilante activado." (El usuario puede deslizar para detener) |
| Privacidad | Los datos de actividad no se guardan, solo se usan para trigger |

### 6.7 Dashboard Web (El Cerebro)

**Valor**: Visibilidad total de la flota en un solo lugar.

**Plan**: Exclusivo Fleet.

| Especificación | Detalle |
|---------------|---------|
| Tecnología | Firebase Hosting + React / Vue.js + Firestore (realtime) |
| Roles | Admin (dueño), Supervisor (gerente), Conductor (solo ve su propio score) |
| Vista principal | Mapa en tiempo real con ubicación de vehículos |
| Toggles por vehículo | Activar/Desactivar: Facial, Anti-somnolencia, OBD, Geofencing |
| Incidentes | Lista con filtros (por fecha, vehículo, tipo) + Reproducción de video (si está en la nube) |
| Exportación | Reportes en CSV/PDF (incidentes, kilometraje, alertas) |

### 6.8 Anti-somnolencia + Wearables (El Copiloto)

**Valor**: Prevención activa de accidentes por fatiga con escalada inteligente en 3 niveles.

**Plan**: Nivel básico (alerta) en Free/Por Evento. 3 niveles completos en Premium/Fleet.

#### Niveles de Actuación (Escalada Inteligente)

| Nivel | Detección | Acción | Componentes |
|-------|-----------|--------|-------------|
| 🟢 Nivel 1: "El Acompañante" (Prevención) | Wearable: FC elevada + HRV baja. Conducción > 2h. | (1) Sugerencia de ruta a gasolinera/descanso. (2) Sugerencia: bajar A/C 1.5°C, subir brillo. (3) Playlist energética (120+ BPM). | Health Connect + Google Maps API + MediaPlayer |
| 🟡 Nivel 2: "El Despertador" (Alerta) | ML Kit: EAR bajo + Wearable: sin movimiento. | (1) Alarma sonora fuerte + Vibración. (2) Pantalla roja parpadeante. (3) Voz: "¡CONDUCTOR, MANTÉN LOS OJOS ABIERTOS!". | CameraX (Frontal) + ML Kit + TTS |
| 🔴 Nivel 3: "El Ángel Guardián" (Emergencia) | No responde al Nivel 2 en 8s, o ojos cerrados > 3s. | (1) Notificación PUSH y SMS al contacto de emergencia con ubicación en Maps. (2) Reduce volumen de música, da instrucciones de voz para detenerse. (3) Muestra ruta al área de descanso más cercana. | Firebase Functions + Twilio SMS + Google Maps Directions API |

**Nota**: La cámara frontal SOLO se activa en Nivel 2 (FatigueScreen), y se apaga al salir de ese nivel. En background, la fuente primaria es el wearable (Health Connect).

#### Explicación de Anti-somnolencia por Plan

| Escenario | Free / Por Evento | Premium / Fleet |
|-----------|-------------------|-----------------|
| ¿Qué detecta? | Wearable (FC/HRV) + Cámara frontal (parpadeo). | Lo mismo + tiempo de conducción. |
| ¿Qué hace? | Suena alarma en el teléfono, vibra y (si tiene wearable), vibra en la muñeca. Fin de la acción. | Nivel 1: Sugiere en voz alta una gasolinera, cambia la playlist a ritmo rápido, sugiere bajar el A/C. Nivel 2: Alarma fuerte + pantalla roja. Nivel 3: Si no responde, envía SMS/notificación a su contacto de emergencia con su ubicación en Google Maps. |
| ¿Dónde se ven métricas? | Solo la alerta en pantalla. | En la app (Premium) se ve historial de episodios de fatiga. En Fleet, el Admin ve el historial de todos los conductores. |

#### Especificaciones técnicas de la cámara frontal

| Especificación | Detalle |
|---------------|---------|
| API | CameraX (Preview + ImageAnalysis) — post-refactorización |
| Resolución | 640 × 480 (VGA) |
| FPS | 10 fps |
| Frame Processor | ML Kit Face Detection |
| Almacenamiento | NINGUNO (solo analiza en tiempo real) |
| Cálculo | Eye Aspect Ratio (EAR) en tiempo real |
| Trigger alerta | EAR < umbral × duración configurable |
| Alcance | Solo cuando FatigueScreen está visible |

#### Selección del conductor en cámara frontal

- ML Kit retorna bounding boxes para cada rostro
- Cámara frontal espeja la imagen
- Selección: rostro con mayor centerX (conductor a la izquierda en preview)
- Edge cases: 1 persona → usar esa; 0 personas → no alertar

#### Umbrales configurables

- Closed eye threshold: 0.1–0.4 (default 0.2)
- Duration: 1–5 segundos (default 2s)
- Snooze: configurable (default 5 min)
- Allowed per hour: 1–5 alertas (default 3)

#### Acción al detectar (Nivel 2)

- Vibración: Patrón [0, 500, 200, 500] ms
- Sonido: Alarma de 2 segundos
- Visual: Overlay rojo en FatigueScreen
- Evento UI: actualización de FaceStatus

#### Consumo estimado (cámara frontal)

- CPU: 4-6% (ML Kit)
- Almacenamiento: 0 bytes
- Batería: ~4-5% por hora (solo con pantalla visible)
- Temperatura: +2-3°C

#### Validación de capacidad del dispositivo

Al iniciar la app o entrar a FatigueScreen:

1. Consultar `CameraManager.getConcurrentCameraIds()`
2. Verificar si el par (trasera, frontal) puede abrirse simultáneamente
3. Si SÍ: modo Vigilante puede seguir activo mientras se usa cámara frontal
4. Si NO:
   - Mostrar explicación al usuario
   - Pausar modo Vigilante al entrar a FatigueScreen
   - Sugerir usar un wearable para detección en background

### 6.9 Modo Offline + Gestión de Almacenamiento

**Valor**: Funciona sin internet y no llena el celular.

**Plan**: Disponible en todos los planes.

| Especificación | Detalle |
|---------------|---------|
| Offline (GPS/Logs) | Si no hay internet, los eventos, ubicaciones y acelerómetros se guardan en SQLite local. Al recuperar señal, sincronizan solos con el Dashboard. |
| Videos (NUNCA suben solos) | Los videos son privados. Solo se suben si: (1) El usuario manualmente toca "Subir" o "Compartir". (2) Ocurre una Colisión Nivel 3 (ver Módulo 6.11). |
| Limpieza automática | Los eventos guardados viven 7 días en el celular. Pasado ese tiempo, la app notifica: "Tienes X videos sin respaldo. Se eliminarán en 3 días." |
| Buffer en cache | El buffer circular (30s) se borra automáticamente al salir del modo Vigilante. |

### 6.10 Integración OBD II (ELM327)

**Valor**: Datos reales del motor para mantenimiento y diagnóstico.

**Plan**: Premium y Fleet.

| Especificación | Detalle |
|---------------|---------|
| Hardware | Dongle ELM327 Bluetooth (costo $10–$15 USD en Mercado Libre) |
| Protocolo | OBD-II estándar (todos los carros >2000) |
| Lecturas | RPM, Temperatura refrigerante, Voltaje batería, Códigos de error (DTC) |
| Instalación | Servicio "Instalación y Sincronización Asistida" por $9.99 USD (único pago). Incluye videollamada de 15 min y configuración remota. |
| Kit de Flota | 5 dongles + configuración por $79.99 (descuento). |

**Flujo de instalación**:
1. La empresa le vende el aparato al conductor
2. Lo conecta al puerto OBD (debajo del volante)
3. Abre la app → Buscar dispositivos Bluetooth → Emparejar (PIN: 0000)
4. La app lee datos en tiempo real y los envía al Dashboard

### 6.11 Colisión + Llamada Automática

**Valor**: Respuesta inmediata en accidentes graves. Filtro de velocidad para evitar falsos positivos.

**Plan**: Premium y Fleet.

| Especificación | Detalle |
|---------------|---------|
| Condición de activación | (G-Force > 3.5G) AND (Velocidad GPS > 40 km/h) AND (Duración impacto > 150ms) |
| Acción 1 (Subida de video) | El video del buffer (15s antes + 15s después) se comprime y sube automáticamente a AWS S3 (solo este caso excepcional). |
| Acción 2 (Llamada de verificación) | La app envía señal a Cloud Function. Twilio llama al usuario. |

**Flujo de llamada (IVR)**:
1. "DuoVial detectó un impacto. Presiona 1 si estás bien. Presiona 2 para ambulancia."
2. Si presiona 1 → Notificación al Admin: "Conductor ileso".
3. Si presiona 2 → Alerta prioritaria a emergencias con ubicación exacta.
4. Si no responde (30s) → Se llama al contacto de emergencia con ubicación en tiempo real.

---

## 🚨 SISTEMA DE DETECCIÓN DE EVENTOS

### Trigger 1: Botón de Pánico / Evento (CRÍTICO)
```
Prioridad: ⭐⭐⭐ IMPLEMENTADO
Cobertura: ~85% (el usuario VE el peligro primero)
Implementación: Botón "Evento" en MonitorScreen
Acción: saveEvent() guarda segmento pre + 15s post-evento
Cooldown: 12 segundos entre eventos (anti-spam)
```

### Trigger 2: Acelerómetro G-Force (ALTA)
```
Prioridad: ⭐⭐⭐ IMPLEMENTADO
Cobertura: Detecta impactos con fuerza calculada desde acelerómetro nativo
Implementación: SensorManager nativo → accelListener → magnitud/gravedad
Umbral: 2.5G (configurable entre 1.5–5.0G desde UI)
Rate-limit: Emisión a UI cada 200ms
Nota: Pruebas reales mostraron ~1.3G en frenadas bruscas; umbral 2.5G es conservador
```

### Trigger 3: Colisión con Filtro de Velocidad (NUEVO — Premium/Fleet)
```
Prioridad: ⭐⭐⭐ EN PLANEACIÓN
Cobertura: Accidentes graves a velocidad de carretera
Implementación: Acelerómetro G-Force + Velocidad GPS
Condición: (G-Force > 3.5G) AND (Velocidad > 40 km/h) AND (Duración > 150ms)
Acción: Subida automática de video a AWS S3 + Llamada Twilio de verificación
Filtro: Requiere >40 km/h para evitar falsos positivos en ciudad/estacionamiento
```

### Trigger 4: Giroscopio (NO IMPLEMENTADO)
```
Prioridad: ⭐ Postergado
Razón: El acelerómetro con umbral configurable cubre los casos necesarios para MVP
Futuro: Evaluar si complementa o se omite definitivamente
```

### Trigger 5: Detección de Audio (NO IMPLEMENTADO)
```
Prioridad: ⭐ Postergado
Razón: Acelerómetro + botón cubren ~85% de casos en pruebas MVP
Futuro: Evaluar tras validación de MVP
```

### Trigger 6: Geofencing (NUEVO — Fleet)
```
Prioridad: ⭐⭐⭐ EN PLANEACIÓN
Cobertura: Cruce de zonas de operación
Implementación: Geofencing API + GPS
Acción: Notificación push al Admin + Guardado de ubicación + Video de 5s
```

---

## 👥 FLUJOS DE USUARIO

### Flujo del Conductor (App)

1. **Auto-Inicio**: Sube al auto, arranca. La app se activa sola (notificación).
2. **Conducción**: La app corre en background (modo Vigilante + Wearable).
3. **Evento**: Si frena brusco o choca, guarda video.
4. **Llegada**: Apaga el auto. La app detecta STILL por 2 min y se auto-detiene.

### Flujo del Administrador (Dashboard Web)

1. **Login**: Ingresa con correo/contraseña.
2. **Dashboard**: Ve mapa en vivo con todos los vehículos.
3. **Gestión**: Da de alta conductores (sube foto para facial), define zonas de operación.
4. **Configuración**: Activa/desactiva funcionalidades por vehículo (Toggles).
5. **Reportes**: Filtra incidentes por fecha, exporta a Excel.
6. **Alertas**: Recibe notificaciones de zona, rostro no autorizado o colisión.

---

## 📋 TABLA DE FUNCIONALIDADES COMERCIALES

> **Nota**: Items excluidos de la tabla (por ser infraestructura/base): Detección acelerómetro, Botón pánico, Velocímetro, Foreground Service/Watchdog, Gestión suscripciones, Roles, Exportación de reportes, Notificaciones push, Wearables (Health Connect) como lectura base, Cámara frontal solo en FatigueScreen. Estos existen en todas las versiones para que la app funcione.

| # | Funcionalidad | 🆓 Free | 💰 Por Evento | ⭐ Premium | 🏢 Fleet (Empresas) |
|---|--------------|---------|---------------|-----------|---------------------|
| 1 | Modo Vigilante (Buffer Circular) | ✅ Sí (buffer 30s) | ✅ Sí (buffer 30s) | ✅ Sí (buffer 30s) | ✅ Sí (buffer 30s) |
| 2 | Auto-inicio por actividad | ✅ Sí | ✅ Sí | ✅ Sí | ✅ Sí |
| 3 | Guardado de videos (cuando hay evento) | ✅ Sí (guarda localmente) | ✅ Sí (guarda localmente) | ✅ Sí (guarda localmente) | ✅ Sí (guarda localmente) |
| 4 | Procesamiento de videos (unir, exportar, descargar) | ❌ No | ✅ Sí (pago por evento). Pagas solo cuando quieres procesar y descargar el video de un incidente específico. | ✅ Ilimitado | ✅ Ilimitado |
| 5 | Anti-somnolencia | ✅ Sí (Modo Básico). Solo alerta sonora y vibración en el móvil y el wearable. NO hay acciones preventivas ni escalada a contactos. | ✅ Sí (Modo Básico). Misma alerta que Free. | ✅ Sí (Modo Avanzado). Tiene los 3 niveles: "Acompañante", "Despertador" y "Ángel Guardián" (notifica a contacto de emergencia). | ✅ Sí (Modo Avanzado + Dashboard). Mismo que Premium, pero el Admin ve métricas de fatiga de todos los conductores y configura umbrales globales. |
| 6 | Geofencing (Perímetro de Operación) | ❌ No | ❌ No | ❌ No | ✅ Sí. Definir zonas, alertas al Admin, video de 5s al cruce. |
| 7 | Reconocimiento Facial | ❌ No | ❌ No | ❌ No | ✅ Sí. (Desactivado por defecto). Notifica al Admin si conductor no registrado. |
| 8 | Mantenimiento Predictivo | ❌ No | ❌ No | ✅ Sí. Alertas de kilometraje dentro de la app. | ✅ Sí. Admin ve estado de mantenimiento de toda la flota en Dashboard. |
| 9 | Integración OBD II | ❌ No | ❌ No | ✅ Sí. (Requiere dongle ELM327 comprado por separado). | ✅ Sí. Datos mecánicos de todos los vehículos agregados al Dashboard. |
| 10 | Servicio de instalación OBD Asistida | ❌ No | ❌ No | 💰 Pago único ($9.99 USD) | 💰 Pago único ($9.99 USD) o incluido en contrato anual. |
| 11 | Detección de Colisión + Llamada automática (Twilio) | ❌ No | ❌ No | ✅ Sí. Filtro >40km/h, sube video automáticamente, llama al usuario, notifica a contacto de emergencia. | ✅ Sí. Mismo que Premium + Admin recibe alerta en tiempo real en Dashboard. |
| 12 | Dashboard Web (Administración) | ❌ No | ❌ No | ❌ No | ✅ Sí. Mapa en vivo, gestión de vehículos/conductores, toggles on/off por vehículo, exportación de reportes (CSV), historial de incidentes. |
| 13 | Modo Offline (GPS/logs sin internet) | ✅ Sí | ✅ Sí | ✅ Sí | ✅ Sí |
| 14 | Limpieza automática de videos (7 días) | ✅ Sí | ✅ Sí | ✅ Sí | ✅ Sí |

---

## 💾 CONSUMO DE RECURSOS Y OPTIMIZACIONES

### Benchmark: 1 hora de operación

#### Grabación Continua (apps tradicionales)
| Componente | Consumo | Notas |
|------------|---------|-------|
| Cámara + ISP | 750 mW | Sensor + procesamiento |
| Encoder H.264 | 800 mW | Compresión 6 Mbps |
| Disco (escritura) | 500 mW | **PROBLEMA PRINCIPAL** |
| Overhead | 250 mW | Android + otras apps |
| **TOTAL** | **2,300 mW** | |
| **Batería/hora** | **15-18%** | 4000 mAh |
| **Temperatura** | **+8-12°C** | Sobrecalentamiento |
| **Datos escritos** | **21.6 GB** | Llena almacenamiento |

#### Buffer Circular (nuestra app - OPTIMIZADO)
| Componente | Consumo | Notas |
|------------|---------|-------|
| Cámara + ISP | 750 mW | Sensor + procesamiento |
| Encoder H.264 (2 Mbps) | 600 mW | Bitrate reducido |
| Cache (OS optimizado) | 50 mW | Standby cuando no hay evento |
| Overhead | 250 mW | Android + otras apps |
| **TOTAL** | **1,650 mW** | |
| **Batería/hora** | **10-12%** | 4000 mAh |
| **Temperatura** | **+4-6°C** | Normal |
| **Datos escritos** | **~2.8 GB** | Solo si hay incidentes |

#### Anti-Somnolencia (cámara frontal solo en FatigueScreen)
| Componente | Consumo | Notas |
|------------|---------|-------|
| Cámara frontal + ISP | 350 mW | 640×480 @ 10fps |
| ML Kit Face Detection | 250 mW | GPU/NPU si está disponible |
| Overhead | 100 mW | Threads + callbacks |
| **TOTAL** | **~700 mW** | |
| **Batería/hora** | **~4-5%** | Solo mientras FatigueScreen visible |
| **Almacenamiento** | **0 bytes** | No guarda video |

#### Anti-Somnolencia (wearable vía Health Connect)
| Componente | Consumo | Notas |
|------------|---------|-------|
| Bluetooth LE | 30-50 mW | Conexión al wearable |
| Health Connect queries | 20 mW | Polling periódico |
| Overhead | 50 mW | Lógica de detección |
| **TOTAL** | **~100-120 mW** | |
| **Batería/hora** | **~1-2%** | Mucho menor que cámara frontal |

### Optimizaciones aplicadas

1. **Bitrate 2 Mbps** para cámara trasera: 66% menos datos que 6 Mbps.
2. **Audio desactivado** en grabación: ahorro de CPU y I/O.
3. **Cache del OS** para buffer circular: reduce escrituras físicas.
4. **Cámara frontal 640×480 @ 10fps**: suficiente para parpadeo, mínimo consumo.
5. **Cámara frontal solo en FatigueScreen**: no consume batería en background.
6. **Wearable como fuente background**: evita mantener cámara frontal siempre activa.

---

## 👥 SEGMENTACIÓN DE USUARIOS

### Segmento 1: Conductores individuales (BASE)
**Tamaño**: 2-3 millones en Latinoamérica
**Perfil**: Uber, taxi, reparto, particulares
**Pain Point**: "Necesito evidencia para disputas con pasajeros/seguros"
**Willingness to Pay**: 💰💰💰 Alta ($5-10/mes)
**Planes**: Free → Por Evento → Premium

### Segmento 2: Empresas de flota/reparto (FLEET — FOCO DE REVENUE)
**Tamaño**: 50,000+ empresas en Latinoamérica
**Perfil**: Dueños de flotas de 5-200+ vehículos, empresas de logística, mensajería, transporte
**Pain Point**: "Necesito monitorear todos mis vehículos y conductores desde un solo lugar"
**Willingness to Pay**: 💰💰💰💰 Muy alta ($19.99+/mes por vehículo)
**Plan**: Fleet

### Segmento 3: Padres con hijos conductores jóvenes (SECUNDARIO)
**Tamaño**: 1-2 millones en Latinoamérica
**Pain Point**: "Mi hijo se duerme manejando, quiero saber"
**Willingness to Pay**: 💰💰 Media ($3-5/mes)
**Planes**: Free → Premium

---

## 📈 ESTRATEGIA GO-TO-MARKET

### Fase 1: Lanzamiento B2C (validación)
- Play Store listing optimizado para "dash cam Android"
- Comunidades de Uber/Taxi en WhatsApp y Facebook
- Incentivo: 30 días de Premium gratis al instalar
- Meta: 500 instalaciones, validar retención y consumo de batería

### Fase 2: Expansión B2B (Fleet)
- Contacto directo a empresas de flota pequeñas/medianas (5-50 vehículos)
- Demo guiada del Dashboard web + 2 semanas gratis
- Partners de instalación OBD (Mercado Libre + talleres locales)
- Meta: 10 empresas piloto, MRR de $2,000+

### Fase 3: Escalamiento
- Integraciones con sistemas de gestión de flotas existentes
- Canal de revendedores (talleres, distribuidores de OBD)
- Publicidad en LinkedIn/Google Ads orientada a dueños de flota
- Meta: 100+ empresas, MRR de $20,000+

---

## ⚠️ RIESGOS Y MITIGACIONES

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|--------|-----------|
| **Daño OIS por vibración** | Media | Alto (legal) | Disclaimer claro en onboarding |
| **Sobrecalentamiento** | Baja | Alto | Tutorial de optimización de batería, reducir bitrate |
| **App muere en background** | Alta | Alto | Foreground Service + WorkManager Watchdog |
| **Preview frontal congelado** | Media | Alto | Migrar a CameraX, manejo robusto de Surface |
| **Dispositivo no soporta cámaras concurrentes** | Media | Medio | Detectar en runtime, pausar Vigilante, sugerir wearable |
| **Wearable no sincroniza con Health Connect** | Media | Medio | Mantener lista actualizada de dispositivos compatibles |
| **Privacidad (cámara frontal)** | Baja | Medio | Solo activa en FatigueScreen, disclaimer "solo local" |
| **iOS no soportado** | Cierto | Medio | Comunicar claramente "Android only" |
| **Compatibilidad en celus viejos** | Media | Medio | Detectar hardware en init, ofrecer modo reducido |
| **Falsas reclamaciones legales** | Baja | Muy alto | Disclaimer: "No usamos para prueba legal válida" |
| **Competencia (dash cams tradicionales)** | Alta | Medio | Diferenciador: bajo consumo + anti-somnolencia + wearables + Fleet |
| **Falsos positivos de colisión** | Media | Alto | Filtro >40km/h + confirmación por llamada Twilio |
| **Costo de Twilio por llamada** | Baja | Medio | Solo en colisiones graves; incluido en precio Fleet |
| **Resistencia de conductores a ser monitoreados** | Media | Alto | Facial off por defecto; enfoque en seguridad, no castigo |
| **Dependencia de Firebase/Google** | Baja | Medio | Arquitectura permite migrar backend si es necesario |

---

## 🔧 ESPECIFICACIONES TÉCNICAS DETALLADAS

### Cámara Trasera (Modo Vigilante)

```
CONFIGURACIÓN:
├── Resolución: 1920 × 1080 (Full HD)
├── FPS: 30 fps
├── Codec: H.264
├── Bitrate: 2 Mbps
├── Audio: DESACTIVADO
├── Buffer: Circular, últimos 30 segundos
├── Almacenamiento: context.cacheDir
├── Limpieza: Automática (máximo 2 segmentos en cache)
└── Trigger guardar: Botón + Acelerómetro + Colisión

SEGMENTACIÓN:
├── Segmento 0: 15 segundos
├── Segmento 1: 15 segundos
└── Al detectar evento:
    ├── Segmentos se copian a Downloads/DuoVial
    ├── Nombrados: incident_[timestamp]_part0.mp4 y part1.mp4
    └── Post-evento: 15 segundos adicionales

CONSUMO ESTIMADO:
├── CPU: 8-10%
├── Almacenamiento write: ~2 MB/seg
├── Batería: ~6% por hora
└── Temperatura: +4-6°C
```

### Cámara Frontal (Anti-Somnolencia)

```
CONFIGURACIÓN (post-refactorización CameraX):
├── API: CameraX (Preview + ImageAnalysis)
├── Resolución: 640 × 480 (VGA)
├── FPS: 10 fps
├── Frame Processor: ML Kit Face Detection
├── Almacenamiento: NINGUNO
├── Cálculo: Eye Aspect Ratio (EAR) en tiempo real
├── Trigger alerta: EAR < umbral × duración configurable
└── Alcance: Solo cuando FatigueScreen está visible

SELECCIÓN DEL CONDUCTOR:
├── ML Kit retorna bounding boxes para cada rostro
├── Cámara frontal espeja la imagen
├── Selección: rostro con mayor centerX (conductor a la izquierda en preview)
└── Edge cases: 1 persona → usar esa; 0 personas → no alertar

UMBRALES CONFIGURABLES:
├── Closed eye threshold: 0.1–0.4 (default 0.2)
├── Duration: 1–5 segundos (default 2s)
├── Snooze: configurable (default 5 min)
└── Allowed per hour: 1–5 alertas (default 3)

ACCIÓN AL DETECTAR:
├── Vibración: Patrón [0, 500, 200, 500] ms
├── Sonido: Alarma de 2 segundos
├── Visual: Overlay rojo en FatigueScreen
└── Evento UI: actualización de FaceStatus

CONSUMO ESTIMADO:
├── CPU: 4-6% (ML Kit)
├── Almacenamiento: 0 bytes
├── Batería: ~4-5% por hora (solo con pantalla visible)
└── Temperatura: +2-3°C
```

### Wearables (Health Connect)

```
CONFIGURACIÓN:
├── API: Health Connect (Android 14+)
├── Datos leídos:
│   ├── HeartRate (frecuencia cardíaca)
│   ├── HeartRateVariability (HRV si disponible)
│   ├── SleepSession (calidad de sueño previo)
│   └── Steps / ActiveCalories (actividad reciente)
├── Frecuencia de lectura: Cada 1-5 minutos
├── Almacenamiento: Solo en memoria, no se guarda
└── Privacidad: Datos solo local, no se envían a servidores

LÓGICA DE DETECCIÓN:
├── Anomalías en FC sostenida (bradicardia/extrema)
├── Caída de HRV
├── Falta de movimiento prolongado
└── Fusión opcional con datos de cámara frontal cuando está activa
```

### Persistencia en Background

```
FOREGROUND SERVICE:
├── Tipo: camera | location | microphone
├── Notificación persistente
├── START_STICKY
└── WorkManager Watchdog cada 15 minutos

PERMISOS REQUERIDOS:
├── CAMERA
├── ACCESS_FINE_LOCATION / COARSE
├── FOREGROUND_SERVICE + CAMERA + LOCATION + MICROPHONE
├── POST_NOTIFICATIONS
├── SYSTEM_ALERT_WINDOW (burbuja flotante)
├── RECORD_AUDIO
├── VIBRATE
├── WAKE_LOCK
├── HEALTH_CONNECT (wearables)
└── ACTIVITY_RECOGNITION (auto-inicio)
```

### Arquitectura de Estado y Recuperación

```
MÁQUINA DE ESTADOS (ServiceState):
┌──────────┬──────────────┬──────────────────────────────────────┐
│  STANDBY │ → RECORDING  │ startRecordingMode()                 │
│          │              │ → Preview + GPS vivos; no graba       │
├──────────┼──────────────┼──────────────────────────────────────┤
│ RECORDING│ → STANDBY    │ forceResetToStandby() / stopAndSave  │
│          │ → SAVING     │ saveEvent()                          │
│          │              │ → Buffer circular rotando            │
├──────────┼──────────────┼──────────────────────────────────────┤
│  SAVING  │ → STANDBY    │ forceResetToStandby()                │
│          │ → RECORDING  │ handleEventSaveTransition            │
└──────────┴──────────────┴──────────────────────────────────────┘

COMUNICACIÓN UI → SERVICIO:
├── startStandby()
├── startRecording()
├── stopRecording()
├── triggerPanic()
├── forceReset()
├── setGForceThreshold()
├── enableFatigueDetection()
├── setEarThreshold()
├── setDurationThreshold()
├── setMaxAlertsPerHour()
└── snoozeFatigueAlert()

COMUNICACIÓN SERVICIO → UI:
├── onCameraStatusChanged
├── onAccelChanged
├── onSpeedChanged
├── onFaceStatusChanged
└── onDrowsinessDetected
```

### Componentes de la UI Actual

```
DuoVialApp.kt (contenedor principal):
├── Scaffold con bottom nav
├── Tabs: Monitor, Eventos, Configurar, Cuenta
└── FatigueScreen (pantalla superpuesta, sin bottom nav)

MonitorScreen.kt:
├── CameraPreview (fondo fullscreen)
├── Header con logo, estado, botón a FatigueScreen
├── Telemetría: G-Force, MPH/KPH
└── Botones: Start/Stop, Evento

FatigueScreen.kt:
├── FrontCameraPreview (fondo)
├── Status pill (Buscando rostro / Alerta / Alerta)
├── Barra de EAR
├── Botón Activar/Detener
├── Botón Ajustes
└── Panel de configuración (sliders)
```

---

## 📊 MÉTRICAS DE ÉXITO

### MVP (Primeras 12 semanas)

| Métrica | Target | Justificación |
|---------|--------|---------------|
| Instalaciones | 500+ | Validar product-market fit |
| MAU | 200+ | 40% de instalaciones activas |
| Conversion a pago | 5%+ | 1 de cada 20 usuarios paga |
| MRR | $500+ | Base inicial recurrente |
| Churn | <10% | Aceptable para consumidor |
| Rating Play Store | 4.0+ | Competitivo |
| Time to Guard | <5 min | Tiempo hasta grabando |
| Battery impact (Vigilante) | <12%/hour | Core metric |
| Preview frontal estable | 0 congelamientos | Crítico para anti-somnolencia |
| Usuarios con wearable | 10%+ | Valida integración Health Connect |

### Fleet (Primeras 12 semanas post-lanzamiento B2B)

| Métrica | Target | Justificación |
|---------|--------|---------------|
| Empresas piloto | 10+ | Validar B2B product-market fit |
| Vehículos activos en flota | 50+ | Tracción inicial empresarial |
| MRR Fleet | $2,000+ | Base recurrente empresarial |
| Churn empresarial | <5% | Más bajo que B2C |
| Satisfacción Admin (NPS) | 40+ | Dashboard útil y funcional |
| Alertas de colisión procesadas | <5% falsos positivos | Precisión del filtro >40km/h |
| Tiempo de onboarding Fleet | <2 días | Desde contrato hasta flota activa |

### Lo que necesita más investigación
⚠️ Falsos positivos de acelerómetro en baches/topes
⚠️ Compatibilidad de Health Connect por marca de wearable
⚠️ Dispositivos que no soportan cámaras concurrentes
⚠️ Estabilidad del servicio en sesiones largas (>2h)
⚠️ Calibración de umbrales de fatiga por wearable
⚠️ Precisión del filtro de colisión (>40km/h + 3.5G)
⚠️ Costo de Twilio por llamada a escala
⚠️ Latencia de Firestore en zonas con mala conexión

---

### Decisiones tomadas para MVP
1. **Kotlin Multiplatform + Compose**: un solo lenguaje, máximo control nativo.
2. **Buffer circular con cache del OS**: balance entre rendimiento y complejidad.
3. **Cámara frontal solo en FatigueScreen**: ahorro de batería y privacidad.
4. **CameraX para cámara frontal**: robustez ante bugs de Surface.
5. **Health Connect para wearables**: estándar abierto, sin acoplamiento a marca.
6. **Servicio como fuente única de verdad**: evita desincronización UI/servicio.
7. **Sugerencia de wearables compatibles**: mejora UX cuando el hardware es limitado.
8. **Firebase como backend principal**: serverless, realtime, bajo costo inicial.
9. **Facial solo alerta, nunca bloqueo**: cumple regulaciones y expectativas del usuario.
10. **Pivot a B2B con Fleet**: mayor MRR por cliente, menor churn, diferenciación clara.

### Próximas validaciones
- [ ] Testing preview frontal en 5+ dispositivos
- [ ] Testing de Health Connect con al menos 3 wearables
- [ ] Calibración de umbrales de fatiga
- [ ] Testing de cámaras concurrentes en gama baja/media/alta
- [ ] NPS de primeros 50 usuarios
- [ ] Análisis de false positive rate por semana
- [ ] Prueba piloto de Dashboard web con 3 empresas
- [ ] Prueba de integración OBD II con 5 dongles ELM327 diferentes
- [ ] Prueba de flujo Twilio (llamada IVR) en simulación de colisión

---

## ⌚ WEARABLES RECOMENDADOS (MVP vía Health Connect)

> **Nota**: Health Connect requiere Android 14+ y que el wearable exponga datos a la app de Health Connect o Google Fit. La compatibilidad exacta varía por modelo y región.

| # | Dispositivo | Tipo | Por qué recomendarlo | Buscar en Mercado Libre |
|---|-------------|------|---------------------|-------------------------|
| 1 | **Samsung Galaxy Watch7 / Watch6** | Smartwatch | Health Connect nativo, buena batería, HRV y FC | [Buscar Galaxy Watch](https://listado.mercadolibre.com.co/samsung-galaxy-watch) |
| 2 | **Samsung Galaxy Watch FE** | Smartwatch | Accesible, mismo ecosistema salud | [Buscar Galaxy Watch FE](https://listado.mercadolibre.com.co/samsung-galaxy-watch-fe) |
| 3 | **Xiaomi Smart Band 9 / 8** | Pulsera | Económica, buena batería, compatible con Google Fit | [Buscar Xiaomi Smart Band](https://listado.mercadolibre.com.co/xiaomi-smart-band) |
| 4 | **Xiaomi Redmi Watch 5 / 4** | Reloj ligero | Barato, pantalla grande, FC continua | [Buscar Redmi Watch](https://listado.mercadolibre.com.co/xiaomi-redmi-watch) |
| 5 | **Amazfit Bip 6 / Bip 5** | Reloj ligero | Excelente batería, sincroniza con Zepp Life → Google Fit | [Buscar Amazfit Bip](https://listado.mercadolibre.com.co/amazfit-bip) |
| 6 | **Amazfit GTS 4 Mini / GTR Mini** | Smartwatch | Compacto, buena autonomía, datos de sueño/FC | [Buscar Amazfit GTS](https://listado.mercadolibre.com.co/amazfit-gts) |
| 7 | **Huawei Band 9 / Band 8** | Pulsera | Económica, buena batería (requiere Huawei Health → Health Connect) | [Buscar Huawei Band](https://listado.mercadolibre.com.co/huawei-band) |
| 8 | **Fitbit Charge 6 / Inspire 3** | Pulsera | Muy popular, buena app salud, FC/HRV | [Buscar Fitbit Charge](https://listado.mercadolibre.com.co/fitbit-charge) |
| 9 | **Garmin Vivosmart 5 / Venu Sq 2** | Pulsera/Reloj | Muy usado por conductores, batería larga | [Buscar Garmin Vivosmart](https://listado.mercadolibre.com.co/garmin-vivosmart) |
| 10 | **Google Pixel Watch 3 / 2** | Smartwatch | Health Connect nativo, integración total Android | [Buscar Pixel Watch](https://listado.mercadolibre.com.co/google-pixel-watch) |
| 11 | **OnePlus Watch 2** | Smartwatch | Wear OS, Health Connect directo, buena batería | [Buscar OnePlus Watch](https://listado.mercadolibre.com.co/oneplus-watch) |

> **Importante**: Los links son búsquedas en Mercado Libre Colombia. Antes de comprar, verificar que el modelo específico soporte sincronización con Google Fit o Health Connect en tu país.

---

## ❓ FAQ TÉCNICO

### 1. ¿Por qué la app no funciona en iPhone?
iOS bloquea el uso de cámara en background por política de privacidad. No hay forma técnica de hacer dash cam en segundo plano en iPhone sin jailbreak.

### 2. ¿La app graba audio?
No. El audio está desactivado en la grabación de video del modo Vigilante. Esto ahorra batería, reduce el tamaño de archivos, y simplifica el cumplimiento legal (leyes de consentimiento de grabación varían por país).

### 3. ¿Qué tan preciso es el reconocimiento facial?
ML Kit Face Detection es altamente preciso para detectar si hay un rostro diferente al registrado. Sin embargo, solo es una alerta — no bloquea el vehículo ni toma acciones automáticas.

### 4. ¿Cuánto consume de datos móviles?
Casi nada en condiciones normales. Los videos NUNCA se suben automáticamente (excepto colisión grave). Los datos de ubicación y telemetría son texto ligero (~1-5 KB por actualización). Si el usuario decide subir un video manualmente, un clip de 30 segundos pesa ~7.5 MB (2 Mbps × 30s).

### 5. ¿Qué pasa si el teléfono se apaga durante un viaje?
Los segmentos en cache se pierden (estaban en RAM/cache del OS). Pero el servicio se reinicia automáticamente al encender el teléfono gracias al WorkManager Watchdog.

### 6. ¿Cómo manejan el sobrecalentamiento?
Monitoreamos temperatura del dispositivo. Si excede umbral seguro, entramos en modo reducido (bajar bitrate a 1 Mbps, reducir FPS a 20, o pausar grabación temporalmente) hasta que la temperatura se normalice.

### 7. ¿Qué dongles OBD II son compatibles?
Cualquier dongle ELM327 Bluetooth estándar funciona (costo $10-$15 USD). Recomendamos versiones con chip PIC18F25K80 por su estabilidad. Evitar versiones "mini" azules de $5 que suelen ser clones inestables.

---

**Documento versión**: 2.0
**Última actualización**: Junio 25, 2026
**Siguiente review**: Julio 2026

**Contactar**: [Oscar's info aquí]
**Repositorio**: [GitHub link aquí]

---

*Este documento es la fuente única de verdad para especificaciones del proyecto DuoVial y DuoVial Fleet. Cualquier desviación debe ser documentada aquí y comunicada a todos los agentes/desarrolladores.*
