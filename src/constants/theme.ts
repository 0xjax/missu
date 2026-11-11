/**
 * Below are the colors that are used in the app. The colors are defined in the light and dark mode.
 * There are many other ways to style your app. For example, [Nativewind](https://www.nativewind.dev/), [Tamagui](https://tamagui.dev/), [unistyles](https://unistyl.es/), etc.
 */

import { Platform } from 'react-native';

export const Colors = {
  light: {
    text: '#000000',
    background: '#ffffff',
    backgroundElement: '#F0F0F3',
    backgroundSelected: '#E0E1E6',
    textSecondary: '#60646C',
    accent: '#d85a76',
    onAccent: '#ffffff',
    danger: '#B3261E',
  },
  dark: {
    text: '#ffffff',
    background: '#000000',
    backgroundElement: '#212225',
    backgroundSelected: '#2E3135',
    textSecondary: '#B0B4BA',
    accent: '#d85a76',
    onAccent: '#ffffff',
    danger: '#F2B8B5',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Spacing = {
  xxs: 2,
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
  huge: 48,
  giant: 64,
} as const;

export const Radius = {
  sm: 8,
  md: 10,
  lg: 12,
  full: 9999,
} as const;

// NOTE: Android 原生底部 tab 栏会覆盖内容，滚动容器需额外留出高度。
export const TabBarInset = Platform.select({ android: 80, default: 0 }) ?? 0;
