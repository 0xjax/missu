import { useEffect, useState, type ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const DURATION = 180;
const LIFT = 40;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

type Props = {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
};

/**
 * 底部弹层：遮罩淡入 + 卡片上滑，全部由这里控制。
 * NOTE: 不用 RN Modal 的 animationType——它会把遮罩和卡片一起滑，观感是整块黑幕在动；
 * 也不要系统 BottomSheet，卡片高度、关闭时机、内容滚动都会交给别人的默认值。
 * NOTE: 关闭要先播完退场动画再卸载，因此内部 mounted 与外部 visible 拆开；Modal 必须一直渲染到动画结束。
 */
export function Sheet({ visible, onClose, title, children }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [mounted, setMounted] = useState(visible);
  const progress = useSharedValue(0);

  // NOTE: 打开方向在渲染期同步挂载，否则 Modal 会晚一帧出现（先露一下底层页面再滑上来）；
  // 关闭方向必须等退场动画播完再卸载，只能交给下面的动画回调，因此两条路径不对称。
  if (visible && !mounted) setMounted(true);

  useEffect(() => {
    progress.value = withTiming(visible ? 1 : 0, { duration: DURATION }, (finished) => {
      if (finished && !visible) scheduleOnRN(setMounted, false);
    });
  }, [progress, visible]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const cardStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [{ translateY: (1 - progress.value) * LIFT }],
  }));

  return (
    <Modal animationType="none" onRequestClose={onClose} statusBarTranslucent transparent visible={mounted}>
      <View style={styles.root}>
        <AnimatedPressable
          accessibilityLabel="关闭"
          accessibilityRole="button"
          onPress={onClose}
          style={[styles.backdrop, backdropStyle]}
        />
        <Animated.View
          style={[styles.card, { backgroundColor: theme.backgroundElement, paddingBottom: insets.bottom + Spacing.lg }, cardStyle]}>
          {title ? <ThemedText type="headline">{title}</ThemedText> : null}
          {children}
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    // NOTE: 遮罩用纯黑+透明度，不跟主题走：浅色模式下也需要压暗背景。
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
  },
  card: {
    borderTopLeftRadius: Radius.lg,
    borderTopRightRadius: Radius.lg,
    borderCurve: 'continuous',
    gap: Spacing.md,
    maxHeight: '80%',
    padding: Spacing.lg,
    paddingTop: Spacing.xl,
  },
});
