import React, { useState, useEffect } from 'react';
import { StyleSheet, View, StatusBar, SafeAreaView, PermissionsAndroid, Platform, TouchableOpacity, Text, DeviceEventEmitter, ActivityIndicator } from 'react-native';
import { colors } from './src/theme/colors';
import { SystemHeader } from './src/components/SystemHeader';
import { StatusCard } from './src/components/StatusCard';
import { BottomNav } from './src/components/BottomNav';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { requireNativeComponent } from 'react-native';

import { BackgroundGuard } from './src/services/BackgroundGuard';

// Cargar el componente de Preview nativo expuesto por Kotlin
const BackgroundCameraPreview = requireNativeComponent('BackgroundCameraPreview');

export default function App() {
  const [activeTab, setActiveTab] = useState('Monitor');
  const [status, setStatus] = useState('INACTIVO');
  const [gForce, setGForce] = useState(1.00);
  const [speed, setSpeed] = useState(0);
  
  // Estado para el control estricto de permisos y evitar la pantalla negra de primer inicio
  const [hasCameraPermission, setHasCameraPermission] = useState<boolean | null>(null);

  const isRecording = status !== 'INACTIVO';
  const isSaving = status.includes('GUARDANDO') || status.includes('GRABANDO') || status.includes('GENERANDO') || status === 'INICIANDO DUOVIAL';

  // Función para solicitar permisos de forma segura
  const requestInitialPermissions = async () => {
    if (Platform.OS === 'android') {
      const permissions = [
        PermissionsAndroid.PERMISSIONS.CAMERA,
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
      ];

      if (Platform.Version >= 33) {
        permissions.push(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
      }

      try {
        const results = await PermissionsAndroid.requestMultiple(permissions);
        const cameraGranted = results[PermissionsAndroid.PERMISSIONS.CAMERA] === PermissionsAndroid.RESULTS.GRANTED;
        setHasCameraPermission(cameraGranted);
      } catch (err) {
        console.warn('Error al solicitar permisos al inicio:', err);
        setHasCameraPermission(false);
      }
    } else {
      setHasCameraPermission(true);
    }
  };

  // Solicitar permisos nativos al iniciar la app
  useEffect(() => {
    requestInitialPermissions();
  }, []);

  // Iniciar la cámara en modo Standby apenas se concedan los permisos y el usuario esté en el Monitor
  useEffect(() => {
    if (activeTab === 'Monitor' && hasCameraPermission === true) {
      console.log('JS: Monitor activo y permisos concedidos. Iniciando cámara en Standby con retraso de seguridad...');
      const timer = setTimeout(() => {
        BackgroundGuard.startStandby();
      }, 250);
      return () => clearTimeout(timer);
    }
  }, [activeTab, hasCameraPermission]);

  // Escuchar eventos nativos en tiempo real desde el servicio en Kotlin
  useEffect(() => {
    // 1. Cambios de estado del buffer / grabación
    const statusSubscription = DeviceEventEmitter.addListener('onCameraStatusChanged', (event) => {
      console.log('JS: Estado de cámara recibido:', event.status);
      setStatus(event.status);
    });

    // 2. Fuerza G en tiempo real rate-limitada a 200ms
    const accelSubscription = DeviceEventEmitter.addListener('onAccelChanged', (event) => {
      setGForce(event.gForce);
    });

    // 3. Velocidad en tiempo real (velocímetro)
    const speedSubscription = DeviceEventEmitter.addListener('onSpeedChanged', (event) => {
      setSpeed(Math.round(event.speed));
    });

    return () => {
      statusSubscription.remove();
      accelSubscription.remove();
      speedSubscription.remove();
    };
  }, []);

  const handleToggle = () => {
    if (isSaving || !isRecording) return;
    console.log('Gatillando pánico manual...');
    BackgroundGuard.triggerPanic();
  };

  const handleStart = async () => {
    if (isSaving || isRecording) return;
    
    // Verificar permisos antes de encender para evitar pantalla negra
    if (!hasCameraPermission) {
      await requestInitialPermissions();
      return;
    }
    
    await BackgroundGuard.startGuarding();
  };

  const handleStop = async () => {
    if (isSaving || !isRecording) return;
    await BackgroundGuard.stopGuarding();
  };

  const getStatusColor = (currentStatus: string) => {
    switch (currentStatus) {
      case 'INACTIVO':
        return colors.textSecondary;
      case 'DUOVIAL ACTIVO':
        return colors.neonGreen;
      default: // INICIANDO DUOVIAL, GENERANDO CONTENIDO POST EVENTO, etc.
        return '#FF9F0A'; // Ámbar
    }
  };

  // ==========================================
  // RENDER PESTAÑA: MONITOR (DASHBOARD)
  // ==========================================
  const renderMonitor = () => {
    return (
      <View style={styles.tabContent}>
        {/* Telemetry Dashboard Card (G-Force & Speedometer) */}
        <View style={styles.telemetryCard}>
          <View style={styles.telemetryItem}>
            <Text style={styles.telemetryValue}>{speed}</Text>
            <Text style={styles.telemetryLabel}>MPH</Text>
          </View>
          <View style={styles.telemetryDivider} />
          <View style={styles.telemetryItemRight}>
            <Text style={styles.telemetryLabelUpper}>GRAVITATIONAL FORCE</Text>
            <View style={styles.gForceValueContainer}>
              <Text style={styles.gForceValue}>{gForce.toFixed(2)}G</Text>
              <Text style={styles.gForceThreshold}>/ 2.5 threshold</Text>
            </View>
          </View>
        </View>

        {/* Caja de texto informativa de estado en tiempo real */}
        <View style={styles.statusBadgeContainer}>
          <View style={[styles.statusDot, { backgroundColor: getStatusColor(status) }]} />
          <Text style={[styles.statusBadgeText, { color: colors.textPrimary }]}>
            {status === 'INACTIVO' ? 'VIGILANTE APAGADO - TOCA ENCENDER' : status}
          </Text>
        </View>

        {/* Cámara Preview Recuadro (Road Scan Viewport) */}
        <View style={[styles.previewContainer, isRecording && styles.previewContainerActive]}>
          {hasCameraPermission === false ? (
            <View style={styles.permissionCard}>
              <MaterialCommunityIcons name="shield-lock-outline" size={40} color="#FF9F0A" />
              <Text style={styles.permissionTitle}>Permiso de Cámara Requerido</Text>
              <Text style={styles.permissionDesc}>
                DuoVial necesita acceso a la cámara trasera para poder funcionar como DashCam.
              </Text>
              <TouchableOpacity 
                activeOpacity={0.8}
                onPress={requestInitialPermissions}
                style={styles.permissionBtn}
              >
                <Text style={styles.permissionBtnText}>OTORGAR ACCESO</Text>
              </TouchableOpacity>
            </View>
          ) : hasCameraPermission === null ? (
            <View style={styles.previewStandby}>
              <ActivityIndicator size="large" color={colors.neonGreen} />
            </View>
          ) : (
            <View style={styles.previewViewport}>
              <BackgroundCameraPreview style={StyleSheet.absoluteFill} />
              {/* Indicador de grabación REC parpadeante */}
              {isRecording && (
                <View style={styles.recBadge}>
                  <View style={styles.recDot} />
                  <Text style={styles.recBadgeText}>REC</Text>
                </View>
              )}
            </View>
          )}
        </View>

        {/* Controles de grabación (Panel rectangular de doble acción) */}
        <View style={styles.controlsContainer}>
          <View style={styles.controlsRowHorizontal}>
            {/* Botón Rectangular Amarillo: Solo PÁNICO */}
            <TouchableOpacity 
              activeOpacity={0.8}
              onPress={handleToggle}
              disabled={isSaving || !isRecording}
              style={[
                styles.panicRectButton,
                !isRecording && styles.panicRectButtonInactive,
                isSaving && styles.panicRectButtonSaving
              ]}
            >
              <MaterialCommunityIcons 
                name={isSaving ? "progress-download" : "alert-decagram"} 
                size={20} 
                color="#000" 
                style={{ marginRight: 8 }}
              />
              <Text style={styles.panicRectButtonText}>
                {isSaving ? "GUARDANDO..." : "⚠️ GATILLAR EVENTO (PÁNICO)"}
              </Text>
            </TouchableOpacity>
            
            {/* Botón de Encendido/Apagado del Vigilante (Verde/Rojo Rectangular) */}
            <TouchableOpacity 
              activeOpacity={0.8}
              onPress={isRecording ? handleStop : handleStart}
              disabled={isSaving}
              style={[
                styles.powerRectButton,
                { backgroundColor: isRecording ? colors.neonRed : colors.neonGreen },
                isSaving && styles.powerRectButtonDisabled
              ]}
            >
              <MaterialCommunityIcons 
                name={isRecording ? "stop" : "play"} 
                size={18} 
                color="#000" 
                style={{ marginRight: 6 }}
              />
              <Text style={styles.powerRectButtonText}>
                {isRecording ? 'APAGAR' : 'ENCENDER'}
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    );
  };

  // ==========================================
  // RENDER PESTAÑA: EVENTOS (GALERÍA CLIPS)
  // ==========================================
  const renderEventos = () => {
    return (
      <View style={styles.tabContent}>
        <View style={styles.headerSpacer}>
          <Text style={styles.tabTitle}>Incidentes Guardados</Text>
          <Text style={styles.tabSubtitle}>Videos grabados automáticamente en /Downloads/DuoVial</Text>
        </View>
        
        <View style={styles.emptyGalleryContainer}>
          <MaterialCommunityIcons name="folder-video" size={60} color={colors.textSecondary} />
          <Text style={styles.emptyGalleryText}>Galería Lista</Text>
          <Text style={styles.emptyGallerySubtext}>Los videos guardados por pánico o colisiones se sincronizan en tu almacenamiento de descargas público.</Text>
        </View>
      </View>
    );
  };

  // ==========================================
  // RENDER PESTAÑA: AJUSTES (SENSORES & PIP)
  // ==========================================
  const renderAjustes = () => {
    return (
      <View style={styles.tabContent}>
        <View style={styles.headerSpacer}>
          <Text style={styles.tabTitle}>Configuración</Text>
          <Text style={styles.tabSubtitle}>Calibración fina y herramientas avanzadas de DuoVial</Text>
        </View>

        <View style={styles.settingsList}>
          {/* Ajuste de Burbuja Flotante PIP */}
          <View style={styles.settingCard}>
            <View style={styles.settingHeader}>
              <MaterialCommunityIcons name="checkbox-blank-circle-outline" size={24} color="#FF9F0A" />
              <Text style={styles.settingTitle}>Burbuja Flotante de Pánico</Text>
            </View>
            <Text style={styles.settingDescription}>
              Habilita un botón flotante arrastrable que permanece visible sobre otras aplicaciones (Maps, Spotify, etc.) para que puedas registrar incidentes instantáneamente.
            </Text>
            
            {/* Mensaje de desbloqueo de Android Restricted Settings (Seguridad APK) */}
            <View style={styles.restrictedAlert}>
              <MaterialCommunityIcons name="alert-circle-outline" size={20} color="#FF9F0A" style={{ marginRight: 8, marginTop: 2 }} />
              <View style={{ flex: 1 }}>
                <Text style={styles.restrictedAlertTitle}>¿Bloqueado por seguridad de Android?</Text>
                <Text style={styles.restrictedAlertText}>
                  Si el sistema te niega el acceso por "ajustes restringidos" (común al instalar APKs), ve a: {'\n'}
                  <Text style={{ fontWeight: 'bold' }}>Ajustes del Teléfono ➡️ Aplicaciones ➡️ DuoVial ➡️ Tres puntos (esquina superior derecha) ➡️ Permitir ajustes restringidos</Text>. {'\n'}
                  Luego regresa aquí y autoriza la burbuja flotante.
                </Text>
              </View>
            </View>

            <TouchableOpacity 
              activeOpacity={0.8}
              onPress={() => BackgroundGuard.requestOverlayPermission()}
              style={styles.actionButton}
            >
              <Text style={styles.actionButtonText}>AUTORIZAR BURBUJA FLOTANTE</Text>
            </TouchableOpacity>
          </View>

          {/* Ajuste de Sensibilidad G-Force */}
          <View style={styles.settingCard}>
            <View style={styles.settingHeader}>
              <MaterialCommunityIcons name="axis-arrow" size={24} color={colors.neonGreen} />
              <Text style={styles.settingTitle}>Sensibilidad del Acelerómetro</Text>
            </View>
            <Text style={styles.settingDescription}>
              El umbral actual es de 2.5G (fijado nativamente para colisiones violentas y evitar falsos positivos).
            </Text>
            <View style={styles.mockSliderContainer}>
              <View style={styles.mockSliderBg}>
                <View style={styles.mockSliderProgress} />
                <View style={styles.mockSliderKnob} />
              </View>
              <Text style={styles.sliderLabel}>2.5G (Recomendado)</Text>
            </View>
          </View>
        </View>
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor={colors.background} />
      <View style={styles.container}>
        {/* Header Section */}
        <SystemHeader />

        {/* Render Tab Dinámico */}
        {activeTab === 'Monitor' && renderMonitor()}
        {activeTab === 'Eventos' && renderEventos()}
        {activeTab === 'Ajustes' && renderAjustes()}

        {/* Barra de Navegación del Menú Principal */}
        <View style={styles.bottomNavContainer}>
          <TouchableOpacity 
            style={styles.navTab}
            onPress={() => setActiveTab('Monitor')}
          >
            <MaterialCommunityIcons 
              name="view-dashboard-outline" 
              size={24} 
              color={activeTab === 'Monitor' ? colors.neonGreen : colors.textSecondary} 
            />
            <Text style={[styles.navLabel, { color: activeTab === 'Monitor' ? colors.neonGreen : colors.textSecondary }]}>
              Monitor
            </Text>
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={styles.navTab}
            onPress={() => setActiveTab('Eventos')}
          >
            <MaterialCommunityIcons 
              name="video-library" 
              size={24} 
              color={activeTab === 'Eventos' ? colors.neonGreen : colors.textSecondary} 
            />
            <Text style={[styles.navLabel, { color: activeTab === 'Eventos' ? colors.neonGreen : colors.textSecondary }]}>
              Eventos
            </Text>
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={styles.navTab}
            onPress={() => setActiveTab('Ajustes')}
          >
            <MaterialCommunityIcons 
              name="cog-outline" 
              size={24} 
              color={activeTab === 'Ajustes' ? colors.neonGreen : colors.textSecondary} 
            />
            <Text style={[styles.navLabel, { color: activeTab === 'Ajustes' ? colors.neonGreen : colors.textSecondary }]}>
              Ajustes
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background,
  },
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  tabContent: {
    flex: 1,
    width: '100%',
  },
  headerSpacer: {
    paddingHorizontal: 20,
    marginTop: 10,
    marginBottom: 25,
  },
  tabTitle: {
    color: colors.textPrimary,
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  tabSubtitle: {
    color: colors.textSecondary,
    fontSize: 12,
    marginTop: 4,
  },
  // --- TELEMETRY CARD ---
  telemetryCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.cardBackground,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: 20,
    marginHorizontal: 20,
    paddingVertical: 18,
    paddingHorizontal: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 6,
    elevation: 5,
  },
  telemetryItem: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 15,
  },
  telemetryValue: {
    color: colors.textPrimary,
    fontSize: 32,
    fontWeight: '900',
  },
  telemetryLabel: {
    color: colors.textSecondary,
    fontSize: 10,
    fontWeight: '700',
    marginTop: 2,
  },
  telemetryDivider: {
    width: 1.5,
    height: '75%',
    backgroundColor: colors.border,
    marginHorizontal: 10,
  },
  telemetryItemRight: {
    flex: 1,
    paddingLeft: 10,
  },
  telemetryLabelUpper: {
    color: colors.textSecondary,
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 1,
  },
  gForceValueContainer: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginTop: 4,
  },
  gForceValue: {
    color: colors.textPrimary,
    fontSize: 28,
    fontWeight: '900',
  },
  gForceThreshold: {
    color: colors.textSecondary,
    fontSize: 10,
    marginLeft: 6,
    fontWeight: '600',
  },
  // --- STATUS BADGE ---
  statusBadgeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 20,
    marginTop: 15,
    marginBottom: 10,
    paddingHorizontal: 6,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 10,
  },
  statusBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
  },
  // --- PREVIEW CONTAINER ---
  previewContainer: {
    height: 355,
    backgroundColor: colors.cardBackground,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: 22,
    marginHorizontal: 20,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 8,
  },
  previewContainerActive: {
    borderColor: 'rgba(0, 250, 154, 0.15)',
    shadowColor: colors.neonGreen,
    shadowOpacity: 0.1,
  },
  previewViewport: {
    flex: 1,
    position: 'relative',
  },
  recBadge: {
    position: 'absolute',
    top: 15,
    left: 15,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.65)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
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
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1,
  },
  previewStandby: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  previewStandbyText: {
    color: colors.textPrimary,
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: 1.5,
    marginTop: 12,
  },
  previewStandbySubtext: {
    color: colors.textSecondary,
    fontSize: 10,
    textAlign: 'center',
    marginTop: 4,
  },
  // --- PERMISSION CARD ---
  permissionCard: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 30,
    backgroundColor: 'rgba(18, 24, 27, 0.95)',
  },
  permissionTitle: {
    color: colors.textPrimary,
    fontSize: 15,
    fontWeight: '800',
    marginTop: 10,
    letterSpacing: 0.5,
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
    marginTop: 15,
  },
  permissionBtnText: {
    color: '#FFD700',
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1,
  },
  // --- CONTROLS ROW ---
  controlsContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    paddingHorizontal: 20,
  },
  controlsRowHorizontal: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    paddingHorizontal: 20,
    marginTop: 10,
  },
  panicRectButton: {
    flex: 2.2,
    height: 56,
    borderRadius: 16,
    backgroundColor: '#FFD700',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    elevation: 6,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    shadowColor: '#FFD700',
  },
  panicRectButtonInactive: {
    backgroundColor: '#3A3F42',
    shadowOpacity: 0,
    elevation: 0,
    opacity: 0.6,
  },
  panicRectButtonSaving: {
    backgroundColor: '#FF9F0A',
    shadowColor: '#FF9F0A',
  },
  panicRectButtonText: {
    color: '#000',
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 0.8,
  },
  powerRectButton: {
    flex: 1.1,
    height: 56,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 6,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    shadowColor: '#000',
  },
  powerRectButtonDisabled: {
    backgroundColor: colors.border,
    opacity: 0.4,
    elevation: 0,
    shadowOpacity: 0,
  },
  powerRectButtonText: {
    color: '#000',
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 0.8,
  },
  stopButton: {
    marginTop: 15,
    backgroundColor: 'rgba(255, 42, 85, 0.1)',
    borderWidth: 1.5,
    borderColor: colors.neonRed,
    paddingHorizontal: 25,
    paddingVertical: 12,
    borderRadius: 30,
    shadowColor: colors.neonRed,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 4,
  },
  stopButtonDisabled: {
    borderColor: colors.border,
    backgroundColor: 'rgba(30, 41, 59, 0.1)',
    shadowOpacity: 0,
    elevation: 0,
  },
  stopButtonText: {
    color: colors.neonRed,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 2,
  },
  // --- GALLERY VIEW ---
  emptyGalleryContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 40,
    marginBottom: 60,
  },
  emptyGalleryText: {
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '800',
    marginTop: 15,
    letterSpacing: 1,
  },
  emptyGallerySubtext: {
    color: colors.textSecondary,
    fontSize: 11,
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 18,
  },
  // --- SETTINGS VIEW ---
  settingsList: {
    paddingHorizontal: 20,
  },
  settingCard: {
    backgroundColor: colors.cardBackground,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    padding: 20,
    marginBottom: 20,
  },
  settingHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  settingTitle: {
    color: colors.textPrimary,
    fontSize: 14,
    fontWeight: '800',
    marginLeft: 10,
    letterSpacing: 0.5,
  },
  settingDescription: {
    color: colors.textSecondary,
    fontSize: 11,
    lineHeight: 18,
  },
  restrictedAlert: {
    flexDirection: 'row',
    backgroundColor: 'rgba(255, 159, 10, 0.05)',
    borderWidth: 1,
    borderColor: 'rgba(255, 159, 10, 0.25)',
    borderRadius: 10,
    padding: 12,
    marginTop: 12,
  },
  restrictedAlertTitle: {
    color: '#FF9F0A',
    fontSize: 11,
    fontWeight: '800',
    marginBottom: 4,
    letterSpacing: 0.5,
  },
  restrictedAlertText: {
    color: colors.textSecondary,
    fontSize: 10,
    lineHeight: 15,
  },
  actionButton: {
    backgroundColor: 'rgba(255, 159, 10, 0.1)',
    borderWidth: 1,
    borderColor: '#FF9F0A',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 15,
  },
  actionButtonText: {
    color: '#FF9F0A',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1,
  },
  mockSliderContainer: {
    marginTop: 15,
  },
  mockSliderBg: {
    height: 6,
    backgroundColor: colors.border,
    borderRadius: 3,
    position: 'relative',
    justifyContent: 'center',
  },
  mockSliderProgress: {
    position: 'absolute',
    left: 0,
    width: '60%',
    height: '100%',
    backgroundColor: colors.neonGreen,
    borderRadius: 3,
  },
  mockSliderKnob: {
    position: 'absolute',
    left: '60%',
    marginLeft: -8,
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: colors.textPrimary,
    borderWidth: 2,
    borderColor: colors.neonGreen,
  },
  sliderLabel: {
    color: colors.textSecondary,
    fontSize: 9,
    fontWeight: '700',
    marginTop: 10,
    textAlign: 'right',
  },
  // --- BOTTOM NAV BAR ---
  bottomNavContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    backgroundColor: colors.cardBackground,
    paddingVertical: 12,
    paddingBottom: 25,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderTopWidth: 1.5,
    borderTopColor: colors.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 10,
  },
  navTab: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  navLabel: {
    fontSize: 10,
    fontWeight: '700',
    marginTop: 4,
  },
});
