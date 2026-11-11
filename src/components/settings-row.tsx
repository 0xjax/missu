import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  label: string;
  detail?: string;
  danger?: boolean;
  onPress?: () => void;
};

/**
 * 个人中心/设置页的列表行：56dp 高、透明容器、右侧 chevron。
 * NOTE: 不用 @expo/ui 的 List/ListItem——Expo Go 内置的 ExpoUI 版本对 ListItem 的 colors 属性
 * 会抛 "Cannot set prop 'modifiers'"，导致 dev 下红条报错，因此这里用 RN 行保持同样的视觉。
 */
export function SettingsRow({ label, detail, danger, onPress }: Props) {
  const theme = useTheme();

  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      disabled={!onPress}
      onPress={onPress}
      style={({ pressed }) => [styles.row, pressed && onPress ? styles.pressed : undefined]}>
      <View style={styles.text}>
        <ThemedText style={danger ? { color: theme.danger } : undefined}>{label}</ThemedText>
        {detail && <ThemedText type="small" themeColor="textSecondary">{detail}</ThemedText>}
      </View>
      {onPress && <Ionicons color={theme.textSecondary} name="chevron-forward" size={18} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
    minHeight: 56,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  pressed: {
    opacity: 0.6,
  },
  text: {
    flex: 1,
    gap: Spacing.xxs,
  },
});
