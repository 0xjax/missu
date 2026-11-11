import NetInfo from '@react-native-community/netinfo';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function OfflineBanner() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [online, setOnline] = useState(true);

  useEffect(() => NetInfo.addEventListener((state) => {
    setOnline(state.isInternetReachable ?? state.isConnected ?? true);
  }), []);

  if (online) return null;

  return (
    <View
      pointerEvents="none"
      style={[styles.banner, { backgroundColor: theme.backgroundSelected, bottom: insets.bottom + Spacing.lg }]}>
      <ThemedText type="smallBold">网络不可用</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    position: 'absolute',
    alignSelf: 'center',
    borderRadius: Radius.full,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    zIndex: 10,
  },
});
