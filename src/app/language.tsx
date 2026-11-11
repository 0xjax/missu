import { useRouter } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { SettingsRow } from '@/components/settings-row';
import { CONTENT_LANGUAGES } from '@/constants/languages';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLanguage } from '@/stores/language';

export default function LanguageScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { language, setLanguage } = useLanguage();

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        {CONTENT_LANGUAGES.map((option) => (
          <SettingsRow
            key={option.value}
            label={option.label}
            selected={option.value === language}
            onPress={() => {
              if (option.value === language) return;
              // NOTE: 列表按语言重建请求，这里退回标签页，避免旧语言解析出的路径留在栈里。
              void setLanguage(option.value);
              router.dismissAll();
            }}
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
    paddingVertical: Spacing.sm,
  },
});
