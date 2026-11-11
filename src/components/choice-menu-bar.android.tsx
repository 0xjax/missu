import { Host } from '@expo/ui';
import {
  DropdownMenu,
  DropdownMenuItem,
  Row,
  Text,
  TextButton,
} from '@expo/ui/jetpack-compose';
import { useState } from 'react';

import { useTheme } from '@/hooks/use-theme';
import type { Choice } from './choice-menu-bar.types';

/**
 * Android 原生筛选工具栏：M3 TextButton 作为触发器，点开是系统 DropdownMenu。
 * NOTE: 用 DropdownMenuItem 而不是 SelectableDropdownMenuItem——Expo Go 内置的 ExpoUI 还没有后者；
 * 选中态用文字颜色表达，不插入 ✓ 前缀（会让菜单项左右跳动）。
 */
export function ChoiceMenuBar({ choices }: { choices: Choice[] }) {
  const theme = useTheme();
  const [openKey, setOpenKey] = useState<string>();

  return (
    <Host matchContents>
      <Row horizontalArrangement={{ spacedBy: 4 }} verticalAlignment="center">
        {choices.map((choice) => {
          const expanded = openKey === choice.key;
          return (
            <DropdownMenu
              expanded={expanded}
              key={choice.key}
              onDismissRequest={() => setOpenKey(undefined)}>
              <DropdownMenu.Trigger>
                <TextButton
                  contentPadding={{ start: 10, top: 8, end: 10, bottom: 8 }}
                  onClick={() => setOpenKey(expanded ? undefined : choice.key)}>
                  <Text>{`${choice.text} ▾`}</Text>
                </TextButton>
              </DropdownMenu.Trigger>
              <DropdownMenu.Items>
                {choice.options.map((option) => (
                  <DropdownMenuItem
                    elementColors={option.value === choice.value ? { textColor: theme.accent } : undefined}
                    key={option.value}
                    onClick={() => {
                      setOpenKey(undefined);
                      choice.onChange(option.value);
                    }}>
                    <DropdownMenuItem.Text>
                      <Text>{option.label}</Text>
                    </DropdownMenuItem.Text>
                  </DropdownMenuItem>
                ))}
              </DropdownMenu.Items>
            </DropdownMenu>
          );
        })}
      </Row>
    </Host>
  );
}
