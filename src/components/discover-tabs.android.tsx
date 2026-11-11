import { Host } from '@expo/ui';
import { Row, Text, ToggleButton } from '@expo/ui/jetpack-compose';
import { defaultMinSize } from '@expo/ui/jetpack-compose/modifiers';

import { useTheme } from '@/hooks/use-theme';
import type { DiscoverTabsProps } from './discover-tabs.types';

/**
 * Android 分类切换：原生 ToggleButton 行，选中态只用容器色。
 * NOTE: 不用 SingleChoiceSegmentedButtonRow——它的选中态自带 ✓ 图标，Expo UI 没暴露关闭方式。
 */
export function DiscoverTabs<T extends string>({ items, value, onChange }: DiscoverTabsProps<T>) {
  const theme = useTheme();

  return (
    <Host matchContents={{ vertical: true }} style={{ alignSelf: 'stretch' }}>
      <Row horizontalArrangement={{ spacedBy: 6 }} verticalAlignment="center">
        {items.map((item) => (
          <ToggleButton
            checked={item.id === value}
            colors={{
              containerColor: theme.backgroundElement,
              contentColor: theme.text,
              checkedContainerColor: theme.accent,
              checkedContentColor: theme.onAccent,
            }}
            key={item.id}
            modifiers={[defaultMinSize({ minWidth: 0 })]}
            onCheckedChange={() => onChange(item.id)}>
            <Text>{item.label}</Text>
          </ToggleButton>
        ))}
      </Row>
    </Host>
  );
}
