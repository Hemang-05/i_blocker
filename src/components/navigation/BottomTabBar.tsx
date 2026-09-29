import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

export type TabType = 'home' | 'tasks' | 'goals' | 'profile';

interface BottomTabBarProps {
  currentTab: TabType;
  onSelectTab: (tab: TabType) => void;
}

interface TabItem {
  key: TabType;
  label: string;
  icon: string;
}

const TABS: TabItem[] = [
  { key: 'home', label: 'Home', icon: '⚡' },
  { key: 'tasks', label: 'Daily Tasks', icon: '📝' },
  { key: 'goals', label: 'Goals & KPI', icon: '🎯' },
  { key: 'profile', label: 'Profile', icon: '👤' },
];

export const BottomTabBar: React.FC<BottomTabBarProps> = ({
  currentTab,
  onSelectTab,
}) => {
  return (
    <View style={styles.container}>
      {TABS.map((tab) => {
        const isActive = currentTab === tab.key;
        return (
          <TouchableOpacity
            key={tab.key}
            style={[styles.tabButton, isActive && styles.activeTabButton]}
            onPress={() => onSelectTab(tab.key)}
            activeOpacity={0.7}
          >
            <Text style={[styles.icon, isActive && styles.activeIcon]}>
              {tab.icon}
            </Text>
            <Text style={[styles.label, isActive && styles.activeLabel]}>
              {tab.label}
            </Text>
            {isActive && <View style={styles.activeDot} />}
          </TouchableOpacity>
        );
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    backgroundColor: '#0D111C',
    borderTopWidth: 1,
    borderTopColor: '#1E293B',
    paddingBottom: 16,
    paddingTop: 8,
    paddingHorizontal: 12,
    justifyContent: 'space-around',
    alignItems: 'center',
  },
  tabButton: {
    alignItems: 'center',
    paddingVertical: 4,
    paddingHorizontal: 12,
    borderRadius: 12,
    minWidth: 70,
  },
  activeTabButton: {
    backgroundColor: 'rgba(56, 189, 248, 0.08)',
  },
  icon: {
    fontSize: 20,
    marginBottom: 2,
    opacity: 0.6,
  },
  activeIcon: {
    opacity: 1,
    transform: [{ scale: 1.1 }],
  },
  label: {
    fontSize: 11,
    fontWeight: '500',
    color: '#64748B',
  },
  activeLabel: {
    color: '#38BDF8',
    fontWeight: '700',
  },
  activeDot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#38BDF8',
    marginTop: 3,
  },
});
