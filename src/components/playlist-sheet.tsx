import { BottomSheet, Button, Checkbox, Column, Host, Row, ScrollView, Text } from '@expo/ui';
import { useEffect, useRef, useState } from 'react';

import { getVideoPlaylists, isAbortError, setPlaylistMembership, type VideoPlaylist } from '@/api/missav';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  dvdId?: string;
  open: boolean;
  onClose: () => void;
  onOpenPlaylists: () => void;
};

// NOTE: Compose 的 Checkbox 自带 48dp 触控尺寸，行高按此估算；超过 6 行改由 ScrollView 滚动。
const ROW_HEIGHT = 48;
const MAX_LIST_HEIGHT = ROW_HEIGHT * 6;

/**
 * 详情页片单面板：列出当前影片可加入的片单，点行或勾选框即加入/移出。
 * NOTE: 走 @expo/ui 的原生 ModalBottomSheet，与女优筛选用同一套浮层；seedColor 让 Compose 控件跟随主题 accent。
 */
export function PlaylistSheet({ dvdId, open, onClose, onOpenPlaylists }: Props) {
  const theme = useTheme();
  const [playlists, setPlaylists] = useState<VideoPlaylist[]>();
  const [failure, setFailure] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  // NOTE: 行点击与勾选框回调是同一次点击上的两条路径，用同步 ref 去重，避免一次点击切两次。
  const busy = useRef(false);

  useEffect(() => {
    if (!open || !dvdId) return;
    const controller = new AbortController();
    let active = true;
    getVideoPlaylists(dvdId, controller.signal).then((result) => {
      if (!active) return;
      // NOTE: 成功后要清掉上一次失败的文案，否则失败提示会和列表同时出现。
      setFailure(undefined);
      setPlaylists(result);
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
    setActionError(undefined);
    onClose();
  };

  const retry = () => {
    setPlaylists(undefined);
    setFailure(undefined);
    setActionError(undefined);
    setAttempt((value) => value + 1);
  };

  // NOTE: 成功不弹提示——勾选框本身就是结果；失败在面板内提示（Snackbar 会被浮层遮住）。
  const toggle = async (playlist: VideoPlaylist) => {
    if (!dvdId || busy.current) return;
    busy.current = true;
    const nextAdded = !playlist.added;
    setActionError(undefined);
    setPlaylists((current) => current?.map((item) => item.key === playlist.key ? { ...item, added: nextAdded } : item));
    try {
      await setPlaylistMembership(dvdId, playlist.key, nextAdded);
    } catch (reason) {
      setPlaylists((current) => current?.map((item) => item.key === playlist.key ? { ...item, added: playlist.added } : item));
      setActionError(reason instanceof Error ? reason.message : '片单操作失败');
    } finally {
      busy.current = false;
    }
  };

  return (
    <Host colorScheme="dark" seedColor={theme.accent}>
      <BottomSheet
        containerColor={theme.backgroundElement}
        contentColor={theme.text}
        contentPadding={0}
        isPresented={open}
        onDismiss={close}>
        <Column spacing={12} style={{ padding: 16 }}>
          <Text textStyle={{ fontSize: 18, fontWeight: '600' }}>加入片单</Text>
          {failure && <Text textStyle={{ color: theme.textSecondary, fontSize: 14 }}>{failure}</Text>}
          {failure && <Button label="重试" onPress={retry} variant="text" />}
          {actionError && <Text textStyle={{ color: theme.danger, fontSize: 14 }}>{actionError}</Text>}
          {!playlists && !failure && (
            <Text textStyle={{ color: theme.textSecondary, fontSize: 14 }}>加载中…</Text>
          )}
          {playlists && playlists.length === 0 && (
            <Text textStyle={{ color: theme.textSecondary, fontSize: 14 }}>还没有片单，先去「我的片单」创建</Text>
          )}
          {playlists && playlists.length > 0 && (
            <ScrollView style={{ height: Math.min(playlists.length * ROW_HEIGHT, MAX_LIST_HEIGHT) }}>
              <Column>
                {playlists.map((playlist) => (
                  <Row alignment="center" key={playlist.key} onPress={() => toggle(playlist)} spacing={8}>
                    <Checkbox onValueChange={() => toggle(playlist)} value={playlist.added} />
                    <Text textStyle={{ color: playlist.added ? theme.accent : theme.text, fontSize: 14 }}>
                      {playlist.name}
                    </Text>
                  </Row>
                ))}
              </Column>
            </ScrollView>
          )}
          <Button label="查看我的片单" onPress={onOpenPlaylists} variant="text" />
        </Column>
      </BottomSheet>
    </Host>
  );
}
