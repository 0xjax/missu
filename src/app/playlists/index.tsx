import { useFocusEffect, useRouter, type Href } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getPlaylists, isAbortError, SessionExpiredError, type PlaylistSummary } from '@/api/missav';
import { SettingsRow } from '@/components/settings-row';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '加载失败，请稍后重试';

export default function PlaylistsScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { user, expire } = useAuth();
  const [items, setItems] = useState<PlaylistSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();

  const load = useCallback(async (signal?: AbortSignal) => {
    setError(undefined);
    try {
      setItems(await getPlaylists(signal));
    } catch (reason) {
      if (isAbortError(reason) || signal?.aborted) return;
      if (reason instanceof SessionExpiredError) {
        await expire();
        setItems([]);
        return;
      }
      setError(errorMessage(reason));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [expire]);

  useFocusEffect(useCallback(() => {
    const controller = new AbortController();
    if (!user) {
      setLoading(false);
      return () => controller.abort();
    }
    void load(controller.signal);
    return () => controller.abort();
  }, [load, user]));

  const refresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const retry = () => {
    setLoading(true);
    void load();
  };

  const goLogin = () => router.push({ pathname: '/login', params: { next: '/playlists' } } as unknown as Href);

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={user ? <RefreshControl refreshing={refreshing} onRefresh={refresh} /> : undefined}>
        {!user && (
          <View style={styles.state}>
            <ThemedText themeColor="textSecondary">登录后查看云端片单</ThemedText>
            <Pressable accessibilityRole="button" onPress={goLogin} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">去登录</ThemedText>
            </Pressable>
          </View>
        )}
        {user && loading && <ActivityIndicator color={theme.text} style={styles.state} />}
        {user && !loading && error && (
          <View style={styles.state}>
            <ThemedText selectable themeColor="textSecondary">{error}</ThemedText>
            <View style={styles.actions}>
              <Pressable accessibilityRole="button" onPress={retry} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
                <ThemedText type="smallBold">重试</ThemedText>
              </Pressable>
              <Pressable accessibilityRole="button" onPress={goLogin} style={[styles.button, { backgroundColor: theme.accent }]}>
                <ThemedText type="smallBold" style={{ color: theme.onAccent }}>重新登录</ThemedText>
              </Pressable>
            </View>
          </View>
        )}
        {user && !loading && !error && items.length === 0 && (
          <View style={styles.state}>
            <ThemedText themeColor="textSecondary">还没有片单</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">在网站创建的片单会显示在这里。</ThemedText>
          </View>
        )}
        {user && !loading && !error && items.map((item) => (
          <SettingsRow
            detail={[item.visibility, item.updatedAt && `更新于 ${item.updatedAt}`].filter(Boolean).join(' · ')}
            key={item.id}
            label={item.name}
            onPress={() => router.push(`/playlists/${encodeURIComponent(item.id)}` as Href)}
          />
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    paddingVertical: Spacing.sm,
  },
  state: {
    alignItems: 'center',
    gap: Spacing.md,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.huge,
  },
  button: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
});
