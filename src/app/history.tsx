import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppNav } from '@/components/app-nav';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { clearHistory, getHistory, type HistoryEntry } from '@/storage/history';

export default function HistoryScreen() {
  const theme = useTheme();
  const [videos, setVideos] = useState<HistoryEntry[]>([]);

  useFocusEffect(useCallback(() => {
    let active = true;
    getHistory().then((result) => {
      if (active) setVideos(result);
    });
    return () => {
      active = false;
    };
  }, []));

  const confirmClear = () => {
    Alert.alert('清空观看历史', '此操作只影响本机记录，且无法撤销。', [
      { text: '取消', style: 'cancel' },
      {
        text: '清空',
        style: 'destructive',
        onPress: () => {
          void clearHistory().then(() => setVideos([]));
        },
      },
    ]);
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <FlatList
        contentContainerStyle={styles.content}
        columnWrapperStyle={styles.row}
        data={videos}
        keyExtractor={(video) => video.id}
        ListEmptyComponent={(
          <View style={styles.empty}>
            <ThemedText themeColor="textSecondary">暂无观看历史，无需登录也会保存在本机。</ThemedText>
          </View>
        )}
        ListHeaderComponent={(
          <View style={styles.header}>
            <ThemedText type="subtitle">观看历史</ThemedText>
            <AppNav current="history" />
            {videos.length > 0 && (
              <Pressable accessibilityRole="button" onPress={confirmClear} style={[styles.clear, { backgroundColor: theme.backgroundElement }]}>
                <ThemedText type="smallBold">清空历史</ThemedText>
              </Pressable>
            )}
          </View>
        )}
        numColumns={2}
        renderItem={({ item }) => <VideoCard video={item} />}
      />
    </SafeAreaView>
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
  header: {
    alignItems: 'flex-start',
    gap: Spacing.lg,
    marginBottom: Spacing.xl,
  },
  clear: {
    borderRadius: Radius.sm,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  empty: {
    paddingVertical: Spacing.huge,
  },
});
