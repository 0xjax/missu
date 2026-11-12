import { useCallback, useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';

import { localizedUrl, setBrowserUserAgent, setChallengeVerifier, type ChallengeSolution } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// NOTE: 挑战页会返回 200 + 挑战脚本的空壳；这里看标题和挑战节点，
// 确认离开挑战状态并等页面内容渲染完，再把整页 HTML 回传给 api 层。
const PROBE = `
(function probe() {
  if (!window.__uaSent) {
    window.__uaSent = true;
    window.ReactNativeWebView.postMessage(JSON.stringify({ userAgent: navigator.userAgent }));
  }
  const blocked = /Just a moment|Checking your browser|請稍候|请稍候|Attention Required/i.test(document.title || '')
    || !!document.querySelector('#challenge-form, #challenge-stage, #challenge-running, .cf-challenge, [data-translate="checking_browser"]');
  const ready = document.readyState === 'complete' && !!document.body;
  if (!blocked && ready) {
    setTimeout(() => {
      window.ReactNativeWebView.postMessage(JSON.stringify({
        done: true,
        userAgent: navigator.userAgent,
        html: document.documentElement.outerHTML,
        url: location.href,
      }));
    }, 1500);
    return;
  }
  setTimeout(probe, 800);
})();
true;
`;

type ActiveVerification = {
  promise: Promise<ChallengeSolution>;
  resolve: (solution: ChallengeSolution) => void;
};

type MessageEvent = { nativeEvent: { data: string } };

/**
 * 全局 Cloudflare 人机验证层：api 层遇到挑战时注册的验证器会打开这个全屏 WebView。
 * 用户在原生 WebView 里完成后，CookieManager 与 RN fetch 共享 cf_clearance；
 * 对 HTML 页面还会把渲染后的整页回传，避免只拿到挑战空壳。
 */
export function ChallengeProvider({ children }: PropsWithChildren) {
  const theme = useTheme();
  const [url, setUrl] = useState<string>();
  const activeRef = useRef<ActiveVerification | undefined>(undefined);
  const userAgentRef = useRef<string | undefined>(undefined);

  const finish = useCallback((verified: boolean, html?: string) => {
    const active = activeRef.current;
    activeRef.current = undefined;
    setUrl(undefined);
    if (verified && userAgentRef.current) {
      // cf_clearance 绑定 UA，验证通过后统一用 WebView 的 UA 发请求。
      setBrowserUserAgent(userAgentRef.current);
    }
    active?.resolve({ verified, html });
  }, []);

  useEffect(() => {
    setChallengeVerifier((path) => {
      if (activeRef.current) return activeRef.current.promise;
      let resolve!: (solution: ChallengeSolution) => void;
      const promise = new Promise<ChallengeSolution>((next) => {
        resolve = next;
      });
      activeRef.current = { promise, resolve };
      // NOTE: API 请求加载首页做验证；HTML 页面加载失败路径，验证后可直接拿渲染结果。
      setUrl(localizedUrl(path.includes('/api/') ? '/' : path));
      return promise;
    });
    return () => setChallengeVerifier(undefined);
  }, []);

  const handleMessage = useCallback((event: MessageEvent) => {
    try {
      const data = JSON.parse(event.nativeEvent.data) as {
        done?: boolean;
        userAgent?: string;
        html?: string;
      };
      if (data.userAgent) userAgentRef.current = data.userAgent;
      if (data.done) finish(true, data.html);
    } catch {
      // NOTE: 页面里的其它 postMessage 与验证无关，忽略。
    }
  }, [finish]);

  return (
    <>
      {children}
      <Modal
        animationType="fade"
        onRequestClose={() => finish(false)}
        statusBarTranslucent
        visible={url !== undefined}>
        <SafeAreaView style={[styles.screen, { backgroundColor: theme.background }]}>
          <View style={styles.header}>
            <View style={styles.headerText}>
              <ThemedText type="headline">需要人机验证</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                请在下方完成验证。通过后会自动继续；若页面已恢复，也可以直接点「我已验证」。
              </ThemedText>
            </View>
            <Pressable accessibilityRole="button" hitSlop={8} onPress={() => finish(false)}>
              <ThemedText type="smallBold" themeColor="textSecondary">取消</ThemedText>
            </Pressable>
          </View>
          {url !== undefined && (
            <WebView
              injectedJavaScript={PROBE}
              onMessage={handleMessage}
              source={{ uri: url }}
              style={[styles.web, { backgroundColor: theme.background }]}
            />
          )}
          <View style={[styles.footer, { borderTopColor: theme.backgroundSelected }]}>
            <Pressable
              accessibilityRole="button"
              onPress={() => finish(true)}
              style={({ pressed }) => [
                styles.continueButton,
                { backgroundColor: theme.accent },
                pressed && styles.pressed,
              ]}>
              <ThemedText type="smallBold" style={{ color: theme.onAccent }}>我已验证，继续</ThemedText>
            </Pressable>
          </View>
        </SafeAreaView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  header: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  headerText: {
    flex: 1,
    gap: Spacing.xs,
  },
  web: {
    flex: 1,
  },
  footer: {
    borderTopWidth: StyleSheet.hairlineWidth,
    padding: Spacing.md,
  },
  continueButton: {
    alignItems: 'center',
    borderRadius: 8,
    justifyContent: 'center',
    minHeight: 44,
  },
  pressed: {
    opacity: 0.7,
  },
});
