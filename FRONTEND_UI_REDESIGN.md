# Frontend Implementation Notes — UI Redesign + Launcher Icon Fix

**Fecha**: Junio 12, 2026
**Estado**: Implementado y compilando (`./gradlew :composeApp:compileDebugKotlinAndroid` → BUILD SUCCESSFUL)
**Stack**: Kotlin Multiplatform + Jetpack Compose (Compose Multiplatform Resources)

---

## Resumen ejecutivo

Se realizaron dos cambios principales:

1. **Corrección del ícono de launcher**: El proyecto usaba un escudo verde hardcodeado en XML. Se reemplazó por el logo de `assets/icon.png` (círculo cyan con pupila roja) en todas las densidades.
2. **Rediseño de `MonitorScreen` con glassmorphism**: Se eliminó el botón central gigante y se reestructuró el layout según el bosquejo del usuario — header glass arriba, fila de telemetría glass en la parte inferior, y dos botones glass (play/stop circular + Evento rectangular).

---

## 1. Cambio de ícono de launcher

### Problema original
- `AndroidManifest.xml` apunta a `@mipmap/ic_launcher` (válido).
- Pero el drawable del adaptive icon era un vector XML (`ic_launcher_foreground.xml`) con un escudo verde hardcodeado — no había referencia al PNG `assets/icon.png`.
- En el dispositivo se mostraba el escudo verde en vez del logo que el usuario había diseñado.

### Solución aplicada

**Archivos nuevos** (todos redimensionados desde `assets/icon.png`):

```
kmp/composeApp/src/androidMain/res/
├── drawable/duovial_logo.png                          (324×324 px, foreground)
├── drawable-nodpi/ic_launcher_foreground.png          (324×324 px, copia)
├── mipmap-mdpi/ic_launcher.png                        (48×48)
├── mipmap-mdpi/ic_launcher_round.png                  (48×48)
├── mipmap-hdpi/ic_launcher.png                        (72×72)
├── mipmap-hdpi/ic_launcher_round.png                  (72×72)
├── mipmap-xhdpi/ic_launcher.png                       (96×96)
├── mipmap-xhdpi/ic_launcher_round.png                 (96×96)
├── mipmap-xxhdpi/ic_launcher.png                      (144×144)
├── mipmap-xxhdpi/ic_launcher_round.png                (144×144)
├── mipmap-xxxhdpi/ic_launcher.png                     (192×192)
├── mipmap-xxxhdpi/ic_launcher_round.png               (192×192)
└── mipmap-anydpi-v26/ic_launcher_round.xml            (NUEVO)
```

**Archivos modificados**:

- `kmp/composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml` — ahora es un `<inset>` que envuelve el PNG:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <inset xmlns:android="http://schemas.android.com/apk/res/android"
      android:drawable="@drawable/duovial_logo"
      android:inset="18%" />
  ```
  El 18% de inset es la zona segura recomendada por Material Design para adaptive icons.

- `kmp/composeApp/src/commonMain/composeResources/drawable/ic_duovial_logo.png` — copia del PNG original, accesible desde Compose con `painterResource(Res.drawable.ic_duovial_logo)`.

### Justificación del cambio
- Adaptive icon en `mipmap-anydpi-v26/ic_launcher.xml` (existente) ya referenciaba `@drawable/ic_launcher_foreground`, así que solo hubo que cambiar el contenido de ese drawable.
- Los PNGs en cada densidad (`mipmap-mdpi/`, `mipmap-hdpi/`, etc.) son necesarios para OEMs (Xiaomi MIUI, Samsung One UI) que a veces ignoran el adaptive icon y buscan PNGs legacy.
- El `_round` se incluye para dispositivos que apliquen máscara circular.

---

## 2. Componente reutilizable `Modifier.glass`

### Archivos nuevos

**`kmp/composeApp/src/commonMain/kotlin/com/duovial/components/GlassSurface.kt`**:
```kotlin
@Composable
fun Modifier.glass(
    cornerRadius: Dp = 16.dp,
    blurRadius: Dp = 20.dp,
    borderAlpha: Float = 0.25f,
    fillAlpha: Float = 0.12f
): Modifier
```

Aplica:
1. `platformBlur(blurRadius)` — desenfoque nativo (RenderEffect en Android 12+, no-op en < 12).
2. Fondo blanco con alpha = `fillAlpha` (12% por defecto) usando `RoundedCornerShape(cornerRadius)`.
3. Borde blanco 1dp con alpha = `borderAlpha` (25% por defecto).

**`kmp/composeApp/src/commonMain/kotlin/com/duovial/components/PlatformBlur.kt`** (expect):
```kotlin
@Composable
expect fun Modifier.platformBlur(radius: Dp): Modifier
```

**`kmp/composeApp/src/androidMain/kotlin/com/duovial/components/PlatformBlur.kt`** (actual):
```kotlin
@Composable
actual fun Modifier.platformBlur(radius: Dp): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val density = LocalDensity.current
    val px = with(density) { radius.toPx() }
    return this.then(Modifier.graphicsLayer {
        renderEffect = RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    })
}
```

### Decisión técnica
- **`expect/actual` en lugar de `Modifier.then` condicional**: Permite que el `glass()` sea `@Composable` en commonMain y degrade elegantemente en < Android 12 (sin blur, solo gradiente + borde).
- **Por qué `RenderEffect` y no `BlurMaskFilter`**: `BlurMaskFilter` no funciona con shapes (RoundedCornerShape). `RenderEffect` (API 31+) sí y es la única forma soportada de hacer glassmorphism real con shapes en Compose.
- **Performance**: 4-5 instancias de `glass()` en pantalla (header + 2 círculos + 2 botones) con `blurRadius=20.dp` — testeable. En dispositivos de gama baja considerar reducir a 10dp.

### Uso
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
        .glass(cornerRadius = 24.dp)
) { /* contenido */ }
```

