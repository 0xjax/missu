import { Host } from '@expo/ui';
import {
  SegmentedButton,
  SingleChoiceSegmentedButtonRow,
  Text,
} from '@expo/ui/jetpack-compose';
import { fillMaxWidth } from '@expo/ui/jetpack-compose/modifiers';

import type { DiscoverTabsProps } from './discover-tabs.types';

/**
 * Android 原生分段按钮（Material 3 SingleChoiceSegmentedButtonRow）。
 */
export function DiscoverTabs<T extends string>({ items, value, onChange }: DiscoverTabsProps<T>) {
  return (
    <Host matchContents={{ vertical: true }} style={{ alignSelf: 'stretch' }}>
      <SingleChoiceSegmentedButtonRow modifiers={[fillMaxWidth()]}>
        {items.map((item) => (
          <SegmentedButton
            key={item.id}
            onClick={() => onChange(item.id)}
            selected={item.id === value}>
            <SegmentedButton.Label>
              <Text>{item.label}</Text>
            </SegmentedButton.Label>
          </SegmentedButton>
        ))}
      </SingleChoiceSegmentedButtonRow>
    </Host>
  );
}
