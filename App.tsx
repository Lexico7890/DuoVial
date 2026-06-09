import React, { useState, useEffect } from 'react';
import { StyleSheet, View, StatusBar, SafeAreaView, PermissionsAndroid, Platform, TouchableOpacity, Text, DeviceEventEmitter, ActivityIndicator } from 'react-native';
import { colors } from './src/theme/colors';
import { SystemHeader } from './src/components/SystemHeader';
import { MonitorScreen } from './src/components/MonitorScreen';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { BackgroundGuard } from './src/services/BackgroundGuard';
import { AuthProvider, useAuth } from './src/services/AuthContext';
import { configureAuth } from './src/services/AuthConfig';
import { LoginScreen } from './src/components/LoginScreen';

configureAuth();

function AppContent() {
  const [activeTab, setActiveTab] = useState('Monitor');
  const [status, setStatus] = useState('INACTIVO');
  const [gForce, setGForce] = useState(1.00);
  const [speed, setSpeed] = useState(0);
  const [showLogin, setShowLogin] = useState(false);
  const { user, logout, loading: authLoading } = useAuth();
  
  // Estado para el control estricto de permisos y evitar la pantalla negra de primer inicio
  const [hasCameraPermission, setHasCameraPermission] = useState<boolean | null>(null);

  const isRecording = status !== 'INACTIVO';
  const isSaving = status.includes('GUARDANDO') || status.includes('GENERANDO') || status === 'INICIANDO DUOVIAL';

  // Umbral G-Force real (única fuente de verdad: el Service). Se inicializa
  // con 2.5G como fallback y se sincroniza al montar para reflejar el valor
  // que el Service tenga (importante tras un reinicio del proceso nativo).
  const [gForceThreshold, setGForceThresholdState] = useState<number>(2.5);

  useEffect(() => {
    BackgroundGuard.getGForceThreshold()
      .then((value) => setGForceThresholdState(value))
      .catch(() => { /* fallback ya seteado */ });
  }, [hasCameraPermission]);

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

  // Iniciar la cámara en modo Standby apenas se concedan los permisos.
  // Importante: NO esperamos a la pestaña Monitor. El Service debe estar vivo
  // ANTES de que el PreviewView se monte en JSX, para evitar que el ViewManager
  // cree un Preview local paralelo (que luego sería destruido por el Service al
  // hacer unbindAll(), causando pantalla negra parpadeante).
  useEffect(() => {
    if (hasCameraPermission === true) {
      console.log('JS: Permisos concedidos. Iniciando cámara en Standby (arranque temprano).');
      BackgroundGuard.startStandby();
    }
  }, [hasCameraPermission]);

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

  // ==========================================
  // RENDER PESTAÑA: MONITOR (DASHBOARD) — fullscreen cámara estilo Maps
  // ==========================================
  const renderMonitor = () => (
    <MonitorScreen
      status={status}
      gForce={gForce}
      speed={speed}
      gForceThreshold={gForceThreshold}
      hasCameraPermission={hasCameraPermission}
      isRecording={isRecording}
      isSaving={isSaving}
      onRequestPermissions={requestInitialPermissions}
      onStart={handleStart}
      onStop={handleStop}
      onToggle={handleToggle}
    />
  );

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
  // RENDER PESTAÑA: CUENTA
  // ==========================================
  const renderCuenta = () => {
    if (authLoading) {
      return (
        <View style={[styles.tabContent, { justifyContent: 'center', alignItems: 'center' }]}>
          <ActivityIndicator size="large" color={colors.neonGreen} />
        </View>
      );
    }

    if (user) {
      return (
        <View style={styles.tabContent}>
          <View style={styles.headerSpacer}>
            <Text style={styles.tabTitle}>Mi Cuenta</Text>
            <Text style={styles.tabSubtitle}>Información de tu perfil</Text>
          </View>
          <View style={styles.settingsList}>
            <View style={styles.settingCard}>
              <View style={styles.settingHeader}>
                <MaterialCommunityIcons name="account-circle" size={24} color={colors.neonGreen} />
                <Text style={styles.settingTitle}>{user.email || user.username}</Text>
              </View>
              <Text style={styles.settingDescription}>
                Has iniciado sesión correctamente.
              </Text>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={async () => { await logout(); }}
                style={[styles.actionButton, { borderColor: colors.neonRed }]}
              >
                <Text style={[styles.actionButtonText, { color: colors.neonRed }]}>CERRAR SESIÓN</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      );
    }

    return (
      <View style={styles.tabContent}>
        <View style={styles.headerSpacer}>
          <Text style={styles.tabTitle}>Cuenta</Text>
          <Text style={styles.tabSubtitle}>Inicia sesión para sincronizar tus datos</Text>
        </View>
        <View style={[styles.emptyGalleryContainer, { marginBottom: 0 }]}>
          <MaterialCommunityIcons name="account-lock" size={60} color={colors.surface} />
          <Text style={styles.emptyGalleryText}>Sin sesión iniciada</Text>
          <Text style={styles.emptyGallerySubtext}>
            Inicia sesión para acceder a funciones premium y sincronización en la nube.
          </Text>
          <TouchableOpacity
            activeOpacity={0.8}
            onPress={() => setShowLogin(true)}
            style={[styles.primaryButton, { marginTop: 24 }]}
          >
            <Text style={styles.primaryButtonText}>INICIAR SESIÓN</Text>
          </TouchableOpacity>
        </View>
        {showLogin && <LoginScreen onClose={() => setShowLogin(false)} />}
      </View>
    );
  };

  // ==========================================
  // RENDER PESTAÑA: CONFIGURACIONES
  // ==========================================
  const renderConfiguraciones = () => {
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
              Umbral actual: {gForceThreshold.toFixed(1)}G. Valor configurable entre 1.5G (muy sensible) y 5.0G (sólo colisiones muy violentas). Por defecto 2.5G.
            </Text>
            <View style={styles.mockSliderContainer}>
              <View style={styles.mockSliderBg}>
                {/* Mapea 1.5G..5.0G → 0%..100% */}
                <View
                  style={[
                    styles.mockSliderProgress,
                    { width: `${Math.max(0, Math.min(100, ((gForceThreshold - 1.5) / 3.5) * 100))}%` },
                  ]}
                />
                <View
                  style={[
                    styles.mockSliderKnob,
                    { left: `${Math.max(0, Math.min(100, ((gForceThreshold - 1.5) / 3.5) * 100))}%` },
                  ]}
                />
              </View>
              <View style={styles.thresholdActions}>
                <TouchableOpacity
                  onPress={() => {
                    const next = Math.max(1.5, +(gForceThreshold - 0.1).toFixed(1));
                    setGForceThresholdState(next);
                    BackgroundGuard.setGForceThreshold(next);
                  }}
                  style={styles.thresholdBtn}
                >
                  <MaterialCommunityIcons name="minus" size={16} color={colors.neonGreen} />
                </TouchableOpacity>
                <Text style={styles.sliderLabel}>{gForceThreshold.toFixed(1)}G</Text>
                <TouchableOpacity
                  onPress={() => {
                    const next = Math.min(5.0, +(gForceThreshold + 0.1).toFixed(1));
                    setGForceThresholdState(next);
                    BackgroundGuard.setGForceThreshold(next);
                  }}
                  style={styles.thresholdBtn}
                >
                  <MaterialCommunityIcons name="plus" size={16} color={colors.neonGreen} />
                </TouchableOpacity>
              </View>
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
        {/* Header legacy — sólo visible en pestañas que no son Monitor. En Monitor
            el header está integrado dentro de MonitorScreen (flotante con glass). */}
        {activeTab !== 'Monitor' && <SystemHeader />}

        {/* Render Tab Dinámico */}
        {activeTab === 'Monitor' && renderMonitor()}
        {activeTab === 'Eventos' && renderEventos()}
        {activeTab === 'Configuraciones' && renderConfiguraciones()}
        {activeTab === 'Cuenta' && renderCuenta()}

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
            onPress={() => setActiveTab('Configuraciones')}
          >
            <MaterialCommunityIcons 
              name="cog-outline" 
              size={24} 
              color={activeTab === 'Configuraciones' ? colors.neonGreen : colors.textSecondary} 
            />
            <Text style={[styles.navLabel, { color: activeTab === 'Configuraciones' ? colors.neonGreen : colors.textSecondary }]}>
              Configurar
            </Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.navTab}
            onPress={() => setActiveTab('Cuenta')}
          >
            <MaterialCommunityIcons 
              name="account-outline" 
              size={24} 
              color={activeTab === 'Cuenta' ? colors.neonGreen : colors.textSecondary} 
            />
            <Text style={[styles.navLabel, { color: activeTab === 'Cuenta' ? colors.neonGreen : colors.textSecondary }]}>
              Cuenta
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
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
  primaryButton: {
    backgroundColor: colors.neonGreen,
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: {
    color: colors.background,
    fontSize: 13,
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
  thresholdActions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 14,
  },
  thresholdBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: colors.neonGreen,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.greenDim,
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
