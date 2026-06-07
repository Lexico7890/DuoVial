import { NativeModules } from 'react-native';
import { schedule, cancel, SchedulerTypes } from '@rn-native-utils/workmanager';

const { BackgroundCameraModule } = NativeModules;

/**
 * Controla la lógica de persistencia en 2do plano y grabación nativa.
 */
export class BackgroundGuard {
  private static watchdogKey = 'duovial_watchdog';

  /**
   * Inicia el servicio nativo de cámara en modo STANDBY (previsualización activa, sin grabar).
   */
  static startStandby() {
    if (BackgroundCameraModule && BackgroundCameraModule.startStandby) {
      BackgroundCameraModule.startStandby();
    } else {
      console.log('startStandby no está disponible en este entorno.');
    }
  }

  /**
   * Inicia la grabación de cámara nativa en Foreground Service
   * y registra el WorkManager Watchdog.
   */
  static async startGuarding() {
    // 1. Iniciamos el Foreground Service nativo de Cámara
    if (BackgroundCameraModule) {
      BackgroundCameraModule.startRecording();
    } else {
      console.error('BackgroundCameraModule no está disponible en este entorno.');
    }

    // 2. Registramos el "Watchdog" de WorkManager
    // Esto asegura que si Android mata el servicio por batería, el WorkManager lo recree periódicamente.
    try {
      await schedule(
        {
          taskKey: this.watchdogKey,
          type: SchedulerTypes.periodic,
          syncInterval: 15,
          syncFlexTime: 5,
          allowedInForeground: true,
        },
        async () => {
          console.log('[WATCHDOG] Asegurando que la grabación nativa esté activa...');
          if (BackgroundCameraModule) {
            BackgroundCameraModule.startRecording();
          }
        }
      );
      console.log('WorkManager Watchdog agendado correctamente.');
    } catch (e) {
      console.error('Error al registrar WorkManager:', e);
    }
  }

  /**
   * Detiene la grabación nativa y el Watchdog.
   */
  static async stopGuarding() {
    if (BackgroundCameraModule) {
      BackgroundCameraModule.stopRecording();
    }

    // Cancelar el WorkManager Watchdog
    try {
      await cancel(this.watchdogKey);
    } catch (e) {
      console.log('Error al cancelar watchdog:', e);
    }
  }

  /**
   * Dispara el guardado de evento de impacto manual.
   */
  static triggerPanic() {
    if (BackgroundCameraModule) {
      BackgroundCameraModule.triggerPanic();
    }
  }

  /**
   * Solicita el permiso para dibujar sobre otras aplicaciones (Burbuja flotante).
   */
  static requestOverlayPermission() {
    if (BackgroundCameraModule && BackgroundCameraModule.requestOverlayPermission) {
      BackgroundCameraModule.requestOverlayPermission();
    }
  }

  /**
   * Setea el umbral de G-Force para la detección de colisiones.
   * Si el Service aún no está vivo, el valor queda pendiente y se aplica al
   * arrancar. Rango válido: 1.5..5.0 (ignorado fuera de rango).
   */
  static setGForceThreshold(threshold: number) {
    if (BackgroundCameraModule && BackgroundCameraModule.setGForceThreshold) {
      BackgroundCameraModule.setGForceThreshold(threshold);
    }
  }

  /**
   * Lee el umbral de G-Force actual del Service. Útil para que la UI muestre
   * el valor real tras un reinicio, no un valor hardcodeado en JS.
   */
  static async getGForceThreshold(): Promise<number> {
    if (BackgroundCameraModule && BackgroundCameraModule.getGForceThreshold) {
      return await BackgroundCameraModule.getGForceThreshold();
    }
    return 2.5;
  }
}
