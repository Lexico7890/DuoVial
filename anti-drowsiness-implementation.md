# Anti-Drowsiness Implementation Guide

**Proyecto**: DuoVial — Dash Cam Inteligente  
**Feature**: Detección de somnolencia del conductor (Anti-Drowsiness)  
**Última actualización**: Junio 15, 2026  
**Alcance**: Documento técnico para la capa nativa/servicios (*backend*) de Android  
**Restricción**: Este documento no contiene código fuente. Solo arquitectura, tecnologías, metodologías, mejores prácticas y criterios de implementación.

---

## 📌 Tabla de contenidos

1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Alcance: ¿qué significa "backend" en esta feature?](#2-alcance-qué-significa-backend-en-esta-feature)
3. [Arquitectura objetivo](#3-arquitectura-objetivo)
4. [Componentes del backend nativo](#4-componentes-del-backend-nativo)
5. [Migración de cámara frontal a CameraX](#5-migración-de-cámara-frontal-a-camerax)
6. [Validación de cámaras concurrentes](#6-validación-de-cámaras-concurrentes)
7. [Integración con Health Connect para wearables](#7-integración-con-health-connect-para-wearables)
8. [Estrategia de fallback y sugerencia de wearables](#8-estrategia-de-fallback-y-sugerencia-de-wearables)
9. [Persistencia y sincronización de estado](#9-persistencia-y-sincronización-de-estado)
10. [Manejo de errores y recuperación](#10-manejo-de-errores-y-recuperación)
11. [Estrategia de testing](#11-estrategia-de-testing)
12. [Mejores prácticas y anti-patrones](#12-mejores-prácticas-y-anti-patrones)
13. [Checklist de implementación](#13-checklist-de-implementación)
14. [Wearables recomendados para Health Connect](#14-wearables-recomendados-para-health-connect)

---

## 1. Resumen ejecutivo

La funcionalidad de **anti-somnolencia** tiene como objetivo detectar cuando el conductor está perdiendo la atención por cansancio y alertarlo de inmediato mediante vibración y sonido.

El sistema debe funcionar bajo las siguientes restricciones de negocio:

- La **cámara frontal** solo se activa cuando el usuario está en la pantalla `FatigueScreen`. No debe funcionar en background para ahorrar batería y cumplir políticas de privacidad.
- La **detección en background** (cuando la app no está visible o el usuario no está en FatigueScreen) se delega a un **wearable** compatible conectado vía Health Connect.
- Si el dispositivo **no permite usar la cámara trasera y frontal al mismo tiempo**, la app debe:
  - Informar claramente al usuario.
  - Pausar el modo Vigilante mientras se usa la cámara frontal.
  - Sugerir el uso de un wearable para detección continua de fatiga.

Esta guía documenta cómo debe implementarse la capa nativa/servicios de Android para cumplir estos requisitos de forma robusta, eficiente y mantenible.

---

## 2. Alcance: ¿qué significa "backend" en esta feature?

En el contexto de DuoVial, el **backend de la feature anti-somnolencia** no es un servidor remoto, sino la capa de lógica de negocio que corre fuera de la UI de Compose Multiplatform. Esto incluye:

| Componente | Responsabilidad |
|------------|-----------------|
| `BackgroundCameraService` | Servicio en primer plano que coordina cámara trasera, sensores y cámara frontal. |
| `FrontFaceDetector` (refactorizado) | Gestiona la cámara frontal con CameraX, procesa frames con ML Kit y calcula EAR. |
| `Health Connect Client` | Lee datos de salud del wearable en background. |
| `FatigueAnalyzer` | Fusiona señales de cámara frontal y wearable para decidir si hay fatiga. |
| `SettingsManager` (nativo) | Persiste configuración de fatiga (umbral EAR, duración, max alertas, estado on/off). |
| `CameraStatusListener` / `AppStateManager` | Comunicación entre servicio y UI mediante StateFlow / callbacks. |

La UI (Compose) no implementa lógica de negocio. Solo muestra el estado que emite el backend y envía comandos al servicio.

---

## 3. Arquitectura objetivo

El sistema de anti-somnolencia se compone de tres fuentes de detección, ordenadas por prioridad y consumo:

```
┌────────────────────────────────────────────────────────────────┐
│                   ANTI-DROWSINESS BACKEND                      │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│   Nivel 1 — Cámara frontal (ML Kit EAR)                       │
│   ├── Solo activa en FatigueScreen                            │
│   ├── Preview + ImageAnalysis con CameraX                     │
│   ├── ML Kit Face Detection procesa cada frame                │
│   └── Alerta inmediata si EAR < umbral × duración             │
│                                                                │
│   Nivel 2 — Wearable vía Health Connect                       │
│   ├── Funciona en background                                  │
│   ├── Lee HeartRate, HRV, Sleep, Steps                        │
│   └── Detección por tendencias y anomalías                    │
│                                                                │
│   Nivel 3 — Fusión y fallback                                 │
│   ├── Si hay cámara + wearable: combinar señales              │
│   ├── Si solo hay cámara: usar cámara frontal                 │
│   ├── Si solo hay wearable: usar wearable                     │
│   └── Si no hay ninguno: sugerir wearable compatible          │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Principios arquitectónicos

1. **Una sola fuente de verdad**: el servicio nativo es el dueño del estado. La UI solo refleja ese estado.
2. **Bajo acoplamiento**: el detector facial, el lector de Health Connect y el analizador de fatiga deben ser componentes independientes que se comunican por interfaces.
3. **Graceful degradation**: si un componente no está disponible, el sistema sigue funcionando con los que quedan.
4. **Ahorro de recursos**: nunca mantener la cámara frontal encendida cuando no sea necesario.

---

## 4. Componentes del backend nativo

### 4.1. FatigueCameraManager

Responsable de iniciar, detener y gestionar la cámara frontal para la detección facial.

**Responsabilidades**:
- Configurar CameraX con `Preview` y `ImageAnalysis`.
- Entregar frames de `ImageAnalysis` al procesador de ML Kit.
- Gestionar el `PreviewView` de forma reactiva (crear/destruir sesión automáticamente según lifecycle).
- Liberar todos los recursos cuando se detiene la detección.

**Diferencias clave con la implementación anterior (Camera2 manual)**:
- No manejar `SurfaceTexture` ni `ImageReader` manualmente.
- No depender de variables estáticas para compartir la superficie de preview.
- Delegar el lifecycle de la cámara a CameraX.

### 4.2. FaceProcessor

Responsable de analizar cada frame con ML Kit Face Detection y calcular el EAR.

**Responsabilidades**:
- Recibir frames en formato compatible con ML Kit.
- Detectar rostros y seleccionar el del conductor.
- Calcular EAR para ambos ojos.
- Emitir eventos con: `faceDetected`, `earValue`, `closedEyeDuration`.
- No emitir alertas directamente; solo informa mediciones.

**Consideraciones**:
- Ejecutar el procesamiento en un hilo dedicado para no bloquear la UI.
- Descartar frames si el procesador anterior aún no ha terminado (evitar saturación).
- No guardar imágenes ni video en disco.

### 4.3. FatigueAnalyzer

Responsable de decidir si el conductor tiene somnolencia basándose en las señales disponibles.

**Responsabilidades**:
- Recibir mediciones de EAR desde FaceProcessor.
- Recibir datos de wearable desde Health Connect.
- Aplicar umbrales configurables.
- Gestionar contadores de alertas por hora, snooze y cooldowns.
- Emitir evento de `drowsinessDetected` cuando se cumplan las condiciones.

**Reglas de negocio**:
- Si EAR < umbral durante más de `durationThresholdMs` → fatiga.
- Máximo `maxAlertsPerHour` alertas por hora.
- Si el usuario activa snooze, ignorar alertas durante el tiempo configurado.
- Si hay wearable, complementar con anomalías de FC/HRV.

### 4.4. HealthConnectFatigueReader

Responsable de leer datos de salud del wearable a través de Health Connect.

**Responsabilidades**:
- Verificar si Health Connect está instalado y disponible.
- Solicitar permisos de lectura de datos de salud relevantes.
- Consultar periódicamente HeartRate, HRV, Steps y SleepSession.
- Emitir tendencias de fatiga (por ejemplo: caída de HRV, falta de movimiento).

**Datos a leer**:
- `HeartRate`: frecuencia cardíaca.
- `HeartRateVariability`: variabilidad del ritmo cardíaco (si está disponible).
- `Steps` / `ActiveCalories`: actividad reciente.
- `SleepSession`: calidad de sueño previo (contexto, no alerta inmediata).

### 4.5. FatigueAlertManager

Responsable de ejecutar la alerta física cuando FatigueAnalyzer lo indica.

**Responsabilidades**:
- Vibración con patrón configurable.
- Sonido de alarma usando recursos locales o ToneGenerator como fallback.
- Notificar a la UI para que muestre overlay visual.
- Respetar snooze y límites por hora.

---

## 5. Migración de cámara frontal a CameraX

### 5.1. ¿Por qué migrar?

La implementación actual usa Camera2 manual con `TextureView` + `ImageReader`. Esto genera:

- Race conditions entre la creación del detector y la disponibilidad del `Surface`.
- Dificultad para reconfigurar la sesión cuando el preview se destruye o recrea.
- Bug actual: el preview se congela tras ~1 segundo.

CameraX resuelve estos problemas porque abstrae:
- Vinculación del `Surface` al `PreviewView`.
- Lifecycle de la cámara.
- Entrega de frames a `ImageAnalysis`.
- Reconfiguración automática ante cambios de orientación o recomposición.

### 5.2. Casos de uso de CameraX

| Caso de uso | Propósito |
|-------------|-----------|
| `Preview` | Mostrar la imagen de la cámara frontal en `FrontCameraPreview`. |
| `ImageAnalysis` | Entregar frames a ML Kit Face Detection para cálculo de EAR. |

### 5.3. Configuración recomendada

- **Resolución**: 640 × 480 (VGA).
- **FPS objetivo**: 10 fps.
- **Modo de análisis**: `STRREAM` o `NON_BLOCKING`, según el caso.
- **Selector de cámara**: `CameraSelector.DEFAULT_FRONT_CAMERA`.
- **Backpressure strategy**: descartar frames si el procesador está ocupado.

### 5.4. Gestión del lifecycle

El detector facial debe vincularse al ciclo de vida del `FatigueScreen`:

- Cuando `FatigueScreen` se muestra y el usuario activa la detección → iniciar detector.
- Cuando `FatigueScreen` se oculta o el usuario desactiva → detener detector.
- Nunca mantener la cámara frontal activa si no hay UI visible.

### 5.5. Separación de responsabilidades

No mezclar la lógica de cámara con la lógica de ML Kit ni con la lógica de alertas. Cada componente debe tener una única responsabilidad.

---

## 6. Validación de cámaras concurrentes

### 6.1. Problema

No todos los dispositivos Android permiten abrir la cámara trasera y frontal simultáneamente. Si la app intenta usar ambas y el hardware no lo soporta, una de las dos cámaras fallará o se cerrará inesperadamente.

### 6.2. Cómo detectar soporte

Android proporciona una API para consultar qué combinaciones de cámaras pueden operar al mismo tiempo:

- `CameraManager.getConcurrentCameraIds()` devuelve conjuntos de IDs de cámara que pueden abrirse simultáneamente.
- Se debe verificar si existe un conjunto que incluya la cámara trasera (`LENS_FACING_BACK`) y la frontal (`LENS_FACING_FRONT`).

Esta validación debe realizarse:
- Al iniciar la app por primera vez.
- Antes de abrir la cámara frontal en `FatigueScreen`.
- Si cambia la configuración de cámaras del dispositivo (raro, pero posible).

### 6.3. Comportamiento según capacidad

| Escenario | Acción del backend |
|-----------|--------------------|
| Soporta cámaras concurrentes | Mantener modo Vigilante activo mientras se usa cámara frontal. |
| No soporta cámaras concurrentes | Pausar modo Vigilante al entrar a FatigueScreen; reanudar al salir. |
| No se puede determinar | Asumir que no soporta (modo seguro). |

### 6.4. Comunicación a la UI

El resultado de la validación debe persistirse en el estado de la app y mostrarse en `FatigueScreen` y/o `SettingsScreen`:

- Mensaje informativo si el dispositivo no soporta ambas cámaras.
- Indicador de que el modo Vigilante se pausará temporalmente.
- Sugerencia de usar wearable para detección continua.

---

## 7. Integración con Health Connect para wearables

### 7.1. ¿Por qué Health Connect?

Health Connect es el estándar de Google para unificar datos de salud de múltiples apps y dispositivos. Ventajas:

- No depende de un fabricante específico.
- Funciona en background en Android 14+.
- Expone datos estandarizados: FC, HRV, pasos, sueño.
- El usuario controla qué datos comparte.

### 7.2. Requisitos

- Dispositivo con Android 14 (API 34) o superior.
- App de Health Connect instalada en el dispositivo.
- Wearable que sincronice datos con Health Connect o Google Fit.
- Permisos de lectura concedidos por el usuario.

### 7.3. Datos a leer

| Tipo de dato | Uso para fatiga |
|--------------|-----------------|
| `HeartRate` | Detectar bradicardia o taquicardia sostenida. |
| `HeartRateVariability` | Caída de HRV asociada a estrés o somnolencia. |
| `Steps` | Detectar inactividad prolongada (conductor quieto). |
| `ActiveCalories` | Complementar información de actividad. |
| `SleepSession` | Contexto de calidad de sueño previo. |

### 7.4. Frecuencia de lectura

- **HeartRate**: cada 1-5 minutos en background.
- **HRV**: cada 15-30 minutos (no siempre disponible en tiempo real).
- **Steps/Calorías**: cada 5-15 minutos.
- **Sleep**: al inicio de la sesión de conducción (contexto histórico).

### 7.5. Lógica de detección por wearable

El wearable no puede detectar parpadeos, pero sí detectar señales fisiológicas de fatiga:

- **Caída de HRV**: correlacionada con fatiga y estrés.
- **Cambios en frecuencia cardíaca**: descenso o ascenso anormal sostenido.
- **Inactividad prolongada**: bajos pasos durante la conducción.
- **Calidad de sueño previo**: contexto para ajustar sensibilidad.

Esta lógica debe ser conservadora al principio para evitar falsos positivos. Se recomienda un sistema de puntuación o umbrales suaves.

### 7.6. Fallback si Health Connect no está disponible

- Si Health Connect no está instalado: ofrecer enlace a Play Store para instalarlo.
- Si el wearable no sincroniza: mostrar guía de emparejamiento y lista de wearables compatibles.
- Si el usuario no concede permisos: deshabilitar detección por wearable y usar solo cámara frontal (si aplica).

---

## 8. Estrategia de fallback y sugerencia de wearables

### 8.1. Matriz de decisiones

| Cámara frontal disponible | Wearable disponible | Concurrente | Acción recomendada |
|---------------------------|---------------------|-------------|--------------------|
| Sí | Sí | Sí | Usar ambas. Cámara frontal en FatigueScreen; wearable en background. |
| Sí | Sí | No | Pausar Vigilante en FatigueScreen; wearable en background. |
| Sí | No | Sí | Usar solo cámara frontal en FatigueScreen. |
| Sí | No | No | Usar cámara frontal en FatigueScreen; sugerir wearable para background. |
| No | Sí | — | Usar wearable para detección en background. |
| No | No | — | Mostrar pantalla de sugerencia de wearables. |

### 8.2. Pantalla de sugerencia de wearables

Cuando el sistema detecta que no hay una fuente viable de anti-somnolencia, debe mostrar una pantalla o sección con:

- Explicación breve de por qué se necesita un wearable o cámara frontal.
- Lista de wearables recomendados (mínimo 10 opciones).
- Indicador de compatibilidad con Health Connect.
- Links de búsqueda a Mercado Libre para cada dispositivo.
- Instrucciones de emparejamiento básicas.

La lista debe ordenarse por popularidad, precio y compatibilidad confirmada con Health Connect.

### 8.3. Mensajes al usuario

- **Dispositivo sin cámaras concurrentes**: "Tu celular no permite usar las dos cámaras al mismo tiempo. El modo Vigilante se pausará mientras uses la cámara frontal. Para monitorear fatiga sin interrumpir la vigilancia, conecta una pulsera o reloj inteligente."
- **Sin wearable**: "La detección de fatiga en background requiere una pulsera o reloj compatible. Aquí tienes opciones recomendadas."
- **Health Connect no disponible**: "Necesitas instalar Health Connect para leer datos de tu wearable."

---

## 9. Persistencia y sincronización de estado

### 9.1. Configuración a persistir

| Campo | Tipo | Default |
|-------|------|---------|
| `earThreshold` | Double | 0.2 |
| `durationThresholdMs` | Long | 2000 |
| `maxAlertsPerHour` | Int | 3 |
| `snoozeMinutes` | Int | 5 |
| `fatigueEnabled` | Boolean | false |
| `wearableEnabled` | Boolean | false |
| `supportsConcurrentCameras` | Boolean | determinado en runtime |

### 9.2. Cuándo guardar

- Al modificar cualquier slider o toggle en `FatigueScreen`.
- Al activar/desactivar la detección de fatiga.
- Al detectar por primera vez si el dispositivo soporta cámaras concurrentes.

### 9.3. Cuándo restaurar

- Al iniciar la app (`MainActivity.restoreSettings()`).
- Al reconectar el servicio si fue destruido y recreado.

### 9.4. Sincronización UI ↔ Servicio

- La UI envía comandos al servicio mediante `CameraServiceManager`.
- El servicio actualiza `AppStateManager` mediante `CameraStatusListener`.
- La UI observa `AppStateManager` con `collectAsState()`.
- Al reconectar, el servicio debe re-emitir su estado actual para evitar desincronización.

---

## 10. Manejo de errores y recuperación

### 10.1. Errores esperados

| Error | Causa probable | Acción |
|-------|----------------|--------|
| Cámara frontal no disponible | Otra app la está usando o permiso denegado | Informar al usuario y desactivar detección facial. |
| Preview congelado | Problema de Surface o cámara ocupada | Detener y reiniciar el detector automáticamente. |
| ML Kit no detecta rostro | Mala iluminación o rostro fuera de frame | Mostrar "Buscando rostro..." sin alerta. |
| Health Connect no disponible | No instalado o no soportado | Ofrecer instalación o usar solo cámara. |
| Permisos de Health Connect denegados | Usuario rechazó | Deshabilitar wearable y usar cámara si aplica. |
| Dispositivo no soporta cámaras concurrentes | Limitación de hardware | Pausar Vigilante y sugerir wearable. |
| Servicio destruido por el OS | Android mató el proceso | WorkManager Watchdog debe reiniciarlo. |

### 10.2. Estrategia de recuperación

- Implementar un mecanismo de **retry con backoff** para apertura de cámara frontal.
- Si el detector falla más de N veces en poco tiempo, desactivarlo y notificar a la UI.
- Mantener siempre una ruta de fallback (wearable o mensaje al usuario).
- Nunca dejar la UI en un estado bloqueado. El botón `forceReset` debe poder recuperar el control.

### 10.3. Logging

Agregar logs estructurados en todos los puntos críticos:

- Inicio/parada del detector.
- Cambios de estado de la cámara.
- Errores de sesión, surface y cámara.
- Eventos de ML Kit (rostro detectado/no detectado).
- Lecturas de Health Connect.
- Alertas de fatiga emitidas.

Esto permitirá diagnosticar problemas sin necesidad de reproducir manualmente.

---

## 11. Estrategia de testing

### 11.1. Testing manual

1. **Preview estable**: entrar y salir de `FatigueScreen` 10 veces. El preview no debe congelarse.
2. **Activar/desactivar**: presionar el botón `ACTIVAR` y `DETENER` repetidamente sin bloqueos.
3. **Detección facial**: simular ojos cerrados durante más del umbral configurado. Debe sonar la alerta.
4. **Rotación**: rotar el dispositivo y verificar que el preview se recupere.
5. **Background**: salir de la app con detección activa. La cámara frontal debe detenerse.

### 11.2. Testing de cámaras concurrentes

- En dispositivos que soportan ambas cámaras: verificar que Vigilante y Fatigue funcionan juntos.
- En dispositivos que no soportan: verificar que Vigilante se pausa y se reanuda correctamente.
- Probar al menos 3 dispositivos de gamas diferentes.

### 11.3. Testing de wearable (Health Connect)

- Probar con al menos 2-3 wearables diferentes si es posible.
- Verificar que los permisos se solicitan correctamente.
- Simular datos de Health Connect y verificar que se reflejan en la lógica de fatiga.

### 11.4. Testing de estrés

- Dejar la cámara frontal activa 30 minutos en `FatigueScreen`.
- Medir consumo de batería y temperatura.
- Verificar que no hay fugas de memoria.

---

## 12. Mejores prácticas y anti-patrones

### ✅ Mejores prácticas

1. **Usar CameraX para la cámara frontal**. Evita gestión manual de `Surface`.
2. **No mantener la cámara frontal activa fuera de `FatigueScreen`**. Ahorra batería y cumple políticas.
3. **Separar detección de alerta**. El detector mide; el analizador decide; el alert manager actúa.
4. **Persistir toda la configuración**. El usuario no debe reconfigurar en cada sesión.
5. **Validar cámaras concurrentes en runtime**. No asumir que todos los dispositivos las soportan.
6. **Usar Health Connect como primera opción** para wearables. Evita acoplamiento a fabricantes.
7. **Implementar fallback claro**. Siempre debe haber una opción para el usuario.
8. **Logging exhaustivo**. Facilita diagnóstico sin reproducir el bug.
9. **Liberar recursos en `onDestroy`**. Cámaras, sesiones, listeners, players, vibradores.
10. **Manejar permisos de forma granular**. No pedir todos los permisos al inicio si no son necesarios.

### ❌ Anti-patrones a evitar

1. **Variables estáticas para compartir estado** (como `pendingPreviewSurface` actual). Causan race conditions y fugas.
2. **Mezclar Camera2 manual con CameraX** sin una razón de peso. Aumenta complejidad innecesariamente.
3. **Procesar frames de ML Kit en el hilo principal**. Congela la UI.
4. **Mantener la cámara frontal activa en background**. Consume batería y viola políticas de privacidad.
5. **Asumir que todos los dispositivos soportan cámaras concurrentes**. Debe validarse en runtime.
6. **Depender de un solo fabricante de wearables**. Limita escalabilidad.
7. **Guardar imágenes o video de la cámara frontal**. La feature promete no almacenar video facial.
8. **No manejar errores de apertura de cámara**. La app puede quedar en estado inconsistente.
9. **Acoplar la UI directamente al servicio**. Siempre pasar por `CameraServiceManager` / `AppStateManager`.
10. **Hardcodear umbrales o strings**. Todo debe ser configurable y localizable.

---

## 13. Checklist de implementación

### Fase A: Refactorización de cámara frontal
- [ ] Reemplazar `FrontFaceDetector` (Camera2 manual) por `FatigueCameraManager` + `FaceProcessor` (CameraX).
- [ ] Reemplazar `TextureView` por `PreviewView` en `FrontCameraPreview`.
- [ ] Configurar `Preview` + `ImageAnalysis` a 640×480 @ 10fps.
- [ ] Entregar frames a ML Kit Face Detection de forma no bloqueante.
- [ ] Calcular EAR y emitir `FaceStatus`.
- [ ] Integrar inicio/parada con lifecycle de `FatigueScreen`.

### Fase B: Validación de cámaras concurrentes
- [ ] Consultar `CameraManager.getConcurrentCameraIds()` al inicio.
- [ ] Persistir resultado en configuración.
- [ ] Si no hay soporte: pausar Vigilante al entrar a FatigueScreen, reanudar al salir.
- [ ] Mostrar mensaje informativo al usuario.

### Fase C: Health Connect
- [ ] Agregar permiso `HEALTH_CONNECT` en el manifest.
- [ ] Verificar disponibilidad de Health Connect en runtime.
- [ ] Solicitar permisos de lectura de HeartRate, HRV, Steps, Sleep.
- [ ] Implementar `HealthConnectFatigueReader` con polling periódico.
- [ ] Integrar datos en `FatigueAnalyzer`.

### Fase D: Persistencia y estado
- [ ] Extender `SettingsManager` con todos los campos de fatiga.
- [ ] Restaurar configuración en `MainActivity.restoreSettings()`.
- [ ] Sincronizar estado UI con estado real del servicio.
- [ ] Implementar `resyncState()` para reconexiones.

### Fase E: UX de fallback
- [ ] Crear pantalla/sección de sugerencia de wearables.
- [ ] Incluir mínimo 10 opciones con links a Mercado Libre.
- [ ] Mostrar mensajes contextuales según capacidad del dispositivo.
- [ ] Agregar guía de emparejamiento básica.

### Fase F: Robustez
- [ ] Agregar logs en todos los puntos críticos.
- [ ] Implementar retry con backoff para apertura de cámara.
- [ ] Manejar todos los errores esperados sin bloquear la app.
- [ ] Probar en Oppo A80 5G y al menos 2 dispositivos adicionales.

---

## 14. Wearables recomendados para Health Connect

> **Nota importante**: Health Connect requiere Android 14+ y que el wearable exponga datos a Health Connect o Google Fit. La compatibilidad exacta varía por modelo, región y versión de firmware. Los links son búsquedas en Mercado Libre Colombia; verifica la compatibilidad del modelo específico antes de comprar.

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

### Criterios para elegir un wearable

1. **Soporte Health Connect**: lo ideal es que el fabricante integre Health Connect nativamente.
2. **Batería**: mínimo 3-5 días para pulseras, 1-2 días para smartwatches.
3. **Frecuencia cardíaca continua**: necesario para detección de tendencias.
4. **HRV**: mejor si lo soporta, aunque no es obligatorio para MVP.
5. **Compatibilidad con Google Fit**: si no tiene Health Connect directo, Google Fit puede actuar como puente.
6. **Precio**: para conductores de gig economy, opciones entre $100.000 y $400.000 COP son preferibles.

---

## 15. Notas finales

- Este documento debe revisarse después de completar la Fase 3B.
- Cualquier cambio en la arquitectura debe reflejarse tanto aquí como en `CONTEXT.md`.
- El objetivo final es que la detección de somnolencia sea estable, respetuosa con la batería y usable en la mayor cantidad de dispositivos posible.
- La prioridad de implementación es: (1) estabilizar cámara frontal con CameraX, (2) validar cámaras concurrentes, (3) integrar Health Connect, (4) pulir UX de fallback.
