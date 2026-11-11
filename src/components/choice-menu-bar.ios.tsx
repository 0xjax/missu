import { Host } from '@expo/ui';
import { Button, HStack, Menu } from '@expo/ui/swift-ui';

import type { Choice } from './choice-menu-bar.types';

/**
 * iOS 原生筛选工具栏：SwiftUI Menu，点击弹出系统菜单。
 */
export function ChoiceMenuBar({ choices }: { choices: Choice[] }) {
  return (
    <Host matchContents>
      <HStack alignment="center" spacing={8}>
        {choices.map((choice) => (
          <Menu key={choice.key} label={choice.text}>
            {choice.options.map((option) => (
              <Button
                key={option.value}
                label={option.value === choice.value ? `✓ ${option.label}` : option.label}
                onPress={() => choice.onChange(option.value)}
              />
            ))}
          </Menu>
        ))}
      </HStack>
    </Host>
  );
}
