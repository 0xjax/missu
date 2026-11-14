import { Ionicons } from '@expo/vector-icons';
import { useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  title: string;
  children: ReactNode;
  defaultOpen?: boolean;
};

export function CollapsibleGroup({ title, children, defaultOpen = false }: Props) {
  const theme = useTheme();
  const [open, setOpen] = useState(defaultOpen);

  return (
    <View>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        onPress={() => setOpen((value) => !value)}
        style={({ pressed }) => [styles.header, pressed && styles.pressed]}>
        <ThemedText type="smallBold">{title}</ThemedText>
        <Ionicons color={theme.textSecondary} name={open ? 'chevron-up' : 'chevron-down'} size={18} />
      </Pressable>
      {open && <View style={styles.content}>{children}</View>}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 52,
    paddingHorizontal: Spacing.lg,
  },
  pressed: {
    opacity: 0.6,
  },
  content: {
    paddingBottom: Spacing.xs,
  },
});
