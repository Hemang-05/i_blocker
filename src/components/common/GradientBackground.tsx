import React from 'react';
import { View, Image, StyleSheet, StyleProp, ViewStyle } from 'react-native';

interface GradientBackgroundProps {
  children?: React.ReactNode;
  style?: StyleProp<ViewStyle>;
}

export const GradientBackground: React.FC<GradientBackgroundProps> = ({
  children,
  style,
}) => {
  return (
    <View style={[styles.container, style]}>
      <Image
        source={require('../../assets/images/pastel_gradient.png')}
        style={StyleSheet.absoluteFill}
        resizeMode="cover"
      />
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#E2D9F1',
  },
});
