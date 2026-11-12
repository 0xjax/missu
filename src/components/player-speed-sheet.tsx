import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { PLAYBACK_RATES } from '@/stores/preferences';

type Props = {
  visible: boolean;
  playbackRate: number;
  onChangePlaybackRate: (value: number) => void;
  onClose: () => void;
};

/** 播放器倍速设置：目前只有常规播放倍速，落进偏好持久化。 */
export function PlayerSpeedSheet({
  visible,
  playbackRate,
  onChangePlaybackRate,
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
          <ThemedText type="small" themeColor="textSecondary">播放倍速对所有视频生效，长按视频不再临时加速。</ThemedText>
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
