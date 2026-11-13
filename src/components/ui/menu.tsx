import { Ionicons } from '@expo/vector-icons';
import { useRef, useState, type ComponentRef } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const WIDTH = 208;
const ROW_HEIGHT = 44;
const MAX_HEIGHT = 320;

export type MenuItem = { label: string; value: string };

type Props = {
  label: string;
  items: MenuItem[];
  value?: string;
  onSelect: (value: string) => void;
};

/**
 * 工具栏下拉菜单：触发器 + 贴住触发器的浮层，选中态只改文字颜色。
 * NOTE: 不用系统 DropdownMenu / Paper Menu——浮层是从哪个点展开、多宽、能不能滚都由别人决定；
 * 这里自己测触发器位置，下方放不开就翻到上方，保证任何位置都能完整看见。
 */
export function Menu({ label, items, value, onSelect }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();
  const triggerRef = useRef<ComponentRef<typeof View> | null>(null);
  const [anchor, setAnchor] = useState<{ x: number; y: number; height: number }>();

  const open = anchor !== undefined;

  const openMenu = () => {
    // NOTE: 坐标在按下时现测，不缓存：滚动、旋转、键盘都会让旧坐标失效。
    triggerRef.current?.measureInWindow((x, y, _width, height) => setAnchor({ x, y, height }));
  };

  const close = () => setAnchor(undefined);

  const height = Math.min(items.length * ROW_HEIGHT + Spacing.sm * 2, MAX_HEIGHT);
  const left = Math.max(Spacing.sm, Math.min(anchor?.x ?? 0, screenWidth - WIDTH - Spacing.sm));
  const belowTop = anchor === undefined ? 0 : anchor.y + anchor.height + Spacing.xs;
  const aboveTop = anchor === undefined ? 0 : anchor.y - height - Spacing.xs;
  const fitsBelow = belowTop + height <= screenHeight - insets.bottom;
  const top = Math.max(insets.top + Spacing.sm, fitsBelow ? belowTop : aboveTop);

  return (
    <View collapsable={false} ref={triggerRef}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        onPress={openMenu}
        style={({ pressed }) => [styles.trigger, pressed && { backgroundColor: theme.backgroundSelected }]}>
        <ThemedText style={styles.triggerText}>{label}</ThemedText>
        <Ionicons color={theme.textSecondary} name="chevron-down" size={14} />
      </Pressable>
      <Modal animationType="fade" onRequestClose={close} statusBarTranslucent transparent visible={open}>
        <Pressable accessibilityLabel="关闭菜单" onPress={close} style={styles.backdrop}>
          {/* NOTE: 内层 Pressable 吞掉点击，避免选中菜单项时顺手把浮层关掉。 */}
          <Pressable
            onPress={() => {}}
            style={[styles.card, {
              backgroundColor: theme.backgroundElement,
              height,
              left,
              top,
              width: WIDTH,
            }]}>
            <ScrollView>
              {items.map((item) => (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ selected: item.value === value }}
                  key={item.value}
                  onPress={() => {
                    close();
                    onSelect(item.value);
                  }}
                  style={({ pressed }) => [styles.row, pressed && { backgroundColor: theme.backgroundSelected }]}>
                  <ThemedText style={item.value === value ? { color: theme.accent } : undefined}>{item.label}</ThemedText>
                </Pressable>
              ))}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  trigger: {
    alignItems: 'center',
    borderRadius: Radius.sm,
    flexDirection: 'row',
    gap: Spacing.xxs,
    minHeight: 40,
    paddingHorizontal: Spacing.sm,
  },
  triggerText: {
    fontSize: 14,
  },
  backdrop: {
    flex: 1,
  },
  card: {
    borderRadius: Radius.md,
    borderCurve: 'continuous',
    overflow: 'hidden',
    paddingVertical: Spacing.xs,
    position: 'absolute',
  },
  row: {
    justifyContent: 'center',
    minHeight: ROW_HEIGHT,
    paddingHorizontal: Spacing.lg,
  },
});
