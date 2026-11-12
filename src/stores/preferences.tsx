import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';

import Storage, { migrateLegacyStorage } from '@/storage/kv';

const PREFERENCES_KEY = 'missav:preferences:v1';

type Preferences = {
  autoplay: boolean;
  /** 常规播放倍速，长按结束后回到这个值。 */
  playbackRate: number;
  /** 长按期间的倍速，直接切换而不是在播放倍速上累乘，避免松手未触发时逐次翻倍。 */
  holdRate: number;
};

export const PLAYBACK_RATES = [0.75, 1, 1.25, 1.5, 2] as const;
export const HOLD_RATES = [1.5, 2, 3] as const;

const DEFAULT_PREFERENCES: Preferences = {
  autoplay: true,
  playbackRate: 1,
  holdRate: 2,
};

type PreferencesContextValue = Preferences & {
  setAutoplay: (value: boolean) => void;
  setPlaybackRate: (value: number) => void;
  setHoldRate: (value: number) => void;
};

const PreferencesContext = createContext<PreferencesContextValue | undefined>(undefined);

function pickRate(value: unknown, allowed: readonly number[], fallback: number): number {
  return typeof value === 'number' && allowed.includes(value) ? value : fallback;
}

function parsePreferences(value: string | null): Preferences {
  if (!value) return DEFAULT_PREFERENCES;
  try {
    const stored = JSON.parse(value) as Partial<Preferences>;
    return {
      autoplay: typeof stored.autoplay === 'boolean' ? stored.autoplay : DEFAULT_PREFERENCES.autoplay,
      playbackRate: pickRate(stored.playbackRate, PLAYBACK_RATES, DEFAULT_PREFERENCES.playbackRate),
      holdRate: pickRate(stored.holdRate, HOLD_RATES, DEFAULT_PREFERENCES.holdRate),
    };
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

  const update = (patch: Partial<Preferences>) => {
    setPreferences((current) => {
      const next = { ...(current ?? DEFAULT_PREFERENCES), ...patch };
      void Storage.setItem(PREFERENCES_KEY, JSON.stringify(next));
      return next;
    });
  };

  const setAutoplay = (value: boolean) => update({ autoplay: value });
  const setPlaybackRate = (value: number) => update({ playbackRate: value });
  const setHoldRate = (value: number) => update({ holdRate: value });

  if (!preferences) return null;

  return (
    <PreferencesContext.Provider value={{ ...preferences, setAutoplay, setPlaybackRate, setHoldRate }}>
      {children}
    </PreferencesContext.Provider>
  );
}

export function usePreferences(): PreferencesContextValue {
  const value = useContext(PreferencesContext);
  if (!value) throw new Error('usePreferences 必须在 PreferencesProvider 内使用');
  return value;
}
