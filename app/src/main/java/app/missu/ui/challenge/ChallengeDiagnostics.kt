package app.missu.ui.challenge

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 人机验证层的取证钩子（**只对 debuggable 构建生效**）。
 *
 * NOTE: 真实挑战何时出现由站点按出口 IP + 指纹判定，无法按需制造（见 `docs/android-native.md`），
 * 所以把「观测」自动化：每次抬起浮层都写事件行 + 存几张截图，事后 `adb pull` 判读，不必守在设备边上。
 * 事件同时走 logcat（tag [TAG]，`adb logcat -s MissuChallenge`）与同目录下的 `events.log` 两份：
 * 前者随手可读，后者在没有电脑时也留证。
 *
 * 产物位置（无需权限，可直接 pull）：
 * `adb pull /sdcard/Android/data/app.missu/files/challenge/`
 */
internal object ChallengeDiagnostics {
    private const val TAG = "MissuChallenge"
    private const val DIR = "challenge"
    private const val KEEP_IMAGES = 12

    /** 用 `FLAG_DEBUGGABLE` 而不是 `BuildConfig.DEBUG`：不必为它打开 buildConfig 特性，且语义就是要的那件事。 */
    private fun enabled(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun directory(context: Context): File? {
        val root = context.getExternalFilesDir(DIR) ?: context.filesDir
        return root?.apply { mkdirs() }
    }

    fun event(context: Context, message: String) {
        if (!enabled(context)) return
        Log.i(TAG, message)
        runCatching {
            val dir = directory(context) ?: return
            File(dir, "events.log").appendText("${timeStamp()}  $message\n")
        }
    }

    /** 主线程调用：把 [view] 所在窗口的整棵视图树画进一张位图（WebView 也走同一条绘制路径）。 */
    fun render(view: View): Bitmap? {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return null
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { bitmap -> view.draw(Canvas(bitmap)) }
        }.getOrNull()
    }

    /** 任意线程调用：落盘，并只保留最近 [KEEP_IMAGES] 张。 */
    fun save(context: Context, bitmap: Bitmap, tag: String) {
        try {
            if (!enabled(context)) return
            val dir = directory(context) ?: return
            val name = "challenge-${fileStamp()}-$tag.png"
            File(dir, name).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            trim(dir)
            Log.i(TAG, "saved $name")
        } catch (error: Exception) {
            Log.w(TAG, "save failed: ${error.message}", error)
        } finally {
            bitmap.recycle()
        }
    }

    /** 把浏览器渲染出来的 HTML 落盘（判读「代取的页面能不能被解析器吃下去」）。只留最近 2 份。 */
    fun saveHtml(context: Context, html: String, tag: String) {
        if (!enabled(context)) return
        runCatching {
            val dir = directory(context) ?: return
            File(dir, "page-${fileStamp()}-$tag.html").writeText(html)
            dir.listFiles { file -> file.name.endsWith(".html") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(2)
                ?.forEach { it.delete() }
        }
    }

    private fun trim(dir: File) {
        val images = dir.listFiles { file -> file.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        images.drop(KEEP_IMAGES).forEach { it.delete() }
    }

    private fun timeStamp(): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun fileStamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
}
