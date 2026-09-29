import { PresetConfig } from '../types/models';

export const INSTAGRAM_PACKAGE = 'com.instagram.android';

export const PRESET_OPTIONS: PresetConfig[] = [
  {
    id: 'gentle',
    name: 'Gentle Reset',
    badge: 'Light Friction',
    isRecommended: false,
    limitMinutes: 15,
    cooldownMinutes: 45,
    maxSessions: 4,
    tagline: 'Moderate boundaries for gradual habit reduction',
    benefitDescription:
      'Gives you reasonable check-in windows (15 mins) while introducing a mandatory 45-minute recovery buffer between sessions.',
    psychologyReason:
      'Ideal for easing into digital discipline without feeling immediately constrained, training you to be deliberate with your time.',
  },
  {
    id: 'deep_focus',
    name: 'Deep Focus',
    badge: 'Recommended',
    isRecommended: true,
    limitMinutes: 10,
    cooldownMinutes: 60,
    maxSessions: 3,
    tagline: 'The scientifically optimal discipline & cognitive balance',
    benefitDescription:
      'Capped at 10 minutes per session followed by a full 1-hour screen reset. Allows at most 3 intentional sessions per day (30 mins total).',
    psychologyReason:
      'Designed to sever the compulsive loop: 10 mins prevents endless scrolling, while 60 mins lets your brain reset baseline dopamine levels to engage in deep, sustained focus.',
  },
  {
    id: 'monk',
    name: 'Monk Mode',
    badge: 'High Discipline',
    isRecommended: false,
    limitMinutes: 5,
    cooldownMinutes: 120,
    maxSessions: 2,
    tagline: 'Ruthless focus for high-stakes productivity phases',
    benefitDescription:
      'Strict 5-minute utility limit followed by a heavy 2-hour cooldown. Only 2 total check-ins per day (10 mins max).',
    psychologyReason:
      'Completely eliminates recreational browsing. Instagram becomes a tool for urgent DMs only, returning massive chunks of attention back to your real goals.',
  },
];

export const DEFAULT_PRESET = PRESET_OPTIONS[1]; // Deep Focus is default & recommended
