import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

interface RecordButtonProps {
  isRecording: boolean;
  onToggle: () => void;
  disabled?: boolean;
}

export const RecordButton: React.FC<RecordButtonProps> = ({ isRecording, onToggle, disabled }) => {
  const isSaving = disabled;

  let currentColor = isRecording ? colors.neonRed : colors.neonGreen;
  let currentShadow = isRecording ? colors.neonRedShadow : colors.neonGreenShadow;
  let iconName: React.ComponentProps<typeof MaterialCommunityIcons>['name'] = isRecording ? 'alert-decagram' : 'video';
  let buttonText = isRecording ? 'PÁNICO' : 'START';
  let subText = isRecording ? 'REGISTRAR EVENTO' : 'VIGILANTE';

  if (isSaving) {
    currentColor = '#FF9F0A'; // Color Ámbar Neón para guardado en proceso
    currentShadow = 'rgba(255, 159, 10, 0.4)';
    iconName = 'progress-download';
    buttonText = 'GUARDANDO';
    subText = 'PROCESANDO...';
  }

  return (
    <View style={[styles.container, isSaving && { opacity: 0.9 }]}>
      {/* Anillos de brillo exterior */}
      <View style={[styles.outerRing2, { borderColor: currentShadow }]} />
      <View style={[styles.outerRing1, { borderColor: currentShadow }]} />
      
      {/* Botón principal */}
      <TouchableOpacity
        activeOpacity={0.8}
        onPress={onToggle}
        disabled={disabled}
        style={[
          styles.button,
          {
            backgroundColor: currentColor,
            shadowColor: currentColor,
          },
        ]}
      >
        <MaterialCommunityIcons 
          name={iconName} 
          size={50} 
          color="#000" 
          style={styles.icon}
        />
        <Text style={styles.text}>
          {buttonText}
        </Text>
        <Text style={styles.subText}>
          {subText}
        </Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 300,
    height: 300,
  },
  outerRing2: {
    position: 'absolute',
    width: 280,
    height: 280,
    borderRadius: 140,
    borderWidth: 1,
    opacity: 0.2,
  },
  outerRing1: {
    position: 'absolute',
    width: 250,
    height: 250,
    borderRadius: 125,
    borderWidth: 1,
    opacity: 0.5,
  },
  button: {
    width: 210,
    height: 210,
    borderRadius: 105,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 20,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.8,
    shadowRadius: 30,
  },
  icon: {
    marginBottom: 5,
  },
  text: {
    color: '#000',
    fontSize: 20,
    fontWeight: '900',
    letterSpacing: 2,
    marginTop: 5,
  },
  subText: {
    color: '#000',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 2,
    marginTop: 2,
  },
});
