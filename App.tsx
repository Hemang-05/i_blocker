import React, { useState, useEffect } from 'react';
import { View, StyleSheet, StatusBar } from 'react-native';
import {
  SplashScreen,
  OnboardingScreen,
  LimitSetupScreen,
  HomeScreen,
  DailyTasksScreen,
  GoalsKpiScreen,
  ProfileScreen,
} from './src/screens';
import { BottomTabBar, TabType } from './src/components/navigation/BottomTabBar';
import { NativeBlockerService } from './src/services/nativeBlocker';
import { PresetConfig } from './src/types/models';

type AppFlowState = 'splash' | 'onboarding' | 'limit_setup' | 'main' | 'change_limit';

export default function App(): React.JSX.Element {
  const [flowState, setFlowState] = useState<AppFlowState>('splash');
  const [currentTab, setCurrentTab] = useState<TabType>('home');
  const [selectedPresetId, setSelectedPresetId] = useState<string>('deep_focus');
  const [isOnboardingDone, setIsOnboardingDone] = useState<boolean>(false);

  useEffect(() => {
    // Check initial native storage state in parallel with splash
    const checkInitialState = async () => {
      try {
        const [done, presetId] = await Promise.all([
          NativeBlockerService.isOnboardingCompleted(),
          NativeBlockerService.getSelectedPresetId(),
        ]);
        setIsOnboardingDone(done);
        if (presetId) setSelectedPresetId(presetId);
      } catch {
        // Fallback for first launch
      }
    };
    checkInitialState();
  }, []);

  const handleSplashFinish = () => {
    if (isOnboardingDone) {
      setFlowState('main');
    } else {
      setFlowState('onboarding');
    }
  };

  const handleOnboardingNext = () => {
    setFlowState('limit_setup');
  };

  const handleApplyPreset = async (preset: PresetConfig) => {
    setSelectedPresetId(preset.id);
    try {
      // 1. Save rules to native enforcement engine (source of truth)
      await NativeBlockerService.setRules({
        usageLimitMs: preset.limitMinutes * 60 * 1000,
        cooldownDurationMs: preset.cooldownMinutes * 60 * 1000,
        maxSessionsPerDay: preset.maxSessions,
        lockUntilMidnight: true,
        cooldownNotifications: false,
      });

      // 2. Persist chosen preset id & onboarding completion
      await NativeBlockerService.setSelectedPresetId(preset.id);
      await NativeBlockerService.setOnboardingCompleted(true);
      setIsOnboardingDone(true);
    } catch (e) {
      console.warn('Failed to commit preset rules to native module:', e);
    }

    setFlowState('main');
  };

  const handleOpenPresetSelector = () => {
    setFlowState('change_limit');
  };

  const handleResetOnboarding = async () => {
    try {
      await NativeBlockerService.setOnboardingCompleted(false);
      setIsOnboardingDone(false);
    } catch {
      // Ignore
    }
    setFlowState('onboarding');
  };

  return (
    <View style={styles.root}>
      <StatusBar
        barStyle={flowState === 'main' ? 'light-content' : 'dark-content'}
      />

      {/* Screen Routing */}
      {flowState === 'splash' && (
        <SplashScreen onFinish={handleSplashFinish} />
      )}

      {flowState === 'onboarding' && (
        <OnboardingScreen onNext={handleOnboardingNext} />
      )}

      {flowState === 'limit_setup' && (
        <LimitSetupScreen
          initialSelectedId={selectedPresetId}
          onApplyPreset={handleApplyPreset}
        />
      )}

      {flowState === 'change_limit' && (
        <LimitSetupScreen
          initialSelectedId={selectedPresetId}
          onApplyPreset={handleApplyPreset}
        />
      )}

      {flowState === 'main' && (
        <View style={styles.mainContainer}>
          <View style={styles.tabContent}>
            {currentTab === 'home' && (
              <HomeScreen
                currentPresetId={selectedPresetId}
                onNavigateToTab={(tab) => setCurrentTab(tab)}
                onOpenPresetSelector={handleOpenPresetSelector}
              />
            )}
            {currentTab === 'tasks' && <DailyTasksScreen />}
            {currentTab === 'goals' && <GoalsKpiScreen />}
            {currentTab === 'profile' && (
              <ProfileScreen
                currentPresetId={selectedPresetId}
                onOpenPresetSelector={handleOpenPresetSelector}
                onResetOnboarding={handleResetOnboarding}
              />
            )}
          </View>

          {/* 4-Tab Bottom Navigation Bar */}
          <BottomTabBar
            currentTab={currentTab}
            onSelectTab={(tab) => setCurrentTab(tab)}
          />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#070A11',
  },
  mainContainer: {
    flex: 1,
    backgroundColor: '#070A11',
  },
  tabContent: {
    flex: 1,
  },
});
