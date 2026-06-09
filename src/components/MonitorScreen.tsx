import React, { ComponentType } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ActivityIndicator, ViewProps, requireNativeComponent } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

const BackgroundCameraPreview: ComponentType<ViewProps> = requireNativeComponent('BackgroundCameraPreview');

interface MonitorScreenProps {
  status: string;
  gForce: number;
  speed: number;
  gForceThreshold: number;
  hasCameraPermission: boolean | null;
  isRecording: boolean;
  isSaving: boolean;
  onRequestPermissions: () => void;
  onStart: () => void;
  onStop: () => void;
  onToggle: () => void;
  onOpenFrontal: () => void;
}

/**
 * Mensajes cortos y claros para el status pill (estilo "Luego" de Google Maps).
 * Mantener sincronizado con los strings que emite BackgroundCameraService.kt.
 */
function getShortStatus(status: string): { text: string; tone: 'idle' | 'ok' | 'saving' } {
  switch (status) {
    case 'INACTIVO':
      return { text: 'Vigilante apagado', tone: 'idle' };
    case 'INICIANDO DUOVIAL':
      return { text: 'Iniciando…', tone: 'saving' };
    case 'DUOVIAL ACTIVO':
      return { text: 'Vigilando', tone: 'ok' };
    default:
      if (status.includes('GUARDANDO')) return { text: 'Guardando evento', tone: 'saving' };
      if (status.includes('GENERANDO')) return { text: 'Guardando evento', tone: 'saving' };
      if (status.includes('GRABANDO')) return { text: 'Vigilando', tone: 'ok' };
      return { text: status, tone: 'idle' };
  }
}

