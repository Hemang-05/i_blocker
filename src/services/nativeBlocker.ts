import { NativeModules, Platform } from 'react-native';
import type {
  BlockingRules,
  EnforcementStatus,
  InstaBlockerNativeModule,
  ServiceStatus,
} from '../types/native';

const { InstaBlocker } = NativeModules;

function getNativeModule(): InstaBlockerNativeModule {
  if (Platform.OS !== 'android') {
    throw new Error('InstaBlocker native module is only available on Android.');
  }
  if (!InstaBlocker) {
    throw new Error(
      'InstaBlocker native module is not linked. Please ensure the app was built with InstaBlockerPackage registered.'
    );
  }
  return InstaBlocker as InstaBlockerNativeModule;
}

/** Debug preset: 30s limit, 60s cooldown, 2 sessions */
const DEBUG_RULES: BlockingRules = {
  usageLimitMs: 30_000,
  cooldownDurationMs: 60_000,
  maxSessionsPerDay: 2,
  lockUntilMidnight: true,
  cooldownNotifications: false,
};

/** Production defaults: 10m limit, 90m cooldown, 4 sessions */
const DEFAULT_RULES: BlockingRules = {
  usageLimitMs: 600_000,
  cooldownDurationMs: 5_400_000,
  maxSessionsPerDay: 4,
  lockUntilMidnight: true,
  cooldownNotifications: false,
};

/**
 * Service providing typed access to the Android Kotlin enforcement engine.
 * Kotlin remains the source of truth for all enforcement state and rules.
 */
export const NativeBlockerService = {
  /**
   * Reads current enforcement status (sessions today, cooldown, state).
   */
  async getStatus(): Promise<EnforcementStatus> {
    const module = getNativeModule();
    return module.getStatus();
  },

  /**
   * Reads configured rules from native storage.
   */
  async getRules(): Promise<BlockingRules> {
    const module = getNativeModule();
    return module.getRules();
  },

  /**
   * Validates and saves new rules into native enforcement storage.
   */
  async setRules(rules: BlockingRules): Promise<void> {
    if (!rules) {
      throw new Error('Rules object is required.');
    }
    if (typeof rules.usageLimitMs !== 'number' || rules.usageLimitMs <= 0) {
      throw new Error('Usage limit must be a positive number of milliseconds.');
    }
    if (typeof rules.cooldownDurationMs !== 'number' || rules.cooldownDurationMs <= 0) {
      throw new Error('Cooldown duration must be a positive number of milliseconds.');
    }
    if (typeof rules.maxSessionsPerDay !== 'number' || rules.maxSessionsPerDay <= 0) {
      throw new Error('Max sessions per day must be an integer greater than zero.');
    }

    const module = getNativeModule();
    return module.setRules(rules);
  },

  /**
   * Checks whether the BlockerAccessibilityService is currently enabled in system settings.
   */
  async isAccessibilityServiceEnabled(): Promise<boolean> {
    const module = getNativeModule();
    return module.isAccessibilityServiceEnabled();
  },

  /**
   * Opens Android Accessibility Settings so the user can enable the service.
   */
  async openAccessibilitySettings(): Promise<void> {
    const module = getNativeModule();
    return module.openAccessibilitySettings();
  },

  /**
   * Returns readiness status of the accessibility service for onboarding.
   */
  async getServiceStatus(): Promise<ServiceStatus> {
    const module = getNativeModule();
    return module.getServiceStatus();
  },

  /**
   * Applies the development-only debug preset (30s limit / 60s cooldown / 2 max sessions).
   * Uses setRules() under the hood — avoids New Architecture interop issues with extra native methods.
   */
  async applyDebugPreset(): Promise<BlockingRules> {
    if (!__DEV__) {
      throw new Error('Debug presets are strictly disabled in production builds.');
    }
    await this.setRules(DEBUG_RULES);
    return { ...DEBUG_RULES };
  },

  /**
   * Resets rules to the production defaults (10m limit / 90m cooldown / 4 max sessions).
   * Uses setRules() under the hood — avoids New Architecture interop issues with extra native methods.
   */
  async resetToDefaultRules(): Promise<BlockingRules> {
    await this.setRules(DEFAULT_RULES);
    return { ...DEFAULT_RULES };
  },

  /**
   * Resets today's enforcement progress (sessions = 0, cooldown = 0, daily lock = false)
   * and clears any active blocker. Available only in debug mode.
   */
  async resetEnforcementState(): Promise<EnforcementStatus> {
    if (!__DEV__) {
      throw new Error('Resetting enforcement state is strictly disabled in production builds.');
    }
    const module = getNativeModule();
    if (typeof module.resetEnforcementState === 'function') {
      return module.resetEnforcementState();
    }
    throw new Error('resetEnforcementState native method is not available.');
  },

  /**
   * Synchronizes daily to-do tasks to native storage.
   */
  async syncDailyTasks(tasks: any[]): Promise<boolean> {
    try {
      const module = getNativeModule();
      return await module.syncDailyTasks(JSON.stringify(tasks));
    } catch {
      return false;
    }
  },

  /**
   * Retrieves daily to-do tasks from native storage.
   */
  async getDailyTasks(): Promise<any[]> {
    try {
      const module = getNativeModule();
      const raw = await module.getDailyTasks();
      return JSON.parse(raw || '[]');
    } catch {
      return [];
    }
  },

  /**
   * Synchronizes long-term vision, goals, and KPIs to native storage.
   */
  async syncGoalsAndKPIs(goals: any[]): Promise<boolean> {
    try {
      const module = getNativeModule();
      return await module.syncGoalsAndKPIs(JSON.stringify(goals));
    } catch {
      return false;
    }
  },

  /**
   * Retrieves long-term goals and KPIs from native storage.
   */
  async getGoalsAndKPIs(): Promise<any[]> {
    try {
      const module = getNativeModule();
      const raw = await module.getGoalsAndKPIs();
      return JSON.parse(raw || '[]');
    } catch {
      return [];
    }
  },

  async isOnboardingCompleted(): Promise<boolean> {
    try {
      const module = getNativeModule();
      return await module.isOnboardingCompleted();
    } catch {
      return false;
    }
  },

  async setOnboardingCompleted(completed: boolean): Promise<boolean> {
    try {
      const module = getNativeModule();
      return await module.setOnboardingCompleted(completed);
    } catch {
      return false;
    }
  },

  async getSelectedPresetId(): Promise<string> {
    try {
      const module = getNativeModule();
      return await module.getSelectedPresetId();
    } catch {
      return 'deep_focus';
    }
  },

  async setSelectedPresetId(presetId: string): Promise<boolean> {
    try {
      const module = getNativeModule();
      return await module.setSelectedPresetId(presetId);
    } catch {
      return false;
    }
  },
};
