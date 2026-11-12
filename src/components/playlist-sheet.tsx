import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { getVideoPlaylists, isAbortError, setPlaylistMembership, type VideoPlaylist } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  dvdId?: string;
  open: boolean;
  onClose: () => void;
  onOpenPlaylists: () => void;
  onNotice: (message: string) => void;
};

/**
 * 详情页片单面板：列出当前影片可加入的片单，点行即加入/移出。
 * NOTE: 选中态用「常驻圆点 + accent 文字」表达，不加勾选符号——勾选会撑动行宽（见 DESIGN.md）。
 */
export function PlaylistSheet({ dvdId, open, onClose, onOpenPlaylists, onNotice }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [playlists, setPlaylists] = useState<VideoPlaylist[]>();
  const [failure, setFailure] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const [pendingKey, setPendingKey] = useState<string>();

  useEffect(() => {
    if (!open || !dvdId) return;
    const controller = new AbortController();
    let active = true;
    getVideoPlaylists(dvdId, controller.signal).then((result) => {
      if (active) setPlaylists(result);
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      setFailure(reason instanceof Error ? reason.message : '片单加载失败');
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [attempt, dvdId, open]);

  // NOTE: 关闭时清空，下次打开重新拉取；避免把上一次的结果或错误带到新一次打开。
  const close = () => {
    setPlaylists(undefined);
    setFailure(undefined);
    setPendingKey(undefined);
    onClose();
  };

  const retry = () => {
    setPlaylists(undefined);
    setFailure(undefined);
    setAttempt((value) => value + 1);
  };

  const toggle = async (playlist: VideoPlaylist) => {
    if (!dvdId || pendingKey) return;
    const nextAdded = !playlist.added;
    setPendingKey(playlist.key);
    setPlaylists((current) => current?.map((item) => item.key === playlist.key ? { ...item, added: nextAdded } : item));
    try {
      await setPlaylistMembership(dvdId, playlist.key, nextAdded);
      onNotice(nextAdded ? `已加入「${playlist.name}」` : `已从「${playlist.name}」移除`);
    } catch (reason) {
      setPlaylists((current) => current?.map((item) => item.key === playlist.key ? { ...item, added: playlist.added } : item));
      onNotice(reason instanceof Error ? reason.message : '片单操作失败');
    } finally {
      setPendingKey(undefined);
    }
  };

  // NOTE: 默认 Modal 窗口会被系统导航栏裁掉一截，底部露出未遮罩的页面；两个 translucent 让遮罩铺满，再由 insets 补内边距。
  return (
    <Modal
      animationType="fade"
      navigationBarTranslucent
      onRequestClose={close}
      statusBarTranslucent
      transparent
      visible={open}>
      <View style={styles.container}>
        <Pressable accessibilityLabel="关闭" accessibilityRole="button" onPress={close} style={styles.backdrop} />
        <View style={[styles.panel, { backgroundColor: theme.backgroundElement, paddingBottom: Spacing.xl + insets.bottom }]}>
          <View style={[styles.handle, { backgroundColor: theme.backgroundSelected }]} />
          <ThemedText type="headline">加入片单</ThemedText>
          {failure && (
            <View style={styles.state}>
              <ThemedText selectable themeColor="textSecondary" type="small">{failure}</ThemedText>
              <Pressable accessibilityRole="button" onPress={retry}>
                <ThemedText type="smallBold">重试</ThemedText>
              </Pressable>
            </View>
          )}
          {!playlists && !failure && <ActivityIndicator color={theme.text} style={styles.state} />}
          {playlists?.length === 0 && (
            <View style={styles.state}>
              <ThemedText themeColor="textSecondary" type="small">还没有片单，先去「我的片单」创建</ThemedText>
            </View>
          )}
          {playlists && playlists.length > 0 && (
            <ScrollView style={styles.list}>
              {playlists.map((playlist) => (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ selected: playlist.added, busy: pendingKey === playlist.key }}
                  disabled={Boolean(pendingKey)}
                  key={playlist.key}
                  onPress={() => toggle(playlist)}
                  style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
                  <View
                    style={[
                      styles.marker,
                      { backgroundColor: playlist.added ? theme.accent : 'transparent' },
                    ]}
                  />
                  <ThemedText themeColor={playlist.added ? 'accent' : 'text'} type="small">
                    {playlist.name}
                  </ThemedText>
                </Pressable>
              ))}
            </ScrollView>
          )}
          <Pressable accessibilityRole="button" onPress={onOpenPlaylists} style={styles.footer}>
            <ThemedText themeColor="textSecondary" type="small">查看我的片单</ThemedText>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    backgroundColor: 'rgba(0, 0, 0, 0.55)',
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  panel: {
    borderTopLeftRadius: Radius.lg,
    borderTopRightRadius: Radius.lg,
    gap: Spacing.md,
    maxHeight: '70%',
    padding: Spacing.lg,
  },
  handle: {
    alignSelf: 'center',
    borderRadius: Radius.full,
    height: 4,
    width: 32,
  },
  state: {
    alignItems: 'center',
    gap: Spacing.sm,
    justifyContent: 'center',
    minHeight: 96,
  },
  list: {
    flexShrink: 1,
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    minHeight: 44,
  },
  marker: {
    borderRadius: Radius.full,
    height: 6,
    width: 6,
  },
  pressed: {
    opacity: 0.6,
  },
  footer: {
    justifyContent: 'center',
    minHeight: 44,
  },
});
