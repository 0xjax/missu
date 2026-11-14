import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  value: string;
  onChangeText: (value: string) => void;
  onSubmit?: (value: string) => void;
  onFocus?: () => void;
  onBlur?: () => void;
  placeholder?: string;
  accessibilityLabel?: string;
};

/** 搜索输入框：放大镜 + 输入 + 一键清空，高度 48dp。 */
export function SearchField({
  value,
  onChangeText,
  onSubmit,
  onFocus,
  onBlur,
  placeholder,
  accessibilityLabel,
}: Props) {
  const theme = useTheme();
  const submit = () => onSubmit?.(value);

  return (
    <View style={[styles.field, { backgroundColor: theme.backgroundElement }]}>
      <Pressable accessibilityLabel="搜索" hitSlop={8} onPress={submit}>
        <Ionicons color={theme.textSecondary} name="search" size={18} />
      </Pressable>
      <TextInput
        accessibilityLabel={accessibilityLabel}
        autoCapitalize="none"
        autoCorrect={false}
        onBlur={onBlur}
        onChangeText={onChangeText}
        onFocus={onFocus}
        onSubmitEditing={submit}
        placeholder={placeholder}
        placeholderTextColor={theme.textSecondary}
        returnKeyType="search"
        selectionColor={theme.accent}
        style={[styles.input, { color: theme.text }]}
        value={value}
      />
      {value.length > 0 ? (
        <Pressable accessibilityLabel="清空输入" hitSlop={8} onPress={() => onChangeText('')}>
          <Ionicons color={theme.textSecondary} name="close-circle" size={18} />
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  field: {
    alignItems: 'center',
    borderRadius: Radius.md,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 48,
    paddingHorizontal: Spacing.md,
  },
  input: {
    flex: 1,
    fontSize: 16,
    padding: 0,
  },
});
