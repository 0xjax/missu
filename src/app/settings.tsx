import Constants from 'expo-constants';
import { Alert, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SettingsRow } from '@/components/settings-row';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { clearHistory } from '@/storage/history';
import { clearSearchHistory } from '@/storage/search-history';

const CANCEL = { text: '取消', style: 'cancel' } as const;

export default function SettingsScreen() {
  const theme = useTheme();
  const version = Constants.expoConfig?.version ?? '—';

  const confirmClearSearch = () => {
    Alert.alert('清空搜索历史', '只删除本机保存的搜索记录，且无法撤销。', [
      CANCEL,
      {
        text: '清空',
        style: 'destructive',
        onPress: () => {
          void clearSearchHistory().then(() => Alert.alert('已清空搜索历史'));
        },
      },
    ]);
  };

  const confirmClearHistory = () => {
    Alert.alert('清空观看历史', '只删除本机保存的观看记录，且无法撤销。', [
      CANCEL,
      {
        text: '清空',
        style: 'destructive',
        onPress: () => {
          void clearHistory().then(() => Alert.alert('已清空观看历史'));
        },
      },
    ]);
  };

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <SettingsRow detail="本机搜索记录" label="清空搜索历史" onPress={confirmClearSearch} />
        <SettingsRow detail="本机观看记录" label="清空观看历史" onPress={confirmClearHistory} />
        <SettingsRow detail={`v${version}`} label="版本" />
        <SettingsRow detail="missav.ai · 客户端直连解析" label="数据来源" />
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
});
