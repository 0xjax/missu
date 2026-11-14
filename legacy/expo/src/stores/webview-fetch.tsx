import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet } from 'react-native';
import { WebView } from 'react-native-webview';

import { localizedUrl, setWebViewHtmlFetcher } from '@/api/missav';

// NOTE: 等挑战/加载状态结束后再取整页 HTML；片单这类页面只有渲染后才有列表。
const PROBE = `
(function probe() {
  const blocked = /Just a moment|Checking your browser|請稍候|请稍候|Attention Required/i.test(document.title || '')
    || !!document.querySelector('#challenge-form, #challenge-stage, #challenge-running, .cf-challenge');
  if (!blocked && document.readyState === 'complete' && document.body) {
    setTimeout(() => {
      window.ReactNativeWebView.postMessage(JSON.stringify({
        html: document.documentElement.outerHTML,
        url: location.href,
      }));
    }, 1200);
    return;
  }
  setTimeout(probe, 500);
})();
true;
`;

type WebViewResult = { html: string; url: string };

type Pending = {
  path: string;
  resolve: (result: WebViewResult) => void;
  reject: (error: Error) => void;
};

/**
 * 隐藏 WebView 取数层：原生请求拿不到渲染内容时（例如片单列表），由这里加载页面并回传最终 HTML。
 * 与 ChallengeProvider 共用 CookieManager，登录态直接生效。
 */
export function WebViewFetchProvider() {
  const [pending, setPending] = useState<Pending>();
  const pendingRef = useRef<Pending | undefined>(undefined);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    setWebViewHtmlFetcher((path) => new Promise<WebViewResult>((resolve, reject) => {
      pendingRef.current = { path, resolve, reject };
      setPending(pendingRef.current);
      setNonce((value) => value + 1);
    }));
    return () => setWebViewHtmlFetcher(undefined);
  }, []);

  const finish = useCallback((result?: WebViewResult, error?: Error) => {
    const current = pendingRef.current;
    pendingRef.current = undefined;
    setPending(undefined);
    if (result) {
      current?.resolve(result);
    } else {
      current?.reject(error ?? new Error('页面加载失败'));
    }
  }, []);

  useEffect(() => {
    if (!pending) return;
    const timer = setTimeout(() => finish(undefined, new Error('页面加载超时')), 25_000);
    return () => clearTimeout(timer);
  }, [finish, pending]);

  if (!pending) return null;

  return (
    <WebView
      injectedJavaScript={PROBE}
      key={nonce}
      onError={() => finish(undefined, new Error('页面加载失败'))}
      onMessage={(event) => {
        try {
          const data = JSON.parse(event.nativeEvent.data) as { html?: string; url?: string };
          if (data.html) finish({ html: data.html, url: data.url ?? '' });
        } catch {
          // NOTE: 页面其它 postMessage 与本层无关。
        }
      }}
      source={{ uri: localizedUrl(pending.path) }}
      pointerEvents="none"
      style={styles.hidden}
    />
  );
}

const styles = StyleSheet.create({
  hidden: {
    bottom: 0,
    left: 0,
    opacity: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
});
