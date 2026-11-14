package app.missu.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.missu.data.store.ColorSource

/**
 * 启动器图标：每个色板一个 `activity-alias`，任意时刻只启用一个（见 AndroidManifest.xml）。
 *
 * NOTE: 为什么是这个机制。桌面枚举的是「带 MAIN/LAUNCHER 的组件」，`<application android:icon>`
 * 只决定读 `ApplicationInfo` 的那些系统界面（应用信息、权限弹窗、分享面板）。所以要让桌面图标
 * 跟色板走，只能把**入口**做成多个 alias，各自带一份 `android:icon`，再用 PackageManager 启停。
 * 启动画面取的是**被启动组件**的图标（`ComponentInfo.getIconResource()`），所以它跟着 alias 一起变。
 *
 * WARNING: 禁用「启动时经过的那个 alias」会结束它所在的 task。所以切换只能在**应用完全不可见
 * 之后**做（见 `MissuRoot` 里的 ProcessLifecycleOwner 观察者）；在前台切的后果是被弹回桌面，
 * 或者正在跑的界面（比如人机验证页）被一起干掉。
 *
 * WARNING: 顺序必须是「先启用新的、再禁用旧的」。反过来会出现「两个都被禁用」的瞬间——那等于
 * 应用从桌面上消失。启动时用 [ensureOne] 兜这种情况。
 */
internal object LauncherIcons {

    /**
     * 色板 → alias 组件名。
     *
     * NOTE: 壁纸取色没有静态图标可用（系统色是运行时按壁纸算的），落到品牌粉。
     */
    fun aliasOf(source: ColorSource): String = when (source) {
        ColorSource.VIOLET -> "app.missu.LauncherViolet"
        ColorSource.BLUE -> "app.missu.LauncherBlue"
        ColorSource.TEAL -> "app.missu.LauncherTeal"
        ColorSource.AMBER -> "app.missu.LauncherAmber"
        ColorSource.WALLPAPER, ColorSource.BRAND -> "app.missu.LauncherBrand"
    }

    /** 所有 alias（去重，因为壁纸和品牌粉共用一个）。 */
    private val aliases: List<String> = ColorSource.entries.map(::aliasOf).distinct()

    /**
     * 某个 alias 现在是不是启用状态。
     *
     * CRITICAL: `getComponentEnabledSetting` 只返回**显式设置过**的状态，没设过一律返回
     * `COMPONENT_ENABLED_STATE_DEFAULT`——它**不等于启用**：这些 alias 在清单里大多是
     * `android:enabled="false"`，DEFAULT 的真实值必须从 `ActivityInfo.enabled` 读
     * （要带 MATCH_DISABLED_COMPONENTS，否则读被禁用的组件会抛 NameNotFound）。
     * 早期版本把 DEFAULT 当成「已启用」，结果切色时既没启用目标、又把其余全关了——
     * 整个应用从桌面消失（真机踩过，只能从应用内启动才修得回来）。
     */
    private fun isEnabled(context: Context, alias: String): Boolean {
        val pm = context.packageManager
        val name = ComponentName(context, alias)
        return when (pm.getComponentEnabledSetting(name)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> runCatching {
                pm.getActivityInfo(name, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            }.getOrDefault(false)
        }
    }

    /** 当前启用的色板；一个都没启用时不返回任何东西（见 [ensureOne]）。 */
    fun enabled(context: Context): List<ColorSource> =
        ColorSource.entries.filter { isEnabled(context, aliasOf(it)) }.distinct()

    /**
     * 切到 [target]：先启用它，再禁用其余。
     *
     * 返回是否成功。ROM 限制改组件状态时按失败处理、不抛给调用方——图标没换色不该让应用崩，
     * 下次进后台会再试一次。
     */
    fun apply(context: Context, target: ColorSource): Boolean {
        val pm = context.packageManager
        val wanted = aliasOf(target)
        return try {
            if (!isEnabled(context, wanted)) {
                pm.setComponentEnabledSetting(
                    ComponentName(context, wanted),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            // CRITICAL: 目标没启用成功就**绝对不要**去关别的——那会让应用从桌面上彻底消失。
            if (!isEnabled(context, wanted)) return false
            for (alias in aliases) {
                if (alias == wanted) continue
                if (isEnabled(context, alias)) {
                    pm.setComponentEnabledSetting(
                        ComponentName(context, alias),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            true
        } catch (e: RuntimeException) {
            // WARNING: 极少数 ROM（或企业策略）会拒绝改组件状态；保持原状即可。
            false
        }
    }

    /**
     * 启动时兜底：只保证「至少还有一个入口」。**不在前台做取舍**——禁用我们正用着的那个
     * 入口会结束当前 task，所以「多余的那个」留到下次进后台再收拾（[apply] 会一并关掉）。
     */
    fun ensureOne(context: Context, fallback: ColorSource) {
        if (enabled(context).isNotEmpty()) return
        apply(context, fallback)
    }
}
