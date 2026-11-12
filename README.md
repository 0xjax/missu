# Missu

基于 Expo SDK 58、React Native、TypeScript 和 Expo Router 的 MissAV 移动客户端，应用名 Missu。

## 开发

```bash
bun install
bun run start
```

使用 Expo Go 扫码运行；需要脱离 Metro 的独立安装包时，按 [Android 真机调试与自动化](docs/android-debugging.md) 中的「构建独立 APK」打包。Android 真机无线 ADB、自动化操作和截图方法也在该文档。其他命令：

```bash
bun check
bun run lint
bunx expo export --platform android
```

## 当前功能

- 底部四栏：首页、列表、发现、我的
- 首页：今日热门、站点分区、最近更新，支持下拉刷新与分页
- 列表：最近更新 / 新作上市 / 无码流出，支持筛选与排序
- 发现：热门（多周期）、分类、女优（身高/罩杯/年龄/出道年份筛选与排序）、厂商、排行
- 搜索：关键词与女优搜索、类型筛选、排序、字幕/无码角标、本地搜索历史
- 详情：番号、日期、女优、类型、系列、厂商、标签（链接可跳转）及相关推荐
- 播放：HLS、双击快进退（同方向连击叠加）、长按倍速、进度条单击/拖动、横屏全屏
- 收藏与片单：登录后云端读取、添加、取消，片单支持新建与删除
- 观看历史与搜索历史存本机，可在设置中清空
- 设置：自动播放、内容语言（影响请求 URL）、清空历史、版本与数据来源
- 账号：登录/退出、Cloudflare 人机验证恢复、会话失效检测

数据层位于 `src/api/missav.ts`。源站提供 HTML 页面而非 JSON API，客户端直连并在本机解析；解析契约见 `docs/api/openapi.json`，HAR 草稿重新生成方式见 `docs/api/README.md`。
