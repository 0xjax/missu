# MissAV

基于 Expo SDK 58、React Native、TypeScript 和 Expo Router 的 MissAV 移动客户端。

## 开发

```bash
bun install
bun run start
```

使用 Expo Go 扫码运行。Android 真机无线 ADB、自动化操作和截图方法见 [Android 真机调试与自动化](docs/android-debugging.md)。其他命令：

```bash
bun check
bun run lint
bunx expo export --platform android
```

## 当前功能

- 首页分区、两列列表、下拉刷新和分页加载
- 热门、分类、女优、厂商和女优排行发现入口
- 关键词搜索、本地搜索历史、类型筛选和多种排序
- 视频详情、封面、日期、时长和演员信息
- HLS 播放、双击快进/快退、长按倍速、拖动进度和横屏全屏
- 免登录本地观看历史
- 账号登录/退出及云端视频收藏

数据层位于 `src/api/missav.ts`。源站提供 HTML 页面而非 JSON API，客户端直连并在本机解析；解析契约见 `docs/api/openapi.json`，HAR 草稿重新生成方式见 `docs/api/README.md`。
