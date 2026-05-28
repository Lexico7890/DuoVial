import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

/**
 * Header de la app que muestra el logo y título de DuoVial, así como el botón inactivo de DuoVial Frontal (Somnolencia).
 */
export const SystemHeader = () => {
  return (
    <View style={styles.container}>
      {/* Sección del Título */}
      <View style={styles.titleContainer}>
        <View style={styles.logoCircle}>
          <MaterialCommunityIcons name="camera-iris" size={24} color="#000" />
        </View>
        <View style={styles.textContainer}>
          <Text style={styles.titleText}>DuoVial</Text>
          <Text style={styles.subtitleText}>STANDBY ACTIVE</Text>
        </View>
      </View>

      {/* Botón DuoVial Frontal (Detección de Somnolencia - Inactivo por ahora) */}
      <TouchableOpacity 
        activeOpacity={0.8}
        style={styles.frontalButton}
      >
        <MaterialCommunityIcons name="emoticon-wink-outline" size={18} color="#FF9F0A" />
        <Text style={styles.frontalButtonText}>DuoVial Frontal</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    marginTop: 50,
    marginBottom: 20,
    width: '100%',
  },
  titleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  logoCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.neonGreen,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    shadowColor: colors.neonGreen,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 8,
    elevation: 6,
  },
  textContainer: {
    justifyContent: 'center',
  },
  titleText: {
    color: colors.textPrimary,
    fontSize: 20,
    fontWeight: '900',
    letterSpacing: 1,
  },
  subtitleText: {
    color: colors.textSecondary,
    fontSize: 9,
    fontWeight: '600',
    letterSpacing: 1.5,
    marginTop: 2,
  },
  frontalButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 159, 10, 0.1)',
    borderWidth: 1,
    borderColor: '#FF9F0A',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    shadowColor: '#FF9F0A',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 3,
  },
  frontalButtonText: {
    color: '#FF9F0A',
    fontSize: 11,
    fontWeight: '800',
    marginLeft: 6,
    letterSpacing: 1,
  },
});
