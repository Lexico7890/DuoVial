# 📋 CONTEXT.md - DASH CAM INTELIGENTE PARA CONDUCTORES

**Última actualización**: Junio 10, 2026  
**Estado del proyecto**: MVP en desarrollo — Fases 1-3 completadas, Fase 4 pendiente  
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
8. [Consumo de Recursos y Optimizaciones](#consumo-de-recursos-y-optimizaciones)
9. [Segmentación de Usuarios](#segmentación-de-usuarios)
10. [Estrategia Go-to-Market](#estrategia-go-to-market)
11. [Riesgos y Mitigaciones](#riesgos-y-mitigaciones)
12. [Especificaciones Técnicas Detalladas](#especificaciones-técnicas-detalladas)
13. [Métricas de Éxito](#métricas-de-éxito)
14. [FAQ Técnico](#faq-técnico)

---

## 🎯 VISIÓN GENERAL DEL PROYECTO

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
- **Buffer circular en RAM**: No graba continuamente, solo guarda los 30 segundos alrededor de un evento
- **Detección multi-sensor**: No depende de un solo trigger (acelerómetro falló en pruebas reales)
- **Bajo consumo**: 40% menos batería que grabación continua
- **Anti-somnolencia integrada**: Prevención de accidentes + registro pasivo

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

### Datos de las pruebas
- **Prueba de acelerómetro**: En frenadas bruscas reales solo registró 1.3G (umbral no confiable)
- **Prueba de velocidad GPS**: La desaceleración medida por GPS tiene 1-2 seg de lag (demasiado lento)
- **Conclusión**: No se puede depender de un único sensor para detección

---

## 💡 SOLUCIÓN PROPUESTA

### Componentes principales

#### 1. **Modo Vigilante (Buffer Circular)**
- Cámara trasera captura video continuamente
- **NO escribe a disco** (o minimal escritura a cache del OS)
- Mantiene solo los últimos 15 segundos en RAM/cache
- Cuando detecta un evento (impacto, audio, rotación), guarda esos 15 seg y los 15 segundos posteriores al evento creando dos videos de 15 segundos cada uno
- Los 15 seg más viejos se descartan automáticamente

#### 2. **Anti-Somnolencia (Detección Facial)**
- Cámara frontal analiza el rostro del conductor
- Mide parpadeo (Eye Aspect Ratio)
- Si ojos cerrados > 2 segundos → alerta sonora + vibración
- **NO guarda video** (solo analiza en tiempo real y descarta)

#### 3. **Gestión de Suscripciones**
- Plan gratis: Buffer circular limitado a 5 incidentes/mes, sin exportación
- Plan premium ($4.99/mes): Ilimitado, exportar videos, configuración avanzada
- Plan flota ($19.99/mes): Dashboard web, múltiples vehículos

---

## 🏗️ ARQUITECTURA TÉCNICA

### Stack tecnológico actual

> **Nota**: La implementación real divergió del plan original (react-native-vision-camera, react-native-sensors). Se optó por acceso nativo directo en Kotlin para máximo control y rendimiento.

| Capa | Tecnología | Propósito |
|------|-----------|----------|
| **Frontend** | React Native (Expo SDK 54) | UI, navegación, estado |
| **Cámara** | CameraX nativo (Kotlin) + PreviewView | Acceso directo a la cámara trasera, buffer circular |
| **Sensores** | SensorManager nativo (Kotlin) | Acelerómetro (G-Force) |
| **GPS** | LocationManager nativo (Kotlin) | Velocímetro (MPH) |
| **Background** | LifecycleService + Foreground Service (Kotlin) | Supervivencia del servicio |
| **Persistencia** | WorkManager (@rn-native-utils/workmanager) | Watchdog que revive el servicio |
| **Puente RN→Nativo** | DeviceEventEmitter + NativeModules | Eventos de estado y telemetría en tiempo real |
| **Almacenamiento** | MediaStore + context.cacheDir (Android nativo) | Segmentos de video temporales y exportación |
| **IA Facial** | ML Kit Face Detection (Android nativo) | Detección de somnolencia (EAR) — activa |
| **Build** | EAS Build (Expo) | APK firmado en la nube |
| **Auth** | AWS Cognito (aws-amplify) | Login/logout (Cuenta) |
| **Iconos** | @expo/vector-icons (MaterialCommunityIcons) | Iconografía de la UI |

### Flujo de datos

```
┌─────────────────────────────────────────────────────────────┐
│                    CÁMARA TRASERA (Vigilante)               │
│ 1080p @ 30fps → Encoder H.264 (2 Mbps) → Buffer Circular    │
│                                                              │
│ Almacenamiento: Cache del sistema Android                    │
│ Duración: Últimos 15 segundos (2 segmentos de 15 seg)       │
│ Escritura: Solo cuando hay evento (trigger)                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
        ┌───────────────────┼───────────────────┐
        ↓                                       ↓
    BOTÓN PÁNICO                           GIROSCOPIO
    (Manual)                               (Rotación)
        ↓                                       ↓
        └───────────────────┼───────────────────┘
                            ↓
                  ¿EVENTO DETECTADO?
                        Sí → Guardar 15 seg previos y 15 seg posteriores a Downloads
                        No → Descartar y continuar
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    CÁMARA FRONTAL (Anti-Somnolencia)         │
│ 640×480 @ 10fps → Camera2 ImageReader → ML Kit Face Det.    │
│                                                              │
│ Selección: Solo conductor (mayor x en raw frame)             │
│ Almacenamiento: NINGUNO (frames se descartan)               │
│ Cálculo: EAR en tiempo real                                 │
│ Trigger: EAR < 0.2 × > 2 segundos → VIBRACIÓN + SONIDO    │
└─────────────────────────────────────────────────────────────┘
```

### Directorios de almacenamiento

```
Android File System:
├── context.cacheDir/
│   ├── segment_15s_001.mp4 (descarta después)
│   └── segment_15s_002.mp4 (descarta después)
│
└── Downloads/
    └── incident_1716835200_part0.mp4 (guardado permanente)
    └── incident_1716835200_part1.mp4 (guardado permanente)
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
**Estado**: ✅ COMPLETADA (con divergencias del plan original)  
**Tareas**:
- [x] Integrar CameraX nativa (Kotlin) con PreviewView — **NO react-native-vision-camera**
- [x] Configurar bitrate a 2 Mbps (HD) vía Recorder de CameraX
- [x] Buffer circular en cacheDir (2 segmentos × 15 seg)
- [x] Implementar botón de pánico / Evento (trigger manual)
- [x] Implementar detección de acelerómetro (G-Force > 2.5G configurable)
- [x] Umbral G-Force configurable desde JS (1.5–5.0G)
- [x] Velocímetro GPS (MPH) desacoplado del sensor manager — vivo en Standby
- [ ] ~~Giroscopio~~ (sustituido por acelerómetro configurable, más fiable en pruebas)
- [ ] ~~Detección de audio~~ (pospuesto: acelerómetro + botón cubren ~85% en pruebas)
- [ ] Testing formal de consumo de batería

**Divergencias del plan**: Se sustituyó react-native-vision-camera y react-native-sensors por acceso nativo directo en Kotlin (CameraX, SensorManager, LocationManager). Esto dio mejor control y rendimiento a costa de más complejidad en el puente RN-nativo.

---

### FASE 3: El Vigilante (Detección de Somnolencia)
**Objetivo**: Alertar al conductor si se duerme  
**Estado**: ✅ COMPLETADA  
**Arquitectura**: 
- Cámara frontal: Android Camera2 API (ImageReader) separado de CameraX
- Face Detection: ML Kit (`com.google.mlkit:face-detection:16.1.6`)
- Alertas: Nativas (Kotlin) — vibración + sonido inmediatos
- UI: Pantalla dedicada (FatigueScreen) accesible desde botón "Frontal"
- Selección: Solo conductor (rostro con mayor x en frame raw = izquierda en preview espejado)

**Tareas**:
- [x] Agregar dependencia ML Kit en `android/app/build.gradle`
- [x] Crear `FrontFaceDetector.kt` — cámara frontal + ML Kit + EAR + alertas
- [x] Integrar `FrontFaceDetector` en `BackgroundCameraService.kt`
- [x] Agregar métodos bridge nativo→JS en `BackgroundCameraModule.kt`
- [x] Agregar métodos JS en `BackgroundGuard.ts`
- [x] Crear `FatigueScreen.tsx` — pantalla dedicada con preview + configuración
- [x] Modificar `MonitorScreen.tsx` — botón "Frontal" navega a FatigueScreen
- [x] Modificar `App.tsx` — estado fatiga + navegación + listeners
- [ ] Persistir configuración (AsyncStorage) — pendiente para Fase 4

**Especificaciones técnicas**:
- Resolución cámara frontal: 640×480 (VGA)
- FPS: 10 fps (suficiente para parpadeo)
- EAR threshold: 0.2 (ajustable 0.1-0.4)
- Duration threshold: 2 segundos (ajustable 1-5 seg)
- Anti-spam: máximo 3 alertas por hora (ajustable 1-5)
- Snooze: 5 minutos
- Vibración: Patrón [0, 500, 200, 500] ms
- Sonido: Alarma 2 segundos

**Selección del conductor**:
- ML Kit retorna bounding boxes con `x, y, width, height` para cada rostro
- Cámara frontal espeja la imagen (como espejo)
- En frame RAW: conductor está a la DERECHA (alto x)
- En preview ESPEJADO: conductor aparece a la IZQUIERDA
- Implementación: `val driverFace = faces.maxByOrNull { it.boundingBox.centerX() }`

**Definición de listo**: 
- [x] Botón "Frontal" navega a FatigueScreen
- [x] Cámara frontal se activa solo en FatigueScreen
- [x] ML Kit detecta rostro y calcula EAR en tiempo real
- [x] Solo se valida el conductor (izquierda de pantalla)
- [x] Alerta nativa (vibración + sonido) cuando ojos cerrados > 2s
- [x] Anti-spam (máx 3 alertas/hora)
- [x] Snooze 5 min funcional
- [x] Sliders de configuración funcional
- [x] Estado visual: 🟢 Abiertos / 🟡 Cerrando / 🔴 Fatiga
- [ ] Persistencia de configuración entre sesiones (pendiente Fase 4)
- [x] Sin errores de compilación
- [x] No interfiere con grabación trasera

---

### FASE 4: La Vitrina (UI + Monetización)
**Objetivo**: App pulida para lanzamiento  
**Duración**: 2-3 semanas  
**Tareas**:
- [ ] Pantalla de onboarding (explicar permisos, riesgos de OIS)
- [ ] Integración con RevenueCat (prueba gratis, suscripciones)
- [ ] Pantalla de "Incidentes Guardados" con preview y acciones (share, delete)
- [x] Settings con controles de sensibilidad — G-Force threshold slider + overlay permission ya implementados en Configuraciones
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
- ✅ 0 MB/seg en operación normal (cache optimizado)
- ✅ ~2 MB/seg durante grabación (bitrate 2 Mbps)
- ✅ Chip de almacenamiento inactivo = frío
- ✅ Batería dura 8-10 horas
- ✅ Solo guarda lo importante

**Justificación**: Un conductor de Uber necesita 6-12 horas de grabación sin agotar batería. La grabación continua es técnicamente inviable.

---

### Decisión 2: ¿Acelerómetro, GPS o múltiples sensores?

**Acelerómetro solo**:
- ❌ Pruebas reales: máximo 1.3G en frenadas brusca (no confiable)
- ❌ No detecta choques laterales (teléfono no se mueve)
- ❌ No detecta cuando estás quieto (choque por atrás)
- ❌ Cobertura: ~60%

**GPS (desaceleración)**:
- ⚠️ Funciona pero con 1-2 seg de lag
- ⚠️ No funciona en túneles/interiores
- ⚠️ Gasto de batería no justificado para MVPGitHub

**Giroscopio (rotación)**:
- ✅ Detecta derrapes, volcamientos, giros bruscos
- ✅ Complementa acelerómetro ortogonalmente
- ✅ Consumo mínimo
- ✅ Cobertura: +15%

**Botón Manual (pánico)**:
- ✅ El usuario VE el peligro antes del impacto
- ✅ Costo: 2 horas de dev
- ✅ Cobertura: +30%

**Decisión Final**: **Multi-sensor** (botón + giroscopio)  
**Cobertura total**: ~95%  
**Implementación**: MVP con botón + giroscopio

---

### Decisión 3: ¿React Native Vision Camera vs. código nativo puro?

**React Native Vision Camera**:
- ✅ Abstracción de CameraX (Android 5.0+)
- ✅ Frame Processors para procesamiento en tiempo real
- ✅ Soporte para Vision Camera Plugins
- ⚠️ `startRecording()` escribe a cache del OS (no RAM pura)

**Código Nativo (Kotlin)**:
- ✅ Control total, máxima performance
- ❌ Requires especialización en Kotlin/JNI
- ❌ Curva de aprendizaje alta
- ❌ Más bugs potenciales

**Decisión Final**: **Código Nativo (Kotlin)** — ver Decisión 6  
**Justificación**: Se optó por acceso nativo directo para máximo control del ciclo de vida de la cámara, crítico para el buffer circular.

---

### Decisión 4: ¿Buffer en RAM puro vs. cache del OS?

**Buffer en RAM puro** (Frame Processors + MediaCodec):
- ✅ 0 escrituras al disco
- ❌ Requiere ~3-5 días de desarrollo Kotlin
- ❌ Mayor complejidad, mayor surface de bugs
- ❌ Posibles incompatibilidades en dispositivos viejos

**Cache del OS** (startRecording + RNFS):
- ✅ Escribe a cache (más rápido que disco)
- ✅ Sistema operativo optimiza I/O automáticamente
- ✅ Implementable hoy con react-native-vision-camera
- ⚠️ Sigue escribiendo, no es 0 bytes

**Decisión Final**: **Cache del OS para MVP** (Fase 2: mejora a RAM puro)  
**Ahorro real**: ~70% menos I/O vs. grabación continua

---

### Decisión 5: ¿Merge de segmentos en memoria o archivos separados?

**Archivos separados**:
- ✅ Más rápido (no requiere FFmpeg)
- ❌ Usuario ve 2 archivos en lugar de 1
- ❌ UX confuso

**Merge con FFmpeg**:
- ✅ Usuario ve 1 archivo limpio
- ⚠️ Requiere FFmpeg (dependency adicional)
- ⚠️ Merge toma ~5-10 seg (aceptable)

**Decisión Final**: **Archivos separados para MVP** → Merge en Fase 2  
**Justificación**: Lanzar primero, iterar después

---

### Decisión 6: ¿React Native wrappers vs. Kotlin nativo directo? (REVISADA)

**Plan original**: react-native-vision-camera + react-native-sensors

**Lo que realmente se implementó**: CameraX nativo + SensorManager nativo + LocationManager nativo, todo en Kotlin

**Razones del cambio**:
- ✅ Control total sobre el ciclo de vida de la cámara — crítico para el buffer circular
- ✅ PreviewView nativo sin depender de abstracciones RN
- ✅ SensorManager nativo permite rate-limiting y filtrado personalizado
- ✅ LocationManager nativo desacoplado del ciclo de grabación (GPS vivo en Standby)
- ✅ El puente RN→Kotlin se hace vía `BackgroundCameraModule` + `DeviceEventEmitter`
- ✅ Sin dependencias npm frágiles para funcionalidad core

**Tradeoff**: Más código Kotlin que mantener, mayor superficie de bugs en la capa nativa

---

### Decisión 7: Arquitectura de estado — Servicio como fuente única de verdad

**Problema encontrado**: El JS y el servicio nativo pueden desincronizarse. Casos:
- Hot reload de Metro (el servicio sobrevive, RN se reinicia)
- Android mata el proceso JS pero el Foreground Service sigue vivo
- El usuario cierra y reabre la app mientras el servicio graba

**Solución implementada**:
- `CameraStatusListener` en el companion object del servicio: interface estática para comunicación
- `resyncJsState()`: llamado desde `Module.init` cuando RN se (re)conecta al servicio. Re-emite estado actual + última telemetría conocida (`lastKnownGForce`, `lastKnownSpeed`)
- `forceResetToStandby()`: red de seguridad. Aborta cualquier estado intermedio y vuelve a STANDBY. Idempotente.
- `onRecordingFinalized()`: guard anti-race que verifica `serviceState` antes de iniciar nuevo segmento (evita "segmentos huérfanos")
- `stop` ya NO mata el servicio (`stopSelf()`) — guarda el buffer y vuelve a STANDBY, preservando la sesión

**Definición**: El servicio nativo es la única fuente de verdad del estado. El JS es un espejo que se sincroniza en cada acción y en cada reconexión.

---

### Decisión 8: UI estilo Google Maps (Monitor fullscreen)

**Diseño implementado** (Junio 2026):
- Cámara ocupa TODA la pantalla como fondo (`StyleSheet.absoluteFill`)
- Header flotante con efecto glass-morphism (top)
- Status pill estilo "Luego" de Maps (debajo del header)
- Telemetría en círculos sin título: G-Force (arriba) y MPH (abajo) — bottom-left
- Power (icono, sin texto) encima de Evento — bottom-right
- REC badge cuando graba (parpadeante, top-right)
- Bottom nav sin cambios (Monitor, Eventos, Configurar, Cuenta)

**Componentes clave**: `MonitorScreen.tsx` (nuevo), `App.tsx` (delegado)

---

### Decisión 9: Recuperación de emergencia ante estado atascado

**Problema**: Si el servicio queda en estado SAVING (e.g. post-evento de 15s que nunca finaliza), el JS mostraba "Guardando evento" y el usuario no podía hacer nada. Ni start ni stop funcionaban porque `isSaving=true` bloqueaba todos los handlers.

**Solución**:
- `forceReset()` expuesto como `@ReactMethod` en el módulo nativo
- Si el servicio está vivo → `forceResetToStandby()` → STANDBY → emite INACTIVO
- Si el servicio está muerto → emite INACTIVO directamente para que la UI se recupere
- `handleStart` y `handleStop` en `App.tsx` ahora detectan `isSaving=true` y llaman `forceReset()` en vez de retornar
- **Garantía**: el usuario SIEMPRE puede recuperar el control, sin importar el estado del servicio

---

## 🚨 SISTEMA DE DETECCIÓN DE EVENTOS

### Trigger 1: Botón de Pánico / Evento (CRÍTICO)
```
Prioridad: ⭐⭐⭐ IMPLEMENTADO
Cobertura: ~85% (el usuario VE el peligro primero)
Implementación: Botón "Evento" en bottom-right de Monitor
Acción: saveEvent() guarda segmento pre + 15s post-evento
Cooldown: 12 segundos entre eventos (anti-spam)
```

### Trigger 2: Acelerómetro G-Force (ALTA)
```
Prioridad: ⭐⭐⭐ IMPLEMENTADO
Cobertura: Detecta impactos con fuerza calculada desde acelerómetro nativo
Implementación: SensorManager nativo → accelListener → magnitud/gravedad
Umbral: 2.5G (configurable entre 1.5–5.0G desde JS)
Rate-limit: Emisión a JS cada 200ms
Nota: Pruebas reales mostraron ~1.3G en frenadas bruscas; umbral 2.5G es conservador
```

### Trigger 3: Giroscopio (NO IMPLEMENTADO)
```
Prioridad: ⭐ Postergado
Razón: El acelerómetro con umbral configurable cubre los casos necesarios para MVP (~85%)
Futuro: Evaluar si complementa o se omite definitivamente
```

### Trigger 4: Detección de Audio (NO IMPLEMENTADO)
```
Prioridad: ⭐ Postergado
Razón: Acelerómetro + botón cubren ~85% de casos en pruebas MVP
Futuro: Evaluar tras validación de MVP
```

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

### Optimizaciones aplicadas

#### 1. Reducir bitrate de 6 Mbps a 2 Mbps
```
Justificación: 2 Mbps es suficiente para leer placas (pruebas de calidad)
Ahorro: 66% menos datos codificados
Tradeoff: Ligeramente menos definición, pero aceptable para dash cam
```

#### 2. Desactivar audio
```
Justificación: Dash cams no necesitan audio para evidencia legal
Ahorro: 30% menos CPU (no codificar AAC), 20% menos I/O
Nota: Micrófono todavía se usa para detección de impactos
```

#### 3. Usar cache del OS (no Downloads directo)
```
Justificación: Cache está optimizado con write-back caching
Beneficio: OS agrupa escrituras, reduced I/O físico ~70%
Nota: Archivos se copian a Downloads solo cuando hay evento
```

#### 4. Frame Processor @ 10 FPS para anti-somnolencia (no 30 FPS)
```
Justificación: Parpadeo se detecta cada 100ms, 30 FPS es overkill
Ahorro: 66% menos frames a procesar
Tradeoff: Mínima latencia de detección
```

#### 5. Resolución 480p para cámara frontal (no 1080p)
```
Justificación: Solo necesitas puntos faciales, no detalles
Ahorro: 75% menos datos a procesar
Cálculo: EAR funciona igual con baja resolución
```

---

## 👥 SEGMENTACIÓN DE USUARIOS

### Segmento 1: Conductores de Uber/Taxi/Reparto (PRIMARY - MVP)
**Tamaño**: 2-3 millones en Latinoamérica  
**Pain Point**: "Necesito evidencia para disputas con pasajeros/seguros"  
**Willingness to Pay**: 💰💰💰 Alta ($5-10/mes)  
**Características**:
- Conducen 6-12 horas diarias
- Usar su teléfono (ya tienen soporte en carro)
- Experimentan accidentes regularmente
- Entienden valor de evidencia

**Tácticas Go-to-Market**:
- Infiltrar grupos de Facebook de conductores
- Videos de testimonios (antes/después de incidente)
- Alianza con talleres mecánicos/tiendas accesorios

---

### Segmento 2: Padres con hijos conductores jóvenes (SECONDARY)
**Tamaño**: 1-2 millones en Latinoamérica  
**Pain Point**: "Mi hijo se duerme manejando, quiero saber"  
**Willingness to Pay**: 💰💰 Media ($3-5/mes)  
**Características**:
- Pagan por seguridad del hijo
- Interesados en feature de anti-somnolencia
- Posible que paguen plan familiar

**Tácticas Go-to-Market**:
- Contenido educativo sobre seguridad vial
- Testimonios de padres que evitaron tragedias
- Integración con apps de control parental

---

### Segmento 3: Empresas de flota/reparto (FUTURE - AÑOS 2+)
**Tamaño**: 50,000+ empresas  
**Pain Point**: "Necesito monitorear 50+ conductores, reducir accidentes, reclamaciones"  
**Willingness to Pay**: 💰💰💰💰 Muy alta ($20-50/mes × conductor)  
**Características**:
- Pain point es económico (accidentes = dinero)
- Necesitan dashboard centralizado
- Requieren reportes + alertas en tiempo real

**Tácticas Go-to-Market** (Future):
- Alianza con aseguradoras (ofrecen descuento a clientes)
- API para integración con sistemas de flota
- Datos agregados de seguridad

---

## ⚠️ RIESGOS Y MITIGACIONES

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|--------|-----------|
| **Daño OIS por vibración** | Media | Alto (legal) | Disclaimer claro en onboarding |
| **Sobrecalentamiento** | Baja | Alto (device damage) | Tutorial de desactivar optimización de batería |
| **App muere en background** | Alta | Alto (no funciona) | Foreground Service + Watchdog + testing |
| **Detección de audio falsos positivos** | Alta | Medio (molestia) | Umbrales ajustables, confirmación cruzada |
| **Privacidad (cámara frontal siempre activa)** | Baja (legal) | Medio (user trust) | Disclaimer: "Solo local, nunca se envía a cloud" |
| **iOS no soportado** | Cierto | Medio (market) | Comunicar claramente "Android only" |
| **Compatibilidad en celus viejos** | Media | Medio | Detectar hardware en init, ofrecer modo reducido |
| **Falsas reclamaciones legales** | Baja | Muy alto (legal) | Disclaimer: "No usamos para prueba legal válida" |
| **Competencia (dash cams tradicionales)** | Alta | Medio | Diferenciador: bajo consumo + anti-somnolencia |

---

## 🔧 ESPECIFICACIONES TÉCNICAS DETALLADAS

### 2.1 Cámara Trasera (Modo Vigilante)

```
CONFIGURACIÓN:
├── Resolución: 1920 × 1080 (Full HD)
├── FPS: 30 fps
├── Codec: H.264
├── Bitrate: 2 Mbps (optimizado, NO 6 Mbps default)
├── Audio: DESACTIVADO
├── Buffer: Circular, últimos 30 segundos
├── Almacenamiento: context.cacheDir (Android)
├── Limpieza: Automática (máximo 2 segmentos en cache)
└── Trigger guardar: Botón + Audio + Giroscopio

SEGMENTACIÓN:
├── Segmento 1: 15 segundos (descartable, en RAM/cache)
├── Segmento 2: 15 segundos (descartable, en RAM/cache)
└── Al detectar evento:
    ├── Ambos segmentos se copian a Downloads
    ├── Nombrados: incident_[timestamp]_part0.mp4 y part1.mp4
    └── Usuario puede ver/exportar/eliminar

CONSUMO ESTIMADO:
├── CPU: 8-10% (encoder H.264)
├── Almacenamiento write: ~2 MB/seg (bitrate reducido)
├── Batería: ~6% por hora
└── Temperatura: +4-6°C sobre ambiente
```

### 2.2 Cámara Frontal (Anti-Somnolencia)

```
CONFIGURACIÓN:
├── Resolución: 640 × 480 (VGA optimizado)
├── FPS: 10 fps (suficiente para parpadeo)
├── API: Android Camera2 (ImageReader) — separado de CameraX
├── Frame Processor: ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.6`)
├── Almacenamiento: NINGUNO (frames se descartan)
├── Cálculo: Eye Aspect Ratio (EAR) en tiempo real
└── Trigger alerta: EAR < 0.2 × > 2 segundos

SELECCIÓN DEL CONDUCTOR:
├── ML Kit retorna bounding boxes con x, y, width, height
├── Cámara frontal espeja la imagen (como espejo)
├── En frame RAW: conductor está a la DERECHA (alto x)
├── En preview ESPEJADO: conductor aparece a la IZQUIERDA
├── Selección: faces.maxByOrNull { it.boundingBox.centerX() }
└── Edge cases: 1 persona → usar esa; 0 personas → no alertar

EAR CÁLCULO:
├── Ojos: FaceContour.LEFT_EYE y FaceContour.RIGHT_EYE
├── Puntos: 6 puntos por ojo (p1-p6)
├── Vertical1 = distance(p2, p6)
├── Vertical2 = distance(p3, p5)
├── Horizontal = distance(p1, p4)
└── EAR = (vertical1 + vertical2) / (2.0 × horizontal) — promedio ambos ojos

UMBRALES CONFIGURABLES:
├── Closed eye threshold: EAR < 0.2 (ajustable 0.1-0.4)
├── Duration: > 2 segundos (ajustable 1-5 seg)
├── Snooze: 5 minutos (usuario puede ignorar alerta)
└── Allowed per hour: 3 alertas (ajustable 1-5)

ACCIÓN AL DETECTAR:
├── Vibración: Patrón [0, 500, 200, 500] ms (nativa Kotlin)
├── Sonido: Alarma de 2 segundos (nativa Kotlin)
├── Visual: Overlay rojo intermitente en FatigueScreen
├── Evento JS: onDrowsinessDetected (para UI feedback)
└── LOG: Registro del evento para análisis

CONSUMO ESTIMADO:
├── CPU: 4-6% (ML Kit en GPU/NPU)
├── Almacenamiento: 0 bytes (nada se guarda)
├── Batería: ~4% por hora
└── Temperatura: +2-3°C sobre ambiente

ARCHIVOS:
├── FrontFaceDetector.kt (nuevo) — cámara frontal + ML Kit + EAR + alertas
├── BackgroundCameraService.kt — integrar FrontFaceDetector
├── BackgroundCameraModule.kt — métodos bridge nativo→JS
├── BackgroundGuard.ts — métodos JS
├── FatigueScreen.tsx (nuevo) — pantalla dedicada con preview + configuración
├── MonitorScreen.tsx — botón "Frontal" navega a FatigueScreen
└── App.tsx — estado fatiga + navegación + listeners
```

### 2.3 Detección Multi-Sensor

#### Botón de Pánico
```
UBICACIÓN: Centro pantalla, tamaño grande
ACCIÓN: saveBufferOnImpact()
CONFIRMACIÓN: Toast "Incidente guardado"
COOLDOWN: 3 segundos (anti-spam)
FEEDBACK: Vibración + sonido distintivo
```

#### Detección de Audio
```
FRECUENCIAS MONITOREADAS:
├── Rango bajo (impacto metal): 0-500 Hz
├── Rango alto (vidrio rompiéndose): 2,000-8,000 Hz
└── Filtrado: Excluye 300-3,000 Hz (voz humana)

UMBRAL: Pico de decibelios > -20 dB
CONFIRMACIÓN: Requiere pico > -20 dB + otros sensores (no solo audio)
FALSO POSITIVO RATE: <5% (target)
FALSO NEGATIVO RATE: <10% (target)
```

#### Giroscopio
```
UMBRAL: Rotación magnitude > 3.0 rad/seg
DURACIÓN: Evento instantáneo (no requiere sostenimiento)
CASOS DETECTA:
├── Derrape (cambio de ángulo brusco)
├── Pérdida de control
├── Vuelco del vehículo
└── Impacto lateral que rota el carro
CALIBRACIÓN: Ajustable en Settings (2.5 - 4.0 rad/seg)
```

### 2.4 Persistencia en Background

```
FOREGROUND SERVICE:
├── Channel ID: 'duovial_camera_service_channel'
├── Notification ID: 144
├── Título: "DuoVial"
├── Mensaje: Dinámico ("Cámara lista." / "Grabación circular activa.")
├── Importancia: LOW (no molesta)
├── Service Type (Q+): CAMERA + LOCATION
└── No dismissable: true

WATCHDOG (WorkManager):
├── Task Key: 'duovial_watchdog'
├── Intervalo: Cada 15 minutos
├── Flex Time: 5 minutos
├── Acción: Re-llama startRecording() si el servicio murió
└── Persistencia: Supera reseteos del OS

ESTADOS DEL SERVICIO:
├── STANDBY → Preview viva, GPS activo, sin grabar
├── RECORDING → Buffer circular rotando, acelerómetro activo
└── SAVING → Guardando evento (transitorio, vuelve a STANDBY o RECORDING)

PERMISOS REQUERIDOS:
├── android.permission.CAMERA
├── android.permission.ACCESS_FINE_LOCATION
├── android.permission.ACCESS_COARSE_LOCATION
├── android.permission.FOREGROUND_SERVICE
├── android.permission.FOREGROUND_SERVICE_CAMERA
├── android.permission.FOREGROUND_SERVICE_LOCATION
├── android.permission.POST_NOTIFICATIONS
├── android.permission.SYSTEM_ALERT_WINDOW (burbuja flotante)
├── android.permission.RECORD_AUDIO (detección de impactos por audio)
├── android.permission.VIBRATE (alertas de fatiga)
└── android.permission.WAKE_LOCK

BATTERY OPTIMIZATION HANDLING:
├── Detectar si optimización está ON
├── Mostrar tutorial en onboarding
├── Link directo a Settings (Intent)
└── Re-prompt cada 30 días
```

```
ESTRUCTURA:
├── CACHE (context.cacheDir):
│   ├── segment_0.mp4 (15 seg, buffer rotativo)
│   └── segment_1.mp4 (15 seg, buffer rotativo)
│
└── DOWNLOADS (MediaStore → Download/DuoVial):
    └── incident_[timestamp]_part0.mp4 (permanente, usuario ve)

LIMPIEZA AUTOMÁTICA:
├── Cache: Máximo 2 archivos (30 seg totales)
├── Si hay >2 segmentos en cache → delete oldest
├── Downloads: Manual (usuario decide)
└── Opción para auto-delete después de 30 días (setting)

ESPACIO REQUERIDO:
├── Por incidente (30 seg): ~7.5 MB (bitrate 2 Mbps)
├── Si 5 incidentes/mes: ~37.5 MB/mes
├── Premium (ilimitado): Target <2 GB/mes
└── Recomendación: Mínimo 5 GB storage libres
```

### 2.6 Arquitectura de Estado y Recuperación

```
MÁQUINA DE ESTADOS (ServiceState):
┌──────────┬──────────────┬──────────────────────────────────────┐
│  STANDBY │ → RECORDING  │ startRecordingMode()                 │
│          │              │ → Preview + GPS vivos; no graba       │
├──────────┼──────────────┼──────────────────────────────────────┤
│ RECORDING│ → STANDBY    │ forceResetToStandby() / stopAndSave  │
│          │ → SAVING     │ saveEvent() (colisión o pánico)      │
│          │              │ → Buffer circular rotando 2 segs     │
├──────────┼──────────────┼──────────────────────────────────────┤
│  SAVING  │ → STANDBY    │ forceResetToStandby() (si atascado)  │
│          │ → RECORDING  │ handleEventSaveTransition (normal)   │
│          │              │ → Post-evento 15s o guardado buffer  │
└──────────┴──────────────┴──────────────────────────────────────┘

COMUNICACIÓN JS → SERVICIO:
┌─────────────────────────────────────────────────────────────────┐
│  BackgroundGuard.ts                                              │
│    ├── startStandby()    → Module.startStandby()                 │
│    ├── startGuarding()   → Module.startRecording() + Watchdog    │
│    ├── stopGuarding()    → Module.stopRecording()                │
│    ├── triggerPanic()    → Module.triggerPanic()                 │
│    ├── forceReset()      → Module.forceReset()  ★ nuevo          │
│    ├── setGForceThreshold(t) → Module.setGForceThreshold(t)       │
│    ├── getGForceThreshold()  → Module.getGForceThreshold()        │
│    ├── enableFatigueDetection(e) → Module.enableFatigueDetection(e) │
│    ├── setEarThreshold(t) → Module.setEarThreshold(t)            │
│    ├── snoozeFatigueAlert(m) → Module.snoozeFatigueAlert(m)      │
│    └── getFatigueStatus()  → Module.getFatigueStatus()           │
└─────────────────────────────────────────────────────────────────┘

COMUNICACIÓN SERVICIO → JS:
┌─────────────────────────────────────────────────────────────────┐
│  DeviceEventEmitter (vía CameraStatusListener)                    │
│    ├── onCameraStatusChanged  → { status: string }               │
│    ├── onAccelChanged         → { gForce: double }               │
│    ├── onSpeedChanged         → { speed: double }                │
│    ├── onFaceStatusChanged    → { enabled, faceDetected, earValue, closedEyeDuration } │
│    └── onDrowsinessDetected   → { timestamp, earValue }          │
│                                                                   │
│  Mecanismos anti-desync:                                          │
│    ├── resyncJsState()  → llamado en Module.init tras reconexión  │
│    ├── lastKnownGForce  → último valor de acelerómetro conocido   │
│    ├── lastKnownSpeed   → último valor de velocidad conocido      │
│    └── forceReset()     → siempre devuelve INACTIVO si Service    │
│                           está muerto (no deja UI colgada)        │
└─────────────────────────────────────────────────────────────────┘

FLUJO DE RECUPERACIÓN DE EMERGENCIA:
1. JS detecta isSaving=true → botón de power llama forceReset()
2. Module.forceReset():
   ├── Service vivo → service.forceResetToStandby() → STANDBY → INACTIVO
   └── Service muerto → sendStatusEventToJS("INACTIVO")
3. JS recibe INACTIVO → isRecording=false, isSaving=false
4. Botón de power vuelve al estado normal (START)
```

### 2.7 Componentes de la UI Actual

```
App.tsx (contenedor principal):
├── AuthProvider (AWS Cognito)
├── SafeAreaView + StatusBar
├── Container con tab dinámico:
│   ├── Monitor → MonitorScreen (fullscreen cámara)
│   ├── Fatigue → FatigueScreen (detección de somnolencia)
│   ├── Eventos → renderEventos (galería)
│   ├── Configuraciones → renderConfig (sliders, overlay, G-Force)
│   └── Cuenta → renderCuenta (login/logout)
└── BottomNav (Monitor, Eventos, Configurar, Cuenta) — oculto en FatigueScreen

MonitorScreen.tsx (estilo Google Maps):
├── BackgroundCameraPreview (absoluteFill — fondo)
├── REC badge (top-right, parpadeante cuando graba)
├── FloatingHeader (glass-morphism, top):
│   ├── Logo + "DuoVial" + "STANDBY ACTIVE"
│   └── Botón "Frontal" (navega a FatigueScreen — activo)
├── StatusPill (debajo del header, estilo "Luego" de Maps)
├── TelemetryStack (bottom-left):
│   ├── Círculo G-Force (valor + unidad "G")
│   └── Círculo MPH (valor + unidad "MPH")
└── BottomActions (bottom-right):
    ├── PowerIconButton (verde=start, rojo=stop, gris=disabled)
    └── EventoButton (amarillo=panic, gris=inactive)
```

## 📊 MÉTRICAS DE ÉXITO

### MVP (Primeras 12 semanas)

| Métrica | Target | Justificación |
|---------|--------|---------------|
| Instalaciones | 500+ | Suficiente para validar product-market fit |
| MAU | 200+ | 40% de instalaciones activas |
| Conversion a pago | 5%+ | 1 de cada 20 usuarios paga |
| MRR (Monthly Recurring) | $500+ | 10 usuarios × $4.99 × 10 meses promedio |
| Churn | <10% | Aceptable para consumidor (promedio 20%) |
| Rating | 4.0+ | Competitivo con dash cams tradicionales |
| Time to Guard | <5 min | Tiempo desde instalar hasta grabando |
| Battery impact | <12%/hour | Core metric (vs. 15-18% sin optimizar) |

### Post-MVP (Meses 3-6)

| Métrica | Target | Justificación |
|---------|--------|---------------|
| Instalaciones | 5,000+ | 10x growth con marketing |
| MRR | $5,000+ | 10x growth |
| Churn | <8% | Mejora con updates |
| NPS | 40+ | Predictivo de growth viral |
| B2B pilots | 2+ | Aseguradoras o empresas de flota |
| Referrals | 30% de installs | Conductores se conocen entre sí |

---

### Lo que necesita más investigación
⚠️ Falsos positivos de acelerómetro en baches/toppes  
⚠️ Optimización de batería en Xiaomi/Samsung/Huawei (cada marca es diferente)  
⚠️ Compatibilidad con frame rates variables en cámaras diferentes
⚠️ Estabilidad del servicio en sesiones largas (>2h) en dispositivos con RAM limitada

### Decisiones tomadas para MVP
1. **Simpler is better**: Cache del OS (no RAM puro) para lanzar rápido
2. **Multi-sensor es crítico**: Un solo trigger falló en pruebas reales (botón + acelerómetro implementados)
3. **Freemium funciona**: 5 incidentes/mes impulsa conversión
4. **Conductor es user, no empresa**: Segmentación clara en Go-to-Market
5. **Kotlin nativo directo**: Se abandonó react-native-vision-camera/sensors por acceso nativo en Kotlin para máximo control del ciclo de vida
6. **Servicio es fuente única de verdad**: El estado del servicio nativo es autoritativo. JS se sincroniza vía `resyncJsState()` en cada reconexión
7. **Stop preserva la sesión**: `stopAndSave` guarda el buffer, vuelve a STANDBY, NO mata el servicio
8. **forceReset como paracaídas**: El usuario siempre puede recuperar el control sin importar el estado del servicio

### Próximas validaciones
- [ ] Testing en mínimo 5 modelos de dispositivos
- [ ] Calibración de umbrales de audio en mundo real
- [ ] A/B testing de UX (dónde poner botón pánico)
- [ ] NPS de primeros 50 usuarios
- [ ] Análisis de false positive rate por semana

---

**Documento versión**: 1.3  
**Última actualización**: Junio 10, 2026  
**Siguiente review**: Después de Fase 4 (UI + Monetización)

**Contactar**: [Oscar's info aquí]  
**Repositorio**: [GitHub link aquí]

---

*Este documento es la fuente única de verdad para especificaciones del proyecto. Cualquier desviación debe ser documentada aquí y comunicada a todos los agentes/desarrolladores.*