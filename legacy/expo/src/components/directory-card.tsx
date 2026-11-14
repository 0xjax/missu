import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter, type Href } from 'expo-router';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import type { DirectoryItem } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function DirectoryCard({ item, columns = 2 }: { item: DirectoryItem; columns?: number }) {
  const router = useRouter();
  const theme = useTheme();
  const windowWidth = useWindowDimensions().width;
  // NOTE: 内容左右留白各 16dp，列间距 12dp；列数变化时按同一公式重算卡片宽度。
  const width = (windowWidth - Spacing.lg * 2 - Spacing.md * (columns - 1)) / columns;

  return (
    <Pressable
      accessibilityLabel={item.name}
      accessibilityRole="button"
      onPress={() => router.push({ pathname: '/latest', params: { source: item.path, title: item.name, query: '' } } as unknown as Href)}
      style={({ pressed }) => [styles.card, { width, backgroundColor: theme.backgroundElement }, pressed && styles.pressed]}>
      <View style={styles.media}>
        {item.imageUrl ? (
          <Image source={{ uri: item.imageUrl }} style={styles.image} contentFit="cover" recyclingKey={item.id} transition={200} />
        ) : (
          <View style={[styles.placeholder, { backgroundColor: theme.backgroundSelected }]}>
            <Ionicons color={theme.textSecondary} name="person" size={26} />
          </View>
        )}
      </View>
      <View style={[styles.content, { padding: columns >= 3 ? Spacing.sm : Spacing.md }]}>
        <ThemedText type="smallBold" numberOfLines={2}>{item.name}</ThemedText>
        {item.subtitle && (
          <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
            {item.subtitle}
          </ThemedText>
        )}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.lg,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  pressed: {
    opacity: 0.7,
  },
  media: {
    width: '100%',
    aspectRatio: 4 / 5,
  },
  image: {
    width: '100%',
    height: '100%',
  },
  placeholder: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
  },
  content: {
    gap: Spacing.xs,
    padding: Spacing.md,
  },
});
