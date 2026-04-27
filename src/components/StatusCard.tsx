import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../theme/colors';

export interface StatusCardProps {
  iconName: React.ComponentProps<typeof MaterialCommunityIcons>['name'];
  title: string;
  value: string;
  valueSuffix?: string;
  // Modo estático (antes)
  isActive?: boolean;
  // Modo dinámico: color semáforo
  accentColor?: string; // '#00FA9A' | '#FFA500' | '#FF2D2D'
}

export const StatusCard: React.FC<StatusCardProps> = ({
  iconName,
  title,
  value,
  valueSuffix,
  isActive,
  accentColor,
}) => {
  // accentColor tiene prioridad; si no, usar lógica isActive anterior
  const resolvedColor = accentColor
    ? accentColor
    : isActive
      ? colors.neonGreen
      : colors.textSecondary;

  const borderColor = accentColor
    ? `${accentColor}30`   // 19% opacidad del color de acento
    : isActive
      ? 'rgba(0,250,154,0.1)'
      : 'transparent';

  return (
    <View style={[styles.card, { borderColor }]}>
      <MaterialCommunityIcons
        name={iconName}
        size={24}
        color={resolvedColor}
        style={styles.icon}
      />
      <Text style={styles.title}>{title}</Text>
      <View style={styles.valueContainer}>
        <Text style={[styles.value, { color: accentColor ? resolvedColor : (isActive ? colors.neonGreen : colors.textPrimary) }]}>
          {value}
        </Text>
        {valueSuffix && (
          <Text style={[styles.suffix, accentColor ? { color: resolvedColor } : {}]}>
            {valueSuffix}
          </Text>
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
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 5,
    elevation: 3,
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
    fontSize: 18,
    fontWeight: '700',
  },
  suffix: {
    color: colors.textSecondary,
    fontSize: 12,
    marginLeft: 4,
  },
});
