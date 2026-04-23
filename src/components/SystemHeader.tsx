import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors } from '../theme/colors';

export const SystemHeader = () => {
  return (
    <View style={styles.container}>
      <View style={styles.badge}>
        <View style={styles.dot} />
        <Text style={styles.text}>SYSTEM READY</Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    marginTop: 50, // For status bar spacing initially
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(0, 250, 154, 0.05)',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(0, 250, 154, 0.2)',
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.neonGreen,
    marginRight: 10,
    shadowColor: colors.neonGreen,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 5,
    elevation: 5,
  },
  text: {
    color: colors.neonGreen,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 2,
  },
});
