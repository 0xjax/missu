import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Pill } from '@/components/ui/pill';
import { Sheet } from '@/components/ui/sheet';
import { Spacing } from '@/constants/theme';
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
  return (
    <Sheet onClose={onClose} title="播放倍速" visible={visible}>
      <View style={styles.row}>
        {PLAYBACK_RATES.map((rate) => (
          <Pill
            key={rate}
            label={`${rate}x`}
            onPress={() => onChangePlaybackRate(rate)}
            selected={rate === playbackRate}
            style={styles.pill}
          />
        ))}
      </View>
      <ThemedText type="small" themeColor="textSecondary">播放倍速对所有视频生效，长按视频不再临时加速。</ThemedText>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  pill: {
    minWidth: 56,
  },
});
