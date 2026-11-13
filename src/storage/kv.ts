import Storage from 'expo-sqlite/kv-store';

/**
 * 存储层的统一出口：全 App 只从这里拿 Storage，换后端时只改这一行。
 * NOTE: 早前版本把历史/搜索/资料写在 AsyncStorage 里，那段一次性迁移已随依赖一起移除（见 DESIGN.md「Known Limits」）。
 */
export default Storage;