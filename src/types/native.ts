/**
 * Types representing the native Kotlin enforcement state and configuration.
 * Kotlin is the source of truth; React Native uses these types for bridge interaction.
 */

export type EnforcementState = 'ALLOWED' | 'IN_SESSION' | 'COOLDOWN' | 'DAILY_LOCKED';

export interface EnforcementStatus {
  sessionsCompletedToday: number;
  maxSessionsPerDay: number;
  state: EnforcementState;
  cooldownRemainingMs: number;
  dailyLocked: boolean;
  isInstagramSessionActive: boolean;
}

export interface BlockingRules {
  usageLimitMs: number;
  cooldownDurationMs: number;
  maxSessionsPerDay: number;
  lockUntilMidnight: boolean;
  cooldownNotifications: boolean;
}

export interface ServiceStatus {
  /** "Usage access" granted — needed to detect when Instagram is opened. */
  hasUsageAccess: boolean;
  /** "Display over other apps" granted — needed to show the blocking screen. */
  hasOverlayPermission: boolean;
  /** Foreground monitor service is currently running. */
  isServiceRunning: boolean;
  isReady: boolean;
}

export interface InstaBlockerNativeModule {
  getStatus(): Promise<EnforcementStatus>;
  getRules(): Promise<BlockingRules>;
  setRules(rules: BlockingRules): Promise<void>;
  openUsageAccessSettings(): Promise<void>;
  openOverlaySettings(): Promise<void>;
  startMonitoring(): Promise<boolean>;
  getServiceStatus(): Promise<ServiceStatus>;
  resetEnforcementState?(): Promise<EnforcementStatus>;
  syncDailyTasks(tasksJson: string): Promise<boolean>;
  getDailyTasks(): Promise<string>;
  syncGoalsAndKPIs(goalsJson: string): Promise<boolean>;
  getGoalsAndKPIs(): Promise<string>;
  isOnboardingCompleted(): Promise<boolean>;
  setOnboardingCompleted(completed: boolean): Promise<boolean>;
  getSelectedPresetId(): Promise<string>;
  setSelectedPresetId(presetId: string): Promise<boolean>;
}
