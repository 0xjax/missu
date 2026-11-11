import { ScrollView, StyleSheet } from 'react-native';
import { Chip } from 'react-native-paper';

import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import type { DiscoverTabsProps } from './discover-tabs.types';

/**
 * iOS / Web 回退：Android 由 discover-tabs.android.tsx 提供原生分段按钮。
 */
export function DiscoverTabs<T extends string>({ items, value, onChange }: DiscoverTabsProps<T>) {
  const theme = useTheme();

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.choices}>
      {items.map((item) => (
        <Chip
          compact
          key={item.id}
          onPress={() => onChange(item.id)}
          selected={item.id === value}
          showSelectedCheck={false}
          style={[styles.choice, { backgroundColor: item.id === value ? theme.accent : theme.backgroundElement }]}
          textStyle={{ color: item.id === value ? theme.onAccent : theme.text, fontSize: 13 }}>
          {item.label}
        </Chip>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  choices: {
    gap: Spacing.sm,
  },
  choice: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: 2,
  },
});
