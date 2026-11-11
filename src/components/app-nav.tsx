import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const items = [
  { id: 'home', label: '首页', path: '/' },
  { id: 'latest', label: '列表', path: '/latest' },
  { id: 'discover', label: '发现', path: '/discover' },
  { id: 'history', label: '历史', path: '/history' },
  { id: 'account', label: '我的', path: '/account' },
] as const;

type Props = {
  current: (typeof items)[number]['id'];
};

export function AppNav({ current }: Props) {
  const router = useRouter();
  const theme = useTheme();

  return (
    <View style={[styles.container, { backgroundColor: theme.backgroundElement }]}>
      {items.map((item) => {
        const selected = current === item.id;

        return (
          <Pressable
            accessibilityLabel={item.label}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            key={item.id}
            onPress={() => router.replace(item.path)}
            style={[
              styles.item,
              selected && { backgroundColor: theme.accent },
            ]}>
            <ThemedText
              type="smallBold"
              themeColor={selected ? 'onAccent' : 'textSecondary'}>
              {item.label}
            </ThemedText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: Radius.lg,
    flexDirection: 'row',
    gap: Spacing.xs,
    padding: Spacing.xs,
  },
  item: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    flex: 1,
    justifyContent: 'center',
    paddingVertical: Spacing.md,
  },
});
