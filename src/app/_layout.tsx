import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import * as SystemUI from 'expo-system-ui';
import { useEffect } from 'react';
import { StyleSheet, useColorScheme } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { MD3DarkTheme, MD3LightTheme, PaperProvider } from 'react-native-paper';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { OfflineBanner } from '@/components/offline-banner';
import { Colors } from '@/constants/theme';
import { AuthProvider } from '@/stores/auth';
import { LanguageProvider } from '@/stores/language';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const colors = Colors[colorScheme === 'dark' ? 'dark' : 'light'];
  const paperBase = colorScheme === 'dark' ? MD3DarkTheme : MD3LightTheme;
  const paperTheme = {
    ...paperBase,
    colors: { ...paperBase.colors, primary: colors.accent },
  };
  // NOTE: 原生栈转场露出的是根视图底色，默认白色会在进出次级页面时闪白；这里跟随主题设置。
  const baseNavigationTheme = colorScheme === 'dark' ? DarkTheme : DefaultTheme;
  const navigationTheme = {
    ...baseNavigationTheme,
    colors: { ...baseNavigationTheme.colors, background: colors.background, card: colors.background },
  };

  useEffect(() => {
    void SystemUI.setBackgroundColorAsync(colors.background);
  }, [colors.background]);

  return (
    <GestureHandlerRootView style={[styles.root, { backgroundColor: colors.background }]}>
      <PaperProvider theme={paperTheme}>
        <ThemeProvider value={navigationTheme}>
          <AuthProvider>
            <LanguageProvider>
              <AnimatedSplashOverlay />
              <Stack screenOptions={{
                contentStyle: { backgroundColor: colors.background },
                headerStyle: { backgroundColor: colors.background },
                headerTintColor: colors.text,
              }}>
                <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
                <Stack.Screen name="login" options={{ title: '登录' }} />
                <Stack.Screen name="saved" options={{ title: '我的收藏' }} />
                <Stack.Screen name="history" options={{ title: '观看历史' }} />
                <Stack.Screen name="playlists/index" options={{ title: '我的片单' }} />
                <Stack.Screen name="playlists/[id]" options={{ title: '片单' }} />
                <Stack.Screen name="settings" options={{ title: '设置' }} />
                <Stack.Screen name="language" options={{ title: '内容语言' }} />
                <Stack.Screen name="video/[id]" options={{ title: '视频详情' }} />
              </Stack>
              <OfflineBanner />
            </LanguageProvider>
          </AuthProvider>
        </ThemeProvider>
      </PaperProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
});
