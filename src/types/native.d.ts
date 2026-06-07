// Tipos de componentes nativos expuestos desde Kotlin (Paper ViewManager).
// Migrar a Fabric con codegen completo en cuanto se estabilice el spec.

declare module '@expo/vector-icons' {
  import { ComponentType } from 'react';
  import { TextProps } from 'react-native';
  export interface IconProps extends TextProps {
    name: string;
    size?: number;
    color?: string;
  }
  export const MaterialCommunityIcons: ComponentType<IconProps>;
  export const MaterialIcons: ComponentType<IconProps>;
  export const Ionicons: ComponentType<IconProps>;
  export const Feather: ComponentType<IconProps>;
  export const FontAwesome: ComponentType<IconProps>;
  export const AntDesign: ComponentType<IconProps>;
  export const Entypo: ComponentType<IconProps>;
  export const EvilIcons: ComponentType<IconProps>;
  export const Octicons: ComponentType<IconProps>;
  export const SimpleLineIcons: ComponentType<IconProps>;
  export const Zocial: ComponentType<IconProps>;
}
