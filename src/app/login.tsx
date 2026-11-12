import { useLocalSearchParams, useRouter, type Href } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';
import { errorHaptic, successHaptic } from '@/utils/haptics';

type LoginParams = { next?: string };

// NOTE: 由 scripts/dev-test-login.js 通过 adb reverse 暴露到设备本地回环，仅 Debug 构建使用。
const DEV_TEST_LOGIN_URL = 'http://127.0.0.1:8787/test-login';

export default function LoginScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<LoginParams>();
  const theme = useTheme();
  const { user, login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  const submit = async () => {
    if (!email.trim() || !password) {
      setError('请输入邮箱和密码');
      return;
    }
    await signIn(email.trim(), password);
  };

  const signIn = async (emailValue: string, passwordValue: string) => {
    setSubmitting(true);
    setError(undefined);
    try {
      await login(emailValue, passwordValue);
      setPassword('');
      successHaptic();
      if (params.next) {
        router.replace(params.next as Href);
      } else if (router.canGoBack()) {
        router.back();
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败');
      errorHaptic();
    } finally {
      setSubmitting(false);
    }
  };

  // NOTE: 仅 Debug 构建可见。凭据由本地脚本 `bun scripts/dev-test-login.js` 经 adb reverse 提供，
  // 命令行参数与日志里都不出现账号密码。
  const signInWithTestAccount = async () => {
    setSubmitting(true);
    setError(undefined);
    try {
      const response = await fetch(DEV_TEST_LOGIN_URL);
      if (!response.ok) throw new Error(`测试登录服务返回 ${response.status}，先运行 bun scripts/dev-test-login.js`);
      const account = await response.json() as { email: string; password: string };
      await signIn(account.email, account.password);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '测试登录失败');
      errorHaptic();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ScrollView
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      style={{ backgroundColor: theme.background }}>
      {user ? (
        <ThemedText themeColor="textSecondary">已登录：{user.email}</ThemedText>
      ) : (
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
            {submitting ? <ActivityIndicator color={theme.onAccent} /> : (
              <ThemedText type="smallBold" style={{ color: theme.onAccent }}>登录</ThemedText>
            )}
          </Pressable>
          {__DEV__ && (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ busy: submitting, disabled: submitting }}
              disabled={submitting}
              onPress={signInWithTestAccount}
              style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">测试登录（Debug）</ThemedText>
            </Pressable>
          )}
          {error && <ThemedText selectable type="small" style={{ color: theme.danger }}>{error}</ThemedText>}
          <ThemedText type="small" themeColor="textSecondary">密码仅用于本次登录，不保存到本地。</ThemedText>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    flexGrow: 1,
    gap: Spacing.lg,
    padding: Spacing.lg,
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
    justifyContent: 'center',
    minHeight: 44,
    paddingVertical: Spacing.md,
  },
});
