import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
} from 'react-native';
import { PRESET_OPTIONS } from '../../constants/rules';
import { PresetConfig } from '../../types/models';
import { BottomSheetDrawer } from '../../components/common/BottomSheetDrawer';
import { GradientBackground } from '../../components/common/GradientBackground';

interface LimitSetupScreenProps {
  onApplyPreset: (preset: PresetConfig) => void;
  initialSelectedId?: string;
}

export const LimitSetupScreen: React.FC<LimitSetupScreenProps> = ({
  onApplyPreset,
  initialSelectedId = 'deep_focus',
}) => {
  const [selectedId, setSelectedId] = useState<string>(initialSelectedId);
  const [activeDrawerPreset, setActiveDrawerPreset] = useState<PresetConfig | null>(null);
  const [drawerVisible, setDrawerVisible] = useState<boolean>(false);

  const selectedPreset =
    PRESET_OPTIONS.find((p) => p.id === selectedId) || PRESET_OPTIONS[1];

  const handlePillPress = (preset: PresetConfig) => {
    setSelectedId(preset.id);
    setActiveDrawerPreset(preset);
    setDrawerVisible(true);
  };

  const handleContinuePress = () => {
    // Open drawer to confirm and apply, or apply directly
    setActiveDrawerPreset(selectedPreset);
    setDrawerVisible(true);
  };

  const handleCloseDrawer = () => {
    setDrawerVisible(false);
  };

  const handleApplyFromDrawer = (preset: PresetConfig) => {
    setDrawerVisible(false);
    onApplyPreset(preset);
  };

  return (
    <GradientBackground>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.contentContainer}>
          {/* Header Title matching user mockup */}
          <View style={styles.header}>
            <Text style={styles.titleLine}>Select the Limit you</Text>
            <Text style={styles.titleLine}>wanna apply</Text>
          </View>

          {/* 3 Clean Rounded White Pill Cards matching user mockup */}
          <View style={styles.pillsContainer}>
            {PRESET_OPTIONS.map((preset) => {
              const isSelected = selectedId === preset.id;
              return (
                <TouchableOpacity
                  key={preset.id}
                  style={[
                    styles.pillCard,
                    isSelected && styles.pillCardSelected,
                  ]}
                  onPress={() => handlePillPress(preset)}
                  activeOpacity={0.88}
                >
                  <View style={styles.pillLeft}>
                    <View style={styles.titleRow}>
                      <Text
                        style={[
                          styles.presetTitle,
                          isSelected && styles.presetTitleSelected,
                        ]}
                      >
                        {preset.name}
                      </Text>
                      {preset.isRecommended ? (
                        <View style={styles.recTag}>
                          <Text style={styles.recTagText}>RECOMMENDED</Text>
                        </View>
                      ) : null}
                    </View>

                    <Text style={styles.presetDetails}>
                      {preset.limitMinutes}m session • {preset.cooldownMinutes}m
                      cooldown • {preset.maxSessions}/day
                    </Text>
                  </View>

                  <View
                    style={[
                      styles.radioCircle,
                      isSelected && styles.radioCircleSelected,
                    ]}
                  >
                    {isSelected && <View style={styles.radioInner} />}
                  </View>
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Spacer to push Continue button to bottom */}
          <View style={styles.spacer} />

          {/* Continue Pill Button matching user mockup */}
          <TouchableOpacity
            style={styles.continueButton}
            onPress={handleContinuePress}
            activeOpacity={0.85}
          >
            <Text style={styles.continueButtonText}>Continue</Text>
          </TouchableOpacity>
        </View>

        {/* 35% Height Bottom Drawer Modal */}
        <BottomSheetDrawer
          visible={drawerVisible}
          preset={activeDrawerPreset}
          onClose={handleCloseDrawer}
          onApply={handleApplyFromDrawer}
        />
      </SafeAreaView>
    </GradientBackground>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  contentContainer: {
    flex: 1,
    paddingHorizontal: 26,
    paddingTop: 36,
    paddingBottom: 28,
    justifyContent: 'space-between',
  },
  header: {
    marginTop: 18,
    marginBottom: 44,
  },
  titleLine: {
    fontSize: 32,
    fontWeight: '700',
    color: '#111827',
    lineHeight: 40,
    letterSpacing: -0.5,
  },
  pillsContainer: {
    gap: 18,
  },
  pillCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 36,
    paddingVertical: 18,
    paddingHorizontal: 24,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    shadowColor: '#1E1B4B',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.07,
    shadowRadius: 14,
    elevation: 4,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  pillCardSelected: {
    borderColor: '#8B5CF6',
    backgroundColor: '#FFFFFF',
    shadowOpacity: 0.14,
    shadowColor: '#8B5CF6',
  },
  pillLeft: {
    flex: 1,
    marginRight: 10,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  presetTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1F2937',
  },
  presetTitleSelected: {
    color: '#6D28D9',
  },
  recTag: {
    backgroundColor: '#EDE9FE',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  recTagText: {
    color: '#7C3AED',
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  presetDetails: {
    fontSize: 12,
    color: '#6B7280',
    fontWeight: '500',
  },
  radioCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#D1D5DB',
    justifyContent: 'center',
    alignItems: 'center',
  },
  radioCircleSelected: {
    borderColor: '#8B5CF6',
  },
  radioInner: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#8B5CF6',
  },
  spacer: {
    flex: 1,
  },
  continueButton: {
    backgroundColor: '#8B5CF6',
    height: 60,
    borderRadius: 30,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#8B5CF6',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.35,
    shadowRadius: 16,
    elevation: 6,
    marginBottom: 10,
  },
  continueButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
});
