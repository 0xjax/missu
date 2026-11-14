import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Switch, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  label: string;
  detail?: string;
  danger?: boolean;
  selected?: boolean;
  switchValue?: boolean;
  onSwitchChange?: (value: boolean) => void;
  onPress?: () => void;
};

/**
 * 个人中心/设置页的列表行：56dp 高、透明容器、右侧 chevron 或 Switch。
 * NOTE: 行是自己画的 RN 行，不用原生列表项：行高、分隔线、按压涟漪都在原生内部，
 * 而且旧版 @expo/ui 的 ListItem 还会因 "Cannot set prop 'modifiers'" 直接红条报错。
 */
export function SettingsRow({ label, detail, danger, selected, switchValue, onSwitchChange, onPress }: Props) {
  const theme = useTheme();
  const isSwitch = switchValue !== undefined && onSwitchChange !== undefined;
  const press = onPress ?? (isSwitch ? () => onSwitchChange(!switchValue) : undefined);

  return (
    <Pressable
      accessibilityRole={isSwitch ? 'switch' : press ? 'button' : undefined}
      accessibilityState={isSwitch ? { checked: switchValue } : undefined}
      disabled={!press}
      onPress={press}
      style={({ pressed }) => [styles.row, pressed && press ? styles.pressed : undefined]}>
      <View style={styles.text}>
        <ThemedText
          style={[
            danger ? { color: theme.danger } : undefined,
            // NOTE: 选中态只用强调色表达，不加 ✓，避免文字位移。
            selected ? { color: theme.accent } : undefined,
          ]}>
          {label}
        </ThemedText>
        {detail && <ThemedText type="small" themeColor="textSecondary">{detail}</ThemedText>}
      </View>
      {isSwitch ? (
        <Switch
          onValueChange={onSwitchChange}
          thumbColor="#ffffff"
          trackColor={{ false: theme.backgroundSelected, true: theme.accent }}
          value={switchValue}
        />
      ) : (
        press && !selected && <Ionicons color={theme.textSecondary} name="chevron-forward" size={18} />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
    minHeight: 56,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  pressed: {
    opacity: 0.6,
  },
  text: {
    flex: 1,
    gap: Spacing.xxs,
  },
});
