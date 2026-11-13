import { Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  label: string;
  onPress: () => void;
  variant?: 'text' | 'filled';
  disabled?: boolean;
  /** 工具栏里的文字按钮左右收紧，避免一排按钮横向撑开。 */
  compact?: boolean;
};

/** 基础按钮：40dp 高，文字按钮用于次要操作，填充按钮用于唯一主操作。 */
export function UiButton({ label, onPress, variant = 'filled', disabled, compact }: Props) {
  const theme = useTheme();
  const filled = variant === 'filled';

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: Boolean(disabled) }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        compact && styles.compact,
        filled ? { backgroundColor: theme.accent } : undefined,
        (pressed || disabled) && styles.dimmed,
      ]}>
      <ThemedText type="smallBold" style={filled ? { color: theme.onAccent } : { color: theme.accent }}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 40,
    paddingHorizontal: Spacing.lg,
  },
  compact: {
    paddingHorizontal: Spacing.md,
  },
  dimmed: {
    opacity: 0.5,
  },
});
