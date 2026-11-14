# 归档：Expo 版的组件框架

> 这份说明只描述**已归档**的 React Native 实现（`legacy/expo/src/`），不再构建、不再维护。
> 现役基线是仓库根的 Kotlin + Jetpack Compose 工程，控件口径见根目录 `DESIGN.md`。

界面只允许用这些原语拼，新控件先加到这里，不要在页面里就地发明：

| 原语 | 文件 | 用途 | 当年取代了 |
|---|---|---|---|
| `Sheet` | `src/components/ui/sheet.tsx` | 底部弹层：遮罩淡入 + 卡片上滑，`onRequestClose` 接返回键 | Compose `BottomSheet`、Paper `Modal` |
| `Menu` | `src/components/ui/menu.tsx` | 工具栏下拉：触发器 + 现测坐标的浮层，下方放不开自动翻到上方 | Paper `Menu`、Compose `DropdownMenu` |
| `Pill` | `src/components/ui/pill.tsx` | 胶囊按钮（选中只填充色） | Paper `Chip`、Compose `ToggleButton` |
| `UiButton` | `src/components/ui/button.tsx` | 40dp 按钮，`text` 次要 / `filled` 主操作 | Paper `Button`、Compose `Button`/`TextButton` |
| `Checkbox` | `src/components/ui/checkbox.tsx` | 22dp 勾选框 | Compose `Checkbox` |
| `SearchField` | `src/components/ui/search-field.tsx` | 搜索输入框（放大镜 + 清空） | Paper `Searchbar` |
| `Toast` | `src/components/ui/toast.tsx` | 底部悬浮提示，自动消失 | Paper `Snackbar` |

现状：现役 native 版**没有**这层自绘原语，直接用 Material 3 Expressive 组件（见根 `DESIGN.md` 的 Control Map）；
保留这张表只是为了让「当年为什么不用 @expo/ui / Paper」这段结论有出处。
