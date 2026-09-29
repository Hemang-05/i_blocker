import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  ScrollView,
} from 'react-native';
import { GradientBackground } from '../../components/common/GradientBackground';

interface OnboardingScreenProps {
  onNext: () => void;
}

interface BulletPoint {
  icon: string;
  title: string;
  description: string;
}

const BULLET_POINTS: BulletPoint[] = [
  {
    icon: '⚡',
    title: 'Boosts Focus & Attention Span',
    description:
      'Stops continuous dopamine fragmentation and trains your brain to sustain deep, uninterrupted attention.',
  },
  {
    icon: '🧠',
    title: 'Rewires Your Brain & Habits',
    description:
      'Replaces the mindless reflex of checking Instagram with intentional, structured friction and cognitive clarity.',
  },
  {
    icon: '🛡️',
    title: 'Unbreakable Doom-Scroll Barrier',
    description:
      'Enforces strict continuous limits. When time is up, Instagram locks — leaving no room for "just one more reel."',
  },
  {
    icon: '🎯',
    title: 'Guarantees Real Work Gets Done',
    description:
      'Reminds you of your daily priorities and long-term vision before compulsive habits steal your day.',
  },
];

export const OnboardingScreen: React.FC<OnboardingScreenProps> = ({ onNext }) => {
  return (
    <GradientBackground>
      <SafeAreaView style={styles.container}>
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Welcome Tag */}
          <View style={styles.tagBadge}>
            <Text style={styles.tagText}>WELCOME TO INSTABLOCKER</Text>
          </View>

          {/* Headline */}
          <Text style={styles.headline}>
            Why You Need{'\n'}
            <Text style={styles.highlightText}>InstaBlocker</Text>
          </Text>

          <Text style={styles.subheadline}>
            Engineered for high-performing individuals who refuse to let algorithms
            dictate their attention and future.
          </Text>

          {/* 4 Core Advantage Cards */}
          <View style={styles.cardContainer}>
            {BULLET_POINTS.map((item, index) => (
              <View key={index} style={styles.bulletCard}>
                <View style={styles.bulletIconBox}>
                  <Text style={styles.bulletIcon}>{item.icon}</Text>
                </View>
                <View style={styles.bulletTextBox}>
                  <Text style={styles.bulletTitle}>{item.title}</Text>
                  <Text style={styles.bulletDesc}>{item.description}</Text>
                </View>
              </View>
            ))}
          </View>

          {/* CTA Button */}
          <TouchableOpacity
            style={styles.nextButton}
            onPress={onNext}
            activeOpacity={0.85}
          >
            <Text style={styles.nextButtonText}>Next: Select Your Limit</Text>
            <Text style={styles.nextButtonArrow}>→</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    </GradientBackground>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 22,
    paddingTop: 24,
    paddingBottom: 36,
  },
  tagBadge: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(139, 92, 246, 0.12)',
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(139, 92, 246, 0.25)',
    marginBottom: 16,
  },
  tagText: {
    color: '#7C3AED',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
  },
  headline: {
    fontSize: 34,
    fontWeight: '800',
    color: '#111827',
    lineHeight: 42,
    letterSpacing: -0.5,
  },
  highlightText: {
    color: '#7C3AED',
  },
  subheadline: {
    fontSize: 15,
    color: '#4B5563',
    lineHeight: 22,
    marginTop: 10,
    marginBottom: 24,
  },
  cardContainer: {
    gap: 12,
    marginBottom: 28,
  },
  bulletCard: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 16,
    borderWidth: 1,
    borderColor: '#EDE9FE',
    alignItems: 'flex-start',
    shadowColor: '#1E1B4B',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 2,
  },
  bulletIconBox: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: '#F5F3FF',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  bulletIcon: {
    fontSize: 22,
  },
  bulletTextBox: {
    flex: 1,
  },
  bulletTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 3,
  },
  bulletDesc: {
    fontSize: 13,
    color: '#6B7280',
    lineHeight: 18,
  },
  nextButton: {
    flexDirection: 'row',
    backgroundColor: '#8B5CF6',
    borderRadius: 30,
    height: 58,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#8B5CF6',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.35,
    shadowRadius: 16,
    elevation: 5,
    gap: 8,
  },
  nextButtonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
  },
  nextButtonArrow: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },
});
