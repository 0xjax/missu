import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router/js-tabs';

import { useTheme } from '@/hooks/use-theme';

// NOTE: 用 expo-router 的 JS Tabs（react-navigation bottom-tabs），底栏是纯 RN 实现：
// 没有 Material 的托盘/波纹/图标淡入，也没有需要等测量才确定的高度，因此不会在切页时抖动。
// 不再使用 NativeTabs（底层 RNS → Material 的 BottomNavigationView，动画不可配置）。
export default function TabsLayout() {
  const theme = useTheme();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.accent,
        tabBarInactiveTintColor: theme.textSecondary,
        tabBarStyle: { backgroundColor: theme.background, borderTopColor: theme.backgroundElement },
      }}>
      <Tabs.Screen
        name="(home)"
        options={{
          title: '首页',
          tabBarIcon: ({ color, size }) => <Ionicons color={typeof color === 'string' ? color : theme.textSecondary} name="home" size={size} />,
        }}
      />
      <Tabs.Screen
        name="latest"
        options={{
          title: '列表',
          tabBarIcon: ({ color, size }) => <Ionicons color={typeof color === 'string' ? color : theme.textSecondary} name="list" size={size} />,
        }}
      />
      <Tabs.Screen
        name="discover"
        options={{
          title: '发现',
          tabBarIcon: ({ color, size }) => <Ionicons color={typeof color === 'string' ? color : theme.textSecondary} name="compass" size={size} />,
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: '我的',
          tabBarIcon: ({ color, size }) => <Ionicons color={typeof color === 'string' ? color : theme.textSecondary} name="person" size={size} />,
        }}
      />
    </Tabs>
  );
}