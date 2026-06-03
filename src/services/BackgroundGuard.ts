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
}
