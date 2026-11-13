import { BottomSheet, Button, Column, Host, Row, Text } from '@expo/ui';

import { useTheme } from '@/hooks/use-theme';
import { PLAYBACK_RATES } from '@/stores/preferences';

type Props = {
  visible: boolean;
  playbackRate: number;
  onChangePlaybackRate: (value: number) => void;
  onClose: () => void;
};

// NOTE: Material 按钮有最小宽度，5 个并排会超出窄屏，因此拆成 3 + 2 两行。
const ROWS = [PLAYBACK_RATES.slice(0, 3), PLAYBACK_RATES.slice(3)];

/** 播放器倍速设置：目前只有常规播放倍速，落进偏好持久化。 */
export function PlayerSpeedSheet({
  visible,
  playbackRate,
  onChangePlaybackRate,
  onClose,
}: Props) {
  const theme = useTheme();

  return (
    <Host colorScheme="dark" seedColor={theme.accent}>
      <BottomSheet
        containerColor={theme.backgroundElement}
        contentColor={theme.text}
        contentPadding={0}
        isPresented={visible}
        onDismiss={onClose}>
        <Column spacing={12} style={{ padding: 16 }}>
          <Text textStyle={{ fontSize: 18, fontWeight: '600' }}>播放倍速</Text>
          {ROWS.map((row, index) => (
            <Row key={index} spacing={8}>
              {row.map((rate) => (
                <Button
                  key={rate}
                  label={`${rate}x`}
                  onPress={() => onChangePlaybackRate(rate)}
                  variant={rate === playbackRate ? 'filled' : 'text'}
                />
              ))}
            </Row>
          ))}
          <Text textStyle={{ color: theme.textSecondary, fontSize: 13 }}>
            播放倍速对所有视频生效，长按视频不再临时加速。
          </Text>
        </Column>
      </BottomSheet>
    </Host>
  );
}