import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';

import {
  login as loginRequest,
  logout as logoutRequest,
  type AccountUser,
} from '@/api/missav';

const PROFILE_KEY = 'missav:profile:v1';

type AuthContextValue = {
  user: AccountUser | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AccountUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(PROFILE_KEY).then((value) => {
      if (value) setUser(JSON.parse(value) as AccountUser);
    }).finally(() => setReady(true));
  }, []);

  const login = async (email: string, password: string) => {
    const nextUser = await loginRequest(email, password);
    setUser(nextUser);
    await AsyncStorage.setItem(PROFILE_KEY, JSON.stringify(nextUser));
  };

  const logout = async () => {
    await logoutRequest();
    setUser(null);
    await AsyncStorage.removeItem(PROFILE_KEY);
  };

  return (
    <AuthContext.Provider value={{ user, ready, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return value;
}
