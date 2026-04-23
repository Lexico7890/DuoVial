import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

export interface StatusCardProps {
  iconName: React.ComponentProps<typeof MaterialCommunityIcons>['name'];
  title: string;
  value: string;
  valueSuffix?: string;
  isActive?: boolean;
}

export const StatusCard: React.FC<StatusCardProps> = ({ 
  iconName, 
  title, 
  value, 
  valueSuffix,
  isActive 
}) => {
  return (
    <View style={[styles.card, isActive && styles.cardActive]}>
      <MaterialCommunityIcons 
        name={iconName} 
        size={24} 
        color={isActive ? colors.neonGreen : colors.textSecondary} 
        style={styles.icon}
      />
      <Text style={styles.title}>{title}</Text>
      <View style={styles.valueContainer}>
        <Text style={[styles.value, isActive && styles.valueActive]}>{value}</Text>
        {valueSuffix && (
          <Text style={styles.suffix}>{valueSuffix}</Text>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.cardBackground,
    borderRadius: 16,
    padding: 20,
    alignItems: 'center',
    width: '30%',
    borderWidth: 1,
    borderColor: 'transparent',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 3,
  },
  cardActive: {
    borderColor: 'rgba(0, 250, 154, 0.1)',
  },
  icon: {
    marginBottom: 15,
  },
  title: {
    color: colors.textSecondary,
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1,
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  valueContainer: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  value: {
    color: colors.textPrimary,
    fontSize: 18,
    fontWeight: '500',
  },
  valueActive: {
    color: colors.neonGreen,
  },
  suffix: {
    color: colors.textSecondary,
    fontSize: 12,
    marginLeft: 4,
  },
});
