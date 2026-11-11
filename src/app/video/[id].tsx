import { Image } from 'expo-image';
import { Stack, useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { Snackbar } from 'react-native-paper';

import { getVideoDetail, isAbortError, setVideoSaved, type VideoDetail } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { VideoPlayer } from '@/components/video-player';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { addToHistory } from '@/storage/history';
import { useAuth } from '@/stores/auth';
import { errorHaptic, successHaptic } from '@/utils/haptics';

export default function VideoDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();
  const { user } = useAuth();
  const [result, setResult] = useState<{ id: string; detail?: VideoDetail; error?: string }>();
  const [attempt, setAttempt] = useState(0);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string>();
  const detail = result?.id === id ? result.detail : undefined;
  const error = result?.id === id ? result.error : undefined;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    getVideoDetail(id, controller.signal).then((nextDetail) => {
      if (!active) return;
      setResult({ id, detail: nextDetail });
      void addToHistory(nextDetail);
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      setResult({ id, error: reason instanceof Error ? reason.message : '加载失败，请稍后重试' });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [attempt, id]);

  const toggleSaved = async () => {
    if (!detail?.internalId) return;
    if (!user) {
      router.push('/account' as Href);
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
              <ThemedText selectable type="small" themeColor="textSecondary">
                {[detail.releaseDate, detail.duration, detail.actors.join('、')].filter(Boolean).join(' · ')}
              </ThemedText>
              {detail.internalId && (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ busy: saving, disabled: saving }}
                  disabled={saving}
                  onPress={toggleSaved}
                  style={[styles.saveButton, { backgroundColor: detail.saved ? theme.accent : theme.backgroundElement }]}>
                  <ThemedText type="smallBold" style={detail.saved ? { color: theme.onAccent } : undefined}>
                    {saving ? '提交中…' : detail.saved ? '已收藏' : user ? '收藏' : '登录后收藏'}
                  </ThemedText>
                </Pressable>
              )}
              {detail.description && <ThemedText selectable type="small">{detail.description}</ThemedText>}
            </View>
          </>
        )}
      </ScrollView>
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
  saveButton: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
  },
});
