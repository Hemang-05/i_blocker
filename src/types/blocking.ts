// App-wide type definitions

export interface BlockingRules {
  continuousUsageLimitMinutes: number;
  cooldownMinutes: number;
  maxSessionsPerDay: number;
  cooldownNotificationsEnabled: boolean;
  extendSessionEnabled: boolean;
}

export interface SessionState {
  sessionsCompletedToday: number;
  isBlocked: boolean;
  isCooldownActive: boolean;
  cooldownEndsAt: number | null;
  currentSessionStartedAt: number | null;
  blockedUntilMidnight: boolean;
  lastResetDate: string; // YYYY-MM-DD format, device-local time
}
