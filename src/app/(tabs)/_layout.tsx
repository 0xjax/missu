import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { NativeTabs } from 'expo-router/unstable-native-tabs';

import { useTheme } from '@/hooks/use-theme';

export default function TabsLayout() {
  const theme = useTheme();

  return (
    <NativeTabs
      activityEnabled
      backgroundColor={theme.background}
      iconColor={{ default: theme.textSecondary, selected: theme.accent }}
      labelStyle={{ default: { color: theme.textSecondary }, selected: { color: theme.accent } }}
      tintColor={theme.accent}>
      <NativeTabs.Trigger labelVisibilityMode="labeled" name="index">
        <NativeTabs.Trigger.Icon
          sf="house.fill"
          src={<NativeTabs.Trigger.VectorIcon family={MaterialCommunityIcons} name="home" />}
        />
        <NativeTabs.Trigger.Label>首页</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger labelVisibilityMode="labeled" name="latest">
        <NativeTabs.Trigger.Icon
          sf="list.bullet"
          src={<NativeTabs.Trigger.VectorIcon family={MaterialCommunityIcons} name="format-list-bulleted" />}
        />
        <NativeTabs.Trigger.Label>列表</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger labelVisibilityMode="labeled" name="discover">
        <NativeTabs.Trigger.Icon
          sf="safari.fill"
          src={<NativeTabs.Trigger.VectorIcon family={MaterialCommunityIcons} name="compass" />}
        />
        <NativeTabs.Trigger.Label>发现</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger labelVisibilityMode="labeled" name="history">
        <NativeTabs.Trigger.Icon
          sf="clock.fill"
          src={<NativeTabs.Trigger.VectorIcon family={MaterialCommunityIcons} name="history" />}
        />
        <NativeTabs.Trigger.Label>历史</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger labelVisibilityMode="labeled" name="account">
        <NativeTabs.Trigger.Icon
          sf="person.crop.circle.fill"
          src={<NativeTabs.Trigger.VectorIcon family={MaterialCommunityIcons} name="account-circle" />}
        />
        <NativeTabs.Trigger.Label>我的</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
