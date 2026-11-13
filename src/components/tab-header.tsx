import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * tab 页的标题栏，自绘。
 *
 * NOTE: tab 里不再嵌原生 Stack。那层原生头部的高度是原生侧异步回报的，切 tab 时内容先按 0
 * 布局再整体下移，表现就是「抖一下」；而它又只为了一个标题栏。这里用 RN 视图自己画：顶部
 * 安全区 + 大标题，布局从第一帧起就是确定的。
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
