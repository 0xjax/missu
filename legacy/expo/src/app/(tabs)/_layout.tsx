import { Tabs } from 'expo-router/js-tabs';

import { NativeTabBar } from '@/components/native-tab-bar';

// NOTE: 实验分支：底栏换成 Compose M3 NavigationBar（见 src/components/native-tab-bar.tsx）。
// 图标由底栏自己按 route name 选，所以这里不再传 tabBarIcon/tabBarStyle；tab 标题仍在这里定义。
export default function TabsLayout() {
  return (
    <Tabs
      tabBar={(props) => <NativeTabBar {...props} />}
      screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="(home)" options={{ title: '首页' }} />
      <Tabs.Screen name="latest" options={{ title: '列表' }} />
      <Tabs.Screen name="discover" options={{ title: '发现' }} />
      <Tabs.Screen name="account" options={{ title: '我的' }} />
    </Tabs>
  );
}