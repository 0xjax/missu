import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';

import Storage, { migrateLegacyStorage } from '@/storage/kv';

const PREFERENCES_KEY = 'missav:preferences:v1';

type Preferences = {
  autoplay: boolean;
};

const DEFAULT_PREFERENCES: Preferences = {
  autoplay: true,
};

type PreferencesContextValue = Preferences & {
  setAutoplay: (value: boolean) => void;
};

const PreferencesContext = createContext<PreferencesContextValue | undefined>(undefined);

function parsePreferences(value: string | null): Preferences {
  if (!value) return DEFAULT_PREFERENCES;
  try {
    const stored = JSON.parse(value) as Partial<Preferences>;
    return { autoplay: typeof stored.autoplay === 'boolean' ? stored.autoplay : DEFAULT_PREFERENCES.autoplay };
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

export function PreferencesProvider({ children }: PropsWithChildren) {
  const [preferences, setPreferences] = useState<Preferences>();

  useEffect(() => {
    (async () => {
      await migrateLegacyStorage();
      setPreferences(parsePreferences(await Storage.getItem(PREFERENCES_KEY)));
    })().catch(() => setPreferences(DEFAULT_PREFERENCES));
  }, []);

  const setAutoplay = (value: boolean) => {
    setPreferences((current) => {
      const next = { ...(current ?? DEFAULT_PREFERENCES), autoplay: value };
      void Storage.setItem(PREFERENCES_KEY, JSON.stringify(next));
      return next;
    });
  };

  if (!preferences) return null;

  return (
    <PreferencesContext.Provider value={{ ...preferences, setAutoplay }}>
      {children}
    </PreferencesContext.Provider>
  );
}

export function usePreferences(): PreferencesContextValue {
  const value = useContext(PreferencesContext);
  if (!value) throw new Error('usePreferences 必须在 PreferencesProvider 内使用');
  return value;
}