---

## 3. Reescritura de `MonitorScreen.kt`

### Archivo
`kmp/composeApp/src/commonMain/kotlin/com/duovial/screens/MonitorScreen.kt` — reescrito completo.

### Cambios estructurales

| Antes | Ahora |
|-------|-------|
| Header en una sola fila con G + REC + km/h comprimidos | Header glass con logo + nombre + dot rojo (REC) + ícono de ojo |
| Botón circular gigante al centro (160dp, "OFF"/"PANICO"/"GUARDANDO") | **Eliminado** |
| Texto "Vigilante apagado" flotando arriba del centro | Movido al centro de la fila de telemetría |
| Badge REC superpuesto | Dot rojo pequeño junto al logo (cuando está grabando) |
| Botón pánico + iniciar/detener en fila inferior plana | Fila inferior con un botón play/stop circular (56dp) + botón Evento rectangular (resto del ancho, 56dp) |

### Layout final (de arriba a abajo)

```
┌──────────────────────────────────────────┐
│ [LOGO cyan/red] DuoVial •    [👁]        │  ← GlassSurface header (h: 56dp)
├──────────────────────────────────────────┤
│                                          │
│                                          │
│        [Vista de cámara]                 │
│                                          │
│                                          │
│                                          │
├──────────────────────────────────────────┤
│ ┌G───┐  Vigilante apagado   ┌km/h─┐     │  ← GlassSurface telemetry (h: 80dp)
│ │1.09│                       │ 1  │     │
│ └────┘                       └────┘     │
├──────────────────────────────────────────┤
│  [▶]              EVENTO                 │  ← GlassSurface actions
└──────────────────────────────────────────┘
```

### Detalles de implementación

**Header** (`MonitorScreen.kt:104-150`):
- `Box` con `Modifier.glass(cornerRadius = 24.dp)`, altura 56dp.
- Logo cargado vía `painterResource(Res.drawable.ic_duovial_logo)` (28dp).
- Texto "DuoVial" en `titleMedium` con FontWeight.W900.
- Dot rojo de 8dp con `CircleShape` que aparece solo cuando `isRecording && !isSaving` (animación implícita con `AnimatedVisibility`).
- `IconButton` con `Icons.Outlined.Visibility` que navega a `FatigueScreen` vía callback `onOpenFatigue` (mismo comportamiento que la versión anterior).

**Fila de telemetría** (`MonitorScreen.kt:152-243`):
- `Box` con `Modifier.glass(cornerRadius = 24.dp)`, altura 80dp, alineado a `BottomCenter` con margen inferior 160dp.
- `Row` con `SpaceBetween` y 3 hijos:
  - **Círculo G** (izquierda, 64dp, `CircleShape`): el círculo también tiene `glass()` con `fillAlpha=0.08f` para que sea ligeramente más oscuro que el contenedor. Borde blanco 1dp separado. Color dinámico verde/amber/rojo según `gForce` (umbrales 2.0G amber, 3.5G rojo).
  - **Centro** (peso 1, padding 8dp): `statusLabel` multi-línea. Línea adicional "Grabando" cuando `isRecording && !isSaving`.
  - **Círculo km/h** (derecha, 64dp): simétrico al G. Color blanco fijo (no dinámico).
- `Column` dentro de cada círculo con label (`G` / `km/h`) arriba y valor grande (18sp) abajo.

**Botones de acción** (`MonitorScreen.kt:245-298`):
- `Row` con `spacedBy(12.dp)`, alineado a `BottomCenter` con padding bottom 16dp.
- **Botón play/stop** (izquierda, 56dp, `CircleShape`, `glass(cornerRadius = 28.dp)`):
  - Ícono: `Icons.Outlined.PlayArrow` (verde) cuando `!isRecording`, `Icons.Outlined.Stop` (rojo) cuando `isRecording`.
  - Acción: `serviceManager?.startRecording()` o `stopRecording()`.
  - `clickable(enabled = !isSaving)` — no permite interacción mientras se guarda.
