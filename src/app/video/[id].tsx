import { Image } from 'expo-image';
import { Stack, useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { Snackbar } from 'react-native-paper';

import { getVideoDetail, isAbortError, setVideoSaved, type VideoDetail } from '@/api/missav';
import { PlaylistSheet } from '@/components/playlist-sheet';
import { ThemedText } from '@/components/themed-text';
import { VideoPlayer } from '@/components/video-player';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { addToHistory } from '@/storage/history';
import { useAuth } from '@/stores/auth';
import { errorHaptic, successHaptic } from '@/utils/haptics';

export default function VideoDetailScreen() {
  const { id, badges: badgesParam } = useLocalSearchParams<{ id: string; badges?: string }>();
  const router = useRouter();
  const theme = useTheme();
  const { user } = useAuth();
  const [result, setResult] = useState<{ id: string; detail?: VideoDetail; error?: string }>();
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string>();
  const [playlistsOpen, setPlaylistsOpen] = useState(false);
  const detail = result?.id === id ? result.detail : undefined;
  const error = result?.id === id ? result.error : undefined;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    getVideoDetail(id, controller.signal).then((nextDetail) => {
      if (!active) return;
      setResult({ id, detail: nextDetail });
      // NOTE: 详情页没有当前影片的封面角标，角标由打开它的卡片带过来，用于历史列表继续显示。
      void addToHistory({ ...nextDetail, badges: badgesParam ? badgesParam.split('|') : undefined });
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      setResult({ id, error: reason instanceof Error ? reason.message : '加载失败，请稍后重试' });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [attempt, badgesParam, id]);

  const toggleSaved = async () => {
    if (!detail?.internalId) return;
    if (!user) {
      router.push({ pathname: '/login', params: { next: `/video/${encodeURIComponent(id)}` } } as unknown as Href);
      return;
    }
    setSaving(true);
    setNotice(undefined);
    try {
      const nextSaved = !detail.saved;
      await setVideoSaved(detail.internalId, nextSaved);
      setResult((current) => current?.detail
        ? { ...current, detail: { ...current.detail, saved: nextSaved } }
        : current);
      setNotice(nextSaved ? '已加入云端收藏' : '已取消云端收藏');
      successHaptic();
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : '收藏操作失败');
      errorHaptic();
    } finally {
      setSaving(false);
    }
  };

  const openPlaylists = () => {
    if (!user) {
      router.push({ pathname: '/login', params: { next: `/video/${encodeURIComponent(id)}` } } as unknown as Href);
      return;
    }
    setPlaylistsOpen(true);
  };

  // NOTE: 信息块里的链接（女优/类型/系列/发行商/标签）都指向影片列表页；用 dismissTo 弹出详情页，
  // 用 push 会把详情页留在根栈里不卸载，播放器组件一直挂在后台。
  const openInfoPath = (path: string, title: string) => {
    router.dismissTo({ pathname: '/latest', params: { source: path, title, query: '' } } as unknown as Href);
  };

  return (
    <View style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} style={{ backgroundColor: theme.background }}>
        <Stack.Screen options={{ title: id.toUpperCase() }} />
        {!detail && !error && <ActivityIndicator color={theme.text} style={styles.state} />}
        {error && (
          <View style={styles.state}>
            <ThemedText selectable themeColor="textSecondary">{error}</ThemedText>
            <Pressable accessibilityRole="button" onPress={() => {
              setResult(undefined);
              setAttempt((value) => value + 1);
            }} style={[styles.retry, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">重试</ThemedText>
            </Pressable>
          </View>
        )}
        {detail && (
          <>
            {detail.playbackUrl ? (
              <VideoPlayer url={detail.playbackUrl} />
            ) : (
              <View style={[styles.player, styles.unavailable, { backgroundColor: theme.backgroundElement }]}>
                {detail.thumbnailUrl && <Image source={{ uri: detail.thumbnailUrl }} style={styles.poster} contentFit="cover" />}
                <ThemedText style={styles.unavailableText}>暂时无法提取播放地址</ThemedText>
              </View>
            )}
            <View style={styles.metadata}>
              <ThemedText selectable type="smallBold" style={styles.title}>{detail.title}</ThemedText>
              {detail.internalId && (
                <View style={styles.actions}>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityState={{ busy: saving, disabled: saving }}
                    disabled={saving}
                    onPress={toggleSaved}
                    style={[styles.actionButton, { backgroundColor: detail.saved ? theme.accent : theme.backgroundElement }]}>
                    <ThemedText type="smallBold" style={detail.saved ? { color: theme.onAccent } : undefined}>
                      {saving ? '提交中…' : detail.saved ? '已收藏' : user ? '收藏' : '登录后收藏'}
                    </ThemedText>
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    onPress={openPlaylists}
                    style={[styles.actionButton, { backgroundColor: theme.backgroundElement }]}>
                    <ThemedText type="smallBold">片单</ThemedText>
                  </Pressable>
                </View>
              )}
              {detail.info.length > 0 && (
                <View style={styles.info}>
                  {detail.info.map((row) => (
                    <ThemedText key={row.label} selectable type="small">
                      <ThemedText themeColor="textSecondary" type="small">{row.label} </ThemedText>
                      {row.values.map((value, index) => {
                        const { path, text } = value;
                        return (
                          <ThemedText key={`${text}-${index}`} type="small">
                            <ThemedText
                              onPress={path ? () => openInfoPath(path, text) : undefined}
                              themeColor={path ? 'accent' : undefined}
                              type="small">
                              {text}
                            </ThemedText>
                            {index < row.values.length - 1 ? ', ' : ''}
                          </ThemedText>
                        );
                      })}
                    </ThemedText>
                  ))}
                </View>
              )}
              {detail.description && <ThemedText selectable type="small">{detail.description}</ThemedText>}
            </View>
          </>
        )}
      </ScrollView>
      <PlaylistSheet
        dvdId={detail?.id}
        onClose={() => setPlaylistsOpen(false)}
        onNotice={setNotice}
        onOpenPlaylists={() => {
          setPlaylistsOpen(false);
          router.push('/playlists');
        }}
        open={playlistsOpen}
      />
      <Snackbar
        action={{ label: '关闭', onPress: () => setNotice(undefined) }}
        duration={2500}
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
    paddingBottom: Spacing.xxxl,
  },
  state: {
    alignItems: 'center',
    gap: Spacing.lg,
    justifyContent: 'center',
    minHeight: 240,
  },
  retry: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
  player: {
    width: '100%',
    aspectRatio: 16 / 9,
    // NOTE: 媒体背景固定为黑色，不随主题变化。
    backgroundColor: '#000000',
  },
  unavailable: {
    justifyContent: 'center',
  },
  poster: {
    position: 'absolute',
    width: '100%',
    height: '100%',
    opacity: 0.35,
  },
  unavailableText: {
    textAlign: 'center',
  },
  metadata: {
    gap: Spacing.md,
    padding: Spacing.lg,
  },
  title: {
    lineHeight: 22,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  actionButton: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
  },
  info: {
    gap: Spacing.sm,
  },
});
