/* oxlint-disable react/refs, react/immutability -- Gesture 回调和 expo-video 命令式播放器属性需要可变引用。 */
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import * as ScreenOrientation from 'expo-screen-orientation';
import { useVideoPlayer, VideoView } from 'expo-video';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, Pressable, StatusBar, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { usePreferences } from '@/stores/preferences';
import { formatTime } from '@/utils/format-time';
import { impactHaptic, selectionHaptic } from '@/utils/haptics';

// NOTE: 与网页播放器下方的快进退档位一致：10 秒 / 1 分钟 / 10 分钟，双向。
const SEEK_BACKWARD = [
  { label: '« 10m', seconds: -600, hint: '-10 分钟' },
  { label: '‹ 1m', seconds: -60, hint: '-1 分钟' },
  { label: '‹ 10s', seconds: -10, hint: '-10 秒' },
] as const;
const SEEK_FORWARD = [
  { label: '10s ›', seconds: 10, hint: '+10 秒' },
  { label: '1m ›', seconds: 60, hint: '+1 分钟' },
  { label: '10m »', seconds: 600, hint: '+10 分钟' },
] as const;

const CONTROLS_TIMEOUT = 3000;
const SEEK_ACCUMULATE_WINDOW = 900;
const HINT_VISIBLE = 700;
const TAP_CHAIN_WINDOW = 350; // 连击窗口：只有同方向点击才叠加

