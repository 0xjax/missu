import * as Haptics from 'expo-haptics';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing, TabBarInset } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';

export default function AccountScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { user, ready, login, logout } = useAuth();
  const displayName = user?.username && user.username !== user.email ? user.username : user?.email;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  const submit = async () => {
    if (!email.trim() || !password) {
      setError('请输入邮箱和密码');
      return;
    }
    setSubmitting(true);
    setError(undefined);
    try {
      await login(email.trim(), password);
      setPassword('');
      void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败');
      void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    } finally {
      setSubmitting(false);
    }
  };

  const signOut = async () => {
    setSubmitting(true);
    setError(undefined);
    try {
      await logout();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '退出失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ThemedText type="subtitle">我的</ThemedText>
        {!ready && <ActivityIndicator color={theme.text} style={styles.state} />}
        {ready && !user && (
          <View style={styles.form}>
            <ThemedText type="headline">登录 MissAV 账号</ThemedText>
            <TextInput
              accessibilityLabel="邮箱"
              autoCapitalize="none"
              autoComplete="email"
              keyboardType="email-address"
              onChangeText={setEmail}
              placeholder="邮箱"
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { backgroundColor: theme.backgroundElement, color: theme.text }]}
              textContentType="emailAddress"
              value={email}
            />
            <TextInput
              accessibilityLabel="密码"
              autoCapitalize="none"
              autoComplete="password"
              onChangeText={setPassword}
              onSubmitEditing={submit}
              placeholder="密码"
              placeholderTextColor={theme.textSecondary}
              secureTextEntry
              style={[styles.input, { backgroundColor: theme.backgroundElement, color: theme.text }]}
              textContentType="password"
              value={password}
            />
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ busy: submitting, disabled: submitting }}
              disabled={submitting}
              onPress={submit}
              style={[styles.button, { backgroundColor: theme.accent }]}>
              <ThemedText type="smallBold" style={{ color: theme.onAccent }}>{submitting ? '登录中…' : '登录'}</ThemedText>
            </Pressable>
            <ThemedText type="small" themeColor="textSecondary">密码仅用于本次登录，不保存到本地。</ThemedText>
          </View>
        )}
        {ready && user && (
          <View style={styles.form}>
            <ThemedText type="headline">{displayName}</ThemedText>
            {displayName !== user.email && (
              <ThemedText selectable themeColor="textSecondary">{user.email}</ThemedText>
            )}
            <Pressable accessibilityRole="button" onPress={() => router.push('/saved')} style={[styles.button, { backgroundColor: theme.backgroundSelected }]}>
              <ThemedText type="smallBold">我的云端收藏</ThemedText>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ busy: submitting, disabled: submitting }}
              disabled={submitting}
              onPress={signOut}
              style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">{submitting ? '退出中…' : '退出登录'}</ThemedText>
            </Pressable>
          </View>
        )}
        {error && <ThemedText selectable type="small" style={{ color: theme.danger }}>{error}</ThemedText>}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    gap: Spacing.lg,
    padding: Spacing.lg,
    paddingBottom: Spacing.xxxl + TabBarInset,
  },
  state: {
    paddingVertical: Spacing.huge,
  },
  form: {
    gap: Spacing.md,
    marginTop: Spacing.sm,
  },
  input: {
    borderRadius: Radius.md,
    fontSize: 16,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  button: {
    alignItems: 'center',
    borderRadius: Radius.md,
    paddingVertical: Spacing.md,
  },
});
