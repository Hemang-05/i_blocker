import React from 'react';
import { NativeModules } from 'react-native';
import ReactTestRenderer from 'react-test-renderer';

NativeModules.InstaBlocker = {
  getStatus: jest.fn().mockResolvedValue({
    sessionsCompletedToday: 0,
    maxSessionsPerDay: 3,
    state: 'ALLOWED',
    cooldownRemainingMs: 0,
    dailyLocked: false,
    isInstagramSessionActive: false,
  }),
  getRules: jest.fn().mockResolvedValue({
    usageLimitMs: 600000,
    cooldownDurationMs: 3600000,
    maxSessionsPerDay: 3,
    lockUntilMidnight: true,
    cooldownNotifications: false,
  }),
  setRules: jest.fn().mockResolvedValue(undefined),
  getServiceStatus: jest.fn().mockResolvedValue({
    isAccessibilityServiceEnabled: true,
    isServiceConnected: true,
    isReady: true,
  }),
  isAccessibilityServiceEnabled: jest.fn().mockResolvedValue(true),
  openAccessibilitySettings: jest.fn().mockResolvedValue(undefined),
  isOnboardingCompleted: jest.fn().mockResolvedValue(false),
  setOnboardingCompleted: jest.fn().mockResolvedValue(true),
  getSelectedPresetId: jest.fn().mockResolvedValue('deep_focus'),
  setSelectedPresetId: jest.fn().mockResolvedValue(true),
  syncDailyTasks: jest.fn().mockResolvedValue(true),
  getDailyTasks: jest.fn().mockResolvedValue('[]'),
  syncGoalsAndKPIs: jest.fn().mockResolvedValue(true),
  getGoalsAndKPIs: jest.fn().mockResolvedValue('[]'),
};

import App from '../App';

test('renders correctly', async () => {
  await ReactTestRenderer.act(() => {
    ReactTestRenderer.create(<App />);
  });
});