export function VideoPlayer({ url }: { url: string }) {
  const player = useVideoPlayer({
    uri: url,
    headers: { Referer: 'https://missav.ai/' },
  });
  const { autoplay } = usePreferences();
  const theme = useTheme();
  const windowWidth = useWindowDimensions().width;

  const [failed, setFailed] = useState(false);
  const [hint, setHint] = useState<string>();
  const [fullscreen, setFullscreen] = useState(false);
  const [inlineSurfaceKey, setInlineSurfaceKey] = useState(0);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [scrubTime, setScrubTime] = useState<number>();

  const hintTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seekTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seekingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seekingRef = useRef(false);
  const seekState = useRef({ direction: 0, total: 0, last: 0 });
  const tapTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastTap = useRef(0);
  const lastTapDirection = useRef(0);
  const wasPlaying = useRef(false);
  const previousRate = useRef(1);
  const durationRef = useRef(0);
  const currentRef = useRef(0);

  const trackWidth = useSharedValue(0);
  const progress = useSharedValue(0);
  const scrubProgress = useSharedValue(0);
  const scrubbing = useSharedValue(false);
  const controlsOpacity = useSharedValue(1);

  const showHint = useCallback((value: string, persistent = false) => {
    if (hintTimer.current) clearTimeout(hintTimer.current);
    setHint(value);
    if (!persistent) hintTimer.current = setTimeout(() => setHint(undefined), HINT_VISIBLE);
  }, []);

  const revealControls = useCallback((autoHide = true) => {
    setControlsVisible(true);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    if (autoHide && player.playing) {
      hideTimer.current = setTimeout(() => setControlsVisible(false), CONTROLS_TIMEOUT);
    }
  }, [player]);

  const clearHideTimer = useCallback(() => {
    if (hideTimer.current) clearTimeout(hideTimer.current);
  }, []);

  // NOTE: seek 期间播放器会短暂报 paused，这是加载过程，不是用户暂停；这段时间内保持播放图标。
  const markSeeking = useCallback((duration = 600) => {
    seekingRef.current = true;
    if (seekingTimer.current) clearTimeout(seekingTimer.current);
    seekingTimer.current = setTimeout(() => {
      seekingRef.current = false;
    }, duration);
  }, []);

  const seekStep = useCallback((direction: -1 | 1) => {
    const now = Date.now();
    const state = seekState.current;
    if (state.direction === direction && now - state.last < SEEK_ACCUMULATE_WINDOW) {
      state.total += 10;
    } else {
      state.direction = direction;
      state.total = 10;
    }
    state.last = now;

    selectionHaptic();
    markSeeking();
    player.seekBy(direction * 10);
    showHint(`${direction > 0 ? '+' : '-'}${state.total} 秒`);
    // NOTE: 快进后让控件保持可见一段时间，播放/暂停键可以直接点，不用先点一次唤出控件。
    revealControls();
    if (seekTimer.current) clearTimeout(seekTimer.current);
    seekTimer.current = setTimeout(() => {
      state.direction = 0;
      state.total = 0;
    }, SEEK_ACCUMULATE_WINDOW);
  }, [markSeeking, player, revealControls, showHint]);

  const jumpBy = useCallback((seconds: number, text: string) => {
    selectionHaptic();
    markSeeking();
    player.seekBy(seconds);
    showHint(text);
    revealControls();
  }, [markSeeking, player, revealControls, showHint]);

  const togglePlay = useCallback(() => {
    if (player.playing) {
      player.pause();
    } else {
      player.play();
    }
    revealControls();
  }, [player, revealControls]);

  const toggleControls = useCallback(() => {
    if (controlsVisible) {
      clearHideTimer();
      setControlsVisible(false);
    } else {
      revealControls();
    }
  }, [clearHideTimer, controlsVisible, revealControls]);

  // NOTE: 点击链式判定：相邻两次点击可共用一次点击，N 次点击产生 N-1 次快进退。
  // 只有同方向的连击才叠加；换方向视为新的一次点击，不跨方向累计。单击始终只显隐控件。
  const handleTap = useCallback((x: number) => {
    const now = Date.now();
    const direction = x < windowWidth / 2 ? -1 : 1;
    const chained = now - lastTap.current < TAP_CHAIN_WINDOW && lastTapDirection.current === direction;
    lastTap.current = now;
    lastTapDirection.current = direction;

    if (chained) {
      if (tapTimer.current) {
        clearTimeout(tapTimer.current);
        tapTimer.current = null;
      }
      seekStep(direction);
      return;
    }

    if (tapTimer.current) {
      clearTimeout(tapTimer.current);
      tapTimer.current = null;
    }
    tapTimer.current = setTimeout(() => {
      tapTimer.current = null;
      toggleControls();
    }, TAP_CHAIN_WINDOW);
  }, [seekStep, toggleControls, windowWidth]);

  const enterFullscreen = useCallback(() => {
    setFullscreen(true);
    void ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.LANDSCAPE);
    StatusBar.setHidden(true, 'fade');
    revealControls();
  }, [revealControls]);

  const exitFullscreen = useCallback(() => {
    const time = player.currentTime;
    const wasPlaying = player.playing;
    // 重挂 surface 期间播放器会短暂 paused，沿用 seek 的保护，避免暂停图标闪一下。
    markSeeking(900);
    setFullscreen(false);
    void ScreenOrientation.unlockAsync();
    StatusBar.setHidden(false, 'fade');
    revealControls();
    // NOTE: 内联 SurfaceView 被 Modal 覆盖后会停在进入全屏时的旧帧；重挂视图并恢复到原时间点。
    // 重挂会带来约 300–400ms 的画面停顿，这是双视图 Modal 方案的已知取舍；暂停图标已由 markSeeking 屏蔽。
    setInlineSurfaceKey((value) => value + 1);
    setTimeout(() => {
      const delta = time - player.currentTime;
      if (Number.isFinite(delta) && Math.abs(delta) > 0.3) player.seekBy(delta);
      if (wasPlaying) player.play();
    }, 400);
  }, [markSeeking, player, revealControls]);

  const beginScrub = useCallback(() => {
    wasPlaying.current = player.playing;
    if (player.playing) player.pause();
    clearHideTimer();
    setControlsVisible(true);
    setScrubTime(player.currentTime);
  }, [clearHideTimer, player]);

  const previewScrub = useCallback((ratio: number) => {
    setScrubTime(ratio * durationRef.current);
  }, []);

  const commitScrub = useCallback((ratio: number) => {
    const target = ratio * durationRef.current;
    const delta = target - player.currentTime;
    markSeeking();
    if (Number.isFinite(delta)) player.seekBy(delta);
    progress.value = ratio;
    currentRef.current = target;
    setCurrentTime(target);
    setScrubTime(undefined);
    if (wasPlaying.current) player.play();
    revealControls();
  }, [markSeeking, player, progress, revealControls]);

  useEffect(() => {
    const subscription = player.addListener('statusChange', ({ status }) => {
      if (status === 'error') setFailed(true);
      if (status === 'readyToPlay') setFailed(false);
    });
    return () => subscription.remove();
  }, [player]);

  // NOTE: 详情页跳走（点信息块链接、切 tab）时页面不会卸载，播放器会继续出声；失焦即暂停。
  useFocusEffect(useCallback(() => () => {
    player.pause();
  }, [player]));

  useEffect(() => {
    if (!autoplay) return;
    // NOTE: 只自动播放一次；缓冲/拖动后的 readyToPlay 不能把用户暂停的视频重新播放。
    let played = false;
    const subscription = player.addListener('statusChange', ({ status }) => {
      if (played || status !== 'readyToPlay') return;
      played = true;
      player.play();
      subscription.remove();
    });
    if (player.status === 'readyToPlay' && !played) {
      played = true;
      player.play();
      subscription.remove();
    }
    return () => subscription.remove();
  }, [autoplay, player]);

  useEffect(() => {
    player.timeUpdateEventInterval = 0.25;
    const subscription = player.addListener('timeUpdate', ({ currentTime: time }) => {
      currentRef.current = time;
      if (!scrubbing.value && durationRef.current > 0) {
        progress.value = time / durationRef.current;
      }
      setCurrentTime(time);
      const nextDuration = Number.isFinite(player.duration) ? player.duration : 0;
      if (nextDuration !== durationRef.current) {
        durationRef.current = nextDuration;
        setDuration(nextDuration);
      }
    });
    return () => subscription.remove();
  }, [player, progress, scrubbing]);

  useEffect(() => {
    const subscription = player.addListener('playingChange', ({ isPlaying }) => {
      // NOTE: seek 期间的短暂 pause 是加载过程，不更新图标，避免误导性的暂停闪烁。
      if (!isPlaying && seekingRef.current) return;
      setPlaying(isPlaying);
      if (isPlaying) {
        revealControls();
      } else {
        clearHideTimer();
        setControlsVisible(true);
      }
    });
    return () => subscription.remove();
  }, [clearHideTimer, player, revealControls]);

  useEffect(() => {
    StatusBar.setHidden(fullscreen, 'fade');
  }, [fullscreen]);

  useEffect(() => () => {
    if (hintTimer.current) clearTimeout(hintTimer.current);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    if (seekTimer.current) clearTimeout(seekTimer.current);
    if (seekingTimer.current) clearTimeout(seekingTimer.current);
    if (tapTimer.current) clearTimeout(tapTimer.current);
    void ScreenOrientation.unlockAsync();
    StatusBar.setHidden(false, 'fade');
  }, []);

  const startSpeed = useCallback(() => {
    impactHaptic();
    previousRate.current = player.playbackRate || 1;
    player.playbackRate = previousRate.current * 2;
    showHint(`${player.playbackRate}x`, true);
  }, [player, showHint]);

  const finishSpeed = useCallback(() => {
    player.playbackRate = previousRate.current;
    setHint(undefined);
  }, [player]);

  const buildGesture = () => {
    const tap = Gesture.Tap().maxDuration(250).onEnd((event) => {
      scheduleOnRN(handleTap, event.x);
    });
    let speedActive = false;
    const longPress = Gesture.LongPress().minDuration(300).onStart(() => {
      speedActive = true;
      scheduleOnRN(startSpeed);
    }).onFinalize(() => {
      // NOTE: onFinalize 在普通点击的长按失败时也会触发；未激活时不能清掉快进提示。
      if (speedActive) scheduleOnRN(finishSpeed);
    });
    return Gesture.Simultaneous(tap, longPress);
  };

  const buildProgressGesture = () => Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const ratio = Math.min(Math.max(event.x / Math.max(trackWidth.value, 1), 0), 1);
      scrubbing.set(true);
      scrubProgress.set(ratio);
      scheduleOnRN(beginScrub);
    })
    .onUpdate((event) => {
      const ratio = Math.min(Math.max(event.x / Math.max(trackWidth.value, 1), 0), 1);
      scrubProgress.set(ratio);
      scheduleOnRN(previewScrub, ratio);
    })
    .onFinalize(() => {
      scheduleOnRN(commitScrub, scrubProgress.value);
      scrubbing.set(false);
    });

  const fillStyle = useAnimatedStyle(() => ({
    width: (scrubbing.value ? scrubProgress.value : progress.value) * trackWidth.value,
  }));
  const thumbStyle = useAnimatedStyle(() => {
    const ratio = scrubbing.value ? scrubProgress.value : progress.value;
    return { left: Math.max(0, ratio * trackWidth.value - 6) };
  });
  // NOTE: 控件显隐用短渐变，避免快速点击时图标瞬间切换造成闪烁。
  const controlsStyle = useAnimatedStyle(() => ({ opacity: controlsOpacity.value }));
  useEffect(() => {
    controlsOpacity.value = withTiming(controlsVisible ? 1 : 0, { duration: 150 });
  }, [controlsOpacity, controlsVisible]);

  const displayedTime = scrubTime ?? currentTime;
  // NOTE: 内联视图始终挂载；全屏 Modal 再挂一个共享同一 player 的视图。退出时重挂内联视图以刷新 SurfaceView。
  const renderSurface = (isFull: boolean) => (
    <View style={isFull ? styles.fullscreenSurface : styles.player}>
      <GestureDetector gesture={buildGesture()}>
        <View style={styles.fill}>
          <VideoView
            contentFit="contain"
            key={isFull ? 'fullscreen' : `inline-${inlineSurfaceKey}`}
            nativeControls={false}
            player={player}
            style={styles.fill}
          />
        </View>
      </GestureDetector>
      <View pointerEvents="box-none" style={styles.overlay}>
        <Animated.View
          pointerEvents={controlsVisible ? 'box-none' : 'none'}
          style={[styles.fill, controlsStyle]}>
          <View style={[styles.bottomBar, isFull && styles.bottomBarFullscreen]}>
            {/* NOTE: 播放键放底部条左侧而不是画面正中：快进快捷键会唤出控件，居中大按钮会盖住画面。 */}
            <GestureDetector gesture={Gesture.Tap().onEnd(() => scheduleOnRN(togglePlay))}>
              <View accessibilityLabel={playing ? '暂停' : '播放'} accessibilityRole="button" style={styles.playButton}>
                <Ionicons color="#ffffff" name={playing ? 'pause' : 'play'} size={22} />
              </View>
            </GestureDetector>
            <ThemedText style={styles.timeText}>{formatTime(displayedTime)}</ThemedText>
            <GestureDetector gesture={buildProgressGesture()}>
              <View
                onLayout={(event) => {
                  if (isFull === fullscreen) {
                    trackWidth.value = event.nativeEvent.layout.width;
                  }
                }}
                style={styles.progressHit}>
                <View style={styles.track}>
                  <Animated.View style={[styles.trackFill, fillStyle]} />
                  <Animated.View style={[styles.thumb, thumbStyle]} />
                </View>
              </View>
            </GestureDetector>
            <ThemedText style={styles.timeText}>{formatTime(duration)}</ThemedText>
            {/* NOTE: 同上，按钮走 RNGH Tap，避免第一次点击丢失。 */}
            <GestureDetector
              gesture={Gesture.Tap().onEnd(() => scheduleOnRN(isFull ? exitFullscreen : enterFullscreen))}>
              <View accessibilityLabel={isFull ? '退出全屏' : '全屏播放'} accessibilityRole="button" style={styles.fullscreenButton}>
                <Ionicons color="#ffffff" name={isFull ? 'contract' : 'expand'} size={22} />
              </View>
            </GestureDetector>
          </View>
        </Animated.View>
        {hint && (
          // NOTE: 快进/倍速共用同一个居中提示；方向靠文字里的正负号表达，不再分左右两个气泡。
          <View pointerEvents="none" style={styles.hint}>
            <ThemedText type="smallBold" style={styles.hintText}>{hint}</ThemedText>
          </View>
        )}
        {failed && (
          <View style={styles.error}>
            <ThemedText type="small">播放失败，请稍后重试</ThemedText>
          </View>
        )}
      </View>
    </View>
  );

  return (
    <View>
      {renderSurface(false)}
      {!fullscreen && (
        <View style={[styles.seekRow, { backgroundColor: theme.background }]}>
          {[SEEK_BACKWARD, SEEK_FORWARD].map((group, index) => (
            <View key={index} style={styles.seekGroup}>
              {group.map((item) => (
                <Pressable
                  accessibilityLabel={item.hint}
                  accessibilityRole="button"
                  key={item.seconds}
                  onPress={() => jumpBy(item.seconds, item.hint)}
                  style={({ pressed }) => [
                    styles.seekButton,
                    // NOTE: 按下用 accent 填充，比单纯降透明度更容易看清。
                    { backgroundColor: pressed ? theme.accent : theme.backgroundElement },
                  ]}>
                  {({ pressed }) => (
                    <ThemedText style={[styles.seekText, pressed && { color: theme.onAccent }]}>{item.label}</ThemedText>
                  )}
                </Pressable>
              ))}
            </View>
          ))}
        </View>
      )}
      <Modal
        animationType="fade"
        onRequestClose={exitFullscreen}
        presentationStyle="fullScreen"
        statusBarTranslucent
        supportedOrientations={['landscape']}
        visible={fullscreen}>
        <StatusBar hidden />
        {/* NOTE: Modal 是独立窗口，手势需要嵌套 GestureHandlerRootView。 */}
        <GestureHandlerRootView style={styles.fullscreenSurface}>
          {fullscreen && renderSurface(true)}
        </GestureHandlerRootView>
      </Modal>
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
  fullscreenSurface: {
    backgroundColor: '#000000',
    flex: 1,
  },
  fill: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  playButton: {
    alignItems: 'center',
    height: 32,
    justifyContent: 'center',
    width: 32,
  },
  fullscreenButton: {
    padding: 4,
  },
  overlay: {
    alignItems: 'center',
    bottom: 0,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  bottomBar: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.45)',
    bottom: 0,
    flexDirection: 'row',
    gap: Spacing.sm,
    left: 0,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    position: 'absolute',
    right: 0,
  },
  bottomBarFullscreen: {
    paddingBottom: Spacing.xl,
    paddingHorizontal: Spacing.xl,
  },
  timeText: {
    // NOTE: 视频控件覆盖在黑色画面上，固定使用白字。
    color: '#ffffff',
    fontSize: 12,
    fontVariant: ['tabular-nums'],
    lineHeight: 16,
  },
  progressHit: {
    flex: 1,
    height: 24,
    justifyContent: 'center',
  },
  track: {
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 2,
    height: 4,
  },
  trackFill: {
    backgroundColor: '#ffffff',
    borderRadius: 2,
    bottom: 0,
    left: 0,
    position: 'absolute',
    top: 0,
  },
  thumb: {
    backgroundColor: '#ffffff',
    borderRadius: Radius.full,
    height: 12,
    position: 'absolute',
    top: -4,
    width: 12,
  },
  hint: {
    alignSelf: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.55)',
    borderRadius: Radius.sm,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    position: 'absolute',
    top: '38%',
  },
  hintText: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 16,
  },
  error: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    bottom: 0,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  seekRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  seekGroup: {
    flexDirection: 'row',
    gap: 6,
  },
  seekButton: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    height: 36,
    justifyContent: 'center',
    minWidth: 48,
    paddingHorizontal: Spacing.sm,
  },
  seekText: {
    fontSize: 12,
    lineHeight: 16,
  },
});
