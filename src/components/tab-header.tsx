import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * 每个 tab 页自己的标题栏。
 *
 * NOTE: tab 里不再嵌原生 Stack（那样会让 tab 宿主在转场中被按内容高度测量、底部栏错位），顶部安全区和标题
 * 由这个组件负责，避免转场时原生 Stack 把内容高度传给 tab 宿主、导致底部栏错位。
 */
export function TabHeader({ title }: { title: string }) {
  const theme = useTheme();

  return (
    <SafeAreaView edges={['top']} style={{ backgroundColor: theme.background }}>
      <View style={styles.bar}>
        <ThemedText type="subtitle">{title}</ThemedText>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  bar: {
    paddingHorizontal: Spacing.lg,
    paddingTop: Spacing.sm,
    paddingBottom: Spacing.xs,
  },
});
