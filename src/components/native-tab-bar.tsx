import {
  Host,
  Icon,
  NavigationBar,
  NavigationBarItem,
  Text,
} from '@expo/ui/jetpack-compose';
import type { BottomTabBarProps } from 'expo-router/js-tabs';
import type { ImageSourcePropType } from 'react-native';
import { View } from 'react-native';

import { useTheme } from '@/hooks/use-theme';

// NOTE: 图标用 Android XML vector drawable（Material Icons 官方路径，见 assets/icons/*.xml）。
// 不能用 Ionicons：那需要在 Compose 里嵌 RN 视图，而 RN 视图会吃掉触摸事件，导致点图标不生效。
// NOTE: expo-router 的 tabBar 回调给的是 navigateToTab(routeKey)，不是 react-navigation 的 navigation 对象。
const TAB_ICONS: Record<string, ImageSourcePropType> = {
  '(home)': require('../../assets/icons/tab-home.xml'),
  latest: require('../../assets/icons/tab-list.xml'),
  discover: require('../../assets/icons/tab-discover.xml'),
  account: require('../../assets/icons/tab-account.xml'),
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
                  {/* NOTE: 不传 tint，图标继承 item 的 selected/unselected 图标色。 */}
                  <Icon size={24} source={TAB_ICONS[route.name]} />
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
