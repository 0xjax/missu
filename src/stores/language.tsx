import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';

import { setContentLanguage, type ContentLanguage } from '@/api/missav';
import { CONTENT_LANGUAGES } from '@/constants/languages';
import Storage, { migrateLegacyStorage } from '@/storage/kv';

const LANGUAGE_KEY = 'missav:language:v1';
const DEFAULT_LANGUAGE: ContentLanguage = 'cn';

type LanguageContextValue = {
  language: ContentLanguage;
  setLanguage: (language: ContentLanguage) => Promise<void>;
};

const LanguageContext = createContext<LanguageContextValue | undefined>(undefined);

function isLanguage(value: string): value is ContentLanguage {
  return CONTENT_LANGUAGES.some((item) => item.value === value);
}

export function LanguageProvider({ children }: PropsWithChildren) {
  const [language, setLanguageState] = useState<ContentLanguage>();

  // NOTE: 请求 URL 依赖语言，必须先读完偏好再挂载子组件，避免首屏按默认语言发一轮请求。
  useEffect(() => {
    (async () => {
      await migrateLegacyStorage();
      const stored = await Storage.getItem(LANGUAGE_KEY);
      const next = stored && isLanguage(stored) ? stored : DEFAULT_LANGUAGE;
      setContentLanguage(next);
      setLanguageState(next);
    })().catch(() => {
      setContentLanguage(DEFAULT_LANGUAGE);
      setLanguageState(DEFAULT_LANGUAGE);
    });
  }, []);

  const setLanguage = async (next: ContentLanguage) => {
    setContentLanguage(next);
    setLanguageState(next);
    await Storage.setItem(LANGUAGE_KEY, next);
  };

  if (!language) return null;

  return (
    <LanguageContext.Provider value={{ language, setLanguage }}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useLanguage(): LanguageContextValue {
  const value = useContext(LanguageContext);
  if (!value) throw new Error('useLanguage 必须在 LanguageProvider 内使用');
  return value;
}