export const MonitorScreen: React.FC<MonitorScreenProps> = ({
  status,
  gForce,
  speed,
  gForceThreshold,
  hasCameraPermission,
  isRecording,
  isSaving,
  onRequestPermissions,
  onStart,
  onStop,
  onToggle,
  onOpenFrontal,
}) => {
  const shortStatus = getShortStatus(status);

  return (
    <View style={styles.root}>
      {/* 1. Cámara fullscreen como fondo */}
      {hasCameraPermission === true ? (
        <BackgroundCameraPreview style={StyleSheet.absoluteFill} />
      ) : (
        <View style={[StyleSheet.absoluteFill, styles.cameraFallback]}>
          {hasCameraPermission === null ? (
            <ActivityIndicator size="large" color={colors.neonGreen} />
          ) : (
            <View style={styles.permissionCard}>
              <MaterialCommunityIcons name="shield-lock-outline" size={48} color="#FF9F0A" />
              <Text style={styles.permissionTitle}>Permiso de Cámara Requerido</Text>
              <Text style={styles.permissionDesc}>
                DuoVial necesita acceso a la cámara trasera para funcionar como DashCam.
              </Text>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={onRequestPermissions}
                style={styles.permissionBtn}
              >
                <Text style={styles.permissionBtnText}>OTORGAR ACCESO</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      )}

      {/* 2. REC badge parpadeante (sólo cuando graba) */}
      {isRecording && (
        <View style={styles.recBadge}>
          <View style={styles.recDot} />
          <Text style={styles.recBadgeText}>REC</Text>
        </View>
      )}

      {/* 3. Header flotante con efecto glass (top) */}
      <View style={styles.floatingHeader}>
        <View style={styles.headerLeft}>
          <View style={styles.logoCircle}>
            <MaterialCommunityIcons name="camera-iris" size={20} color="#000" />
          </View>
          <View>
            <Text style={styles.headerTitle}>DuoVial</Text>
            <Text style={styles.headerSubtitle}>STANDBY ACTIVE</Text>
          </View>
        </View>
        <TouchableOpacity activeOpacity={0.8} onPress={onOpenFrontal} style={styles.frontalButton}>
          <MaterialCommunityIcons name="emoticon-wink-outline" size={16} color="#FF9F0A" />
          <Text style={styles.frontalButtonText}>Frontal</Text>
        </TouchableOpacity>
      </View>

      {/* 4. Status pill (estilo "Luego" de Maps) — debajo del header */}
      <View style={[styles.statusPill, shortStatus.tone === 'ok' && styles.statusPillOk]}>
        <View
          style={[
            styles.statusDot,
            {
              backgroundColor:
                shortStatus.tone === 'ok' ? colors.neonGreen :
                shortStatus.tone === 'saving' ? '#FF9F0A' :
                colors.textSecondary,
            },
          ]}
        />
        <Text style={styles.statusPillText}>{shortStatus.text}</Text>
      </View>

      {/* 5. Bottom-left: Acelerómetro (G-Force) sobre Velocímetro (MPH) — círculos sin título */}
      <View style={styles.telemetryStack}>
        <View style={styles.telemetryCircle}>
          <Text style={styles.telemetryValue}>{gForce.toFixed(2)}</Text>
          <Text style={styles.telemetryUnit}>G · {gForceThreshold.toFixed(1)}</Text>
        </View>
        <View style={styles.telemetryCircle}>
          <Text style={styles.telemetryValue}>{speed}</Text>
          <Text style={styles.telemetryUnit}>MPH</Text>
        </View>
      </View>

      {/* 6. Bottom-right: ENCENDER (icono) sobre EVENTO */}
      <View style={styles.bottomActions}>
        <TouchableOpacity
          activeOpacity={0.8}
          onPress={isRecording ? onStop : onStart}
          disabled={isSaving}
          style={[
            styles.powerIconButton,
            { backgroundColor: isRecording ? colors.neonRed : colors.neonGreen },
            isSaving && styles.powerIconButtonDisabled,
          ]}
        >
          <MaterialCommunityIcons
            name={isRecording ? 'stop' : 'power'}
            size={26}
            color="#000"
          />
        </TouchableOpacity>

        <TouchableOpacity
          activeOpacity={0.8}
          onPress={onToggle}
          disabled={isSaving || !isRecording}
          style={[
            styles.eventoButton,
            !isRecording && styles.eventoButtonInactive,
            isSaving && styles.eventoButtonSaving,
          ]}
        >
          <MaterialCommunityIcons
            name={isSaving ? 'progress-download' : 'alert-decagram'}
            size={20}
            color={isSaving ? '#000' : '#1A1A1A'}
            style={{ marginRight: 8 }}
          />
          <Text style={styles.eventoButtonText}>
            {isSaving ? 'Guardando' : 'Evento'}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000',
  },
  cameraFallback: {
    backgroundColor: '#0D1B2A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  permissionCard: {
    alignItems: 'center',
    paddingHorizontal: 30,
  },
  permissionTitle: {
    color: colors.textPrimary,
    fontSize: 15,
    fontWeight: '800',
    marginTop: 12,
    letterSpacing: 0.5,
    textAlign: 'center',
  },
  permissionDesc: {
    color: colors.textSecondary,
    fontSize: 11,
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 16,
  },
  permissionBtn: {
    backgroundColor: 'rgba(255, 215, 0, 0.1)',
    borderWidth: 1.5,
    borderColor: '#FFD700',
    borderRadius: 20,
    paddingHorizontal: 20,
    paddingVertical: 10,
    marginTop: 18,
  },
  permissionBtnText: {
    color: '#FFD700',
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1,
  },
  // --- HEADER FLOTANTE (glass) ---
  floatingHeader: {
    position: 'absolute',
    top: 50,
    left: 20,
    right: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: 'rgba(13, 27, 42, 0.55)',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 6,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  logoCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.neonGreen,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
    shadowColor: colors.neonGreen,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 6,
    elevation: 4,
  },
  headerTitle: {
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  headerSubtitle: {
    color: colors.textSecondary,
    fontSize: 8,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginTop: 2,
  },
  frontalButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 159, 10, 0.12)',
    borderWidth: 1,
    borderColor: '#FF9F0A',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  frontalButtonText: {
    color: '#FF9F0A',
    fontSize: 10,
    fontWeight: '800',
    marginLeft: 6,
    letterSpacing: 0.8,
  },
  // --- STATUS PILL (estilo "Luego" de Maps) ---
  statusPill: {
    position: 'absolute',
    top: 118,
    left: 20,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.55)',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
  },
  statusPillOk: {
    backgroundColor: 'rgba(0, 230, 118, 0.12)',
    borderColor: 'rgba(0, 230, 118, 0.35)',
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  statusPillText: {
    color: colors.textPrimary,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
  // --- REC BADGE ---
  recBadge: {
    position: 'absolute',
    top: 118,
    right: 20,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.65)',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(255, 42, 85, 0.4)',
  },
  recDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.neonRed,
    marginRight: 6,
  },
  recBadgeText: {
    color: colors.textPrimary,
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
  // --- TELEMETRÍA (bottom-left, G-Force arriba de MPH) — círculos sin título ---
  telemetryStack: {
    position: 'absolute',
    left: 20,
    bottom: 16,
  },
  telemetryCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: 'rgba(13, 27, 42, 0.55)',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 3,
  },
  telemetryValue: {
    color: colors.textPrimary,
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  telemetryUnit: {
    color: colors.textSecondary,
    fontSize: 9,
    fontWeight: '700',
    marginTop: 2,
    letterSpacing: 0.5,
  },
  // --- ACCIONES (bottom-right: ENCENDER encima de EVENTO) ---
  bottomActions: {
    position: 'absolute',
    right: 20,
    bottom: 16,
    alignItems: 'flex-end',
  },
  powerIconButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
    elevation: 8,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    shadowColor: '#000',
  },
  powerIconButtonDisabled: {
    backgroundColor: colors.border,
    opacity: 0.5,
  },
  eventoButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 22,
    paddingVertical: 14,
    borderRadius: 28,
    backgroundColor: '#FFD700',
    elevation: 6,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.35,
    shadowRadius: 6,
    shadowColor: '#FFD700',
  },
  eventoButtonInactive: {
    backgroundColor: 'rgba(58, 63, 66, 0.7)',
    shadowOpacity: 0,
  },
  eventoButtonSaving: {
    backgroundColor: '#FF9F0A',
    shadowColor: '#FF9F0A',
  },
  eventoButtonText: {
    color: '#1A1A1A',
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 0.6,
  },
});
