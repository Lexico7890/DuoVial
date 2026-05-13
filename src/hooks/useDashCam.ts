import { useRef, useState, useCallback } from 'react';
import { Alert } from 'react-native';
import { Camera } from 'react-native-vision-camera';
import RNFS from 'react-native-fs';

// ── Configuración del buffer circular ────────────────────────────────────────
const MINI_SEGMENT_MS  = 15_000; // Cada mini-segmento dura 15s
const PRE_QUEUE_SIZE   = 1;      // Mantener solo el último segmento completado (max 15s)
const POST_SEGMENT_MS  = 16_000; // 16s de grabación POST-impacto
const COOLDOWN_MS      = 10_000; // Bloquear re-trigger por 10s
const CAM_SETTLE_MS    = 800;    // Tiempo entre stop/start de la cámara

export type DashCamStatus = 'idle' | 'recording' | 'post_impact' | 'cooldown';

export function useDashCam(cameraRef: React.RefObject<Camera | null>) {
  const [status, setStatus]       = useState<DashCamStatus>('idle');
  const [queueSize, setQueueSize] = useState(0); // Para mostrar en UI

  // Cola circular de segmentos completados (paths reales de VisionCamera)
  const preQueue         = useRef<string[]>([]);
  const isRecordingRef   = useRef(false);
  const isHandlingImpact = useRef(false);

  const segmentTimerRef    = useRef<ReturnType<typeof setTimeout> | null>(null);
  const postTimerRef       = useRef<ReturnType<typeof setTimeout> | null>(null);
  const settleTimerRef     = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Resolver para esperar el cierre del segmento actual
  const resolverRef        = useRef<((p: string | null) => void) | null>(null);

  // ── Helpers ────────────────────────────────────────────────────────────────
  const clearAllTimers = () => {
    [segmentTimerRef, postTimerRef, settleTimerRef].forEach((t) => {
      if (t.current) { clearTimeout(t.current); t.current = null; }
    });
  };

  const safeDelete = (path: string | null) => {
    if (!path) return;
    RNFS.exists(path).then((e) => { if (e) RNFS.unlink(path).catch(() => {}); });
  };

  const addToQueue = (path: string) => {
    const q = preQueue.current;
    q.push(path);
    // Eliminar el más antiguo si supera el límite
    while (q.length > PRE_QUEUE_SIZE) {
      safeDelete(q.shift() ?? null);
    }
    setQueueSize(q.length);
    console.log(`[DashCam] Cola PRE: ${q.length}/${PRE_QUEUE_SIZE} segmentos`);
  };

  const clearQueue = useCallback(() => {
    preQueue.current.forEach((p) => safeDelete(p));
    preQueue.current = [];
    setQueueSize(0);
  }, []);

  // ── Mini-segmento (15s, se rota automáticamente) ────────────────────────────
  const startMiniSegment = useCallback(() => {
    const cam = cameraRef.current;
    if (!cam || !isRecordingRef.current || isHandlingImpact.current) return;

    console.log('[DashCam] ▶ Iniciando mini-segmento PRE (15s)...');

    try {
      cam.startRecording({
                onRecordingFinished: (video) => {
          console.log(`[DashCam] ✅ Mini-segmento: ${video.path}`);

          // Si handleImpact está esperando este path, entregárselo
          if (resolverRef.current) {
            resolverRef.current(video.path);
            resolverRef.current = null;
            return;
          }

          if (isHandlingImpact.current) {
            safeDelete(video.path);
            return;
          }

          // Rotación normal: añadir a la cola
          addToQueue(video.path);

          if (isRecordingRef.current && !isHandlingImpact.current) {
            settleTimerRef.current = setTimeout(startMiniSegment, CAM_SETTLE_MS);
          }
        },
        onRecordingError: (err) => {
          console.error('[DashCam] ❌ Error mini-segmento:', err.message);
          if (resolverRef.current) { resolverRef.current(null); resolverRef.current = null; }
          // Reintentar
          if (isRecordingRef.current && !isHandlingImpact.current) {
            settleTimerRef.current = setTimeout(startMiniSegment, 2000);
          }
        },
      });
    } catch (e: any) {
      console.error('[DashCam] Excepción startRecording:', e?.message);
      return;
    }

    // Auto-parar a los 15s para rotar
    segmentTimerRef.current = setTimeout(() => {
      if (!isRecordingRef.current || isHandlingImpact.current) return;
      try { cameraRef.current?.stopRecording(); } catch (_) {}
    }, MINI_SEGMENT_MS);
  }, [cameraRef]);

  // ── Esperar que el segmento en curso termine ───────────────────────────────
  const waitForCurrentSegment = (): Promise<string | null> =>
    new Promise((resolve) => {
      resolverRef.current = resolve;
      try { cameraRef.current?.stopRecording(); } catch (_) {}
      setTimeout(() => {
        if (resolverRef.current) { resolverRef.current(null); resolverRef.current = null; }
      }, 8000);
    });

  // ── Cooldown compartido: solo bloquea trigger, cámara sigue grabando ───────
  const resumeAfterCooldown = useCallback(() => {
    setTimeout(() => {
      isHandlingImpact.current = false;
      if (isRecordingRef.current) {
        setStatus('recording');
        settleTimerRef.current = setTimeout(startMiniSegment, CAM_SETTLE_MS);
      } else {
        setStatus('idle');
      }
    }, COOLDOWN_MS);
  }, [startMiniSegment]);

  // ── API pública ────────────────────────────────────────────────────────────
  const startRecording = useCallback(() => {
    if (isRecordingRef.current) return;
    clearAllTimers();
    isRecordingRef.current   = true;
    isHandlingImpact.current = false;
    clearQueue();
    setStatus('recording');
    console.log('[DashCam] 🎬 Buffer circular iniciado');
    startMiniSegment();
  }, [startMiniSegment, clearQueue]);

  const stopRecording = useCallback(async () => {
    if (!isRecordingRef.current) return;
    isRecordingRef.current   = false;
    isHandlingImpact.current = false;
    clearAllTimers();
    if (resolverRef.current) { resolverRef.current(null); resolverRef.current = null; }
    try { cameraRef.current?.stopRecording(); } catch (_) {}
    await new Promise<void>((r) => setTimeout(r, 2000));
    clearQueue();
    setStatus('idle');
  }, [cameraRef, clearQueue]);

  const handleImpact = useCallback(async () => {
    if (!isRecordingRef.current || isHandlingImpact.current) {
      console.log('[DashCam] handleImpact ignorado');
      return;
    }

    console.log('[DashCam] 💥 ¡IMPACTO! Iniciando captura continua sin cortes...');
    isHandlingImpact.current = true;
    setStatus('post_impact');

    if (segmentTimerRef.current) { clearTimeout(segmentTimerRef.current); segmentTimerRef.current = null; }
    if (settleTimerRef.current)  { clearTimeout(settleTimerRef.current);  settleTimerRef.current = null; }

    // Snapshot de la cola ANTES de parar (preserva los segmentos pre-impacto)
    const queueSnapshot = [...preQueue.current];
    preQueue.current = []; // Vaciamos para que no sean borrados por addToQueue
    setQueueSize(0);

    // Event ID único para identificar el par de clips
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const eventId = `${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;

    console.log(`[DashCam] EventID=${eventId} · PRE en cola: ${queueSnapshot.length}`);

    // Obtener el segmento actual en curso (del momento del choque)
    const currentPath = await waitForCurrentSegment();
    if (currentPath) {
      queueSnapshot.push(currentPath);
      console.log(`[DashCam] Segmento del momento del choque añadido: ${currentPath}`);
    }

    // Guardar todos los segmentos PRE en Descargas
    const downloadsDir = RNFS.DownloadDirectoryPath || `${RNFS.ExternalStorageDirectoryPath}/Download`;
    const savedPre: string[] = [];

    for (let i = 0; i < queueSnapshot.length; i++) {
      const src = queueSnapshot[i];
      try {
        if (src && await RNFS.exists(src)) {
          const dest = `${downloadsDir}/DuoVial_${eventId}_pre${i + 1}.mp4`;
          await RNFS.copyFile(src, dest);
          await RNFS.unlink(src).catch(() => {});
          savedPre.push(dest);
          console.log(`[DashCam] ✅ pre${i+1} → ${dest}`);
        }
      } catch (e: any) {
        console.error(`[DashCam] Error pre${i+1}:`, e?.message);
      }
    }

    // Iniciar grabación POST-impacto (16s)
    const cam = cameraRef.current;
    if (!cam || !isRecordingRef.current) {
      Alert.alert('Clips PRE guardados', `${savedPre.length} clip(s) en Descargas.\nID: ${eventId}`);
      resumeAfterCooldown();
      return;
    }

    console.log('[DashCam] ▶ POST-impacto (16s)...');
    try {
      cam.startRecording({
        onRecordingFinished: async (video) => {
          let postSaved = false;
          try {
            if (await RNFS.exists(video.path)) {
              const dest = `${downloadsDir}/DuoVial_${eventId}_post.mp4`;
              await RNFS.copyFile(video.path, dest);
              await RNFS.unlink(video.path).catch(() => {});
              postSaved = true;
              console.log(`[DashCam] ✅ post → ${dest}`);
            }
          } catch (e: any) { console.error('[DashCam] Error post:', e?.message); }

          const preSeconds = queueSnapshot.length * (MINI_SEGMENT_MS / 1000);
          Alert.alert(
            '🎬 Evento grabado',
            `ID: ${eventId}\n\n` +
            `• PRE: ${savedPre.length} clip(s) (~${preSeconds}s antes del choque)\n` +
            `• POST: ${postSaved ? '16s ✅' : 'Error ❌'}\n\n` +
            `Busca archivos "DuoVial_${eventId}_*" en Descargas.`
          );

          resumeAfterCooldown();
        },
        onRecordingError: (err) => {
          console.error('[DashCam] Error POST:', err.message);
          Alert.alert('Clips PRE guardados', `${savedPre.length} clip(s) PRE en Descargas.\nError grabando POST: ${err.message}`);
          resumeAfterCooldown();
        },
      });
    } catch (e: any) {
      console.error('[DashCam] Excepción POST:', e?.message);
      isHandlingImpact.current = false;
      setStatus('idle');
      return;
    }

    // Parar POST después de 16s
    postTimerRef.current = setTimeout(() => {
      console.log('[DashCam] ⏱ Parando POST-impacto...');
      try { cameraRef.current?.stopRecording(); } catch (_) {}
    }, POST_SEGMENT_MS);

  }, [cameraRef, resumeAfterCooldown]);

  return { status, queueSize, startRecording, stopRecording, handleImpact };
}
