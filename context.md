# 📋 CONTEXT.md - DASH CAM INTELIGENTE PARA CONDUCTORES

**Última actualización**: Mayo 2026  
**Estado del proyecto**: MVP en desarrollo  
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

| Capa | Tecnología | Propósito |
|------|-----------|----------|
| **Frontend** | React Native | UI multiplataforma (Android - iOS no soportado) |
| **Cámara** | react-native-vision-camera | Acceso a CameraX nativo, Frame Processors |
| **Sensores** | react-native-sensors | Acelerómetro, giroscopio, etc. |
| **Persistencia** | @rn-native-utils/workmanager | Mantener servicio en background |
| **IA Facial** | vision-camera-face-detector (ML Kit) | Detección de parpadeo, on-device |
| **Almacenamiento** | RNFS (React Native File System) | Gestionar archivos de video |
| **Monetización** | RevenueCat / Stripe | Gestionar suscripciones |
| **Background** | rn-foreground-service | Servicio persistente en foreground |

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
│ 480p @ 10fps → Frame Processor → ML Kit Face Detection      │
│                                                              │
│ Almacenamiento: NINGUNO (frames se descartan)               │
│ Cálculo: Eye Aspect Ratio en tiempo real                    │
│ Trigger: Ojos cerrados > 2 segundos → VIBRACIÓN + SONIDO    │
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
**Duración**: 1-2 semanas  
**Tareas**:
- [ ] Crear Foreground Service con notificación persistente
- [ ] Tutorial para desactivar optimización de batería (crítico en Xiaomi, Samsung, Huawei)
- [ ] WorkManager Watchdog que reinicia proceso si Android lo mata
- [ ] Testing en mínimo 3 dispositivos diferentes

**Definición de listo**: La app sigue grabando incluso si la cierras manualmente

---

### FASE 2: Los Sentidos (Buffer Circular + Detección Multi-Sensor)
**Objetivo**: Implementar el sistema de bajo consumo  
**Duración**: 2-3 semanas  
**Tareas**:
- [ ] Integrar react-native-vision-camera con Frame Processor
- [ ] Configurar bitrate a 2 Mbps (vs. default 6 Mbps)
- [ ] Desactivar audio en grabación (ahorra 30% CPU)
- [ ] Buffer circular en cache del sistema (2 segmentos × 15 seg)
- [ ] Implementar botón de pánico (trigger manual)
- [ ] Implementar detección de giroscopio (rotación > 3.0 rad/s)
- [ ] Implementar detección de audio (picos > -20 dB)
- [ ] Testing de consumo de batería (debe ser ~10-12%/hora)

**Definición de listo**: Grabar sin escribir continuamente al disco, detección multi-sensor funcionando

---

### FASE 3: El Vigilante (Detección de Somnolencia)
**Objetivo**: Alertar al conductor si se duerme  
**Duración**: 1-2 semanas  
**Tareas**:
- [ ] Implementar Frame Processor para cámara frontal (480p @ 10fps)
- [ ] Usar ML Kit Face Detection para puntos faciales
- [ ] Calcular Eye Aspect Ratio (EAR)
- [ ] Lógica: Si EAR < threshold por > 2 segundos → vibrar + sonido
- [ ] Permite ajustar sensibilidad (slider en Settings)
- [ ] Permite desactivar temporalmente (snooze 5 min)

**Definición de listo**: El conductor recibe alerta cuando se duerme, EAR calibrado por persona

---

### FASE 4: La Vitrina (UI + Monetización)
**Objetivo**: App pulida para lanzamiento  
**Duración**: 2-3 semanas  
**Tareas**:
- [ ] Pantalla de onboarding (explicar permisos, riesgos de OIS)
- [ ] Integración con RevenueCat (prueba gratis, suscripciones)
- [ ] Pantalla de "Incidentes Guardados" con preview y acciones (share, delete)
- [ ] Settings con controles de sensibilidad (audio, giroscopio, etc.)
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

**Decisión Final**: **React Native Vision Camera para MVP**  
**Justificación**: Lanzar rápido validar. Post-MVP, migrar a nativo si necesario.

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

## 🚨 SISTEMA DE DETECCIÓN DE EVENTOS

### Trigger 1: Botón de Pánico (CRÍTICO)
```
Prioridad: ⭐⭐⭐ DEBE estar en MVP
Cobertura: 70% (el usuario VE el peligro primero)
Implementación: 2 horas
Ubicación: Centro de pantalla, prominente
Acción: saveBufferOnImpact() guarda últimos 30 seg
```

### Trigger 2: Giroscopio (ALTA)
```
Prioridad: ⭐⭐⭐ Implementar en MVP
Cobertura: 15% adicional (derrapes, volcamientos)
Implementación: 1 día (integrate react-native-sensors)
Umbral: Rotación > 3.0 rad/seg indica evento anormal
Casos: Derrape, pérdida de control, vuelco, impacto lateral
```

