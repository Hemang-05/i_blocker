import React from 'react';
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';
import {DEFAULT_RULES} from '@/constants/rules';

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';

  const backgroundColor = isDarkMode ? '#0D0D0D' : '#FAFAFA';
  const textColor = isDarkMode ? '#E8E8E8' : '#1A1A1A';
  const subtitleColor = isDarkMode ? '#888888' : '#666666';

  return (
    <SafeAreaView style={[styles.container, {backgroundColor}]}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundColor}
      />
      <View style={styles.content}>
        <Text style={[styles.title, {color: textColor}]}>InstaBlocker</Text>
        <Text style={[styles.subtitle, {color: subtitleColor}]}>
          Take back control of your time
        </Text>
        <View style={styles.ruleCard}>
          <Text style={[styles.ruleLabel, {color: subtitleColor}]}>
            Default session limit
          </Text>
          <Text style={[styles.ruleValue, {color: textColor}]}>
            {DEFAULT_RULES.continuousUsageLimitMinutes} minutes
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 16,
    marginTop: 8,
    fontWeight: '400',
  },
  ruleCard: {
    marginTop: 40,
    paddingVertical: 20,
    paddingHorizontal: 28,
    borderRadius: 16,
    backgroundColor: 'rgba(128, 128, 128, 0.1)',
    alignItems: 'center',
  },
  ruleLabel: {
    fontSize: 13,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  ruleValue: {
    fontSize: 28,
    fontWeight: '600',
    marginTop: 8,
  },
});

export default App;
