import { useRouter, type Href } from 'expo-router';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SettingsRow } from '@/components/settings-row';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';

export default function AccountScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { user, ready, logout } = useAuth();
  const displayName = user?.username && user.username !== user.email ? user.username : user?.email;

  /** NOTE: 未登录时把登录页作为中转，登录成功后直接落到目标页。 */
  const openProtected = (next: string) => {
    if (user) {
      router.push(next as Href);
      return;
    }
    router.push({ pathname: '/login', params: { next } } as unknown as Href);
  };

  const signOut = async () => {
    try {
      await logout();
    } catch (reason) {
      Alert.alert('退出失败', reason instanceof Error ? reason.message : '请稍后重试');
    }
  };

  if (!ready) {
    return (
      <View style={[styles.state, { backgroundColor: theme.background }]}>
        <ActivityIndicator color={theme.text} />
      </View>
    );
  }

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        {user ? (
          <SettingsRow detail={displayName === user.email ? undefined : user.email} label={displayName ?? user.email} />
        ) : (
          <SettingsRow label="登录 MissAV 账号" onPress={() => router.push('/login' as Href)} />
        )}
        <SettingsRow
          detail={user ? undefined : '登录后可用'}
          label="我的收藏"
          onPress={() => openProtected('/saved')}
        />
        <SettingsRow label="观看历史" onPress={() => router.push('/history' as Href)} />
        <SettingsRow
          detail={user ? undefined : '登录后可用'}
          label="我的片单"
          onPress={() => openProtected('/playlists')}
        />
        <SettingsRow label="设置" onPress={() => router.push('/settings' as Href)} />
        {user && <SettingsRow danger label="退出登录" onPress={() => void signOut()} />}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    paddingVertical: Spacing.sm,
  },
  state: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
  },
});
