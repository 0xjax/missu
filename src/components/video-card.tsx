import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import type { VideoSummary } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function VideoCard({ video, width }: { video: VideoSummary; width?: number }) {
  const theme = useTheme();
  const router = useRouter();
  const windowWidth = useWindowDimensions().width;
  const cardWidth = width ?? (windowWidth - 44) / 2;

  return (
    <Pressable
      accessibilityLabel={video.title}
      accessibilityRole="button"
      onPress={() => router.push({ pathname: '/video/[id]', params: { id: video.id } })}
      style={({ pressed }) => [styles.card, { width: cardWidth }, pressed && styles.pressed]}>
      <View style={[styles.imageContainer, { backgroundColor: theme.backgroundElement }]}>
        {video.thumbnailUrl && (
          <Image source={{ uri: video.thumbnailUrl }} style={styles.image} contentFit="cover" recyclingKey={video.id} transition={200} />
        )}
        {video.duration && (
          <View style={styles.durationBadge}>
            <ThemedText style={styles.duration}>{video.duration}</ThemedText>
          </View>
        )}
      </View>
      <ThemedText type="small" numberOfLines={2} style={styles.title}>
        {video.title}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    minWidth: 0,
  },
  pressed: {
    opacity: 0.7,
  },
  imageContainer: {
    width: '100%',
    aspectRatio: 16 / 9,
    borderRadius: Radius.md,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  image: {
    width: '100%',
    height: '100%',
  },
  durationBadge: {
    position: 'absolute',
    right: 6,
    bottom: 6,
    borderRadius: 5,
    paddingHorizontal: 6,
    paddingVertical: 2,
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
  },
  duration: {
    // NOTE: 封面遮罩上的固定白字，不随主题变化。
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 16,
  },
  title: {
    marginTop: Spacing.sm,
  },
});
