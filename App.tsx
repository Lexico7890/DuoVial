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

export default function App() {
  const [isRecording, setIsRecording] = useState(false);
  const [hasCameraPermission, setHasCameraPermission] = useState(false);
  const [currentGForce, setCurrentGForce] = useState(0);
  
  // Flag: la cámara ya renderizó y está lista para recibir comandos
  const [isCameraReady, setIsCameraReady] = useState(false);
  // Indica que queremos empezar a grabar en cuanto la cámara esté lista
  const pendingStartRef = useRef(false);

  const cameraRef = useRef<Camera>(null);
  const device = useCameraDevice('back');

  const { status, segmentCount, startRecording, stopRecording, handleImpact } = useDashCam(cameraRef);

  // ── Cuando la cámara se inicializa, disparar grabación si estaba pendiente ──
  useEffect(() => {
    if (isCameraReady && pendingStartRef.current) {
      pendingStartRef.current = false;
      console.log('[App] Cámara lista → iniciando grabación');
      startRecording();
    }
  }, [isCameraReady, startRecording]);

  const onGForceUpdate = useCallback((g: number) => {
    setCurrentGForce(parseFloat(g.toFixed(2)));
  }, []);

  useImpactDetector(
    () => { if (isRecording) handleImpact(); },
    isRecording,
    onGForceUpdate,
  );

  const requestPermissions = async (): Promise<boolean> => {
    if (Platform.OS === 'android') {
      if (Platform.Version >= 33) {
        await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
      }

      const camPerm = await Camera.requestCameraPermission();
      if (camPerm !== 'granted') {
        console.warn('[App] Permiso de cámara denegado');
        return false;
      }
      setHasCameraPermission(true);

      const micR = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO);
      if (micR !== PermissionsAndroid.RESULTS.GRANTED) {
        console.warn('[App] Permiso de micrófono denegado');
        return false;
      }

      if (Platform.Version < 33) {
        const storageR = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE
        );
        if (storageR !== PermissionsAndroid.RESULTS.GRANTED) {
          console.warn('[App] Permiso de almacenamiento denegado');
          return false;
        }
      }
    }
    return true;
  };

  const toggleRecording = async () => {
    if (isRecording) {
      await stopRecording();
      await BackgroundGuard.stopGuarding();
      setIsRecording(false);
      setIsCameraReady(false);
    } else {
      const hasPermissions = await requestPermissions();
      if (!hasPermissions) return;

      await BackgroundGuard.startGuarding();
      
      // Marcar que queremos grabar; la grabación real arrancará cuando
      // onInitialized confirme que la cámara está lista
      pendingStartRef.current = true;
      setIsRecording(true); // Esto hace que <Camera> se renderice
      console.log('[App] Cámara montándose... esperando onInitialized');
    }
  };

  const simulateImpact = () => {
    console.log('[App] Simulando impacto manual...');
    handleImpact();
  };

  const statusLabel: Record<string, string> = {
    idle: 'Detenida',
    recording: '● Grabando',
    saving: '⏳ Guardando clip...',
    cooldown: '⏱ Cooldown activo...',
  };

  const gPercent = Math.min(currentGForce / (G_FORCE_THRESHOLD * 1.5), 1);
  const gBarColor = currentGForce >= G_FORCE_THRESHOLD
    ? '#FF2D2D'
    : currentGForce >= G_FORCE_THRESHOLD * 0.7
      ? '#FFA500'
      : '#00E5A0';

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor={colors.background} />
      <View style={styles.container}>

        {/* Cámara: se monta cuando isRecording=true y hay permisos */}
        {hasCameraPermission && device && isRecording && (
          <Camera
            ref={cameraRef}
            style={StyleSheet.absoluteFill}
            device={device}
            isActive={true}
            video={true}
            audio={true}
            onInitialized={() => {
              // Este callback confirma que la cámara está completamente lista
              console.log('[App] onInitialized → cámara lista para grabar');
              setIsCameraReady(true);
            }}
            onError={(e) => {
              console.error('[App] Camera error:', e.message);
            }}
          />
        )}

        <View style={[StyleSheet.absoluteFill, styles.overlay]} />

        <SystemHeader />

        {/* Badge de estado */}
        {isRecording && (
          <View style={[
            styles.statusBadge,
            status === 'saving' && { backgroundColor: 'rgba(255,165,0,0.9)' },
            status === 'cooldown' && { backgroundColor: 'rgba(100,100,200,0.9)' },
            !isCameraReady && { backgroundColor: 'rgba(100,100,100,0.9)' },
          ]}>
            <Text style={styles.statusText}>
              {!isCameraReady ? '⏳ Iniciando cámara...' : (statusLabel[status] ?? status)}
            </Text>
          </View>
        )}

        {/* ── Panel G-Force ─────────────────────────── */}
        <View style={styles.gforcePanel}>
          <Text style={styles.gforceLabel}>ACELERÓMETRO</Text>

          <Text style={[styles.gforceValue, { color: gBarColor }]}>
            {currentGForce.toFixed(2)} <Text style={styles.gforceUnit}>G</Text>
          </Text>

          <View style={styles.gforceBarBg}>
            <View style={[styles.gforceBarFill, { width: `${gPercent * 100}%`, backgroundColor: gBarColor }]} />
            <View style={[styles.thresholdLine, { left: `${(1 / 1.5) * 100}%` }]} />
          </View>

          <View style={styles.gforceScaleRow}>
            <Text style={styles.gforceScaleText}>0G</Text>
            <Text style={[styles.gforceScaleText, { color: '#FF2D2D' }]}>↑ Umbral {G_FORCE_THRESHOLD}G</Text>
            <Text style={styles.gforceScaleText}>{(G_FORCE_THRESHOLD * 1.5).toFixed(1)}G</Text>
          </View>

          <Text style={styles.sensorStatus}>
            {currentGForce > 0.5 ? '✅ Acelerómetro leyendo datos' : '⏳ Esperando movimiento...'}
          </Text>

          {/* Estado de la cámara y segmentos grabados */}
          {isRecording && (
            <Text style={[styles.sensorStatus, { marginTop: 2, color: isCameraReady ? '#00E5A0' : '#FFA500' }]}>
              {isCameraReady
                ? `📹 Grabando · Segmentos completos: ${segmentCount}`
                : '📷 Inicializando cámara...'}
            </Text>
          )}
        </View>

        <View style={styles.recordContainer}>
          <RecordButton isRecording={isRecording} onToggle={toggleRecording} />
        </View>

        {/* Botón de prueba */}
        {isRecording && isCameraReady && status === 'recording' && (
          <TouchableOpacity style={styles.testButton} onPress={simulateImpact}>
            <Text style={styles.testButtonText}>🧪 PROBAR GUARDADO</Text>
          </TouchableOpacity>
        )}

        <View style={styles.statusContainer}>
          <StatusCard iconName="harddisk" title="STORAGE" value="128" valueSuffix="GB" />
          <StatusCard iconName="battery-charging-outline" title="POWER" value="EXT." isActive={true} />
          <StatusCard iconName="satellite-uplink" title="GPS FIX" value="3D" valueSuffix="Lock" />
        </View>

        <BottomNav />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  container: { flex: 1, backgroundColor: colors.background },
  overlay: { backgroundColor: 'rgba(0,0,0,0.55)' },

  statusBadge: {
    alignSelf: 'center',
    marginTop: 8,
    backgroundColor: 'rgba(255,60,60,0.85)',
    paddingHorizontal: 16,
    paddingVertical: 5,
    borderRadius: 20,
  },
  statusText: { color: '#fff', fontWeight: '700', fontSize: 13, letterSpacing: 1 },

  gforcePanel: {
    marginHorizontal: 20,
    marginTop: 14,
    backgroundColor: 'rgba(255,255,255,0.06)',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  gforceLabel: { color: 'rgba(255,255,255,0.5)', fontSize: 10, fontWeight: '700', letterSpacing: 2, marginBottom: 4 },
  gforceValue: { fontSize: 36, fontWeight: '800', marginBottom: 10 },
  gforceUnit: { fontSize: 18, fontWeight: '400' },
  gforceBarBg: {
    height: 14,
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: 7,
    overflow: 'hidden',
    position: 'relative',
  },
  gforceBarFill: { height: '100%', borderRadius: 7 },
  thresholdLine: { position: 'absolute', top: 0, bottom: 0, width: 2, backgroundColor: 'rgba(255,45,45,0.8)' },
  gforceScaleRow: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 4 },
  gforceScaleText: { color: 'rgba(255,255,255,0.4)', fontSize: 10 },
  sensorStatus: { color: 'rgba(255,255,255,0.6)', fontSize: 11, marginTop: 8, textAlign: 'center' },

  recordContainer: { flex: 1, alignItems: 'center', justifyContent: 'center' },

  testButton: {
    alignSelf: 'center',
    marginBottom: 12,
    backgroundColor: 'rgba(255, 200, 0, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255,200,0,0.6)',
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 24,
  },
  testButtonText: { color: '#FFD700', fontWeight: '700', fontSize: 13, letterSpacing: 1 },

  statusContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
});
