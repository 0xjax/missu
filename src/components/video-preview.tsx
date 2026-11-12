import { Image } from 'expo-image';
import { useVideoPlayer, VideoView } from 'expo-video';
import { useEffect, useState } from 'react';
import { Modal, Pressable, StatusBar, StyleSheet, useWindowDimensions, View } from 'react-native';

import type { VideoSummary } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';

// NOTE: 原站卡片自带预览片，与封面同目录、固定名 preview.mp4；由封面地址推导，长按不产生任何额外请求。
const COVER_PATTERN = /\/cover[^/]*\.(?:jpe?g|png|webp)$/i;

function previewUrl(video: VideoSummary): string | undefined {
  return video.thumbnailUrl?.replace(COVER_PATTERN, '/preview.mp4');
}

function PreviewVideo({ url }: { url: string }) {
  const player = useVideoPlayer({ uri: url, headers: { Referer: 'https://missav.ai/' } }, (instance) => {
    // NOTE: 与网页预览一致：静音、循环、自动播放。
    instance.muted = true;
    instance.loop = true;
    instance.play();
  });
  const [ready, setReady] = useState(false);

  // NOTE: 没有预览片的影片会加载失败，此时保留封面，不露出黑框。
  useEffect(() => player.addListener('statusChange', ({ status }) => {
    if (status === 'readyToPlay') setReady(true);
  }).remove, [player]);

  if (!ready) return null;
  return <VideoView contentFit="cover" nativeControls={false} player={player} style={StyleSheet.absoluteFill} />;
}

export function VideoPreviewOverlay({ video, onClose }: { video: VideoSummary; onClose: () => void }) {
  const { width } = useWindowDimensions();
  const url = previewUrl(video);
  const cardWidth = Math.min(width - Spacing.xl * 2, 480);

  return (
    <Modal animationType="fade" onRequestClose={onClose} statusBarTranslucent transparent visible>
      <StatusBar barStyle="light-content" />
      <Pressable accessibilityLabel="关闭预览" onPress={onClose} style={styles.backdrop}>
        {/* NOTE: 内层 Pressable 吞掉点击，避免点视频时连带关闭预览。 */}
        <Pressable onPress={() => {}} style={[styles.card, { width: cardWidth }]}>
          <View style={styles.media}>
            {video.thumbnailUrl && (
              <Image contentFit="cover" source={{ uri: video.thumbnailUrl }} style={StyleSheet.absoluteFill} />
            )}
            {url && <PreviewVideo url={url} />}
          </View>
          <ThemedText numberOfLines={2} style={styles.title}>{video.title}</ThemedText>
          <ThemedText style={styles.hint}>点击空白处关闭</ThemedText>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.88)',
    flex: 1,
    justifyContent: 'center',
    padding: Spacing.xl,
  },
  card: {
    gap: Spacing.sm,
  },
  media: {
    aspectRatio: 16 / 9,
    backgroundColor: '#000000',
    borderRadius: Radius.lg,
    borderCurve: 'continuous',
    overflow: 'hidden',
    width: '100%',
  },
  title: {
    color: '#ffffff',
    marginTop: Spacing.xs,
  },
  hint: {
    color: 'rgba(255, 255, 255, 0.6)',
    fontSize: 12,
  },
});
