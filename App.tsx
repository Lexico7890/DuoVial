import React, { useState, useEffect } from 'react';
import { StyleSheet, View, StatusBar, SafeAreaView, PermissionsAndroid, Platform, TouchableOpacity, Text, DeviceEventEmitter } from 'react-native';
import { colors } from './src/theme/colors';
import { SystemHeader } from './src/components/SystemHeader';
import { RecordButton } from './src/components/RecordButton';
import { StatusCard } from './src/components/StatusCard';
import { BottomNav } from './src/components/BottomNav';

import { BackgroundGuard } from './src/services/BackgroundGuard';

export default function App() {
  // Estado que refleja el estado nativo real de la cámara
  const [status, setStatus] = useState('INACTIVO');

  // Sincronizar isRecording con el estado del servicio nativo
  const isRecording = status !== 'INACTIVO';

  // Bloquear acciones del usuario mientras se procesa/graba un incidente
  const isSaving = status.includes('GUARDANDO') || status.includes('GRABANDO');

  // Solicitud transparente de permisos (Cámara y Notificaciones, Acelerómetro no requiere permiso runtime)
  useEffect(() => {
    const requestInitialPermissions = async () => {
      if (Platform.OS === 'android') {
        const permissions = [
          PermissionsAndroid.PERMISSIONS.CAMERA,
        ];

        // Notificaciones necesarias para Android 13+ (API 33+)
        if (Platform.Version >= 33) {
          permissions.push(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
        }

        try {
          const results = await PermissionsAndroid.requestMultiple(permissions);
          console.log('Permisos solicitados al arrancar:', results);
        } catch (err) {
          console.warn('Error al solicitar permisos al inicio:', err);
        }
      }
    };

    requestInitialPermissions();
  }, []);

  // Escuchar estados nativos reales del servicio mediante DeviceEventEmitter
  useEffect(() => {
    const subscription = DeviceEventEmitter.addListener('onCameraStatusChanged', (event) => {
      console.log('Estado de la cámara recibido en JS:', event.status);
      setStatus(event.status);
    });

    return () => {
      subscription.remove();
    };
  }, []);

  const handleToggle = async () => {
    if (isSaving) return; // Bloquear clicks adicionales si está guardando

    if (isRecording) {
      // Si ya está grabando, presionar el botón central dispara PÁNICO manualmente
      console.log('Botón central presionado durante grabación: Gatillando pánico manual...');
      BackgroundGuard.triggerPanic();
    } else {
      // Si no estaba grabando, iniciar el servicio nativo
      await BackgroundGuard.startGuarding();
    }
  };

  const handleStop = async () => {
    if (isSaving) return; // Bloquear detención si está guardando un incidente crítico
    await BackgroundGuard.stopGuarding();
  };

  // Retorna color armónico dependiendo del estado del servicio
  const getStatusColor = (currentStatus: string) => {
    switch (currentStatus) {
      case 'INACTIVO':
        return colors.textSecondary;
      case 'VIGILANDO':
        return colors.neonGreen;
      default: // GUARDANDO PRE-EVENTO, GRABANDO POST-EVENTO, etc.
        return '#FF9F0A'; // Ámbar Neón de precaución
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor={colors.background} />
      <View style={styles.container}>
        {/* Header Section */}
        <SystemHeader />

        {/* Main Recording Area */}
        <View style={styles.recordContainer}>
          {/* Caja de texto informativa de estado en tiempo real (Premium Glassmorphism) */}
          <View style={[styles.statusBox, { borderColor: getStatusColor(status) + '33' }]}>
            <View style={[styles.statusDot, { backgroundColor: getStatusColor(status) }]} />
            <Text style={[styles.statusBoxText, { color: getStatusColor(status) }]}>
              {status === 'INACTIVO' ? 'SISTEMA LISTO - INACTIVO' : status}
            </Text>
          </View>

          <RecordButton isRecording={isRecording} onToggle={handleToggle} disabled={isSaving} />
          
          {isRecording && (
            <TouchableOpacity 
              activeOpacity={0.8}
              onPress={handleStop}
              disabled={isSaving}
              style={[
                styles.stopButton, 
                isSaving && styles.stopButtonDisabled
              ]}
            >
              <Text style={[
                styles.stopButtonText,
                isSaving && { color: colors.textSecondary }
              ]}>
                DETENER VIGILANTE
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Status Cards (Storage, Accelerometer / G-Force, Speedometer) */}
        <View style={styles.statusContainer}>
          <StatusCard 
            iconName="harddisk" 
            title="STORAGE" 
            value="128" 
            valueSuffix="GB" 
          />
          <StatusCard 
            iconName="pulse" 
            title="ACCEL" 
            value="1.0" 
            valueSuffix="G" 
            isActive={isRecording}
          />
          <StatusCard 
            iconName="speedometer" 
            title="SPEED" 
            value="0" 
            valueSuffix="km/h" 
          />
        </View>

        {/* Bottom Navigation */}
        <BottomNav />
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
  recordContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  statusBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(18, 24, 27, 0.75)',
    borderWidth: 1.5,
    paddingHorizontal: 25,
    paddingVertical: 14,
    borderRadius: 20,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 4,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: 12,
    shadowColor: '#fff',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 3,
  },
  statusBoxText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  stopButton: {
    marginTop: 20,
    backgroundColor: 'rgba(255, 42, 85, 0.1)',
    borderWidth: 1.5,
    borderColor: colors.neonRed,
    paddingHorizontal: 25,
    paddingVertical: 12,
    borderRadius: 30,
    shadowColor: colors.neonRed,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.4,
    shadowRadius: 10,
    elevation: 5,
  },
  stopButtonDisabled: {
    borderColor: colors.border,
    backgroundColor: 'rgba(30, 41, 59, 0.1)',
    shadowOpacity: 0,
    elevation: 0,
  },
  stopButtonText: {
    color: colors.neonRed,
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 2,
  },
  statusContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
});
