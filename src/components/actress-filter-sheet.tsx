import { BottomSheet, Button, Column, Host, Picker, Row, Text } from '@expo/ui';
import { useWindowDimensions } from 'react-native';

import { ACTRESS_FILTER_SECTIONS, type ActressFilterKey, type ActressFilters } from '@/constants/actress-filters';
import { useTheme } from '@/hooks/use-theme';

const NONE = '';
const SHEET_PADDING = 16;
const COLUMN_GAP = 12;

type Props = {
  open: boolean;
  filters: ActressFilters;
  onChange: (key: ActressFilterKey, value?: string) => void;
  onReset: () => void;
  onClose: () => void;
};

const PAIRS = [ACTRESS_FILTER_SECTIONS.slice(0, 2), ACTRESS_FILTER_SECTIONS.slice(2, 4)];

/** 女优过滤面板：四个维度 2×2 排布，选中即生效，点「完成」关闭。 */
export function ActressFilterSheet({ open, filters, onChange, onReset, onClose }: Props) {
  const theme = useTheme();
  const { width } = useWindowDimensions();
  // NOTE: Compose Row 不解析百分比宽度，这里按屏宽算出固定 dp。
  const columnWidth = Math.floor((width - SHEET_PADDING * 2 - COLUMN_GAP) / 2);

  return (
    <Host>
      <BottomSheet contentPadding={0} isPresented={open} onDismiss={onClose}>
        <Column spacing={12} style={{ padding: SHEET_PADDING }}>
          <Text textStyle={{ fontSize: 18, fontWeight: '600' }}>筛选女优</Text>
          {PAIRS.map((pair, index) => (
            <Row key={index} spacing={COLUMN_GAP}>
              {pair.map((section) => (
                <Column key={section.key} spacing={4} style={{ width: columnWidth }}>
                  <Text textStyle={{ color: theme.textSecondary, fontSize: 13 }}>{section.title}</Text>
                  <Picker
                    accessibilityLabel={section.title}
                    onValueChange={(value) => onChange(section.key, value === NONE ? undefined : String(value))}
                    selectedValue={filters[section.key] ?? NONE}>
                    <Picker.Item label="不限" value={NONE} />
                    {section.options.map((option) => (
                      <Picker.Item key={option.value} label={option.label} value={option.value} />
                    ))}
                  </Picker>
                </Column>
              ))}
            </Row>
          ))}
          <Row spacing={12}>
            <Button label="重置" onPress={onReset} variant="text" />
            <Button label="完成" onPress={onClose} />
          </Row>
        </Column>
      </BottomSheet>
    </Host>
  );
}
