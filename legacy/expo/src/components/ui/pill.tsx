import { Pressable, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  label: string;
  selected?: boolean;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
};

/**
 * 胶囊按钮：发现页一级分类、搜索历史、女优筛选选项共用。
 * 选中态只有填充色，不插 ✓、不加边框——同维度多种形态会让选中态被平均掉。
 */
export function Pill({ label, selected, onPress, style, accessibilityLabel }: Props) {
  const theme = useTheme();

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.pill,
        { backgroundColor: selected ? theme.accent : theme.backgroundElement },
        pressed && styles.pressed,
        style,
      ]}>
      <ThemedText style={[styles.text, selected && { color: theme.onAccent }]}>{label}</ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pill: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 36,
    paddingHorizontal: Spacing.md,
  },
  pressed: {
    opacity: 0.7,
  },
  text: {
    fontSize: 13,
    lineHeight: 18,
  },
});
