import { Ionicons } from '@expo/vector-icons';
import {
  Host,
  NavigationBar,
  NavigationBarItem,
  RNHostView,
  Text,
} from '@expo/ui/jetpack-compose';
import type { BottomTabBarProps } from 'expo-router/js-tabs';
import { Pressable, View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

// NOTE: 图标仍是 Ionicons（字体图标）：Compose 的 Icon 只吃 XML vector drawable，
// 而实测 XML 资产在这条链路上画不出来（底栏整体不渲染），见 DESIGN.md 的「Known Limits」。
// NOTE: RN 视图会吃掉触摸事件，所以图标自己带一个 Pressable 直接切 tab——
// 用户的观感是「图标也能点」，代价是这一小块没有 Material 波纹。
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
                    <Pressable
                      accessibilityLabel={label}
                      accessibilityRole="tab"
                      accessibilityState={{ selected: focused }}
                      hitSlop={6}
                      onPress={() => navigateToTab(route.key)}
                      style={{ padding: 8 }}>
                      <Ionicons color={focused ? theme.accent : theme.textSecondary} name={icon} size={22} />
                    </Pressable>
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