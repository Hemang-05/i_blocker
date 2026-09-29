export interface DailyTask {
  id: string;
  title: string;
  isCompleted: boolean;
  createdAt: number;
}

export type GoalTimeframe = '1_YEAR' | '5_10_YEAR';

export interface GoalKPI {
  id: string;
  title: string;
  timeframe: GoalTimeframe;
  kpiTarget?: string;
  createdAt: number;
}

export type PresetId = 'gentle' | 'deep_focus' | 'monk';

export interface PresetConfig {
  id: PresetId;
  name: string;
  badge?: string;
  isRecommended: boolean;
  limitMinutes: number;
  cooldownMinutes: number;
  maxSessions: number;
  tagline: string;
  benefitDescription: string;
  psychologyReason: string;
}
