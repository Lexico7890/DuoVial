# AGENTS.md — DuoVial

## What is this

Android app (KMP + Compose Multiplatform) that turns a phone into a smart dash cam. Includes a Supabase backend. The "Fleet" tier adds a web dashboard for fleet management (not yet built).

**Language**: Code is Kotlin (English). UI labels and enums are in Spanish (`INACTIVO`, `ACTIVO`, `GUARDANDO`).

## Build & run

All Gradle commands run from `kmp/` (not repo root):

```bash
cd kmp
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # run unit tests
./gradlew lintDebug              # lint check
./gradlew :composeApp:assembleRelease  # release build
```

No CI workflows exist yet (`.github/` is empty). No test source sets exist in the codebase yet.

## Project structure

```
kmp/composeApp/src/
  commonMain/kotlin/com/duovial/   # Compose UI, interfaces, state managers
  androidMain/kotlin/com/duovial/  # CameraX, ML Kit, Services, Sensors
supabase/
  migrations/      # 6 SQL files (001-006), 17 tables
  functions/       # 7 Edge Functions (Deno)
```

**Key directories**:
- `kmp/` — all Kotlin code lives here (Gradle project root)
- `supabase/` — backend (migrations + Edge Functions)
- `.opencode/agents/` — OpenCode agent configs (mid-level-dev, tech-lead, qa-engineer)
- `assets/` — source images for app icon/splash
- `scripts/` — just `resize_icon.ps1`

## Critical files (do not modify without review)

| File | Why |
|------|-----|
| `BackgroundCameraService.kt` | Core service (~1189 lines). Buffer circular, sensors, GPS, floating bubble. |
| `AppState.kt` | Central state. Any change affects all UI. |
| `CameraServiceManagerAndroid.kt` | Bridge between UI and service via Intents and callbacks. |
| `FatigueCameraManager.kt` | CameraX pipeline for drowsiness detection. |
| `FaceProcessor.kt` | EAR calculation logic. |

## Legacy code — do not use

| File | Status |
|------|--------|
| `FrontFaceDetector.kt` | Legacy Camera2. Use `FatigueCameraManager.kt` instead. |
| `AuthServiceAndroid.kt` | Demo mode only. Migrating to Supabase Auth. |
| `DuoVialConfig.kt` | AWS Cognito config. Being replaced by Supabase. |

## Architecture patterns

- **Interface in commonMain, implementation in androidMain** (e.g. `CameraServiceManager` interface → `CameraServiceManagerAndroid`).
- **expect/actual** for platform-specific composables (`CameraPreview`, `FrontCameraPreview`).
- **CompositionLocal** for DI (`LocalCameraServiceManager`).
- **Service is the single source of truth** for camera state. UI mirrors it via `AppStateManager` (a singleton `object`). The UI never holds independent camera state.
- **Pending values pattern**: if service isn't running when UI sets config, values are stored as `@Volatile` pending vars and applied in `onCreate`.

## Backend

Supabase project is deployed (cloud). Key details:
- **6 migrations** (`001_initial_schema.sql` through `006_billing.sql`)
- **7 Edge Functions**: `verify-google-purchase`, `create-wompi-link`, `wompi-webhook`, `process-recurring-billing`, `trigger-mux-transcode`, `mux-webhook-handler`, `send-push-notification`
- **Multi-tenancy** via `organizations` table + RLS with `current_org_id()` helper
- **Video processing**: Mux (partner of Supabase) for transcoding
- **Payments**: Google Play Billing (app) + Wompi (web/dashboard)
- **Local dev**: `supabase start` (uses Docker, config in `supabase/config.toml`)
- **Edge Functions tests**: `supabase/functions/__tests__/` (Deno test runner)

## Environment variables

See `.env.example` for the full list. Key vars use `SB_` prefix (not `SUPABASE_`) to avoid Supabase reserved names. Local dev uses `supabase/.env`.

## Gotchas

- No `gradlew` at repo root — it's inside `kmp/`.
- `BackgroundCameraService.kt` is the only file over 500 lines. It's the exception — keep it under review.
- Audio is **always off** in recording (legal + battery reasons). Never add audio capture.
- Front camera **never** records video. It only analyzes frames in real-time (ML Kit) and discards them.
- Facial recognition is **alert-only** — never block or take automated action against drivers.
- The `node_modules/` and `android/` directories at repo root are legacy remnants — ignore them.
- minSdk is 26 (Android 8.0). targetSdk is 35.
- Kotlin 2.1.0, Compose Multiplatform 1.7.3, CameraX 1.4.1.

## For OpenCode agents

Agent configs are in `.opencode/agents/`:
- `tech-lead.md` — architecture decisions, delegation (can't bash/edit)
- `mid-level-developer.md` — implementation tasks
- `qa-engineer.md` — testing

Full project context: `CONTEXT.md` (comprehensive spec). Development tickets: `TICKETS_DESARROLLO.md`.
