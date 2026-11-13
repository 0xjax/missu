/**
 * 设计令牌：颜色、间距、圆角、底部栏高度。界面只允许用这里的值，
 * 不在组件里写裸数字/裸色值（参见 DESIGN.md 的「Design Tokens」）。
 */

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
