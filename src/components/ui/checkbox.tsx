import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

type Props = {
  value: boolean;
  onValueChange: (value: boolean) => void;
  accessibilityLabel?: string;
};

/** 勾选框：22dp 方框，选中填充 accent + 白色对勾。 */
export function Checkbox({ value, onValueChange, accessibilityLabel }: Props) {
  const theme = useTheme();

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: value }}
      hitSlop={10}
      onPress={() => onValueChange(!value)}
      style={[
        styles.box,
        {
          backgroundColor: value ? theme.accent : 'transparent',
          borderColor: value ? theme.accent : theme.textSecondary,
        },
      ]}>
      {value ? <Ionicons color={theme.onAccent} name="checkmark" size={15} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  box: {
    alignItems: 'center',
    borderRadius: 6,
    borderWidth: 2,
    height: 22,
    justifyContent: 'center',
    width: 22,
  },
});
