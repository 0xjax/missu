import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * TODO: 片单功能待适配（源站 /playlists），当前只占位，避免入口缺失。
 */
export default function PlaylistsScreen() {
  const theme = useTheme();

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <View style={styles.empty}>
        <ThemedText themeColor="textSecondary">片单功能尚未接入</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">将在后续版本支持查看与管理云端片单。</ThemedText>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  empty: {
    alignItems: 'center',
    gap: Spacing.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.huge,
  },
});
