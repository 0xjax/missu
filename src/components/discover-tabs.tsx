import { StyleSheet, View } from 'react-native';

import { Pill } from '@/components/ui/pill';
import { Spacing } from '@/constants/theme';
import type { DiscoverTabsProps } from './discover-tabs.types';

/**
 * 一级分类切换：等宽胶囊行，选中态只用填充色。
 * NOTE: 等宽而不是按文字宽度排——分类名长短不一，按内容排会让整行左右跳动。
 */
export function DiscoverTabs<T extends string>({ items, value, onChange }: DiscoverTabsProps<T>) {
  return (
    <View style={styles.row}>
      {items.map((item) => (
        <Pill
          key={item.id}
          label={item.label}
          onPress={() => onChange(item.id)}
          selected={item.id === value}
          style={styles.item}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  item: {
    flex: 1,
    paddingHorizontal: Spacing.xs,
  },
});
