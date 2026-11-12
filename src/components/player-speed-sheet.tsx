import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { HOLD_RATES, PLAYBACK_RATES } from '@/stores/preferences';

type Props = {
  visible: boolean;
  playbackRate: number;
  holdRate: number;
  onChangePlaybackRate: (value: number) => void;
  onChangeHoldRate: (value: number) => void;
  onClose: () => void;
};

/** 播放器倍速设置：常规倍速 + 长按倍速，两项都落到偏好里持久化。 */
export function PlayerSpeedSheet({
  visible,
  playbackRate,
  holdRate,
  onChangePlaybackRate,
  onChangeHoldRate,
  onClose,
}: Props) {
  const theme = useTheme();

  return (
    <Modal animationType="fade" onRequestClose={onClose} statusBarTranslucent transparent visible={visible}>
      <Pressable accessibilityLabel="关闭倍速设置" onPress={onClose} style={styles.backdrop}>
        {/* NOTE: 内层 Pressable 吞掉点击，避免点和按钮时连带关闭。 */}
        <Pressable onPress={() => {}} style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
          <ThemedText type="smallBold">播放倍速</ThemedText>
          <View style={styles.row}>
            {PLAYBACK_RATES.map((rate) => (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected: rate === playbackRate }}
                key={rate}
                onPress={() => onChangePlaybackRate(rate)}
                style={[styles.pill, { backgroundColor: rate === playbackRate ? theme.accent : theme.backgroundSelected }]}>
                <ThemedText style={[styles.pillText, rate === playbackRate && { color: theme.onAccent }]}>{rate}x</ThemedText>
              </Pressable>
            ))}
          </View>
          <ThemedText type="smallBold">长按倍速</ThemedText>
          <View style={styles.row}>
            {HOLD_RATES.map((rate) => (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected: rate === holdRate }}
                key={rate}
                onPress={() => onChangeHoldRate(rate)}
                style={[styles.pill, { backgroundColor: rate === holdRate ? theme.accent : theme.backgroundSelected }]}>
                <ThemedText style={[styles.pillText, rate === holdRate && { color: theme.onAccent }]}>{rate}x</ThemedText>
              </Pressable>
            ))}
          </View>
          <ThemedText type="small" themeColor="textSecondary">长按视频期间用长按倍速，松手立即回到播放倍速。</ThemedText>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    flex: 1,
    justifyContent: 'flex-end',
  },
  card: {
    borderTopLeftRadius: Radius.lg,
    borderTopRightRadius: Radius.lg,
    borderCurve: 'continuous',
    gap: Spacing.sm,
    padding: Spacing.lg,
    paddingBottom: Spacing.xxl,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
    marginBottom: Spacing.xs,
  },
  pill: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 36,
    minWidth: 56,
    paddingHorizontal: Spacing.md,
  },
  pillText: {
    fontSize: 13,
  },
});
