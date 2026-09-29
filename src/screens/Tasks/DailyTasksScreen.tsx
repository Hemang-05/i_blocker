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
import { DailyTask } from '../../types/models';
import { NativeBlockerService } from '../../services/nativeBlocker';

export const DailyTasksScreen: React.FC = () => {
  const [tasks, setTasks] = useState<DailyTask[]>([]);
  const [inputText, setInputText] = useState<string>('');

  useEffect(() => {
    loadTasks();
  }, []);

  const loadTasks = async () => {
    try {
      const loaded = await NativeBlockerService.getDailyTasks();
      if (Array.isArray(loaded) && loaded.length > 0) {
        setTasks(loaded);
      } else {
        // Starter defaults if empty
        const initialTasks: DailyTask[] = [
          {
            id: 'task-1',
            title: 'Complete high-priority project tasks',
            isCompleted: false,
            createdAt: Date.now(),
          },
          {
            id: 'task-2',
            title: '45-minute deep focus study or work sprint',
            isCompleted: false,
            createdAt: Date.now(),
          },
          {
            id: 'task-3',
            title: 'Daily exercise & physical workout',
            isCompleted: false,
            createdAt: Date.now(),
          },
        ];
        setTasks(initialTasks);
        await NativeBlockerService.syncDailyTasks(initialTasks);
      }
    } catch {
      // Ignore
    }
  };

  const syncTasks = async (updated: DailyTask[]) => {
    setTasks(updated);
    await NativeBlockerService.syncDailyTasks(updated);
  };

  const handleAddTask = () => {
    const trimmed = inputText.trim();
    if (!trimmed) return;

    const newTask: DailyTask = {
      id: `task-${Date.now()}`,
      title: trimmed,
      isCompleted: false,
      createdAt: Date.now(),
    };

    const updated = [newTask, ...tasks];
    syncTasks(updated);
    setInputText('');
  };

  const handleToggleTask = (id: string) => {
    const updated = tasks.map((t) =>
      t.id === id ? { ...t, isCompleted: !t.isCompleted } : t
    );
    syncTasks(updated);
  };

  const handleDeleteTask = (id: string) => {
    const updated = tasks.filter((t) => t.id !== id);
    syncTasks(updated);
  };

  const completedCount = tasks.filter((t) => t.isCompleted).length;

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.keyboardAvoid}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.title}>Today's To-Dos</Text>
            <Text style={styles.subtitle}>
              Synced to Instagram Blocker Overlay
            </Text>
          </View>
          <View style={styles.counterBadge}>
            <Text style={styles.counterText}>
              {completedCount}/{tasks.length} Done
            </Text>
          </View>
        </View>

        {/* Motivational Info Banner */}
        <View style={styles.infoBanner}>
          <Text style={styles.infoIcon}>💡</Text>
          <Text style={styles.infoText}>
            When your Instagram session time expires, these tasks will flash on
            the blocking overlay to re-route your attention!
          </Text>
        </View>

        {/* Input Field Row */}
        <View style={styles.inputRow}>
          <TextInput
            style={styles.input}
            placeholder="Add priority task for today..."
            placeholderTextColor="#64748B"
            value={inputText}
            onChangeText={setInputText}
            onSubmitEditing={handleAddTask}
            returnKeyType="done"
          />
          <TouchableOpacity
            style={styles.addButton}
            onPress={handleAddTask}
            activeOpacity={0.8}
          >
            <Text style={styles.addButtonText}>Add</Text>
          </TouchableOpacity>
        </View>

        {/* Task List */}
        <FlatList
          data={tasks}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyIcon}>✨</Text>
              <Text style={styles.emptyTitle}>No pending tasks</Text>
              <Text style={styles.emptySubtitle}>
                Add the key tasks you want to accomplish today.
              </Text>
            </View>
          }
          renderItem={({ item }) => (
            <View style={styles.taskCard}>
              <TouchableOpacity
                style={[
                  styles.checkbox,
                  item.isCompleted && styles.checkboxChecked,
                ]}
                onPress={() => handleToggleTask(item.id)}
                activeOpacity={0.7}
              >
                {item.isCompleted ? (
                  <Text style={styles.checkmark}>✓</Text>
                ) : null}
              </TouchableOpacity>

              <Text
                style={[
                  styles.taskTitle,
                  item.isCompleted && styles.taskTitleCompleted,
                ]}
                numberOfLines={2}
              >
                {item.title}
              </Text>

              <TouchableOpacity
                style={styles.deleteButton}
                onPress={() => handleDeleteTask(item.id)}
                activeOpacity={0.6}
              >
                <Text style={styles.deleteText}>✕</Text>
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
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
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
  counterBadge: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  counterText: {
    color: '#38BDF8',
    fontSize: 12,
    fontWeight: '700',
  },
  infoBanner: {
    flexDirection: 'row',
    backgroundColor: 'rgba(56, 189, 248, 0.08)',
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.25)',
    borderRadius: 14,
    padding: 12,
    marginHorizontal: 20,
    marginBottom: 16,
    alignItems: 'center',
  },
  infoIcon: {
    fontSize: 18,
    marginRight: 10,
  },
  infoText: {
    flex: 1,
    color: '#BAE6FD',
    fontSize: 12,
    lineHeight: 17,
  },
  inputRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    gap: 10,
    marginBottom: 16,
  },
  input: {
    flex: 1,
    backgroundColor: '#111726',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#F8FAFC',
    fontSize: 15,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  addButton: {
    backgroundColor: '#2563EB',
    paddingHorizontal: 18,
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  addButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 30,
    gap: 10,
  },
  taskCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#111726',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#475569',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  checkboxChecked: {
    backgroundColor: '#10B981',
    borderColor: '#10B981',
  },
  checkmark: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '900',
  },
  taskTitle: {
    flex: 1,
    fontSize: 15,
    color: '#F1F5F9',
    lineHeight: 20,
  },
  taskTitleCompleted: {
    color: '#64748B',
    textDecorationLine: 'line-through',
  },
  deleteButton: {
    padding: 6,
    marginLeft: 8,
  },
  deleteText: {
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
