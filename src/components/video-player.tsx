/* oxlint-disable react/refs, react/immutability -- Gesture 回调和 expo-video 命令式播放器属性需要可变引用。 */
import { useVideoPlayer, VideoView } from 'expo-video';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { ThemedText } from '@/components/themed-text';

function formatTime(value: number): string {
  const seconds = Math.max(0, Math.floor(value));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
    : `${minutes}:${String(remainder).padStart(2, '0')}`;
}

export function VideoPlayer({ url }: { url: string }) {
  const player = useVideoPlayer({
    uri: url,
    headers: { Referer: 'https://missav.ai/' },
  });
  const videoRef = useRef<VideoView | null>(null);
  const windowWidth = useWindowDimensions().width;
  const [failed, setFailed] = useState(false);
  const [hint, setHint] = useState<string>();
  const hintTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seekOrigin = useSharedValue(0);
  const seekPreview = useSharedValue(0);
  const seekDuration = useSharedValue(0);
  const reportedSecond = useSharedValue(-1);
  const panActive = useSharedValue(false);
  const wasPlaying = useRef(false);
  const previousRate = useRef(1);

  useEffect(() => {
    const subscription = player.addListener('statusChange', ({ status }) => {
      if (status === 'error') setFailed(true);
      if (status === 'readyToPlay') setFailed(false);
    });
    return () => subscription.remove();
  }, [player]);

  useEffect(() => () => {
    if (hintTimer.current) clearTimeout(hintTimer.current);
  }, []);

  const showHint = useCallback((value: string, persistent = false) => {
    if (hintTimer.current) clearTimeout(hintTimer.current);
    setHint(value);
    if (!persistent) hintTimer.current = setTimeout(() => setHint(undefined), 900);
  }, []);

  const seekBy = useCallback((seconds: number) => {
    player.seekBy(seconds);
    showHint(seconds > 0 ? `+${seconds} 秒` : `${seconds} 秒`);
  }, [player, showHint]);

  const beginSeek = useCallback(() => {
    if (!panActive.get()) return;
    const duration = Number.isFinite(player.duration) ? player.duration : 0;
    if (duration <= 0) return;
    const current = player.currentTime;
    seekOrigin.set(current);
    seekPreview.set(current);
    seekDuration.set(duration);
    reportedSecond.set(Math.floor(current));
    wasPlaying.current = player.playing;
    if (player.playing) player.pause();
    if (hintTimer.current) clearTimeout(hintTimer.current);
    setHint(`${formatTime(current)} / ${formatTime(duration)}`);
  }, [panActive, player, reportedSecond, seekDuration, seekOrigin, seekPreview]);

  const showSeekHint = useCallback((next: number, duration: number) => {
    setHint(`${formatTime(next)} / ${formatTime(duration)}`);
  }, []);

  const finishSeek = useCallback((target: number) => {
    player.seekBy(target - player.currentTime);
    if (wasPlaying.current) player.play();
    setHint(undefined);
  }, [player]);

  const startSpeed = useCallback(() => {
    previousRate.current = player.playbackRate || 1;
    player.playbackRate = previousRate.current * 2;
    showHint(`${player.playbackRate}x`, true);
  }, [player, showHint]);

  const finishSpeed = useCallback(() => {
    player.playbackRate = previousRate.current;
    setHint(undefined);
  }, [player]);

  const doubleTap = Gesture.Tap().numberOfTaps(2).onEnd((event) => {
    scheduleOnRN(seekBy, event.x < windowWidth / 2 ? -10 : 10);
  });
  const longPress = Gesture.LongPress().minDuration(300).onStart(() => {
    scheduleOnRN(startSpeed);
  }).onFinalize(() => {
    scheduleOnRN(finishSpeed);
  });
  const pan = Gesture.Pan().activeOffsetX([-12, 12]).failOffsetY([-12, 12]).onStart(() => {
    panActive.set(true);
    scheduleOnRN(beginSeek);
  }).onUpdate((event) => {
    const duration = seekDuration.get();
    if (duration <= 0) return;
    const next = Math.min(Math.max(seekOrigin.get() + (event.translationX / windowWidth) * duration, 0), duration);
    seekPreview.set(next);
    const second = Math.floor(next);
    if (second !== reportedSecond.get()) {
      reportedSecond.set(second);
      scheduleOnRN(showSeekHint, next, duration);
    }
  }).onFinalize(() => {
    panActive.set(false);
    if (seekDuration.get() > 0) {
      scheduleOnRN(finishSeek, seekPreview.get());
    }
  });

  return (
    <View style={styles.player}>
      <GestureDetector gesture={Gesture.Exclusive(doubleTap, longPress, pan)}>
        <View style={styles.video}>
          <VideoView
            contentFit="contain"
            fullscreenOptions={{ enable: true, orientation: 'landscape' }}
            nativeControls
            player={player}
            ref={videoRef}
            style={styles.video}
          />
          {hint && (
            <View pointerEvents="none" style={styles.hint}>
              <ThemedText type="smallBold" style={styles.controlText}>{hint}</ThemedText>
            </View>
          )}
          {failed && (
            <View style={styles.error}>
              <ThemedText type="small">播放失败，请稍后重试</ThemedText>
            </View>
          )}
        </View>
      </GestureDetector>
      <Pressable accessibilityLabel="全屏播放" accessibilityRole="button" onPress={() => void videoRef.current?.enterFullscreen()} style={styles.fullscreen}>
        <ThemedText type="smallBold" style={styles.controlText}>全屏</ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  player: {
    width: '100%',
    aspectRatio: 16 / 9,
    // NOTE: 媒体背景固定为黑色，不随主题变化。
    backgroundColor: '#000000',
  },
  video: {
    width: '100%',
    height: '100%',
  },
  fullscreen: {
    position: 'absolute',
    top: 10,
    right: 10,
    borderRadius: 7,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: 'rgba(0, 0, 0, 0.55)',
  },
  controlText: {
    // NOTE: 视频控件覆盖在黑色画面上，固定使用白字。
    color: '#ffffff',
  },
  hint: {
    position: 'absolute',
    alignSelf: 'center',
    top: '40%',
    borderRadius: 9,
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.72)',
  },
  error: {
    position: 'absolute',
    inset: 0,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
  },
});
