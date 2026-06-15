# 📋 CONTEXT.md - DASH CAM INTELIGENTE PARA CONDUCTORES

**Última actualización**: Junio 15, 2026  
**Estado del proyecto**: MVP en desarrollo — Fases 1-3 completadas, Fase 3B (Anti-somnolencia refactorizada) en progreso, Fase 4 pendiente  
**Audiencia**: Agentes de IA, desarrolladores, stakeholders técnicos

---

## 📌 TABLA DE CONTENIDOS

1. [Visión General del Proyecto](#visión-general-del-proyecto)
2. [Problema Identificado](#problema-identificado)
3. [Solución Propuesta](#solución-propuesta)
4. [Arquitectura Técnica](#arquitectura-técnica)
5. [Fases de Desarrollo](#fases-de-desarrollo)
6. [Decisiones Técnicas Clave](#decisiones-técnicas-clave)
7. [Sistema de Detección de Eventos](#sistema-de-detección-de-eventos)
8. [Anti-Somnolencia y Wearables](#anti-somnolencia-y-wearables)
9. [Consumo de Recursos y Optimizaciones](#consumo-de-recursos-y-optimizaciones)
10. [Segmentación de Usuarios](#segmentación-de-usuarios)
11. [Estrategia Go-to-Market](#estrategia-go-to-market)
12. [Riesgos y Mitigaciones](#riesgos-y-mitigaciones)
13. [Especificaciones Técnicas Detalladas](#especificaciones-técnicas-detalladas)
14. [Métricas de Éxito](#métricas-de-éxito)
15. [FAQ Técnico](#faq-técnico)

---

## 🎯 VISIÓN GENERAL DEL PROYyecto

### Qué es
Una aplicación Android que convierte el teléfono del usuario en una **dash cam inteligente de bajo consumo**.

### Por qué existe
El creador ha vivido personalmente el problema de los accidentes sin evidencia. Conductores de Uber, taxi y particulares necesitan:
- Registrar incidentes para defenderse legalmente
- Detectar fatiga al volante para evitar accidentes
- Hacer esto sin que el teléfono se sobrecaliente ni drene la batería

### Quién la va a usar
**Segmento principal (MVP)**: Conductores de Uber/Taxi/Reparto que necesitan evidencia de incidentes  
**Segmento secundario**: Padres monitorando conductores jóvenes  
**Segmento futuro**: Empresas de flota (B2B)

### Qué la hace diferente
- **Buffer circular en cache del OS**: No graba continuamente, solo guarda los 30 segundos alrededor de un evento
- **Detección multi-sensor**: No depende de un solo trigger (acelerómetro falló en pruebas reales)
- **Bajo consumo**: 40% menos batería que grabación continua
- **Anti-somnolencia integrada**: Prevención de accidentes con cámara frontal + wearables compatibles

### Qué NO es
- ❌ No es una dash cam de hardware (es software en el teléfono)
- ❌ No funciona en iPhone (iOS bloquea cámara en background por política de privacidad)
- ❌ No graba continuamente 2+ horas (sobrecalienta el teléfono)
- ❌ No es una solución perfecta para todos los casos (ver sección de riesgos)

---

## 🔍 PROBLEMA IDENTIFICADO

### Experiencia del creador
El creador de este proyecto fue chocado mientras conducía Uber. El otro conductor negó la culpa. Sin video:
- El seguro no quiso cubrir
- Fue responsabilidad 50/50
- Perdió dinero en reparaciones

### Problema general en conductores
1. **Falta de evidencia**: Accidentes sin testigos = palabra contra palabra
2. **Costos de dash cams**: Hardware de calidad cuesta $100-300 (caro para conductores de gig economy)
3. **Riesgo de fatiga**: Trayectos largos = riesgo de micro-sueños
4. **Soluciones inadecuadas**:
   - Apps tradicionales graban TODO (matan batería, sobrecalientan, llenan almacenamiento)
   - Sensores del teléfono no son confiables para detectar impactos
   - Mantener cámara frontal siempre activa para detectar somnolencia consume batería y genera problemas de privacidad

### Datos de las pruebas
- **Prueba de acelerómetro**: En frenadas bruscas reales solo registró 1.3G (umbral no confiable)
- **Prueba de velocidad GPS**: La desaceleración medida por GPS tiene 1-2 seg de lag (demasiado lento)
- **Prueba de cámara frontal continua**: Alto consumo de batería y riesgo de sobre-calentamiento; no viable para detección en background
- **Conclusión**: No se puede depender de un único sensor para detección, y la cámara frontal no debe estar siempre activa

---

## 💡 SOLUCIÓN PROPUESTA

### Componentes principales

#### 1. **Modo Vigilante (Buffer Circular)**
- Cámara trasera captura video continuamente
- **NO escribe a disco** (usa cache optimizada del OS)
- Mantiene solo los últimos 15 segundos en cache
- Cuando detecta un evento (impacto, botón de pánico), guarda esos 15 seg y los 15 segundos posteriores al evento creando dos videos de 15 segundos cada uno
- Los 15 seg más viejos se descartan automáticamente
- Funciona en background vía Foreground Service

#### 2. **Anti-Somnolencia (Detección Facial + Wearables)**
- **Cámara frontal**: activa SOLO cuando el usuario está en `FatigueScreen`. Analiza el rostro del conductor con ML Kit, mide parpadeo (Eye Aspect Ratio) y emite alerta si ojos cerrados > umbral configurable.
- **Wearables compatibles**: fuente primaria de detección de fatiga en background. Lee datos de salud (frecuencia cardíaca, HRV, actividad) vía Health Connect.
- **NO guarda video** de la cámara frontal (solo analiza en tiempo real y descarta)
- Si el dispositivo no soporta cámaras concurrentes, la app sugiere usar un wearable o pausa el modo Vigilante mientras se usa la cámara frontal

#### 3. **Gestión de Suscripciones**
- Plan gratis: Buffer circular limitado a 5 incidentes/mes, sin exportación
- Plan premium ($4.99/mes): Ilimitado, exportar videos, configuración avanzada
- Plan flota ($19.99/mes): Dashboard web, múltiples vehículos

---

## 🏗️ ARQUITECTURA TÉCNICA

### Stack tecnológico actual

> **Nota**: La implementación real divergió del plan original (React Native / Expo). El proyecto actual usa **Kotlin Multiplatform + Compose Multiplatform** para máximo control, rendimiento y mantenibilidad. Todo el acceso nativo está en Kotlin.

| Capa | Tecnología | Propósito |
|------|-----------|----------|
| **Frontend** | Compose Multiplatform (Kotlin) | UI, navegación, estado |
| **Cámara trasera** | CameraX nativo (Kotlin) + PreviewView | Modo Vigilante, buffer circular, preview |
| **Cámara frontal** | CameraX nativo (Kotlin) + PreviewView + ImageAnalysis | Anti-somnolencia en `FatigueScreen` |
| **Sensores** | SensorManager nativo (Kotlin) | Acelerómetro (G-Force) |
| **GPS** | LocationManager nativo (Kotlin) | Velocímetro (MPH/KPH) |
| **Background** | LifecycleService + Foreground Service (Kotlin) | Supervivencia del servicio de vigilancia |
| **Persistencia local** | Multiplatform Settings (SharedPreferences) | Configuración de usuario |
| **Watchdog** | WorkManager (Kotlin) | Revive el servicio si Android lo mata |
| **Comunicación UI↔Servicio** | StateFlow / SharedFlow + callbacks nativos | Eventos de estado y telemetría en tiempo real |
| **IA Facial** | ML Kit Face Detection (Android nativo) | Detección de somnolencia (EAR) |
| **Wearables** | Health Connect (Android nativo) | Lectura de datos de salud para fatiga en background |
| **Build** | Gradle + Android Studio | APK firmado localmente |
| **Auth** | AWS Cognito (aws-amplify Kotlin) | Login/logout (Cuenta) |

### Flujo de datos

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
└── Downloads/DuoVial/
    └── incident_[timestamp]_part0.mp4 (guardado permanente)
    └── incident_[timestamp]_part1.mp4 (guardado permanente)
```

---

## 📈 FASES DE DESARROLLO

### FASE 1: El Guardián (Persistencia en Segundo Plano)
**Objetivo**: Que la app sobreviva al sistema operativo  
**Estado**: ✅ COMPLETADA  
**Tareas**:
- [x] Crear Foreground Service con notificación persistente (`BackgroundCameraService.kt`)
- [x] Servicio sobrevive a cierre de app (START_STICKY)
- [x] WorkManager Watchdog que reinicia proceso si Android lo mata
- [x] Notificación dinámica que refleja estado (Standby / Vigilando)
- [x] Burbuja flotante draggable (PIP) para pánico rápido

**Definición de listo**: La app sigue grabando incluso si la cierras manualmente ✅

---

### FASE 2: Los Sentidos (Buffer Circular + Detección Multi-Sensor)
**Objetivo**: Implementar el sistema de bajo consumo  
**Estado**: ✅ COMPLETADA  
**Tareas**:
- [x] Integrar CameraX nativa (Kotlin) con PreviewView
- [x] Configurar bitrate a 2 Mbps (HD) vía Recorder de CameraX
- [x] Buffer circular en cacheDir (2 segmentos × 15 seg)
- [x] Implementar botón de pánico / Evento (trigger manual)
- [x] Implementar detección de acelerómetro (G-Force > 2.5G configurable)
- [x] Umbral G-Force configurable desde UI (1.5–5.0G)
- [x] Velocímetro GPS (KPH/MPH) desacoplado del sensor manager — vivo en Standby
- [ ] ~~Giroscopio~~ (sustituido por acelerómetro configurable, más fiable en pruebas)
- [ ] ~~Detección de audio~~ (pospuesto: acelerómetro + botón cubren ~85% en pruebas)
- [ ] Testing formal de consumo de batería

---

### FASE 3: El Vigilante (Detección de Somnolencia)
**Objetivo**: Alertar al conductor si se duerme  
**Estado**: ✅ COMPLETADA (v1 con Camera2 manual)  
**Nota**: La implementación inicial usa Camera2 manual con `ImageReader` + `TextureView`. Se detectó un bug crítico donde el preview frontal se congela tras ~1 segundo por problemas de gestión del `Surface`. Esta fase será refactorizada en la **Fase 3B**.

**Tareas completadas (v1)**:
- [x] Agregar dependencia ML Kit
- [x] Crear `FrontFaceDetector.kt` — cámara frontal + ML Kit + EAR + alertas
- [x] Integrar `FrontFaceDetector` en `BackgroundCameraService.kt`
- [x] Crear `FatigueScreen.kt` — pantalla dedicada con preview + configuración
- [x] Modificar `MonitorScreen.kt` — botón "Frontal" navega a FatigueScreen
- [x] ML Kit detecta rostro y calcula EAR en tiempo real
- [x] Alerta nativa (vibración + sonido) cuando ojos cerrados > umbral

**Pendientes identificados**:
- [ ] Migrar cámara frontal de Camera2 manual a CameraX (Preview + ImageAnalysis)
- [ ] Validar soporte de cámaras concurrentes en el dispositivo
- [ ] Integrar Health Connect para detección por wearable en background
- [ ] Persistir configuración completa de fatiga (duration, max alerts, enabled)
- [ ] Sugerir wearables compatibles cuando no se pueda usar cámara frontal

---

### FASE 3B: Anti-Somnolencia Refactorizada (CameraX + Health Connect + Wearables)
**Objetivo**: Anti-somnolencia estable, eficiente y con soporte para wearables  
**Estado**: 🚧 EN PROGRESO  
**Tareas**:
- [ ] Migrar `FrontFaceDetector` a CameraX (`Preview` + `ImageAnalysis`)
- [ ] Reemplazar `TextureView` por `PreviewView` en `FrontCameraPreview`
- [ ] Detectar `cameraManager.concurrentCameraIds` al inicio
- [ ] Si no hay cámaras concurrentes: pausar modo Vigilante al entrar a FatigueScreen
- [ ] Integrar Health Connect para lectura de datos de wearables
- [ ] Implementar lógica de detección de fatiga por wearable (FC/HRV/movimiento)
- [ ] Persistir y restaurar configuración de fatiga completa
- [ ] Pantalla de sugerencia de wearables con lista de dispositivos recomendados
- [ ] Testing en Oppo A80 5G y al menos 2 dispositivos adicionales

**Definición de listo**: 
- Preview frontal nunca se congela
- Detección funciona con cámara frontal en FatigueScreen
- Si hay wearable compatible, funciona en background sin cámara frontal
- Si no hay cámaras concurrentes, el usuario recibe mensaje claro y opciones

---

### FASE 4: La Vitrina (UI + Monetización)
**Objetivo**: App pulida para lanzamiento  
**Duración**: 2-3 semanas  
**Tareas**:
- [ ] Pantalla de onboarding (explicar permisos, riesgos de OIS, wearables)
- [ ] Integración con RevenueCat (prueba gratis, suscripciones)
- [ ] Pantalla de "Incidentes Guardados" con preview y acciones (share, delete)
- [x] Settings con controles de sensibilidad — G-Force threshold + overlay permission
- [ ] Disclaimer sobre desgaste OIS y responsabilidades legales
- [ ] Testing de flujo completo end-to-end

**Definición de listo**: App lista para publicar en Play Store

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

**Justificación**: Un conductor de Uber necesita 6-12 horas de grabación sin agotar batería. La grabación continua es técnicamente inviable.

---

### Decisión 2: ¿Acelerómetro, GPS o múltiples sensores?

**Decisión Final**: **Multi-sensor** (botón + acelerómetro configurable)  
**Implementación**: MVP con botón + acelerómetro

---

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

---

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

---

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

---

### Decisión 6: Arquitectura de estado — Servicio como fuente única de verdad

**Problema encontrado**: La UI y el servicio nativo pueden desincronizarse.

**Solución implementada**:
- `CameraStatusListener`: interface para comunicación UI↔Servicio
- `resyncJsState()`: re-emite estado actual + última telemetría conocida al reconectar UI
- `forceResetToStandby()`: red de seguridad para recuperar control
- `onRecordingFinalized()`: guard anti-race que verifica estado antes de iniciar nuevo segmento
- `stopAndSave` guarda el buffer y vuelve a STANDBY, NO mata el servicio

**Definición**: El servicio nativo es la única fuente de verdad del estado. La UI es un espejo que se sincroniza en cada acción y reconexión.

---

### Decisión 7: Wearables — Health Connect como estándar principal

**Opciones evaluadas**:
- Health Connect: estandarizado, background, Android 14+
- SDK de fabricante: fuerte acoplamiento a marca
- Bluetooth LE directo: máximo control, máxima complejidad

**Decisión Final**: **Health Connect** para MVP. Permite soportar múltiples marcas sin acoplamiento. Fallback a SDK de fabricante si Health Connect no está disponible.

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

### Trigger 3: Giroscopio (NO IMPLEMENTADO)
```
Prioridad: ⭐ Postergado
Razón: El acelerómetro con umbral configurable cubre los casos necesarios para MVP
Futuro: Evaluar si complementa o se omite definitivamente
```

### Trigger 4: Detección de Audio (NO IMPLEMENTADO)
```
Prioridad: ⭐ Postergado
Razón: Acelerómetro + botón cubren ~85% de casos en pruebas MVP
Futuro: Evaluar tras validación de MVP
```

---

## 😴 ANTI-SOMNOLENCIA Y WEARABLES

### Estrategia de detección por niveles

```
Nivel 1 — Cámara frontal (ML Kit EAR):
├── Solo disponible cuando FatigueScreen está visible
├── Alta precisión de parpadeo
├── Alerta inmediata: vibración + sonido
└── Consumo medio-alto

Nivel 2 — Wearable vía Health Connect:
├── Funciona en background
├── Lee FC, HRV, movimiento
├── Detección basada en tendencias
└── Bajo consumo

Nivel 3 — Fallback:
├── Si no hay wearable ni cámaras concurrentes
├── Sugerir comprar wearable compatible
└── Mostrar lista de dispositivos recomendados
```

### Validación de capacidad del dispositivo

Al iniciar la app o entrar a FatigueScreen:

1. Consultar `CameraManager.getConcurrentCameraIds()`
2. Verificar si el par (trasera, frontal) puede abrirse simultáneamente
3. Si SÍ: modo Vigilante puede seguir activo mientras se usa cámara frontal
4. Si NO:
   - Mostrar explicación al usuario
   - Pausar modo Vigilante al entrar a FatigueScreen
   - Sugerir usar un wearable para detección en background

### Wearables recomendados (MVP vía Health Connect)

> Nota: Health Connect requiere Android 14+ y que el wearable exponga datos a la app de Health Connect o Google Fit. La compatibilidad exacta varía por modelo y región.

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

### Segmento 1: Conductores de Uber/Taxi/Reparto (PRIMARY - MVP)
**Tamaño**: 2-3 millones en Latinoamérica  
**Pain Point**: "Necesito evidencia para disputas con pasajeros/seguros"  
**Willingness to Pay**: 💰💰💰 Alta ($5-10/mes)

### Segmento 2: Padres con hijos conductores jóvenes (SECONDARY)
**Tamaño**: 1-2 millones en Latinoamérica  
**Pain Point**: "Mi hijo se duerme manejando, quiero saber"  
**Willingness to Pay**: 💰💰 Media ($3-5/mes)

### Segmento 3: Empresas de flota/reparto (FUTURE - AÑOS 2+)
**Tamaño**: 50,000+ empresas  
**Pain Point**: "Necesito monitorear 50+ conductores"  
**Willingness to Pay**: 💰💰💰💰 Muy alta

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
| **Competencia (dash cams tradicionales)** | Alta | Medio | Diferenciador: bajo consumo + anti-somnolencia + wearables |

---

## 🔧 ESPECIFICACIONES TÉCNICAS DETALLADAS

### 2.1 Cámara Trasera (Modo Vigilante)

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
└── Trigger guardar: Botón + Acelerómetro

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

### 2.2 Cámara Frontal (Anti-Somnolencia)

```
CONFIGURACIÓN (post-refactorización Fase 3B):
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

### 2.3 Wearables (Health Connect)

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

LÓGICA DE DETECCIÓN (futura):
├── Anomalías en FC sostenida (bradicardia/extrema)
├── Caída de HRV
├── Falta de movimiento prolongado
└── Fusión opcional con datos de cámara frontal cuando está activa
```

### 2.4 Persistencia en Background

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
└── HEALTH_CONNECT (nuevo, para wearables)
```

### 2.5 Arquitectura de Estado y Recuperación

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

### 2.6 Componentes de la UI Actual

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
| Rating | 4.0+ | Competitivo |
| Time to Guard | <5 min | Tiempo hasta grabando |
| Battery impact (Vigilante) | <12%/hour | Core metric |
| Preview frontal estable | 0 congelamientos | Critico para anti-somnolencia |
| Usuarios con wearable | 10%+ | Valida integración Health Connect |

---

### Lo que necesita más investigación
⚠️ Falsos positivos de acelerómetro en baches/toppes  
⚠️ Compatibilidad de Health Connect por marca de wearable  
⚠️ Dispositivos que no soportan cámaras concurrentes  
⚠️ Estabilidad del servicio en sesiones largas (>2h)  
⚠️ Calibración de umbrales de fatiga por wearable

### Decisiones tomadas para MVP
1. **Kotlin Multiplatform + Compose**: un solo lenguaje, máximo control nativo.
2. **Buffer circular con cache del OS**: balance entre rendimiento y complejidad.
3. **Cámara frontal solo en FatigueScreen**: ahorro de batería y privacidad.
4. **CameraX para cámara frontal**: robustez ante bugs de Surface.
5. **Health Connect para wearables**: estándar abierto, sin acoplamiento a marca.
6. **Servicio como fuente única de verdad**: evita desincronización UI/servicio.
7. **Sugerencia de wearables compatibles**: mejora UX cuando el hardware es limitado.

### Próximas validaciones
- [ ] Testing preview frontal en 5+ dispositivos
- [ ] Testing de Health Connect con al menos 3 wearables
- [ ] Calibración de umbrales de fatiga
- [ ] Testing de cámaras concurrentes en gama baja/media/alta
- [ ] NPS de primeros 50 usuarios
- [ ] Análisis de false positive rate por semana

---

**Documento versión**: 1.4  
**Última actualización**: Junio 15, 2026  
**Siguiente review**: Después de Fase 3B (Anti-somnolencia refactorizada)

**Contactar**: [Oscar's info aquí]  
**Repositorio**: [GitHub link aquí]

---

*Este documento es la fuente única de verdad para especificaciones del proyecto. Cualquier desviación debe ser documentada aquí y comunicada a todos los agentes/desarrolladores.*
