import { useRef, useEffect, useCallback } from 'react';
import * as Location from 'expo-location';

// Umbral de desaceleración brusca: 5 m/s² ≈ frenar de 60km/h a 0 en ~3.3s
// Un stop de emergencia suele ser 7-9 m/s²
const DECEL_THRESHOLD_MS2 = 5.0; // m/s²

// Intervalo de muestreo GPS (ms) — mayor frecuencia = mejor detección pero más batería
const GPS_INTERVAL_MS = 500;

// Cooldown para no re-disparar inmediatamente
const COOLDOWN_MS = 35_000;

export interface SpeedInfo {
  speedKmh: number;      // Velocidad actual en km/h
  deceleration: number;  // Desaceleración actual en m/s² (positivo = frenando)
}

export function useSpeedDetector(
  onSuddenBrake: () => void,
  enabled: boolean = true,
  onSpeedUpdate?: (info: SpeedInfo) => void,
) {
  const lastSpeedRef      = useRef<number | null>(null); // m/s
  const lastTimestampRef  = useRef<number | null>(null); // ms
  const isCooldownRef     = useRef(false);
  const subscriptionRef   = useRef<Location.LocationSubscription | null>(null);
  const cooldownTimerRef  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onBrakeRef        = useRef(onSuddenBrake);
  const onSpeedUpdateRef  = useRef(onSpeedUpdate);
  onBrakeRef.current     = onSuddenBrake;
  onSpeedUpdateRef.current = onSpeedUpdate;

  const startWatching = useCallback(async () => {
    // Pedir permiso de ubicación
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      console.warn('[SpeedDetector] Permiso de ubicación denegado');
      return;
    }

    console.log('[SpeedDetector] Iniciando monitoreo de velocidad GPS...');

    subscriptionRef.current = await Location.watchPositionAsync(
      {
        accuracy: Location.Accuracy.BestForNavigation,
        timeInterval: GPS_INTERVAL_MS,
        distanceInterval: 0, // Notificar en cada intervalo de tiempo
      },
      (location) => {
        const currentSpeed = location.coords.speed ?? 0; // m/s
        const currentTime  = location.timestamp;          // ms

        if (currentSpeed < 0) return; // GPS a veces retorna -1 cuando no hay señal

        const speedKmh = currentSpeed * 3.6;

        // Calcular desaceleración si tenemos un punto previo
        let deceleration = 0;
        if (lastSpeedRef.current !== null && lastTimestampRef.current !== null) {
          const dt = (currentTime - lastTimestampRef.current) / 1000; // segundos
          if (dt > 0) {
            const dv = lastSpeedRef.current - currentSpeed; // positivo = reducción de velocidad
            deceleration = dv / dt; // m/s²
          }
        }

        // Actualizar UI
        onSpeedUpdateRef.current?.({ speedKmh, deceleration });

        // Detectar frenada brusca
        if (enabled && !isCooldownRef.current && deceleration > DECEL_THRESHOLD_MS2) {
          console.log(`[SpeedDetector] 🛑 FRENADA BRUSCA: ${deceleration.toFixed(2)} m/s² · ${speedKmh.toFixed(1)} km/h`);
          isCooldownRef.current = true;
          onBrakeRef.current();

          cooldownTimerRef.current = setTimeout(() => {
            isCooldownRef.current = false;
            console.log('[SpeedDetector] Cooldown terminado');
          }, COOLDOWN_MS);
        }

        lastSpeedRef.current     = currentSpeed;
        lastTimestampRef.current = currentTime;
      }
    );
  }, [enabled]);

  useEffect(() => {
    startWatching();

    return () => {
      subscriptionRef.current?.remove();
      if (cooldownTimerRef.current) clearTimeout(cooldownTimerRef.current);
      console.log('[SpeedDetector] Monitoreo detenido');
    };
  }, [startWatching]);

  return {};
}
