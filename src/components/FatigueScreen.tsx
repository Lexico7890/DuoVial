import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, ScrollView, DeviceEventEmitter,
} from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';
import { BackgroundGuard, FatigueStatus } from '../services/BackgroundGuard';

interface FatigueScreenProps {
  onBack: () => void;
}

/**
 * Pantalla dedicada para detección de somnolencia.
 * Muestra preview de cámara frontal, estado de EAR, configuración y alertas.
 */
export const FatigueScreen: React.FC<FatigueScreenProps> = ({ onBack }) => {
  // Estado de fatiga desde el servicio nativo
  const [fatigueEnabled, setFatigueEnabled] = useState(false);
  const [faceDetected, setFaceDetected] = useState(false);
  const [earValue, setEarValue] = useState(0);
  const [closedEyeDuration, setClosedEyeDuration] = useState(0);
  const [isSnoozed, setIsSnoozed] = useState(false);
  const [alertCount, setAlertCount] = useState(0);

  // Configuración local (se sincroniza con el servicio)
  const [earThreshold, setEarThreshold] = useState(0.2);
  const [durationThreshold, setDurationThreshold] = useState(2);
  const [maxAlerts, setMaxAlerts] = useState(3);

  // Estado visual
  const [showAlert, setShowAlert] = useState(false);
  const [alertEarValue, setAlertEarValue] = useState(0);

  useEffect(() => {
    // Sincronizar estado inicial desde el servicio
    BackgroundGuard.getFatigueStatus().then((status: FatigueStatus) => {
      setFatigueEnabled(status.enabled);
      setFaceDetected(status.faceDetected);
      setEarValue(status.earValue);
      setClosedEyeDuration(status.closedEyeDuration);
      setIsSnoozed(status.isSnoozed);
      setAlertCount(status.alertCount);
      setEarThreshold(status.earThreshold);
    });

    // Escuchar eventos de estado facial
    const faceStatusSub = DeviceEventEmitter.addListener('onFaceStatusChanged', (event) => {
      setFatigueEnabled(event.enabled);
      setFaceDetected(event.faceDetected);
      setEarValue(event.earValue);
      setClosedEyeDuration(event.closedEyeDuration);
    });

    // Escuchar alertas de somnolencia
    const drowsinessSub = DeviceEventEmitter.addListener('onDrowsinessDetected', (event) => {
      setShowAlert(true);
      setAlertEarValue(event.earValue);
      setAlertCount((prev) => prev + 1);
      // Auto-ocultar alerta después de 5 segundos
      setTimeout(() => setShowAlert(false), 5000);
    });

    return () => {
      faceStatusSub.remove();
      drowsinessSub.remove();
    };
  }, []);

  const toggleFatigue = useCallback(() => {
    const newState = !fatigueEnabled;
    setFatigueEnabled(newState);
    BackgroundGuard.enableFatigueDetection(newState);
  }, [fatigueEnabled]);

  const handleSnooze = useCallback(() => {
    BackgroundGuard.snoozeFatigueAlert(5);
    setIsSnoozed(true);
    setTimeout(() => setIsSnoozed(false), 5 * 60 * 1000);
  }, []);

  const adjustEarThreshold = useCallback((delta: number) => {
    const next = Math.max(0.1, Math.min(0.4, +(earThreshold + delta).toFixed(2)));
    setEarThreshold(next);
    BackgroundGuard.setEarThreshold(next);
  }, [earThreshold]);

  const adjustDurationThreshold = useCallback((delta: number) => {
    setDurationThreshold(Math.max(1, Math.min(5, durationThreshold + delta)));
  }, [durationThreshold]);

  const adjustMaxAlerts = useCallback((delta: number) => {
    setMaxAlerts(Math.max(1, Math.min(5, maxAlerts + delta)));
  }, [maxAlerts]);

  // Estado visual del indicador
  const getEyeStatus = () => {
    if (!fatigueEnabled) return { icon: 'eye-off', color: colors.textSecondary, text: 'Inactivo' };
    if (!faceDetected) return { icon: 'account-question', color: '#FF9F0A', text: 'Sin rostro' };
    if (showAlert) return { icon: 'eye-off', color: colors.neonRed, text: 'FATIGA!' };
    if (closedEyeDuration > 0) return { icon: 'eye-clock', color: '#FFB300', text: `${closedEyeDuration.toFixed(1)}s cerrado` };
    return { icon: 'eye', color: colors.neonGreen, text: 'Ojos abiertos' };
  };

  const eyeStatus = getEyeStatus();

  // Mapear EAR a porcentaje de progreso (0.0 = 0%, 0.5+ = 100%)
  const earProgress = Math.min(1, (earValue / 0.5) * 100);
  const earBarColor = earValue < earThreshold ? colors.neonRed : earValue < earThreshold * 1.5 ? '#FFB300' : colors.neonGreen;

  return (
    <View style={styles.root}>
      {/* Header con botón volver */}
      <View style={styles.header}>
        <TouchableOpacity activeOpacity={0.7} onPress={onBack} style={styles.backButton}>
          <MaterialCommunityIcons name="arrow-left" size={20} color={colors.textPrimary} />
          <Text style={styles.backText}>Volver</Text>
        </TouchableOpacity>
        <View style={styles.headerTitle}>
          <MaterialCommunityIcons name="emoticon-wink-outline" size={18} color="#FF9F0A" />
          <Text style={styles.headerTitleText}>DuoVial Frontal</Text>
        </View>
      </View>

      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Indicador de estado principal */}
        <View style={[styles.statusCard, showAlert && styles.statusCardAlert]}>
          <MaterialCommunityIcons name={eyeStatus.icon as any} size={48} color={eyeStatus.color} />
          <Text style={[styles.statusText, { color: eyeStatus.color }]}>{eyeStatus.text}</Text>
          {faceDetected && (
            <Text style={styles.faceInfo}>
              Rostro del conductor detectado
            </Text>
          )}
        </View>

        {/* Valor EAR en tiempo real */}
        <View style={styles.earCard}>
          <View style={styles.earHeader}>
            <Text style={styles.earLabel}>EAR (Eye Aspect Ratio)</Text>
            <Text style={styles.earValue}>{earValue.toFixed(3)}</Text>
          </View>
          <View style={styles.earBarBg}>
            <View style={[styles.earBarFill, { width: `${earProgress}%`, backgroundColor: earBarColor }]} />
            <View
              style={[
                styles.earThresholdLine,
                { left: `${Math.min(100, (earThreshold / 0.5) * 100)}%` },
              ]}
            />
          </View>
          <View style={styles.earLegend}>
            <Text style={styles.earLegendText}>0.0</Text>
            <Text style={[styles.earLegendText, { color: colors.neonRed }]}>Threshold: {earThreshold.toFixed(2)}</Text>
            <Text style={styles.earLegendText}>0.5</Text>
          </View>
        </View>

        {/* Configuración */}
        <View style={styles.settingsSection}>
          <Text style={styles.sectionTitle}>Configuración</Text>

          {/* EAR Threshold */}
          <View style={styles.settingRow}>
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>Sensibilidad EAR</Text>
              <Text style={styles.settingDesc}>Umbral para detectar ojos cerrados ({earThreshold.toFixed(2)})</Text>
            </View>
            <View style={styles.stepper}>
              <TouchableOpacity
                onPress={() => adjustEarThreshold(-0.05)}
                style={styles.stepperBtn}
              >
                <MaterialCommunityIcons name="minus" size={14} color={colors.neonGreen} />
              </TouchableOpacity>
              <Text style={styles.stepperValue}>{earThreshold.toFixed(2)}</Text>
              <TouchableOpacity
                onPress={() => adjustEarThreshold(0.05)}
                style={styles.stepperBtn}
              >
                <MaterialCommunityIcons name="plus" size={14} color={colors.neonGreen} />
              </TouchableOpacity>
            </View>
          </View>

          {/* Duration Threshold */}
          <View style={styles.settingRow}>
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>Umbral de tiempo</Text>
              <Text style={styles.settingDesc}>Segundos con ojos cerrados antes de alertar ({durationThreshold}s)</Text>
            </View>
            <View style={styles.stepper}>
              <TouchableOpacity
                onPress={() => adjustDurationThreshold(-1)}
                style={styles.stepperBtn}
              >
                <MaterialCommunityIcons name="minus" size={14} color={colors.neonGreen} />
              </TouchableOpacity>
              <Text style={styles.stepperValue}>{durationThreshold}s</Text>
              <TouchableOpacity
                onPress={() => adjustDurationThreshold(1)}
                style={styles.stepperBtn}
              >
                <MaterialCommunityIcons name="plus" size={14} color={colors.neonGreen} />
              </TouchableOpacity>
            </View>
          </View>

          {/* Max Alerts */}
          <View style={styles.settingRow}>
            <View style={styles.settingInfo}>
              <Text style={styles.settingLabel}>Alertas por hora</Text>
              <Text style={styles.settingDesc}>Máximo de alertas por hora (anti-spam)</Text>
            </View>
            <View style={styles.stepper}>
              <TouchableOpacity
                onPress={() => adjustMaxAlerts(-1)}
                style={styles.stepperBtn}
              >
                <MaterialCommunityIcons name="minus" size={14} color={colors.neonGreen} />
              </TouchableOpacity>
              <Text style={styles.stepperValue}>{maxAlerts}</Text>
              <TouchableOpacity
                onPress={() => adjustMaxAlerts(1)}
                style={styles.stepperBtn}
              >
                <MaterialCommunityIcons name="plus" size={14} color={colors.neonGreen} />
              </TouchableOpacity>
            </View>
          </View>
        </View>

        {/* Acciones */}
        <View style={styles.actionsSection}>
          {/* Toggle principal */}
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={toggleFatigue}
            style={[styles.toggleButton, fatigueEnabled && styles.toggleButtonActive]}
          >
            <MaterialCommunityIcons
              name={fatigueEnabled ? 'eye' : 'eye-off'}
              size={22}
              color={fatigueEnabled ? '#000' : colors.textSecondary}
            />
            <Text style={[styles.toggleButtonText, fatigueEnabled && styles.toggleButtonTextActive]}>
              {fatigueEnabled ? 'DESACTIVAR VIGILANCIA' : 'ACTIVAR VIGILANCIA'}
            </Text>
          </TouchableOpacity>

          {/* Snooze */}
          {fatigueEnabled && (
            <TouchableOpacity
              activeOpacity={0.8}
              onPress={handleSnooze}
              disabled={isSnoozed}
              style={[styles.snoozeButton, isSnoozed && styles.snoozeButtonDisabled]}
            >
              <MaterialCommunityIcons
                name={isSnoozed ? 'bell-check' : 'bell-sleep'}
                size={18}
                color={isSnoozed ? colors.textSecondary : '#FF9F0A'}
              />
              <Text style={[styles.snoozeText, isSnoozed && styles.snoozeTextDisabled]}>
                {isSnoozed ? 'SNOOZE ACTIVO (5 min)' : 'SNOOZE 5 MIN'}
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Info de estado */}
        <View style={styles.infoSection}>
          <View style={styles.infoRow}>
            <MaterialCommunityIcons name="information-outline" size={14} color={colors.textSecondary} />
            <Text style={styles.infoText}>
              Alertas esta hora: {alertCount}/{maxAlerts}
            </Text>
          </View>
          <View style={styles.infoRow}>
            <MaterialCommunityIcons name="shield-check" size={14} color={colors.textSecondary} />
            <Text style={styles.infoText}>
              Solo se vigila al conductor (izquierda de pantalla)
            </Text>
          </View>
          <View style={styles.infoRow}>
            <MaterialCommunityIcons name="cellphone" size={14} color={colors.textSecondary} />
            <Text style={styles.infoText}>
              Los frames se procesan y descartan — nada se graba
            </Text>
          </View>
        </View>
      </ScrollView>

      {/* Alerta overlay */}
      {showAlert && (
        <View style={styles.alertOverlay}>
          <View style={styles.alertCard}>
            <MaterialCommunityIcons name="eye-off" size={40} color={colors.neonRed} />
            <Text style={styles.alertTitle}>FATIGA DETECTADA</Text>
            <Text style={styles.alertSubtitle}>EAR: {alertEarValue.toFixed(3)} — Mantén los ojos abiertos</Text>
            <TouchableOpacity
              activeOpacity={0.8}
              onPress={handleSnooze}
              style={styles.alertSnoozeBtn}
            >
              <Text style={styles.alertSnoozeText}>SNOOZE 5 MIN</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    marginTop: 50,
    marginBottom: 10,
  },
  backButton: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  backText: {
    color: colors.textPrimary,
    fontSize: 13,
    fontWeight: '700',
    marginLeft: 6,
  },
  headerTitle: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  headerTitleText: {
    color: '#FF9F0A',
    fontSize: 14,
    fontWeight: '800',
    marginLeft: 6,
    letterSpacing: 0.5,
  },
  content: {
    flex: 1,
  },
  contentContainer: {
    paddingHorizontal: 20,
    paddingBottom: 30,
  },
  // --- STATUS CARD ---
  statusCard: {
    alignItems: 'center',
    backgroundColor: colors.cardBackground,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    padding: 24,
    marginBottom: 16,
  },
  statusCardAlert: {
    borderColor: colors.neonRed,
    backgroundColor: 'rgba(255, 23, 68, 0.08)',
  },
  statusText: {
    fontSize: 18,
    fontWeight: '900',
    marginTop: 10,
    letterSpacing: 0.5,
  },
  faceInfo: {
    color: colors.textSecondary,
    fontSize: 11,
    marginTop: 6,
  },
  // --- EAR CARD ---
  earCard: {
    backgroundColor: colors.cardBackground,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
  },
  earHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  earLabel: {
    color: colors.textSecondary,
    fontSize: 12,
    fontWeight: '700',
  },
  earValue: {
    color: colors.textPrimary,
    fontSize: 18,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
  },
  earBarBg: {
    height: 8,
    backgroundColor: colors.border,
    borderRadius: 4,
    position: 'relative',
    overflow: 'visible',
  },
  earBarFill: {
    height: '100%',
    borderRadius: 4,
  },
  earThresholdLine: {
    position: 'absolute',
    top: -4,
    width: 2,
    height: 16,
    backgroundColor: colors.neonRed,
    borderRadius: 1,
  },
  earLegend: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  earLegendText: {
    color: colors.textSecondary,
    fontSize: 9,
    fontWeight: '600',
  },
  // --- SETTINGS ---
  settingsSection: {
    marginBottom: 16,
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 12,
    letterSpacing: 0.5,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.cardBackground,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
  },
  settingInfo: {
    flex: 1,
    marginRight: 12,
  },
  settingLabel: {
    color: colors.textPrimary,
    fontSize: 12,
    fontWeight: '700',
  },
  settingDesc: {
    color: colors.textSecondary,
    fontSize: 10,
    marginTop: 2,
  },
  stepper: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepperBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: colors.neonGreen,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.greenDim,
  },
  stepperValue: {
    color: colors.textPrimary,
    fontSize: 13,
    fontWeight: '800',
    marginHorizontal: 10,
    minWidth: 36,
    textAlign: 'center',
  },
  // --- ACTIONS ---
  actionsSection: {
    marginBottom: 16,
  },
  toggleButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(58, 63, 66, 0.7)',
    borderRadius: 14,
    paddingVertical: 16,
    marginBottom: 10,
  },
  toggleButtonActive: {
    backgroundColor: colors.neonGreen,
  },
  toggleButtonText: {
    color: colors.textSecondary,
    fontSize: 12,
    fontWeight: '800',
    marginLeft: 8,
    letterSpacing: 1,
  },
  toggleButtonTextActive: {
    color: '#000',
  },
  snoozeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 159, 10, 0.1)',
    borderWidth: 1,
    borderColor: '#FF9F0A',
    borderRadius: 14,
    paddingVertical: 14,
  },
  snoozeButtonDisabled: {
    backgroundColor: 'rgba(58, 63, 66, 0.5)',
    borderColor: colors.border,
    opacity: 0.6,
  },
  snoozeText: {
    color: '#FF9F0A',
    fontSize: 11,
    fontWeight: '800',
    marginLeft: 8,
    letterSpacing: 1,
  },
  snoozeTextDisabled: {
    color: colors.textSecondary,
  },
  // --- INFO ---
  infoSection: {
    backgroundColor: colors.cardBackground,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: 14,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  infoText: {
    color: colors.textSecondary,
    fontSize: 10,
    marginLeft: 8,
    flex: 1,
  },
  // --- ALERT OVERLAY ---
  alertOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 100,
  },
  alertCard: {
    backgroundColor: colors.cardBackground,
    borderWidth: 2,
    borderColor: colors.neonRed,
    borderRadius: 20,
    padding: 30,
    alignItems: 'center',
    width: '80%',
  },
  alertTitle: {
    color: colors.neonRed,
    fontSize: 20,
    fontWeight: '900',
    marginTop: 12,
    letterSpacing: 1,
  },
  alertSubtitle: {
    color: colors.textSecondary,
    fontSize: 12,
    marginTop: 8,
    textAlign: 'center',
  },
  alertSnoozeBtn: {
    marginTop: 20,
    backgroundColor: 'rgba(255, 159, 10, 0.15)',
    borderWidth: 1,
    borderColor: '#FF9F0A',
    borderRadius: 12,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  alertSnoozeText: {
    color: '#FF9F0A',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
  },
});