### Trigger 3: Acelerómetro (BAJA - BACKUP)
```
Prioridad: ⭐ Mantener por compatibilidad
Cobertura: No confiable (máximo ~1.3G en pruebas reales)
Umbral: Si implementar, usar > 2.5G (muy conservador)
Nota: NO CONFIAR en este como trigger primario
```

### Trigger 4: GPS Desaceleración (OPCIONAL - POST-MVP)
```
Prioridad: ⭐ Implementar en Fase 2 si necesario
Cobertura: 10% adicional
Problema: 1-2 seg de lag, no funciona en túneles
Decisión: Esperar a validación de MVP antes de agregar
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
├── Frame Processor: ML Kit Face Detection
├── Almacenamiento: NINGUNO (frames se descartan)
├── Cálculo: Eye Aspect Ratio (EAR)
└── Trigger alerta: EAR < 0.2 × > 2 segundos

EAR CÁLCULO:
├── Puntos faciales: p1-p6 (ojos)
├── Vertical1 = distance(p2, p6)
├── Vertical2 = distance(p3, p5)
├── Horizontal = distance(p1, p4)
└── EAR = (vertical1 + vertical2) / (2.0 × horizontal)

UMBRALES CONFIGURABLES:
├── Closed eye threshold: EAR < 0.2 (ajustable)
├── Duration: > 2 segundos (ajustable 1-5 seg)
├── Snooze: 5 minutos (usuario puede ignorar alerta)
└── Allowed per hour: 3 alertas (anti-spam)

ACCIÓN AL DETECTAR:
├── Vibración: Patrón [0, 500, 200, 500] ms
├── Sonido: Alarma de 2 segundos (ajustable volumen)
├── Visual: Notificación en pantalla
└── LOG: Registro del evento para análisis

CONSUMO ESTIMADO:
├── CPU: 4-6% (ML Kit en GPU/NPU)
├── Almacenamiento: 0 bytes (nada se guarda)
├── Batería: ~4% por hora
└── Temperatura: +2-3°C sobre ambiente
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
├── Channel ID: 'dashcam-service'
├── Notification ID: 1234
├── Título: "🎥 DashCam activa"
├── Mensaje: "Vigilando"
├── Importancia: high
└── No dismissable: true

WATCHDOG (WorkManager):
├── Intervalo: Cada 15 minutos
├── Acción: Verificar si servicio está activo
├── Si muere: Reiniciar Foreground Service
├── Persistencia: Supera reseteos del OS

PERMISOS REQUERIDOS:
├── android.permission.CAMERA
├── android.permission.RECORD_AUDIO
├── android.permission.WRITE_EXTERNAL_STORAGE
├── android.permission.FOREGROUND_SERVICE
├── android.permission.POST_NOTIFICATIONS
└── android.permission.BODY_SENSORS (acelerómetro/giroscopio)

BATTERY OPTIMIZATION HANDLING:
├── Detectar si optimización está ON
├── Mostrar tutorial en onboarding
├── Link directo a Settings (Intent)
└── Re-prompt cada 30 días
```

### 2.5 Almacenamiento y Gestión de Archivos

```
ESTRUCTURA:
├── CACHE (context.cacheDir):
│   └── segment_[timestamp].mp4 (15 seg, se descarta automático)
│
└── DOWNLOADS (RNFS.DownloadDirectoryPath):
    └── incident_[timestamp]_part[0-1].mp4 (permanente, usuario ve)

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
⚠️ Falsos positivos de giroscopio en curvas normales  
⚠️ Optimización de batería en Xiaomi/Samsung/Huawei (cada marca es diferente)  
⚠️ Compatibilidad con frame rates variables en cámaras diferentes

### Decisiones tomadas para MVP
1. **Simpler is better**: Cache del OS (no RAM puro) para lanzar rápido
2. **Multi-sensor es crítico**: Un solo trigger falló en pruebas reales
3. **Freemium funciona**: 5 incidentes/mes impulsa conversión
4. **Conductor es user, no empresa**: Segmentación clara en Go-to-Market

### Próximas validaciones
- [ ] Testing en mínimo 5 modelos de dispositivos
- [ ] Calibración de umbrales de audio en mundo real
- [ ] A/B testing de UX (dónde poner botón pánico)
- [ ] NPS de primeros 50 usuarios
- [ ] Análisis de false positive rate por semana

---

**Documento versión**: 1.0  
**Última actualización**: Mayo 27, 2026  
**Siguiente review**: Después de beta testing inicial (estimado Julio 2026)

**Contactar**: [Oscar's info aquí]  
**Repositorio**: [GitHub link aquí]

---

*Este documento es la fuente única de verdad para especificaciones del proyecto. Cualquier desviación debe ser documentada aquí y comunicada a todos los agentes/desarrolladores.*