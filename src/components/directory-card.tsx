import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import type { DirectoryItem } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function DirectoryCard({ item }: { item: DirectoryItem }) {
  const router = useRouter();
  const theme = useTheme();
  const windowWidth = useWindowDimensions().width;
  const width = (windowWidth - 44) / 2;

  return (
    <Pressable
      accessibilityLabel={item.name}
      accessibilityRole="button"
      onPress={() => router.push({ pathname: '/latest', params: { source: item.path, title: item.name } })}
      style={({ pressed }) => [styles.card, { width, backgroundColor: theme.backgroundElement }, pressed && styles.pressed]}>
      {item.imageUrl && <Image source={{ uri: item.imageUrl }} style={styles.image} contentFit="cover" transition={200} />}
      <View style={styles.content}>
        <ThemedText type="smallBold" numberOfLines={2}>{item.name}</ThemedText>
        {item.subtitle && <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>{item.subtitle}</ThemedText>}
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
  image: {
    width: '100%',
    aspectRatio: 4 / 5,
  },
  content: {
    gap: Spacing.xs,
    padding: Spacing.md,
  },
});