- **Botón Evento** (derecha, `weight(1f)`, altura 56dp, `RoundedCornerShape(28.dp)`, `glass(cornerRadius = 28.dp)`):
  - Texto "EVENTO" en `labelLarge` con `letterSpacing = 2.sp`, color amber cuando `isRecording`, gris cuando no.
  - Cuando `isSaving`, muestra "GUARDANDO...".
  - Acción: `serviceManager?.triggerPanic()`.
  - `clickable(enabled = isRecording && !isSaving)`.

**Gradiente inferior** (`MonitorScreen.kt:91-99`):
- Altura aumentada de 200dp a 280dp para que el glass de los botones tenga suficiente contraste con la imagen de cámara.
- Color: `DuoVialBackground.copy(alpha = 0.85f)` para oscurecer sin hacer opaco (sigue viéndose el preview debajo).

### Imports añadidos
```kotlin
import androidx.compose.foundation.Image
import com.duovial.components.glass
import duovialkmp.composeapp.generated.resources.Res
import duovialkmp.composeapp.generated.resources.ic_duovial_logo
import org.jetbrains.compose.resources.painterResource
```

### Imports eliminados (ya no se usan)
```kotlin
import androidx.compose.material.icons.outlined.PlayArrow  // (sí se usa, falso)
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
```

`Button` y `ButtonDefaults` ya no se usan en MonitorScreen (los botones son `Box` con `clickable` y `glass()`).

---

## 4. Verificación

### Compilación
```bash
cd kmp
./gradlew :composeApp:compileDebugKotlinAndroid
```
**Resultado**: `BUILD SUCCESSFUL in 1m 39s`. Solo warnings de deprecations pre-existentes (VIBRATOR_SERVICE, CameraCaptureSession.createCaptureSession en FrontFaceDetector.kt) que no están relacionados con estos cambios.

### Verificación visual (manual en dispositivo)
1. Instalar APK.
2. **Launcher**: debe verse el círculo cyan con pupila roja sobre fondo azul oscuro (`#0D1B2A`).
3. **App abierta en Monitor**:
   - Header glass arriba con logo + "DuoVial" + ícono de ojo.
   - Centro: preview de cámara.
   - Fila de telemetría glass con G a la izquierda, estado en el centro, km/h a la derecha.
   - Abajo: botón play/stop circular a la izquierda, "EVENTO" rectangular a la derecha.
   - El efecto glass debe ser visible (desenfoque) en Android 12+; en < 12 se ve como un contenedor translúcido sólido.

### Casos de prueba
| Acción | Resultado esperado |
|--------|--------------------|
| Tap play/stop (estado OFF) | Pasa a REC, dot rojo aparece en header, ícono cambia a stop |
| Tap play/stop (estado ACTIVO) | Vuelve a INACTIVO, dot desaparece |
| Tap Evento (en ACTIVO) | Toast "Guardando incidente" + servicio captura 15s pre/post |
| Tap ícono de ojo | Navega a FatigueScreen |
| Bajar G-Force slider en Settings | Color del círculo G cambia según threshold |

---

## 5. Riesgos y notas

1. **Adaptive icon en Android < 8.0**: Si el target fuera < API 26, se necesitaría también un `mipmap/ic_launcher.xml` con `<bitmap>` en lugar de `<adaptive-icon>`. El proyecto usa `minSdk = 26`, así que está cubierto.

2. **Themed icon (Android 13+)**: No se agregó `mipmap-anydpi-v33/ic_launcher.xml` con la variante monocromática. Si en el futuro se quiere soportar themed icons, se debe agregar ese recurso. **No es bloqueante para la entrega actual**.

3. **Performance del blur**: 4-5 instancias de `RenderEffect.createBlurEffect` en pantalla pueden consumir ~3-5% CPU extra en gama baja. Si se observa lag, reducir `blurRadius` a 10dp o usar `BlurMaskFilter` como fallback (no soportado con shapes, requiere pre-render).

4. **Compatibilidad de `painterResource`**: `org.jetbrains.compose.resources:painterResource` requiere que el plugin `compose.multiplatform` esté aplicado. Ya está en `build.gradle.kts:7`. La dependencia `compose.components.resources` ya está en `commonMain.dependencies:26`.

5. **El ícono en la app store / Play Store**: El PNG se incluye en el APK pero NO se subirá a Play Console. Para Play Store se debe generar un ícono 512×512 desde el mismo `assets/icon.png`.

---

## 6. Próximos pasos sugeridos

- [ ] Reemplazar el botón "DETENER" gigante en el centro (ya hecho en este PR).
- [ ] Aplicar glass al `NavigationBar` inferior en `App.kt` para consistencia visual.
- [ ] Aplicar glass a la `FloatingActionButton` de pánico en `BackgroundCameraService.kt` (la burbuja flotante).
- [ ] Crear variante `mipmap-anydpi-v33/ic_launcher.xml` para themed icon en Android 13+.
- [ ] Reemplazar `ic_launcher_foreground.xml` por un vector en lugar de bitmap inset (más liviano en APK, mejor para OEMs que recomprimen PNGs).

---

**Autor de los cambios**: Implementación automatizada según bosquejo del usuario.
**Reviewer**: Pendiente.
**Build verificado**: `:composeApp:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL in 1m 39s`.
