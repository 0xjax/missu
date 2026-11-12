import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, StyleSheet, View } from 'react-native';
import { Snackbar } from 'react-native-paper';

import { deletePlaylist, getPlaylistDetail, isAbortError, SessionExpiredError, type PlaylistDetail } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '加载失败，请稍后重试';

export default function PlaylistDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();
  const { expire } = useAuth();
  const [detail, setDetail] = useState<PlaylistDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const [deleting, setDeleting] = useState(false);
  const [notice, setNotice] = useState<string>();

  useEffect(() => {
    if (typeof id !== 'string') return;
    const controller = new AbortController();
    let active = true;
    getPlaylistDetail(id, controller.signal).then((result) => {
      if (active) setDetail(result);
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      if (reason instanceof SessionExpiredError) {
        void expire();
        setError(reason.message);
        return;
      }
      setError(errorMessage(reason));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [attempt, expire, id]);

  const retry = () => {
    setLoading(true);
    setError(undefined);
    setAttempt((value) => value + 1);
  };

  const remove = async () => {
    if (typeof id !== 'string' || deleting) return;
    setDeleting(true);
    setNotice(undefined);
    try {
      await deletePlaylist(id);
      router.back();
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : '删除片单失败');
    } finally {
      setDeleting(false);
    }
  };

  // NOTE: 删除片单是不可撤销的破坏性操作，入口只放在片单详情页页头，且必须二次确认。
  const confirmRemove = () => {
    Alert.alert(
      '删除片单',
      `「${detail?.name ?? '这个片单'}」会从我的片单里移除，此操作无法撤销。`,
      [
        { text: '取消', style: 'cancel' },
        { text: '删除', style: 'destructive', onPress: () => void remove() },
      ],
    );
  };

  return (
    <View style={styles.screen}>
      <Stack.Screen
        options={{
          headerRight: detail
            ? () => (
              <Pressable accessibilityRole="button" disabled={deleting} hitSlop={8} onPress={confirmRemove}>
                <ThemedText themeColor="danger" type="small">{deleting ? '删除中…' : '删除'}</ThemedText>
              </Pressable>
            )
            : undefined,
          title: detail?.name || '片单',
        }}
      />
      <FlatList
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.content}
        data={detail?.videos ?? []}
        keyExtractor={(video) => video.id}
        ListEmptyComponent={(
          <View style={styles.state}>
            {loading ? <ActivityIndicator color={theme.text} /> : (
              <>
                <ThemedText themeColor="textSecondary">{error || '这个片单还没有影片'}</ThemedText>
                {error && (
                  <Pressable accessibilityRole="button" onPress={retry} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
                    <ThemedText type="smallBold">重试</ThemedText>
                  </Pressable>
                )}
              </>
            )}
          </View>
        )}
        numColumns={2}
        renderItem={({ item }) => <VideoCard video={item} />}
        style={{ backgroundColor: theme.background }}
      />
      <Snackbar
        action={{ label: '关闭', onPress: () => setNotice(undefined) }}
        duration={3000}
        onDismiss={() => setNotice(undefined)}
        visible={Boolean(notice)}>
        {notice}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    padding: Spacing.lg,
    paddingBottom: Spacing.xxxl,
  },
  row: {
    width: '100%',
    gap: Spacing.md,
    marginBottom: Spacing.xl,
  },
  state: {
    alignItems: 'center',
    gap: Spacing.lg,
    paddingVertical: Spacing.huge,
  },
  button: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
});
