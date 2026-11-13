import { ScrollView, StyleSheet } from 'react-native';

import { UiButton } from '@/components/ui/button';
import { Menu } from '@/components/ui/menu';
import { Spacing } from '@/constants/theme';
import type { Choice } from './choice-menu-bar.types';

/**
 * 列表页工具栏：每个维度一个 40dp 触发器，有下拉选项的展开菜单。
 * NOTE: 触发器的文案就是当前选项值（或「筛选」等入口名），不加「排序：」之类前缀，维度由位置表达。
 */
export function ChoiceMenuBar({ choices }: { choices: Choice[] }) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.bar}>
      {choices.map((choice) => (
        choice.onPress ? (
          <UiButton key={choice.key} compact label={choice.text} onPress={choice.onPress} variant="text" />
        ) : (
          <Menu
            items={choice.options}
            key={choice.key}
            label={choice.text}
            onSelect={choice.onChange}
            value={choice.value}
          />
        )
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  bar: {
    alignItems: 'center',
    gap: Spacing.xs,
  },
});
