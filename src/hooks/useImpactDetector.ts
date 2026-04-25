import { useRef, useEffect } from 'react';
import { accelerometer, setUpdateIntervalForType, SensorTypes } from 'react-native-sensors';
import type { Subscription } from 'rxjs';

// Muestrear 20 veces por segundo
setUpdateIntervalForType(SensorTypes.accelerometer, 50);

export const G_FORCE_THRESHOLD = 2.5; // umbral real de impacto
const COOLDOWN_MS = 35_000;

export const useImpactDetector = (
  onImpact: () => void,
  enabled: boolean = true,
  onGForceUpdate?: (gForce: number) => void  // <-- callback de diagnóstico
) => {
  const isImpactedRef = useRef(false);
  const cooldownTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onImpactRef = useRef(onImpact);
  onImpactRef.current = onImpact;

  const onGForceUpdateRef = useRef(onGForceUpdate);
  onGForceUpdateRef.current = onGForceUpdate;

  useEffect(() => {
    let subscription: Subscription | null = null;

    // Siempre escuchar el acelerómetro para mostrar G-Force en tiempo real,
    // pero solo disparar el impacto si enabled=true
    subscription = accelerometer.subscribe(({ x, y, z }) => {
      const magnitude = Math.sqrt(x * x + y * y + z * z);
      const gForce = magnitude / 9.81;

      // Notificar UI con el valor actual (para el medidor)
      onGForceUpdateRef.current?.(gForce);

      if (!enabled) return;
      if (isImpactedRef.current) return;

      if (gForce > G_FORCE_THRESHOLD) {
        console.log(`[ImpactDetector] ¡IMPACTO! ${gForce.toFixed(2)}G detectados`);
        isImpactedRef.current = true;
        onImpactRef.current();

        cooldownTimerRef.current = setTimeout(() => {
          isImpactedRef.current = false;
          console.log('[ImpactDetector] Cooldown terminado.');
        }, COOLDOWN_MS);
      }
    });

    return () => {
      subscription?.unsubscribe();
      if (cooldownTimerRef.current) clearTimeout(cooldownTimerRef.current);
    };
  }, [enabled]);

  return { isImpacted: isImpactedRef.current };
};
