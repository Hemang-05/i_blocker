import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  SafeAreaView,
  FlatList,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { GoalKPI, GoalTimeframe } from '../../types/models';
import { NativeBlockerService } from '../../services/nativeBlocker';

export const GoalsKpiScreen: React.FC = () => {
  const [goals, setGoals] = useState<GoalKPI[]>([]);
  const [selectedTimeframe, setSelectedTimeframe] = useState<GoalTimeframe>('1_YEAR');
  const [titleInput, setTitleInput] = useState<string>('');
  const [kpiInput, setKpiInput] = useState<string>('');

  useEffect(() => {
    loadGoals();
  }, []);

  const loadGoals = async () => {
    try {
      const loaded = await NativeBlockerService.getGoalsAndKPIs();
      if (Array.isArray(loaded) && loaded.length > 0) {
        setGoals(loaded);
      } else {
        // Starter defaults
        const defaults: GoalKPI[] = [
          {
            id: 'goal-1',
            title: 'Scale my SaaS business to financial independence',
            timeframe: '1_YEAR',
            kpiTarget: '$10,000/mo MRR',
            createdAt: Date.now(),
          },
          {
            id: 'goal-2',
            title: 'Achieve peak physical conditioning & stamina',
            timeframe: '1_YEAR',
            kpiTarget: 'Sub-22min 5K & 12% body fat',
            createdAt: Date.now(),
          },
          {
            id: 'goal-3',
            title: 'Master deep engineering & architectural leadership',
            timeframe: '5_10_YEAR',
            kpiTarget: 'Lead world-class tech ventures',
            createdAt: Date.now(),
          },
        ];
        setGoals(defaults);
        await NativeBlockerService.syncGoalsAndKPIs(defaults);
      }
    } catch {
      // Ignore
    }
  };

  const syncGoals = async (updated: GoalKPI[]) => {
    setGoals(updated);
    await NativeBlockerService.syncGoalsAndKPIs(updated);
  };

  const handleAddGoal = () => {
    const titleTrimmed = titleInput.trim();
    if (!titleTrimmed) return;

    const newGoal: GoalKPI = {
      id: `goal-${Date.now()}`,
      title: titleTrimmed,
      timeframe: selectedTimeframe,
      kpiTarget: kpiInput.trim() || undefined,
      createdAt: Date.now(),
    };

    const updated = [newGoal, ...goals];
    syncGoals(updated);
    setTitleInput('');
    setKpiInput('');
  };

  const handleDeleteGoal = (id: string) => {
    const updated = goals.filter((g) => g.id !== id);
    syncGoals(updated);
  };

  const filteredGoals = goals.filter((g) => g.timeframe === selectedTimeframe);

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.keyboardAvoid}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.title}>Vision & KPIs</Text>
            <Text style={styles.subtitle}>
              High-Friction Cognitive Reminder
            </Text>
          </View>
        </View>

        {/* Warning Banner about Flashing Overlay */}
        <View style={styles.flashAlertBanner}>
          <Text style={styles.flashIcon}>⚡</Text>
          <Text style={styles.flashText}>
            If you attempt to breach or exceed your daily Instagram limit, the
            blocker will aggressively flash these goals to protect your future.
          </Text>
        </View>

        {/* Timeframe Switcher */}
        <View style={styles.tabSwitcher}>
          <TouchableOpacity
            style={[
              styles.switchTab,
              selectedTimeframe === '1_YEAR' && styles.switchTabActive,
            ]}
            onPress={() => setSelectedTimeframe('1_YEAR')}
            activeOpacity={0.8}
          >
            <Text
              style={[
                styles.switchTabText,
                selectedTimeframe === '1_YEAR' && styles.switchTabTextActive,
              ]}
            >
              1-Year Goals
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.switchTab,
              selectedTimeframe === '5_10_YEAR' && styles.switchTabActive,
            ]}
            onPress={() => setSelectedTimeframe('5_10_YEAR')}
            activeOpacity={0.8}
          >
            <Text
              style={[
                styles.switchTabText,
                selectedTimeframe === '5_10_YEAR' && styles.switchTabTextActive,
              ]}
            >
              5-10 Year Vision
            </Text>
          </TouchableOpacity>
        </View>

        {/* Form Inputs */}
        <View style={styles.formContainer}>
          <TextInput
            style={styles.textInput}
            placeholder={
              selectedTimeframe === '1_YEAR'
                ? 'e.g. Launch my digital product...'
                : 'e.g. Build generational wealth and freedom...'
            }
            placeholderTextColor="#64748B"
            value={titleInput}
            onChangeText={setTitleInput}
          />
          <View style={styles.kpiRow}>
            <TextInput
              style={[styles.textInput, styles.kpiInput]}
              placeholder="Target KPI (optional, e.g. $10k/mo)"
              placeholderTextColor="#64748B"
              value={kpiInput}
              onChangeText={setKpiInput}
            />
            <TouchableOpacity
              style={styles.addGoalBtn}
              onPress={handleAddGoal}
              activeOpacity={0.8}
            >
              <Text style={styles.addGoalBtnText}>Set Goal</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Goals List */}
        <FlatList
          data={filteredGoals}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyIcon}>🎯</Text>
              <Text style={styles.emptyTitle}>No goals set for this timeframe</Text>
              <Text style={styles.emptySubtitle}>
                Add your major milestones to hold yourself accountable.
              </Text>
            </View>
          }
          renderItem={({ item }) => (
            <View style={styles.goalCard}>
              <View style={styles.goalContent}>
                <Text style={styles.goalTitle}>{item.title}</Text>
                {item.kpiTarget ? (
                  <View style={styles.kpiBadge}>
                    <Text style={styles.kpiBadgeText}>KPI: {item.kpiTarget}</Text>
                  </View>
                ) : null}
              </View>
              <TouchableOpacity
                style={styles.deleteBtn}
                onPress={() => handleDeleteGoal(item.id)}
                activeOpacity={0.6}
              >
                <Text style={styles.deleteBtnText}>✕</Text>
              </TouchableOpacity>
            </View>
          )}
        />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#070A11',
  },
  keyboardAvoid: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 14,
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
  flashAlertBanner: {
    flexDirection: 'row',
    backgroundColor: 'rgba(244, 63, 94, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(244, 63, 94, 0.35)',
    borderRadius: 14,
    padding: 12,
    marginHorizontal: 20,
    marginBottom: 16,
    alignItems: 'center',
  },
  flashIcon: {
    fontSize: 20,
    marginRight: 10,
  },
  flashText: {
    flex: 1,
    color: '#FDA4AF',
    fontSize: 12,
    lineHeight: 17,
  },
  tabSwitcher: {
    flexDirection: 'row',
    backgroundColor: '#111726',
    borderRadius: 12,
    marginHorizontal: 20,
    padding: 4,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  switchTab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 8,
  },
  switchTabActive: {
    backgroundColor: '#1E293B',
  },
  switchTabText: {
    color: '#64748B',
    fontSize: 13,
    fontWeight: '600',
  },
  switchTabTextActive: {
    color: '#F8FAFC',
    fontWeight: '700',
  },
  formContainer: {
    paddingHorizontal: 20,
    marginBottom: 16,
    gap: 10,
  },
  textInput: {
    backgroundColor: '#111726',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#F8FAFC',
    fontSize: 14,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  kpiRow: {
    flexDirection: 'row',
    gap: 10,
  },
  kpiInput: {
    flex: 1,
  },
  addGoalBtn: {
    backgroundColor: '#E11D48',
    paddingHorizontal: 18,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  addGoalBtnText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 30,
    gap: 12,
  },
  goalCard: {
    flexDirection: 'row',
    backgroundColor: '#111726',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: '#1E293B',
    alignItems: 'flex-start',
  },
  goalContent: {
    flex: 1,
  },
  goalTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#F1F5F9',
    lineHeight: 20,
    marginBottom: 6,
  },
  kpiBadge: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(225, 29, 72, 0.15)',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderWidth: 1,
    borderColor: 'rgba(225, 29, 72, 0.3)',
  },
  kpiBadgeText: {
    color: '#FDA4AF',
    fontSize: 11,
    fontWeight: '700',
  },
  deleteBtn: {
    padding: 6,
    marginLeft: 8,
  },
  deleteBtnText: {
    color: '#64748B',
    fontSize: 16,
    fontWeight: '700',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingTop: 50,
  },
  emptyIcon: {
    fontSize: 36,
    marginBottom: 10,
  },
  emptyTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  emptySubtitle: {
    fontSize: 13,
    color: '#64748B',
    marginTop: 4,
  },
});
