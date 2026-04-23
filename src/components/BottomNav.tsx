import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

export const BottomNav = () => {
  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.tab}>
        <MaterialCommunityIcons name="home-outline" size={26} color={colors.neonGreen} />
        <Text style={[styles.label, { color: colors.neonGreen }]}>Home</Text>
      </TouchableOpacity>
      
      <TouchableOpacity style={styles.tab}>
        <MaterialCommunityIcons name="radiobox-marked" size={26} color={colors.textSecondary} />
        <Text style={styles.label}>REC</Text>
      </TouchableOpacity>
      
      <TouchableOpacity style={styles.tab}>
        <MaterialCommunityIcons name="view-grid-outline" size={26} color={colors.textSecondary} />
        <Text style={styles.label}>Gallery</Text>
      </TouchableOpacity>
      
      <TouchableOpacity style={styles.tab}>
        <MaterialCommunityIcons name="account-outline" size={26} color={colors.textSecondary} />
        <Text style={styles.label}>Profile</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    backgroundColor: colors.cardBackground,
    paddingVertical: 15,
    paddingBottom: 30, // For bottom safe area
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -5 },
    shadowOpacity: 0.5,
    shadowRadius: 10,
    elevation: 20,
  },
  tab: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: colors.textSecondary,
    fontSize: 11,
    marginTop: 6,
    fontWeight: '500',
  },
});
