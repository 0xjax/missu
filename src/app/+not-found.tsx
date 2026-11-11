import { Stack, useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function NotFoundScreen() {
  const theme = useTheme();
  const router = useRouter();

  return (
    <>
      <Stack.Screen options={{ title: '页面不存在' }} />
      <View style={[styles.screen, { backgroundColor: theme.background }]}>
        <ThemedText type="subtitle">页面不存在</ThemedText>
        <ThemedText themeColor="textSecondary">这个链接可能已经失效。</ThemedText>
        <Pressable accessibilityRole="button" onPress={() => router.replace('/')} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
          <ThemedText type="smallBold">返回首页</ThemedText>
        </Pressable>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.lg,
    padding: Spacing.xxl,
  },
  button: {
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
});
