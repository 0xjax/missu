import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { UiButton } from '@/components/ui/button';
import { Pill } from '@/components/ui/pill';
import { Sheet } from '@/components/ui/sheet';
import { ACTRESS_FILTER_SECTIONS, type ActressFilterKey, type ActressFilters } from '@/constants/actress-filters';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  open: boolean;
  filters: ActressFilters;
  onChange: (key: ActressFilterKey, value?: string) => void;
  onReset: () => void;
  onClose: () => void;
};

/**
 * 女优过滤面板：四个维度各占一行，点行展开该维度的选项（胶囊网格），选中即生效并收起。
 * NOTE: 每个维度最多 26 个选项（出道年份），四列同时铺开会把面板撑到看不见选项；
 * 改成一次只展开一维，行内显示当前值，选择后收起，避免滚动整块面板去找下一个维度。
 */
export function ActressFilterSheet({ open, filters, onChange, onReset, onClose }: Props) {
  const theme = useTheme();
  const [expanded, setExpanded] = useState<ActressFilterKey>();

  return (
    <Sheet onClose={onClose} title="筛选女优" visible={open}>
      <ScrollView contentContainerStyle={styles.list} style={styles.scroll}>
        {ACTRESS_FILTER_SECTIONS.map((section) => {
          const current = filters[section.key];
          const isOpen = expanded === section.key;
          const currentLabel = section.options.find((option) => option.value === current)?.label;

          return (
            <View key={section.key}>
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ expanded: isOpen }}
                onPress={() => setExpanded(isOpen ? undefined : section.key)}
                style={styles.header}>
                <ThemedText type="small" themeColor="textSecondary">{section.title}</ThemedText>
                <View style={styles.value}>
                  <ThemedText style={current ? { color: theme.accent } : undefined}>
                    {current ? currentLabel ?? current : '不限'}
                  </ThemedText>
                  <Ionicons color={theme.textSecondary} name={isOpen ? 'chevron-up' : 'chevron-down'} size={16} />
                </View>
              </Pressable>
              {isOpen ? (
                <View style={styles.options}>
                  <Pill
                    label="不限"
                    onPress={() => {
                      onChange(section.key, undefined);
                      setExpanded(undefined);
                    }}
                    selected={!current}
                  />
                  {section.options.map((option) => (
                    <Pill
                      key={option.value}
                      label={option.label}
                      onPress={() => {
                        onChange(section.key, option.value);
                        setExpanded(undefined);
                      }}
                      selected={current === option.value}
                    />
                  ))}
                </View>
              ) : null}
            </View>
          );
        })}
      </ScrollView>
      <View style={styles.actions}>
        <UiButton label="重置" onPress={onReset} variant="text" />
        <UiButton label="完成" onPress={onClose} />
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flexShrink: 1,
  },
  list: {
    gap: Spacing.xs,
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
    minHeight: 48,
    paddingHorizontal: Spacing.md,
  },
  value: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.xs,
  },
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
    paddingBottom: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'flex-end',
  },
});
