import { Ionicons } from '@expo/vector-icons';
import {
  Host,
  NavigationBar,
  NavigationBarItem,
  RNHostView,
  Text,
} from '@expo/ui/jetpack-compose';
import type { BottomTabBarProps } from 'expo-router/js-tabs';
import { View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

// NOTE: 实验分支专用——用 Compose 的 M3 NavigationBar 替换 JS tab 栏，验证“原生 UI 迁移”的底栏可行性。
// 图标仍用 Ionicons（Compose 的 Icon 只吃 XML vector drawable），通过 RNHostView 嵌进 Icon 插槽。
// NOTE: expo-router 的 tabBar 回调给的是 navigateToTab(routeKey)，不是 react-navigation 的 navigation 对象。
const TAB_ICONS: Record<string, keyof typeof Ionicons.glyphMap> = {
  '(home)': 'home',
  latest: 'list',
  discover: 'compass',
  account: 'person',
};

export function NativeTabBar({ state, descriptors, navigateToTab, insets }: BottomTabBarProps) {
  const theme = useTheme();

  return (
    <View style={{ backgroundColor: theme.background, paddingBottom: insets.bottom }}>
      <Host colorScheme="dark" seedColor={theme.accent} style={{ alignSelf: 'stretch' }}>
        <NavigationBar containerColor={theme.background} contentColor={theme.textSecondary} tonalElevation={0}>
          {state.routes.map((route, index) => {
            const focused = state.index === index;
            const label = descriptors[route.key].options.title ?? route.name;
            const icon = TAB_ICONS[route.name] ?? 'ellipse';

            return (
              <NavigationBarItem
                colors={{
                  selectedIconColor: theme.accent,
                  selectedIndicatorColor: theme.backgroundSelected,
                  selectedTextColor: theme.accent,
                  unselectedIconColor: theme.textSecondary,
                  unselectedTextColor: theme.textSecondary,
                }}
                key={route.key}
                onClick={() => navigateToTab(route.key)}
                selected={focused}>
                <NavigationBarItem.Icon>
                  <RNHostView matchContents>
                    <Ionicons color={focused ? theme.accent : theme.textSecondary} name={icon} size={22} />
                  </RNHostView>
                </NavigationBarItem.Icon>
                <NavigationBarItem.Label>
                  <Text>{label}</Text>
                </NavigationBarItem.Label>
              </NavigationBarItem>
            );
          })}
        </NavigationBar>
      </Host>
    </View>
  );
}
