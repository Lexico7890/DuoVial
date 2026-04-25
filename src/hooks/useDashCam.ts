import { useRef, useState, useCallback } from 'react';
import { Alert } from 'react-native';
import { Camera } from 'react-native-vision-camera';
import RNFS from 'react-native-fs';

const SEGMENT_DURATION_MS = 15_000;
const COOLDOWN_MS = 20_000; // Reducido para facilitar pruebas

export type DashCamStatus = 'idle' | 'recording' | 'saving' | 'cooldown';

export function useDashCam(cameraRef: React.RefObject<Camera | null>) {
  const [status, setStatus] = useState<DashCamStatus>('idle');
  // Exponer segmentos completados para debug en la UI
  const [segmentCount, setSegmentCount] = useState(0);

  // Paths REALES retornados por onRecordingFinished
  const prevSegmentPath = useRef<string | null>(null);
  const currentSegmentPath = useRef<string | null>(null);

  // Resolver para esperar onRecordingFinished
  const segmentResolverRef = useRef<((path: string | null) => void) | null>(null);

  const segmentTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isRecordingRef = useRef(false);
  const isSavingRef = useRef(false);

  // ── Iniciar un nuevo segmento de grabación ──────────────────────────────────
  const startNewSegment = useCallback(() => {
    const cam = cameraRef.current;
    if (!cam) {
      console.warn('[DashCam] startNewSegment: cameraRef.current es null');
      return;
    }

    // Rotar: el segmento actual (path conocido) pasa a ser el "previo"
    prevSegmentPath.current = currentSegmentPath.current;
    currentSegmentPath.current = null; // Lo llenará onRecordingFinished con el path real

    console.log(`[DashCam] ▶ Nuevo segmento. Previo: ${prevSegmentPath.current ?? 'ninguno'}`);

    try {
      cam.startRecording({
        onRecordingFinished: (video) => {
          const realPath = video.path;
          console.log(`[DashCam] ✅ onRecordingFinished → path: ${realPath}`);
          currentSegmentPath.current = realPath;
          setSegmentCount((n) => n + 1);

          // Resolver si handleImpact está esperando
          if (segmentResolverRef.current) {
            segmentResolverRef.current(realPath);
            segmentResolverRef.current = null;
          }
        },
        onRecordingError: (err) => {
          console.error(`[DashCam] ❌ onRecordingError: ${err.message}`);
          if (segmentResolverRef.current) {
            segmentResolverRef.current(null);
            segmentResolverRef.current = null;
          }
        },
      });
      console.log('[DashCam] cam.startRecording() llamado exitosamente');
    } catch (e: any) {
      console.error('[DashCam] ❌ Excepción en startRecording:', e?.message ?? e);
    }

    // Auto-rotar después de SEGMENT_DURATION_MS
    if (segmentTimerRef.current) clearTimeout(segmentTimerRef.current);
    segmentTimerRef.current = setTimeout(() => {
      if (!isRecordingRef.current || isSavingRef.current) return;
      console.log('[DashCam] ⏱ Auto-rotando segmento...');

      cameraRef.current?.stopRecording();

      // Dar tiempo a onRecordingFinished y luego iniciar el siguiente
      setTimeout(() => {
        if (isRecordingRef.current && !isSavingRef.current) {
          startNewSegment();
        }
      }, 3000);
    }, SEGMENT_DURATION_MS);
  }, [cameraRef]);

  // ── Detener grabación actual y obtener su path real ─────────────────────────
  const stopAndGetCurrentPath = useCallback((): Promise<string | null> => {
    return new Promise((resolve) => {
      const cam = cameraRef.current;

      // Si ya tenemos un path registrado (segmento cerrado), usarlo directamente
      if (currentSegmentPath.current) {
        console.log(`[DashCam] Path ya disponible: ${currentSegmentPath.current}`);
        resolve(currentSegmentPath.current);
        return;
      }

      if (!cam) {
        console.warn('[DashCam] stopAndGetCurrentPath: cameraRef nulo');
        resolve(null);
        return;
      }

      console.log('[DashCam] Esperando onRecordingFinished para obtener path...');
      segmentResolverRef.current = resolve;

      try {
        cam.stopRecording();
      } catch (e: any) {
        console.error('[DashCam] Error al llamar stopRecording:', e?.message);
      }

      // Timeout de seguridad aumentado a 12 segundos
      setTimeout(() => {
        if (segmentResolverRef.current) {
          console.warn('[DashCam] ⚠ Timeout (12s) esperando onRecordingFinished. Path actual:', currentSegmentPath.current);
          const fallback = currentSegmentPath.current; // Intentar usar lo que tengamos
          segmentResolverRef.current(fallback);
          segmentResolverRef.current = null;
        }
      }, 12_000);
    });
  }, [cameraRef]);

  // ── API pública ─────────────────────────────────────────────────────────────
  const startRecording = useCallback(() => {
    if (isRecordingRef.current) return;
    isRecordingRef.current = true;
    prevSegmentPath.current = null;
    currentSegmentPath.current = null;
    setSegmentCount(0);
    setStatus('recording');
    console.log('[DashCam] 🎬 Iniciando grabación...');
    startNewSegment();
  }, [startNewSegment]);

  const stopRecording = useCallback(async () => {
    if (!isRecordingRef.current) return;
    console.log('[DashCam] ⏹ Deteniendo grabación...');
    isRecordingRef.current = false;
    isSavingRef.current = false;
    if (segmentTimerRef.current) clearTimeout(segmentTimerRef.current);

    try { cameraRef.current?.stopRecording(); } catch (_) {}

    await new Promise<void>((r) => setTimeout(r, 2000));

    for (const p of [prevSegmentPath.current, currentSegmentPath.current]) {
      if (p) RNFS.exists(p).then((e) => { if (e) RNFS.unlink(p).catch(() => {}); });
    }

    prevSegmentPath.current = null;
    currentSegmentPath.current = null;
    setSegmentCount(0);
    setStatus('idle');
  }, [cameraRef]);

  const handleImpact = useCallback(async () => {
    if (!isRecordingRef.current || isSavingRef.current) {
      console.log('[DashCam] handleImpact ignorado');
      return;
    }

    console.log('[DashCam] 💥 IMPACTO detectado');
    isSavingRef.current = true;
    setStatus('saving');
    if (segmentTimerRef.current) clearTimeout(segmentTimerRef.current);

    // ⚠ CRÍTICO: Capturar prevPath ANTES del await para no perderlo durante la rotación
    const prePathSnapshot = prevSegmentPath.current;

    // Obtener path del segmento actual (puede requerir esperar onRecordingFinished)
    const postPath = await stopAndGetCurrentPath();

    console.log(`[DashCam] Pre-path capturado: ${prePathSnapshot ?? 'null'}`);
    console.log(`[DashCam] Post-path recibido: ${postPath ?? 'null'}`);

    const downloadsDir = `${RNFS.ExternalStorageDirectoryPath}/Download`;
    const timestamp = Date.now();
    let savedCount = 0;
    const savedFiles: string[] = [];

    for (const [label, srcPath] of [['pre', prePathSnapshot], ['post', postPath]] as const) {
      if (!srcPath) {
        console.log(`[DashCam] ${label}: sin path disponible, omitiendo`);
        continue;
      }
      try {
        const exists = await RNFS.exists(srcPath);
        console.log(`[DashCam] ${label} existe=${exists} → ${srcPath}`);
        if (exists) {
          const dest = `${downloadsDir}/DuoVial_${label}_${timestamp}.mp4`;
          await RNFS.copyFile(srcPath, dest);
          await RNFS.unlink(srcPath).catch(() => {});
          if (label === 'pre') prevSegmentPath.current = null;
          if (label === 'post') currentSegmentPath.current = null;
          savedCount++;
          savedFiles.push(dest);
          console.log(`[DashCam] ✅ ${label} → ${dest}`);
        }
      } catch (e: any) {
        console.error(`[DashCam] Error guardando ${label}:`, e?.message ?? e);
      }
    }

    if (savedCount > 0) {
      Alert.alert(
        '¡Impacto registrado! 🎬',
        `${savedCount} clip(s) guardados en Descargas:\n${savedFiles.map((f) => f.split('/').pop()).join('\n')}`
      );
    } else {
      Alert.alert(
        'Sin video disponible',
        `Segmentos completados: ${segmentCount}\nPre: ${prePathSnapshot ?? 'null'}\nPost: ${postPath ?? 'null'}\n\n` +
        (segmentCount === 0
          ? '⚠ Ningún segmento se completó aún. Espera al menos 15s grabando antes de agitar.'
          : '⚠ Los archivos existen en metadata pero no en disco. Revisa permisos de almacenamiento.')
      );
    }

    setStatus('cooldown');
    setTimeout(() => {
      if (isRecordingRef.current) {
        isSavingRef.current = false;
        setStatus('recording');
        startNewSegment();
      } else {
        isSavingRef.current = false;
        setStatus('idle');
      }
    }, COOLDOWN_MS);
  }, [cameraRef, stopAndGetCurrentPath, startNewSegment, segmentCount]);

  return { status, segmentCount, startRecording, stopRecording, handleImpact };
}
