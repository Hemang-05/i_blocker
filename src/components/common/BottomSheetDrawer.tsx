import React, { useEffect, useRef } from 'react';
import {
  Modal,
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Animated,
  Dimensions,
  TouchableWithoutFeedback,
} from 'react-native';
import { PresetConfig } from '../../types/models';

interface BottomSheetDrawerProps {
  visible: boolean;
  preset: PresetConfig | null;
  onClose: () => void;
  onApply: (preset: PresetConfig) => void;
}

const { height: SCREEN_HEIGHT } = Dimensions.get('window');
const DRAWER_HEIGHT = Math.round(SCREEN_HEIGHT * 0.38); // ~35-38% of screen height

export const BottomSheetDrawer: React.FC<BottomSheetDrawerProps> = ({
  visible,
  preset,
  onClose,
  onApply,
}) => {
  const slideAnim = useRef(new Animated.Value(DRAWER_HEIGHT)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 220,
          useNativeDriver: true,
        }),
        Animated.spring(slideAnim, {
          toValue: 0,
          tension: 65,
          friction: 10,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 180,
          useNativeDriver: true,
        }),
        Animated.timing(slideAnim, {
          toValue: DRAWER_HEIGHT,
          duration: 180,
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible, slideAnim, fadeAnim]);

  if (!preset) return null;

  return (
    <Modal
      transparent
      visible={visible}
      animationType="none"
      onRequestClose={onClose}
    >
      <View style={styles.overlayContainer}>
        <TouchableWithoutFeedback onPress={onClose}>
          <Animated.View style={[styles.backdrop, { opacity: fadeAnim }]} />
        </TouchableWithoutFeedback>

        <Animated.View
          style={[
            styles.sheetContainer,
            {
              height: DRAWER_HEIGHT,
              transform: [{ translateY: slideAnim }],
            },
          ]}
        >
          {/* Grab Handle Bar */}
          <View style={styles.handleContainer}>
            <View style={styles.handle} />
          </View>

          {/* Drawer Header */}
          <View style={styles.headerRow}>
            <View style={styles.titleArea}>
              <Text style={styles.presetTitle}>{preset.name}</Text>
              {preset.isRecommended ? (
                <View style={styles.recBadge}>
                  <Text style={styles.recBadgeText}>RECOMMENDED</Text>
                </View>
              ) : null}
            </View>
          </View>

          {/* Key Metric Chips */}
          <View style={styles.metricsRow}>
            <View style={styles.metricChip}>
              <Text style={styles.metricValue}>{preset.limitMinutes}m</Text>
              <Text style={styles.metricLabel}>Session</Text>
            </View>
            <View style={styles.metricChip}>
              <Text style={styles.metricValue}>{preset.cooldownMinutes}m</Text>
              <Text style={styles.metricLabel}>Cooldown</Text>
            </View>
            <View style={styles.metricChip}>
              <Text style={styles.metricValue}>{preset.maxSessions}x</Text>
              <Text style={styles.metricLabel}>Daily Limit</Text>
            </View>
          </View>

          {/* Rationale & Benefit Description */}
          <View style={styles.bodyContent}>
            <Text style={styles.descriptionText} numberOfLines={3}>
              {preset.psychologyReason}
            </Text>
          </View>

          {/* Action Buttons: Back & Apply */}
          <View style={styles.actionRow}>
            <TouchableOpacity
              style={styles.backButton}
              onPress={onClose}
              activeOpacity={0.7}
            >
              <Text style={styles.backButtonText}>Back</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.applyButton}
              onPress={() => onApply(preset)}
              activeOpacity={0.85}
            >
              <Text style={styles.applyButtonText}>Apply Limit</Text>
            </TouchableOpacity>
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlayContainer: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(30, 27, 75, 0.4)',
  },
  sheetContainer: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    borderWidth: 1,
    borderColor: '#EDE9FE',
    paddingHorizontal: 22,
    paddingTop: 10,
    paddingBottom: 22,
    justifyContent: 'space-between',
    shadowColor: '#1E1B4B',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.1,
    shadowRadius: 16,
    elevation: 10,
  },
  handleContainer: {
    alignItems: 'center',
    paddingVertical: 6,
  },
  handle: {
    width: 40,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: '#CBD5E1',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  titleArea: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  presetTitle: {
    fontSize: 21,
    fontWeight: '800',
    color: '#1E1B4B',
  },
  recBadge: {
    backgroundColor: '#EDE9FE',
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 8,
  },
  recBadgeText: {
    color: '#7C3AED',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.6,
  },
  metricsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 10,
    marginVertical: 4,
  },
  metricChip: {
    flex: 1,
    backgroundColor: '#F5F3FF',
    borderRadius: 14,
    paddingVertical: 10,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#DDD6FE',
  },
  metricValue: {
    color: '#7C3AED',
    fontSize: 17,
    fontWeight: '800',
  },
  metricLabel: {
    color: '#6B7280',
    fontSize: 11,
    fontWeight: '600',
    marginTop: 2,
  },
  bodyContent: {
    marginVertical: 4,
  },
  descriptionText: {
    color: '#4B5563',
    fontSize: 13,
    lineHeight: 19,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
  },
  backButton: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 24,
    backgroundColor: '#F3F4F6',
    alignItems: 'center',
    justifyContent: 'center',
  },
  backButtonText: {
    color: '#4B5563',
    fontSize: 15,
    fontWeight: '700',
  },
  applyButton: {
    flex: 2,
    paddingVertical: 14,
    borderRadius: 24,
    backgroundColor: '#8B5CF6',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#8B5CF6',
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 4,
  },
  applyButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
});
