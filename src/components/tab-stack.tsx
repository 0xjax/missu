import { Stack } from 'expo-router';

import { useTheme } from '@/hooks/use-theme';

export function TabStack({ title }: { title: string }) {
  const theme = useTheme();

  return (
    <Stack
      screenOptions={{
        contentStyle: { backgroundColor: theme.background },
        headerLargeTitleEnabled: true,
        headerLargeTitleShadowVisible: false,
        headerShadowVisible: false,
        headerStyle: { backgroundColor: theme.background },
        headerTintColor: theme.text,
      }}>
      <Stack.Screen name="index" options={{ title }} />
    </Stack>
  );
}
