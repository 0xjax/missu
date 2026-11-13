import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import {
  createPlaylist,
  getVideoPlaylists,
  isAbortError,
  setPlaylistMembership,
  type VideoPlaylist,
} from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { UiButton } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Sheet } from '@/components/ui/sheet';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  dvdId?: string;
  open: boolean;
  onClose: () => void;
  onOpenPlaylists: () => void;
};

const ROW_HEIGHT = 48;
const MAX_LIST_HEIGHT = ROW_HEIGHT * 6;

/**
 * 详情页片单面板：列出当前影片可加入的片单，点行或勾选框即加入/移出，也可以就地新建一个片单。
 * NOTE: 行内容超过 6 条改由内部滚动，面板本身高度不跟着条数涨。
 */
export function PlaylistSheet({ dvdId, open, onClose, onOpenPlaylists }: Props) {
  const theme = useTheme();
  const [draftText, setDraftText] = useState('');
  const [playlists, setPlaylists] = useState<VideoPlaylist[]>();
  const [failure, setFailure] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [attempt, setAttempt] = useState(0);
  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
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
    setCreating(false);
    setSubmitting(false);
    onClose();
  };

  const retry = () => {
    setPlaylists(undefined);
    setFailure(undefined);
    setActionError(undefined);
    setAttempt((value) => value + 1);
  };

  // NOTE: 成功不弹提示——勾选框本身就是结果；失败在面板内提示（浮层之外的提示会被遮罩盖住）。
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

  // NOTE: 新建后重新拉一次列表，既刷新勾选状态，也用它验证服务端真的建成功了。
  const submit = async () => {
    if (!dvdId || submitting) return;
    const name = draftText.trim();
    if (!name) {
      setActionError('片单名不能为空');
      return;
    }
    setSubmitting(true);
    setActionError(undefined);
    try {
      await createPlaylist(dvdId, name);
      const next = await getVideoPlaylists(dvdId);
      setPlaylists(next);
      if (!next.some((item) => item.name === name)) throw new Error('创建失败，请换个名字再试');
      setDraftText('');
      setCreating(false);
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : '新建片单失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Sheet onClose={close} title="加入片单" visible={open}>
      {failure && <ThemedText type="small" themeColor="textSecondary">{failure}</ThemedText>}
      {failure && <UiButton label="重试" onPress={retry} variant="text" />}
      {actionError && <ThemedText style={{ color: theme.danger }} type="small">{actionError}</ThemedText>}
      {!playlists && !failure && <ThemedText type="small" themeColor="textSecondary">加载中…</ThemedText>}
      {playlists?.length === 0 && (
        <ThemedText type="small" themeColor="textSecondary">还没有片单，先新建一个</ThemedText>
      )}
      {playlists && playlists.length > 0 && (
        <ScrollView style={{ height: Math.min(playlists.length * ROW_HEIGHT, MAX_LIST_HEIGHT) }}>
          {playlists.map((playlist) => (
            <Pressable key={playlist.key} onPress={() => toggle(playlist)} style={styles.row}>
              <Checkbox accessibilityLabel={playlist.name} onValueChange={() => toggle(playlist)} value={playlist.added} />
              <ThemedText
                numberOfLines={1}
                style={[styles.rowText, playlist.added && { color: theme.accent }]}>
                {playlist.name}
              </ThemedText>
            </Pressable>
          ))}
        </ScrollView>
      )}
      {creating ? (
        <View style={styles.create}>
          <TextInput
            editable={!submitting}
            onChangeText={setDraftText}
            placeholder="片单名"
            placeholderTextColor={theme.textSecondary}
            selectionColor={theme.accent}
            style={[styles.input, { backgroundColor: theme.background, color: theme.text }]}
            value={draftText}
          />
          <View style={styles.actions}>
            <UiButton
              label="取消"
              onPress={() => {
                setDraftText('');
                setCreating(false);
                setActionError(undefined);
              }}
              variant="text"
            />
            <UiButton disabled={submitting} label={submitting ? '创建中…' : '创建'} onPress={submit} />
          </View>
        </View>
      ) : (
        <UiButton label="新建片单" onPress={() => setCreating(true)} variant="text" />
      )}
      <UiButton label="查看我的片单" onPress={onOpenPlaylists} variant="text" />
    </Sheet>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    height: ROW_HEIGHT,
  },
  rowText: {
    flex: 1,
    fontSize: 14,
    lineHeight: 20,
  },
  create: {
    gap: Spacing.sm,
  },
  input: {
    borderRadius: Radius.sm,
    fontSize: 14,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    justifyContent: 'flex-end',
  },
});
