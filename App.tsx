import React, { useState } from 'react';
import { StyleSheet, View, StatusBar, SafeAreaView, PermissionsAndroid, Platform } from 'react-native';
import { colors } from './src/theme/colors';
import { SystemHeader } from './src/components/SystemHeader';
import { RecordButton } from './src/components/RecordButton';
import { StatusCard } from './src/components/StatusCard';
import { BottomNav } from './src/components/BottomNav';

import { BackgroundGuard } from './src/services/BackgroundGuard';

export default function App() {
  const [isRecording, setIsRecording] = useState(false);

  const requestNotificationPermission = async () => {
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
      );
      return granted === PermissionsAndroid.RESULTS.GRANTED;
    }
    return true; // En Android < 13 o en iOS (aunque no lo soportemos), el permiso es tácito
  };

  const toggleRecording = async () => {
    if (isRecording) {
      // Si estaba grabando, detener
      await BackgroundGuard.stopGuarding();
      setIsRecording(false);
    } else {
      // Si no estaba grabando, pedir permisos e iniciar
      const hasPermission = await requestNotificationPermission();
      if (!hasPermission) {
        console.warn('Permiso de notificaciones denegado. No se puede iniciar el guardián de forma persistente garantizada.');
      }
      await BackgroundGuard.startGuarding();
      setIsRecording(true);
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
          <RecordButton isRecording={isRecording} onToggle={toggleRecording} />
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
  },
  statusContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
});
