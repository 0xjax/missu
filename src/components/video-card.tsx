import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';

import type { VideoSummary } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// NOTE: 角标文案来自卡片 HTML；颜色只按语义分档，不做标签白名单。
const badgeBackground = (label: string) => {
  if (label.includes('字幕')) return 'rgba(153, 27, 27, 0.85)';
  if (label.includes('碼') || label.includes('码')) return 'rgba(30, 64, 175, 0.85)';
  return 'rgba(31, 41, 55, 0.85)';
};

export function VideoCard({ video, width }: { video: VideoSummary; width?: number }) {
  const theme = useTheme();
  const router = useRouter();
  const windowWidth = useWindowDimensions().width;
  const cardWidth = width ?? (windowWidth - 44) / 2;

  return (
    <Pressable
      accessibilityLabel={video.title}
      accessibilityRole="button"
      onPress={() => router.push({
        pathname: '/video/[id]',
        params: video.badges?.length
          ? { id: video.id, badges: video.badges.join('|') }
          : { id: video.id },
      })}
      style={({ pressed }) => [styles.card, { width: cardWidth }, pressed && styles.pressed]}>
      <View style={[styles.imageContainer, { backgroundColor: theme.backgroundElement }]}>
        {video.thumbnailUrl && (
          <Image source={{ uri: video.thumbnailUrl }} style={styles.image} contentFit="cover" recyclingKey={video.id} transition={200} />
        )}
        {video.badges && video.badges.length > 0 && (
          <View style={styles.badgeRow}>
            {video.badges.map((badge) => (
              <View key={badge} style={[styles.coverBadge, { backgroundColor: badgeBackground(badge) }]}>
                <ThemedText style={styles.coverText}>{badge}</ThemedText>
              </View>
            ))}
          </View>
        )}
        {video.duration && (
          <View style={styles.durationBadge}>
            <ThemedText style={styles.coverText}>{video.duration}</ThemedText>
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
  badgeRow: {
    position: 'absolute',
    left: 6,
    bottom: 6,
    flexDirection: 'row',
    gap: 4,
  },
  coverBadge: {
    borderRadius: 5,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  coverText: {
    // NOTE: 封面遮罩上的固定白字，不随主题变化。
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 16,
  },
  title: {
    marginTop: Spacing.sm,
  },
});
