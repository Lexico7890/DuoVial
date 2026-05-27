import React, { useState, useEffect } from 'react';
import { StyleSheet, View, StatusBar, SafeAreaView, PermissionsAndroid, Platform, TouchableOpacity, Text } from 'react-native';
import { colors } from './src/theme/colors';
import { SystemHeader } from './src/components/SystemHeader';
import { RecordButton } from './src/components/RecordButton';
import { StatusCard } from './src/components/StatusCard';
import { BottomNav } from './src/components/BottomNav';

import { BackgroundGuard } from './src/services/BackgroundGuard';

export default function App() {
  const [isRecording, setIsRecording] = useState(false);

  // Solicitud transparente de todos los permisos al iniciar por primera vez
  useEffect(() => {
    const requestInitialPermissions = async () => {
      if (Platform.OS === 'android') {
        const permissions = [
          PermissionsAndroid.PERMISSIONS.CAMERA,
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
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

  const handleToggle = async () => {
    if (isRecording) {
      // Si ya está grabando, presionar el botón central dispara PÁNICO manualmente
      console.log('Botón central presionado durante grabación: Gatillando pánico manual...');
      BackgroundGuard.triggerPanic();
    } else {
      // Si no estaba grabando, iniciar el servicio nativo
      await BackgroundGuard.startGuarding();
      setIsRecording(true);
    }
  };

  const handleStop = async () => {
    // Detener de forma segura
    await BackgroundGuard.stopGuarding();
    setIsRecording(false);
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor={colors.background} />
      <View style={styles.container}>
        {/* Header Section */}
        <SystemHeader />

        {/* Main Recording Area */}
        <View style={styles.recordContainer}>
          <RecordButton isRecording={isRecording} onToggle={handleToggle} />
          
          {isRecording && (
            <TouchableOpacity 
              activeOpacity={0.8}
              onPress={handleStop}
              style={styles.stopButton}
            >
              <Text style={styles.stopButtonText}>DETENER VIGILANTE</Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Status Cards */}
        <View style={styles.statusContainer}>
          <StatusCard 
            iconName="harddisk" 
            title="STORAGE" 
            value="128" 
            valueSuffix="GB" 
          />
          <StatusCard 
            iconName="battery-charging-outline" 
            title="POWER" 
            value="EXT." 
            isActive={true}
          />
          <StatusCard 
            iconName="satellite-uplink" 
            title="GPS FIX" 
            value="3D" 
            valueSuffix="Lock" 
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
