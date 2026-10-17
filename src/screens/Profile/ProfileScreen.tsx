import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  ScrollView,
  Alert,
  AppState,
} from 'react-native';
import { NativeBlockerService } from '../../services/nativeBlocker';
import { ServiceStatus } from '../../types/native';
import { PRESET_OPTIONS } from '../../constants/rules';

interface ProfileScreenProps {
  currentPresetId: string;
  onOpenPresetSelector: () => void;
  onResetOnboarding: () => void;
}

export const ProfileScreen: React.FC<ProfileScreenProps> = ({
  currentPresetId,
  onOpenPresetSelector,
  onResetOnboarding,
}) => {
  const [serviceStatus, setServiceStatus] = useState<ServiceStatus | null>(null);

  const currentPreset =
    PRESET_OPTIONS.find((p) => p.id === currentPresetId) || PRESET_OPTIONS[1];

  useEffect(() => {
    loadService();
    // Re-check permissions when the user comes back from Android Settings.
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') loadService();
    });
    return () => sub.remove();
  }, []);

  const loadService = async () => {
    try {
      const s = await NativeBlockerService.getServiceStatus();
      setServiceStatus(s);
    } catch {
      // Ignore
    }
  };

  const openSettings = async (open: () => Promise<void>) => {
    try {
      await open();
    } catch {
      Alert.alert('Error', 'Could not open Android settings.');
    }
  };

  const handleStartMonitoring = async () => {
    try {
      const started = await NativeBlockerService.startMonitoring();
      if (!started) {
        Alert.alert(
          'Permissions needed',
          'Grant Usage Access and Display Over Other Apps first.'
        );
      }
      await loadService();
    } catch (e: any) {
      Alert.alert('Error', e?.message || 'Could not start protection.');
    }
  };

  const renderStatusRow = (title: string, desc: string, ok: boolean | undefined) => (
    <View style={styles.settingRow}>
      <View style={styles.settingInfo}>
        <Text style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingDesc}>{desc}</Text>
      </View>
      <View
        style={[
          styles.statusPill,
          { backgroundColor: ok ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)' },
        ]}
      >
        <Text style={[styles.statusPillText, { color: ok ? '#10B981' : '#EF4444' }]}>
          {ok ? 'ENABLED' : 'DISABLED'}
        </Text>
      </View>
    </View>
  );

  const handleResetState = async () => {
    Alert.alert(
      'Reset Enforcement State',
      'This will reset your session count and cooldown for today.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset',
          style: 'destructive',
          onPress: async () => {
            try {
              if (NativeBlockerService.resetEnforcementState) {
                await NativeBlockerService.resetEnforcementState();
                Alert.alert('Success', 'Enforcement state reset for today.');
              }
            } catch (e: any) {
              Alert.alert('Error', e?.message || 'Failed to reset state.');
            }
          },
        },
      ]
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>Profile & Settings</Text>
          <Text style={styles.subtitle}>System Preferences & Protection</Text>
        </View>

        {/* User Avatar Card */}
        <View style={styles.avatarCard}>
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarEmoji}>👤</Text>
          </View>
          <View style={styles.avatarInfo}>
            <Text style={styles.avatarName}>Focus Practitioner</Text>
            <Text style={styles.avatarSubtext}>Strict Discipline Mode</Text>
          </View>
        </View>

        {/* Active Protocol Card */}
        <View style={styles.card}>
          <Text style={styles.cardHeader}>ACTIVE PROTOCOL</Text>
          <View style={styles.protocolRow}>
            <View>
              <Text style={styles.protocolName}>{currentPreset.name}</Text>
              <Text style={styles.protocolDetails}>
                {currentPreset.limitMinutes}m session • {currentPreset.cooldownMinutes}m
                cooldown • {currentPreset.maxSessions} sessions/day
              </Text>
            </View>
            <TouchableOpacity
              style={styles.changeBtn}
              onPress={onOpenPresetSelector}
              activeOpacity={0.7}
            >
              <Text style={styles.changeBtnText}>Change</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Protection Permissions Card */}
        <View style={styles.card}>
          <Text style={styles.cardHeader}>ENFORCEMENT ENGINE</Text>
          {renderStatusRow(
            'Usage Access',
            'Detects when Instagram is opened',
            serviceStatus?.hasUsageAccess
          )}
          {renderStatusRow(
            'Display Over Other Apps',
            'Shows the blocking screen over Instagram',
            serviceStatus?.hasOverlayPermission
          )}
          {renderStatusRow(
            'Protection Service',
            'Runs in the background (does not affect UPI / payment apps)',
            serviceStatus?.isServiceRunning
          )}

          {!serviceStatus?.hasUsageAccess ? (
            <TouchableOpacity
              style={styles.actionBtn}
              onPress={() => openSettings(NativeBlockerService.openUsageAccessSettings)}
              activeOpacity={0.75}
            >
              <Text style={styles.actionBtnText}>Grant Usage Access</Text>
            </TouchableOpacity>
          ) : !serviceStatus?.hasOverlayPermission ? (
            <TouchableOpacity
              style={styles.actionBtn}
              onPress={() => openSettings(NativeBlockerService.openOverlaySettings)}
              activeOpacity={0.75}
            >
              <Text style={styles.actionBtnText}>Allow Display Over Other Apps</Text>
            </TouchableOpacity>
          ) : !serviceStatus?.isServiceRunning ? (
            <TouchableOpacity
              style={styles.actionBtn}
              onPress={handleStartMonitoring}
              activeOpacity={0.75}
            >
              <Text style={styles.actionBtnText}>Start Protection</Text>
            </TouchableOpacity>
          ) : null}
        </View>

        {/* Onboarding Restart */}
        <View style={styles.card}>
          <Text style={styles.cardHeader}>ONBOARDING</Text>
          <TouchableOpacity
            style={styles.actionBtnSecondary}
            onPress={onResetOnboarding}
            activeOpacity={0.75}
          >
            <Text style={styles.actionBtnSecondaryText}>
              View Onboarding Guide Again
            </Text>
          </TouchableOpacity>
        </View>

        {/* Debug Dev Actions */}
        {__DEV__ ? (
          <View style={styles.card}>
            <Text style={styles.cardHeader}>DEVELOPER TOOLS</Text>
            <TouchableOpacity
              style={styles.dangerBtn}
              onPress={handleResetState}
              activeOpacity={0.75}
            >
              <Text style={styles.dangerBtnText}>Reset Today's State (Debug)</Text>
            </TouchableOpacity>
          </View>
        ) : null}
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
    paddingBottom: 40,
    gap: 16,
  },
  header: {
    marginBottom: 8,
  },
  title: {
    fontSize: 26,
    fontWeight: '800',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 13,
    color: '#94A3B8',
    marginTop: 2,
  },
  avatarCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#111726',
    borderRadius: 18,
    padding: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  avatarCircle: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: '#1E293B',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  avatarEmoji: {
    fontSize: 26,
  },
  avatarInfo: {
    flex: 1,
  },
  avatarName: {
    fontSize: 17,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  avatarSubtext: {
    fontSize: 13,
    color: '#38BDF8',
    marginTop: 2,
  },
  card: {
    backgroundColor: '#111726',
    borderRadius: 18,
    padding: 18,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  cardHeader: {
    fontSize: 11,
    fontWeight: '800',
    color: '#64748B',
    letterSpacing: 1,
    marginBottom: 12,
  },
  protocolRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  protocolName: {
    fontSize: 17,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  protocolDetails: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 3,
  },
  changeBtn: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  changeBtnText: {
    color: '#38BDF8',
    fontSize: 12,
    fontWeight: '700',
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  settingInfo: {
    flex: 1,
    marginRight: 10,
  },
  settingTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#F1F5F9',
  },
  settingDesc: {
    fontSize: 12,
    color: '#64748B',
    marginTop: 2,
  },
  statusPill: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusPillText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  actionBtn: {
    backgroundColor: '#2563EB',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  actionBtnText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  actionBtnSecondary: {
    backgroundColor: '#1E293B',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#334155',
  },
  actionBtnSecondaryText: {
    color: '#CBD5E1',
    fontSize: 14,
    fontWeight: '600',
  },
  dangerBtn: {
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  dangerBtnText: {
    color: '#EF4444',
    fontSize: 14,
    fontWeight: '700',
  },
});
