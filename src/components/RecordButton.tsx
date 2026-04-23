import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Animated } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

interface RecordButtonProps {
  isRecording: boolean;
  onToggle: () => void;
}

export const RecordButton: React.FC<RecordButtonProps> = ({ isRecording, onToggle }) => {
  const currentColor = isRecording ? colors.neonRed : colors.neonGreen;
  const currentShadow = isRecording ? colors.neonRedShadow : colors.neonGreenShadow;

  return (
    <View style={styles.container}>
      {/* Outer Glow Rings */}
      <View style={[styles.outerRing2, { borderColor: currentShadow }]} />
      <View style={[styles.outerRing1, { borderColor: currentShadow }]} />
      
      {/* Main Button */}
      <TouchableOpacity
        activeOpacity={0.8}
        onPress={onToggle}
        style={[
          styles.button,
          {
            backgroundColor: currentColor,
            shadowColor: currentColor,
          },
        ]}
      >
        <MaterialCommunityIcons 
          name={isRecording ? 'stop' : 'video'} 
          size={50} 
          color="#000" 
          style={styles.icon}
        />
        <Text style={styles.text}>
          {isRecording ? 'STOP' : 'START'}
        </Text>
        <Text style={styles.subText}>
          RECORDING
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
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: 2,
    marginTop: 5,
  },
  subText: {
    color: '#000',
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 3,
    marginTop: 2,
  },
});
