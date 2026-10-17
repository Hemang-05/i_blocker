import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  ScrollView,
  RefreshControl,
} from 'react-native';
import { NativeBlockerService } from '../../services/nativeBlocker';
import { EnforcementStatus, ServiceStatus } from '../../types/native';
import { PRESET_OPTIONS } from '../../constants/rules';

interface HomeScreenProps {
  onNavigateToTab: (tab: 'home' | 'tasks' | 'goals' | 'profile') => void;
  onOpenPresetSelector: () => void;
  currentPresetId: string;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  onNavigateToTab,
  onOpenPresetSelector,
  currentPresetId,
}) => {
  const [status, setStatus] = useState<EnforcementStatus | null>(null);
  const [serviceStatus, setServiceStatus] = useState<ServiceStatus | null>(null);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [cooldownCountdown, setCooldownCountdown] = useState<string>('00:00');

  const currentPreset =
    PRESET_OPTIONS.find((p) => p.id === currentPresetId) || PRESET_OPTIONS[1];

  const fetchState = useCallback(async () => {
    try {
      const [curStatus, curService] = await Promise.all([
        NativeBlockerService.getStatus(),
        NativeBlockerService.getServiceStatus(),
      ]);
      setStatus(curStatus);
      setServiceStatus(curService);

      if (curStatus.cooldownRemainingMs > 0) {
        const totalSec = Math.floor(curStatus.cooldownRemainingMs / 1000);
        const mins = Math.floor(totalSec / 60);
        const secs = totalSec % 60;
        setCooldownCountdown(
          `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
        );
      }
    } catch {
      // Ignore in offline / dev state
    }
  }, []);

  useEffect(() => {
    fetchState();
    const interval = setInterval(fetchState, 3000);
    return () => clearInterval(interval);
  }, [fetchState]);

  const onRefresh = async () => {
    setRefreshing(true);
    await fetchState();
    setRefreshing(false);
  };

  // Walk the user through whichever step is still missing.
  const handleFixProtection = async () => {
    try {
      if (!serviceStatus?.hasUsageAccess) {
        await NativeBlockerService.openUsageAccessSettings();
      } else if (!serviceStatus?.hasOverlayPermission) {
        await NativeBlockerService.openOverlaySettings();
      } else {
        await NativeBlockerService.startMonitoring();
        await fetchState();
      }
    } catch {
      // Ignore
    }
  };

  // Determine badge styling based on state
  const isProtected = serviceStatus?.isReady;
  const setupStep = !serviceStatus?.hasUsageAccess
    ? {
        title: 'Usage Access Required',
        desc: 'Tap to allow InstaBlocker to detect when Instagram is opened.',
      }
    : !serviceStatus?.hasOverlayPermission
    ? {
        title: 'Display Over Other Apps Required',
        desc: 'Tap to allow InstaBlocker to show the blocking screen.',
      }
    : {
        title: 'Protection Paused',
        desc: 'Tap to start InstaBlocker protection.',
      };
  const isCooldown = status?.state === 'COOLDOWN';
  const isDailyLocked = status?.dailyLocked || status?.state === 'DAILY_LOCKED';
  const isInSession = status?.state === 'IN_SESSION';

  let statusBadgeColor = '#10B981'; // Green (Allowed)
  let statusBadgeText = 'ACTIVE & PROTECTED';
  if (!isProtected) {
    statusBadgeColor = '#F59E0B'; // Amber
    statusBadgeText = 'SERVICE OFF';
  } else if (isDailyLocked) {
    statusBadgeColor = '#EF4444'; // Red
    statusBadgeText = 'DAILY LOCKED';
  } else if (isCooldown) {
    statusBadgeColor = '#38BDF8'; // Sky
    statusBadgeText = 'IN COOLDOWN';
  } else if (isInSession) {
    statusBadgeColor = '#A855F7'; // Purple
    statusBadgeText = 'SESSION RUNNING';
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor="#38BDF8"
          />
        }
      >
        {/* Top Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Focus Dashboard</Text>
            <Text style={styles.headerSubtitle}>Instagram Usage Guard</Text>
          </View>
          <View style={[styles.statusBadge, { borderColor: statusBadgeColor }]}>
            <View
              style={[styles.statusDot, { backgroundColor: statusBadgeColor }]}
            />
            <Text style={[styles.statusBadgeText, { color: statusBadgeColor }]}>
              {statusBadgeText}
            </Text>
          </View>
        </View>

        {/* Protection setup notice if a permission is missing or the service is stopped */}
        {!isProtected ? (
          <TouchableOpacity
            style={styles.warningBanner}
            onPress={handleFixProtection}
            activeOpacity={0.8}
          >
            <Text style={styles.warningIcon}>⚠️</Text>
            <View style={styles.warningTextContainer}>
              <Text style={styles.warningTitle}>{setupStep.title}</Text>
              <Text style={styles.warningDesc}>{setupStep.desc}</Text>
            </View>
            <Text style={styles.warningArrow}>→</Text>
          </TouchableOpacity>
        ) : null}

        {/* Main Status Hero Card */}
        <View style={styles.heroCard}>
          <Text style={styles.heroLabel}>CURRENT PROTOCOL</Text>
          <View style={styles.heroTitleRow}>
            <Text style={styles.heroTitle}>{currentPreset.name}</Text>
            <TouchableOpacity
              style={styles.changePresetBtn}
              onPress={onOpenPresetSelector}
              activeOpacity={0.7}
            >
              <Text style={styles.changePresetText}>Change Limit</Text>
            </TouchableOpacity>
          </View>

          {isCooldown ? (
            <View style={styles.cooldownAlertBox}>
              <Text style={styles.cooldownIcon}>⏳</Text>
              <View>
                <Text style={styles.cooldownTitle}>Cooldown In Effect</Text>
                <Text style={styles.cooldownCountdownText}>
                  {cooldownCountdown} remaining
                </Text>
              </View>
            </View>
          ) : isDailyLocked ? (
            <View style={styles.lockedAlertBox}>
              <Text style={styles.lockedIcon}>🔒</Text>
              <View>
                <Text style={styles.lockedTitle}>Daily Cap Reached</Text>
                <Text style={styles.lockedSubtitle}>
                  Instagram locked until midnight reset.
                </Text>
              </View>
            </View>
          ) : (
            <Text style={styles.heroDesc}>
              Instagram sessions are limited to {currentPreset.limitMinutes} mins
              followed by a mandatory {currentPreset.cooldownMinutes} min cooldown.
            </Text>
          )}

          {/* Metrics Grid */}
          <View style={styles.statsRow}>
            <View style={styles.statBox}>
              <Text style={styles.statNumber}>
                {status ? status.sessionsCompletedToday : 0} /{' '}
                {currentPreset.maxSessions}
              </Text>
              <Text style={styles.statLabel}>Sessions Today</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.statBox}>
              <Text style={styles.statNumber}>{currentPreset.limitMinutes}m</Text>
              <Text style={styles.statLabel}>Per Session</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.statBox}>
              <Text style={styles.statNumber}>{currentPreset.cooldownMinutes}m</Text>
              <Text style={styles.statLabel}>Cooldown</Text>
            </View>
          </View>
        </View>

        {/* Quick Action Cards: Daily Tasks & Goals */}
        <Text style={styles.sectionHeader}>Quick Access</Text>
        <View style={styles.actionCardsRow}>
          <TouchableOpacity
            style={styles.actionCard}
            onPress={() => onNavigateToTab('tasks')}
            activeOpacity={0.8}
          >
            <View style={styles.actionIconBox}>
              <Text style={styles.actionIcon}>📝</Text>
            </View>
            <Text style={styles.actionCardTitle}>Today's To-Dos</Text>
            <Text style={styles.actionCardSubtitle}>
              Flashes on overlay when limit is reached
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.actionCard}
            onPress={() => onNavigateToTab('goals')}
            activeOpacity={0.8}
          >
            <View style={[styles.actionIconBox, { backgroundColor: '#2E1065' }]}>
              <Text style={styles.actionIcon}>🎯</Text>
            </View>
            <Text style={styles.actionCardTitle}>Vision & KPIs</Text>
            <Text style={styles.actionCardSubtitle}>
              Flashes aggressively on daily lock
            </Text>
          </TouchableOpacity>
        </View>

        {/* Day-by-Day Consistency Tracker */}
        <View style={styles.streakCard}>
          <View style={styles.streakHeader}>
            <Text style={styles.streakTitle}>Daily Discipline Streak</Text>
            <Text style={styles.streakBadge}>🔥 On Track</Text>
          </View>
          <Text style={styles.streakText}>
            You’ve honored your screen limits today. Keep up the high cognitive
            standard!
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#070A11',
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 30,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  greeting: {
    fontSize: 24,
    fontWeight: '800',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  headerSubtitle: {
    fontSize: 13,
    color: '#94A3B8',
    marginTop: 2,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 20,
    borderWidth: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    marginRight: 6,
  },
  statusBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  warningBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(245, 158, 11, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(245, 158, 11, 0.3)',
    borderRadius: 14,
    padding: 14,
    marginBottom: 18,
  },
  warningIcon: {
    fontSize: 22,
    marginRight: 12,
  },
  warningTextContainer: {
    flex: 1,
  },
  warningTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FBBF24',
  },
  warningDesc: {
    fontSize: 12,
    color: '#FCD34D',
    marginTop: 2,
  },
  warningArrow: {
    fontSize: 16,
    color: '#FBBF24',
    fontWeight: 'bold',
  },
  heroCard: {
    backgroundColor: '#111726',
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: '#1E293B',
    marginBottom: 24,
  },
  heroLabel: {
    fontSize: 11,
    fontWeight: '800',
    color: '#38BDF8',
    letterSpacing: 1,
  },
  heroTitleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginVertical: 8,
  },
  heroTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: '#F8FAFC',
  },
  changePresetBtn: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  changePresetText: {
    color: '#38BDF8',
    fontSize: 12,
    fontWeight: '700',
  },
  heroDesc: {
    fontSize: 13,
    color: '#94A3B8',
    lineHeight: 19,
    marginBottom: 16,
  },
  cooldownAlertBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(56, 189, 248, 0.1)',
    borderRadius: 12,
    padding: 12,
    marginVertical: 12,
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.3)',
  },
  cooldownIcon: {
    fontSize: 24,
    marginRight: 10,
  },
  cooldownTitle: {
    color: '#E0F2FE',
    fontSize: 13,
    fontWeight: '700',
  },
  cooldownCountdownText: {
    color: '#38BDF8',
    fontSize: 18,
    fontWeight: '800',
    fontFamily: 'monospace',
  },
  lockedAlertBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    borderRadius: 12,
    padding: 12,
    marginVertical: 12,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  lockedIcon: {
    fontSize: 24,
    marginRight: 10,
  },
  lockedTitle: {
    color: '#FEE2E2',
    fontSize: 13,
    fontWeight: '700',
  },
  lockedSubtitle: {
    color: '#FCA5A5',
    fontSize: 12,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    borderRadius: 14,
    paddingVertical: 14,
    borderWidth: 1,
    borderColor: '#1E293B',
    marginTop: 6,
  },
  statBox: {
    alignItems: 'center',
    flex: 1,
  },
  statDivider: {
    width: 1,
    height: 28,
    backgroundColor: '#1E293B',
  },
  statNumber: {
    fontSize: 16,
    fontWeight: '800',
    color: '#F8FAFC',
  },
  statLabel: {
    fontSize: 11,
    color: '#64748B',
    marginTop: 3,
  },
  sectionHeader: {
    fontSize: 17,
    fontWeight: '700',
    color: '#F1F5F9',
    marginBottom: 12,
  },
  actionCardsRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  actionCard: {
    flex: 1,
    backgroundColor: '#111726',
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  actionIconBox: {
    width: 36,
    height: 36,
    borderRadius: 10,
    backgroundColor: '#1E3A8A',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  actionIcon: {
    fontSize: 18,
  },
  actionCardTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#F1F5F9',
    marginBottom: 4,
  },
  actionCardSubtitle: {
    fontSize: 11,
    color: '#94A3B8',
    lineHeight: 15,
  },
  streakCard: {
    backgroundColor: '#111726',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  streakHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  streakTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  streakBadge: {
    fontSize: 12,
    fontWeight: '700',
    color: '#F59E0B',
  },
  streakText: {
    fontSize: 13,
    color: '#94A3B8',
    lineHeight: 18,
  },
});
