import { useEffect, useRef } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import Animated, { FadeInDown, FadeOutDown } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  visible: boolean;
  message?: string;
  actionLabel?: string;
  /** 自动消失时间，单位毫秒。 */
  duration?: number;
  onAction: () => void;
  onDismiss: () => void;
};

/** 底部提示条：悬浮在内容之上，倒计时后自动消失。 */
export function Toast({ visible, message, actionLabel, duration = 3000, onAction, onDismiss }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  // NOTE: 调用方普遍传内联箭头函数，直接进 deps 会让定时器每次父组件渲染都重置；用 ref 取最新值。
  const dismiss = useRef(onDismiss);

  useEffect(() => {
    dismiss.current = onDismiss;
  }, [onDismiss]);

  useEffect(() => {
    if (!visible) return;
    const timer = setTimeout(() => dismiss.current(), duration);
    return () => clearTimeout(timer);
  }, [duration, message, visible]);

  if (!visible || !message) return null;

  return (
    <Animated.View
      accessibilityLiveRegion="polite"
      entering={FadeInDown.duration(160)}
      exiting={FadeOutDown.duration(160)}
      style={[styles.toast, { backgroundColor: theme.backgroundSelected, bottom: insets.bottom + Spacing.lg }]}>
      <ThemedText style={styles.message}>{message}</ThemedText>
      {actionLabel ? (
        <Pressable accessibilityRole="button" hitSlop={8} onPress={onAction}>
          <ThemedText type="smallBold" style={{ color: theme.accent }}>{actionLabel}</ThemedText>
        </Pressable>
      ) : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  toast: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    flexDirection: 'row',
    gap: Spacing.lg,
    left: Spacing.lg,
    minHeight: 48,
    paddingHorizontal: Spacing.lg,
    position: 'absolute',
    right: Spacing.lg,
  },
  message: {
    flex: 1,
    fontSize: 14,
    lineHeight: 20,
  },
});
