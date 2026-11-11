import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';

import {
  checkSession,
  login as loginRequest,
  logout as logoutRequest,
  type AccountUser,
} from '@/api/missav';
import Storage, { migrateLegacyStorage } from '@/storage/kv';

const PROFILE_KEY = 'missav:profile:v1';

type AuthContextValue = {
  user: AccountUser | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** 源站会话失效时调用：清掉本地登录态，避免界面继续显示“已登录”。 */
  expire: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AccountUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    (async () => {
      await migrateLegacyStorage();
      const value = await Storage.getItem(PROFILE_KEY);
      if (!value) return;
      // NOTE: 会话 cookie 只活在当前进程，重启后本地资料可能已失效；先向源站确认，避免“假装已登录”。
      if (!await checkSession()) {
        await Storage.removeItem(PROFILE_KEY);
        return;
      }
      setUser(JSON.parse(value) as AccountUser);
    })().catch(() => {
      // NOTE: 恢复失败按未登录处理，不阻塞启动。
    }).finally(() => setReady(true));
  }, []);

  const login = async (email: string, password: string) => {
    const nextUser = await loginRequest(email, password);
    setUser(nextUser);
    await Storage.setItem(PROFILE_KEY, JSON.stringify(nextUser));
  };

  const expire = async () => {
    setUser(null);
    await Storage.removeItem(PROFILE_KEY);
  };

  const logout = async () => {
    try {
      await logoutRequest();
    } finally {
      await expire();
    }
  };

  return (
    <AuthContext.Provider value={{ user, ready, login, logout, expire }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return value;
}
