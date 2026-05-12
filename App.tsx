import React, { useRef, useState, useCallback, useEffect } from 'react';
import {
  StyleSheet, View, StatusBar, SafeAreaView,
  PermissionsAndroid, Platform, Text, TouchableOpacity,
} from 'react-native';
import { colors } from './src/theme/colors';
import { SystemHeader } from './src/components/SystemHeader';
import { RecordButton } from './src/components/RecordButton';
import { StatusCard } from './src/components/StatusCard';
import { BottomNav } from './src/components/BottomNav';

import { BackgroundGuard } from './src/services/BackgroundGuard';
import { Camera, useCameraDevice } from 'react-native-vision-camera';
import { useDashCam } from './src/hooks/useDashCam';
import { useImpactDetector, G_FORCE_THRESHOLD } from './src/hooks/useImpactDetector';
import { useSpeedDetector, type SpeedInfo } from './src/hooks/useSpeedDetector';

// Umbrales del velocímetro GPS (m/s²)
const SPEED_WARN_THRESHOLD  = 3.0; // Naranja: frenada fuerte
const SPEED_ALERT_THRESHOLD = 5.0; // Rojo: frenada de emergencia → graba

// Helpers de color semáforo ────────────────────────────────────────────────────
const gForceColor = (g: number): string => {
  if (g >= G_FORCE_THRESHOLD)          return '#FF2D2D'; // 🔴 Activa grabación
  if (g >= G_FORCE_THRESHOLD * 0.7)    return '#FFA500'; // 🟠 Advertencia
  return '#00FA9A';                                       // 🟢 Normal
};

const decelColor = (decel: number): string => {
  if (decel >= SPEED_ALERT_THRESHOLD)  return '#FF2D2D'; // 🔴 Activa grabación
  if (decel >= SPEED_WARN_THRESHOLD)   return '#FFA500'; // 🟠 Frenada fuerte
  return '#00FA9A';                                       // 🟢 Normal
};

