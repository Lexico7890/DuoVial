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
  const isSaving = disabled && isRecording; // Está guardando si está disabled pero grabando
  const isInactive = !isRecording; // Inactivo si el vigilante está apagado

  let currentColor = '#FFD700'; // Amarillo Neón de Pánico
  let currentShadow = 'rgba(255, 215, 0, 0.4)';
  let iconName: React.ComponentProps<typeof MaterialCommunityIcons>['name'] = 'alert-decagram';
  let buttonText = 'PÁNICO';
  let subText = 'GATILLAR EVENTO';

  if (isSaving) {
    currentColor = '#FF9F0A'; // Ámbar de guardado en proceso
    currentShadow = 'rgba(255, 159, 10, 0.5)';
    iconName = 'progress-download';
    buttonText = 'GUARDANDO';
    subText = 'PROCESANDO...';
  } else if (isInactive) {
    currentColor = '#3A3F42'; // Gris inactivo si el vigilante está apagado
    currentShadow = 'rgba(58, 63, 66, 0.1)';
    iconName = 'alert-decagram-outline';
    buttonText = 'PÁNICO';
    subText = 'VIGILANTE APAGADO';
  }

  return (
    <View style={[styles.container, isInactive && { opacity: 0.6 }]}>
      {/* Anillos de brillo exterior */}
      <View style={[styles.outerRing2, { borderColor: currentShadow }]} />
      <View style={[styles.outerRing1, { borderColor: currentShadow }]} />
      
      {/* Botón principal de Pánico */}
      <TouchableOpacity
        activeOpacity={0.8}
        onPress={onToggle}
        disabled={disabled || isInactive}
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
          size={46} 
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
    width: 180,
    height: 180,
  },
  outerRing2: {
    position: 'absolute',
    width: 172,
    height: 172,
    borderRadius: 86,
    borderWidth: 1,
    opacity: 0.15,
  },
  outerRing1: {
    position: 'absolute',
    width: 154,
    height: 154,
    borderRadius: 77,
    borderWidth: 1,
    opacity: 0.4,
  },
  button: {
    width: 136,
    height: 136,
    borderRadius: 68,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 15,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 20,
  },
  icon: {
    marginBottom: 2,
  },
  text: {
    color: '#000',
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: 1.5,
    marginTop: 2,
  },
  subText: {
    color: '#000',
    fontSize: 7,
    fontWeight: '800',
    letterSpacing: 1.2,
    marginTop: 2,
  },
});
