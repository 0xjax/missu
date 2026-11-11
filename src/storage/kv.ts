import AsyncStorage from '@react-native-async-storage/async-storage';
import Storage from 'expo-sqlite/kv-store';

const MIGRATION_KEY = 'missav:kv-migrated:v1';
const LEGACY_KEYS = ['missav:history:v1', 'missav:search-history:v1', 'missav:profile:v1'];

let migration: Promise<void> | undefined;

export function migrateLegacyStorage(): Promise<void> {
  if (!migration) {
    migration = (async () => {
      if (await Storage.getItem(MIGRATION_KEY)) return;

      const pairs = await AsyncStorage.multiGet(LEGACY_KEYS);
      for (const [key, value] of pairs) {
        if (value != null) await Storage.setItem(key, value);
      }
      await AsyncStorage.multiRemove(LEGACY_KEYS);
      await Storage.setItem(MIGRATION_KEY, '1');
    })().catch(() => {
      // NOTE: 迁移失败不阻塞应用，下次启动会重试。
    });
  }
  return migration;
}

export default Storage;