export default function App() {
  const [isRecording, setIsRecording] = useState(false);
  const [hasCameraPermission, setHasCameraPermission] = useState(false);
  const [isCameraReady, setIsCameraReady] = useState(false);
  const [currentGForce, setCurrentGForce] = useState(0);
  const [speedInfo, setSpeedInfo] = useState<SpeedInfo>({ speedKmh: 0, deceleration: 0 });

  const cameraRef       = useRef<Camera>(null);
  const fallbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const device          = useCameraDevice('back');

  const { status, queueSize, startRecording, stopRecording, handleImpact } = useDashCam(cameraRef);

  // ── Fallback 5s si onInitialized no dispara ───────────────────────────────
  useEffect(() => {
    if (!isRecording || isCameraReady) return;
    fallbackTimerRef.current = setTimeout(() => {
      if (!isCameraReady) {
        console.warn('[App] Fallback: forzando inicio');
        setIsCameraReady(true);
        startRecording();
      }
    }, 5000);
    return () => { if (fallbackTimerRef.current) clearTimeout(fallbackTimerRef.current); };
  }, [isRecording, isCameraReady, startRecording]);

  // ── Acelerómetro ──────────────────────────────────────────────────────────
  const onGForceUpdate = useCallback((g: number) => setCurrentGForce(parseFloat(g.toFixed(2))), []);
  useImpactDetector(
    () => { if (isRecording) handleImpact(); },
    isRecording,
    onGForceUpdate,
  );

  // ── GPS Velocidad ─────────────────────────────────────────────────────────
  const onSpeedUpdate = useCallback((info: SpeedInfo) => setSpeedInfo(info), []);
  useSpeedDetector(
    () => { if (isRecording) handleImpact(); },
    isRecording,
    onSpeedUpdate,
  );

  // ── Permisos ──────────────────────────────────────────────────────────────
  const requestPermissions = async (): Promise<boolean> => {
    let micGranted = false;
    if (Platform.OS === 'android') {
      if (Platform.Version >= 33) {
        await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
      }
      const camPerm = await Camera.requestCameraPermission();
      if (camPerm !== 'granted') return false;
      setHasCameraPermission(true);

      // VisionCamera needs explicit microphone permission on newer versions
      const micPerm = await Camera.requestMicrophonePermission();
      if (micPerm === 'granted') micGranted = true;

      const micR = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
      if (micR !== PermissionsAndroid.RESULTS.GRANTED && !micGranted) return false;

      await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION);

      if (Platform.Version < 33) {
        await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE);
      }
    }
    return true;
  };

  // ── Toggle grabación ──────────────────────────────────────────────────────
  const toggleRecording = async () => {
    if (isRecording) {
      await stopRecording();
      await BackgroundGuard.stopGuarding();
      setIsCameraReady(false);
      setIsRecording(false);
    } else {
      const ok = await requestPermissions();
      if (!ok) return;
      await BackgroundGuard.startGuarding();
      setIsRecording(true);
    }
  };

  // ── Valores para los StatusCards ──────────────────────────────────────────
  // Card 2: Acelerómetro
  const gColor = gForceColor(currentGForce);
  const gIcon: React.ComponentProps<typeof import('./src/components/StatusCard').StatusCard>['iconName'] =
    currentGForce >= G_FORCE_THRESHOLD          ? 'alert-circle'
    : currentGForce >= G_FORCE_THRESHOLD * 0.7  ? 'alert'
    : 'vibrate';

  // Card 3: Velocímetro GPS
  const dColor = decelColor(speedInfo.deceleration);
  const speedIcon: React.ComponentProps<typeof import('./src/components/StatusCard').StatusCard>['iconName'] =
    speedInfo.deceleration >= SPEED_ALERT_THRESHOLD  ? 'car-brake-alert'
    : speedInfo.deceleration >= SPEED_WARN_THRESHOLD ? 'car-brake-retarder'
    : 'speedometer';

  // Badge de estado
  const statusLabel: Record<string, string> = {
    idle:        'Detenida',
    recording:   '● Grabando',
    post_impact: '🔴 POST-impacto',
    cooldown:    '⏱ Cooldown',
  };
  const statusBadgeBg: Record<string, string> = {
    recording:   'rgba(255,60,60,0.85)',
    post_impact: 'rgba(255,120,0,0.9)',
    cooldown:    'rgba(80,80,200,0.9)',
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor={colors.background} />
      <View style={styles.container}>

        {/* Cámara — siempre montada una vez hay permisos */}
        {hasCameraPermission && device && (
          <Camera
            ref={cameraRef}
            style={StyleSheet.absoluteFill}
            device={device}
            isActive={true} // Siempre activa para que se vea el preview
            video={true}
            audio={true}
            onInitialized={() => {
              if (fallbackTimerRef.current) clearTimeout(fallbackTimerRef.current);
              if (!isCameraReady) {
                setIsCameraReady(true);
                if (isRecording) {
                  startRecording();
                }
              }
            }}
            onError={(e) => console.error('[App] Camera error:', e.message)}
          />
        )}

        <View style={[StyleSheet.absoluteFill, styles.overlay]} pointerEvents="none" />
        <SystemHeader />

        {/* Badge de estado de grabación */}
        {isRecording && (
          <View style={[styles.statusBadge, { backgroundColor: statusBadgeBg[status] ?? 'rgba(80,80,80,0.9)' }]}>
            <Text style={styles.statusText}>
              {!isCameraReady
                ? '⏳ Iniciando cámara...'
                : `${statusLabel[status]} · Buffer: ${queueSize}/2 seg (~${queueSize * 15}s)`}
            </Text>
          </View>
        )}

        {/* Botón central de grabación */}
        <View style={styles.recordContainer}>
          <RecordButton isRecording={isRecording} onToggle={toggleRecording} />

          {/* Botón de prueba manual (solo visible cuando hay PRE listo) */}
          {isRecording && isCameraReady && status === 'recording' && (
            <TouchableOpacity
              style={[styles.testButton, queueSize === 0 && styles.testButtonDisabled]}
              onPress={() => handleImpact()}
              disabled={queueSize === 0}
            >
              <Text style={[styles.testButtonText, queueSize === 0 && { color: 'rgba(255,215,0,0.4)' }]}>
                🧪 {queueSize > 0 ? `PROBAR (${queueSize * 15}s capturados)` : 'Espera 15s...'}
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* ── Tres StatusCards ────────────────────────────────────────────── */}
        <View style={styles.statusContainer}>

          {/* Card 1: STORAGE (sin cambios) */}
          <StatusCard
            iconName="harddisk"
            title="STORAGE"
            value="128"
            valueSuffix="GB"
          />

          {/* Card 2: ACELERÓMETRO — verde / naranja / rojo */}
          <StatusCard
            iconName={gIcon}
            title="G-FORCE"
            value={currentGForce.toFixed(2)}
            valueSuffix="G"
            accentColor={gColor}
          />

          {/* Card 3: VELOCÍMETRO GPS — verde / naranja / rojo */}
          <StatusCard
            iconName={speedIcon}
            title="SPEED"
            value={Math.round(speedInfo.speedKmh).toString()}
            valueSuffix="km/h"
            accentColor={dColor}
          />

        </View>

        <BottomNav />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea:  { flex: 1, backgroundColor: colors.background },
  container: { flex: 1, backgroundColor: colors.background },
  overlay:   { backgroundColor: 'rgba(0,0,0,0.55)' },

  statusBadge: {
    alignSelf: 'center',
    marginTop: 8,
    paddingHorizontal: 16,
    paddingVertical: 5,
    borderRadius: 20,
  },
  statusText: { color: '#fff', fontWeight: '700', fontSize: 12, letterSpacing: 0.8 },

  recordContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 20,
  },

  testButton: {
    backgroundColor: 'rgba(255,200,0,0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255,200,0,0.6)',
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 24,
  },
  testButtonDisabled: {
    borderColor: 'rgba(255,200,0,0.2)',
    backgroundColor: 'rgba(255,200,0,0.05)',
  },
  testButtonText: {
    color: '#FFD700',
    fontWeight: '700',
    fontSize: 13,
    letterSpacing: 1,
  },

  statusContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
});
