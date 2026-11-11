import { useState } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { Button, Menu } from 'react-native-paper';

import { useTheme } from '@/hooks/use-theme';
import type { Choice } from './choice-menu-bar.types';

/**
 * Web 回退实现（Android 由同名平台文件接管）。
 * 选中态靠背景色区分，不插入 ✓ 前缀。
 */
export function ChoiceMenuBar({ choices }: { choices: Choice[] }) {
  const theme = useTheme();
  const [openKey, setOpenKey] = useState<string>();

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.bar}>
      {choices.map((choice) => (
        <Menu
          anchor={(
            <Button compact mode="text" onPress={() => setOpenKey(choice.key)}>
              {`${choice.text} ▾`}
            </Button>
          )}
          key={choice.key}
          onDismiss={() => setOpenKey(undefined)}
          visible={openKey === choice.key}>
          {choice.options.map((option) => (
            <Menu.Item
              key={option.value}
              onPress={() => {
                setOpenKey(undefined);
                choice.onChange(option.value);
              }}
              style={option.value === choice.value ? { backgroundColor: theme.backgroundSelected } : undefined}
              title={option.label}
            />
          ))}
        </Menu>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  bar: {
    gap: 4,
  },
});
