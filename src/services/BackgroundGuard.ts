import ReactNativeForegroundService from '@supersami/rn-foreground-service';
import { schedule, cancel, SchedulerTypes } from '@rn-native-utils/workmanager';

/**
 * Controla la lógica de persistencia en 2do plano.
 */
export class BackgroundGuard {
  private static taskName = 'duovial_dashcam_task';
  private static watchdogKey = 'duovial_watchdog';

  /**
   * Inicia tanto la notificación persistente (Foreground Service)
   * como el WorkManager para que funcione como Watchdog.
   */
  static async startGuarding() {
    // 1. Iniciamos el Foreground Service con Notificación Persistente
    ReactNativeForegroundService.add_task(
      () => {
        // Aquí irá la lógica de Buffer Circular en el futuro
        console.log('Dash Cam activa en segundo plano...');
      },
      {
        delay: 1000,
        onLoop: true,
        taskId: this.taskName,
        onError: (e) => console.log('Error en tarea de dashcam', e),
      }
    );

    // Iniciar el servicio nativo a nivel SO Android (Muestra la notificación verde/default)
    ReactNativeForegroundService.start({
      id: 144,
      title: 'DuoVial - Grabando',
      message: 'La cámara está vigilando en segundo plano.',
      icon: 'ic_launcher', // Se usará este al no tener uno personalizado para esta fase
      button: true, // Mostrar un botón en la notificación (ej: Parar)
      buttonText: 'VER APP',
      buttonOnPress: 'crayola', 
    });

    // 2. Registramos el "Watchdog" de WorkManager
    // Esto asegura que si Android mata el servicio por batería, el WorkManager lo recree periódicamente.
    try {
      await schedule(
        {
          taskKey: this.watchdogKey,
          type: SchedulerTypes.periodic,
          syncInterval: 15, // Mínimo soportado por Android es frecuentemente 15 mins
          allowedInForeground: true, // Si también queremos que chequee en foreground
        },
        async () => {
          // Lógica del Watchdog: revisar si el foreground service murió. 
          // Si murió y Debería estar grabando -> lo relanzamos.
          console.log('[WATCHDOG] Verificando estado del servicio...');
          if (!ReactNativeForegroundService.is_running()) {
            console.log('[WATCHDOG] Servicio purgado por OS. Reiniciándolo...');
            // Lógica para volver a arrancar el start() aquí si es necesario
          }
        }
      );
      console.log('WorkManager Watchdog agendado correctamente.');
    } catch (e) {
      console.error('Error al registrar WorkManager:', e);
    }
  }

  /**
   * Detiene todo (la dashcam real y los procesos de segundo plano)
   */
  static async stopGuarding() {
    // Apagar tarea de JS
    ReactNativeForegroundService.remove_task(this.taskName);
    // Apagar Foreground Service (Quitar Notificación)
    ReactNativeForegroundService.stop();

    // Cancelar el WorkManager Watchdog
    try {
      await cancel(this.watchdogKey);
    } catch (e) {
      console.log('Error al cancelar watchdog:', e);
    }
  }
}
