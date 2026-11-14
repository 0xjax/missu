package app.missu.data.api

import android.content.Context
import android.content.Intent
import app.missu.ui.challenge.ChallengeActivity
import kotlinx.coroutines.CompletableDeferred

/**
 * 把「请用户过一下人机验证」桥接成挂起调用（Android 侧实现，纯 JVM 的契约在 `:core` 的 [ChallengeHandler]）。
 *
 * NOTE: Activity 与调用方之间只靠这个单例交接，不把 Activity 引用塞进数据层。
 * NOTE: 顺序必须是 prepare() → startActivity → await()；先弹 Activity 会让它的 finish 早于 pending 建立而丢结果。
 */
object ChallengeHost {
    private var pending: CompletableDeferred<ChallengeResult>? = null

    @Volatile
    var targetUrl: String? = null
        private set

    fun prepare(url: String): CompletableDeferred<ChallengeResult> {
        pending?.cancel()
        targetUrl = url
        return CompletableDeferred<ChallengeResult>().also { pending = it }
    }

    fun start(context: Context) {
        context.startActivity(
            Intent(context, ChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 由 [ChallengeActivity] 在判定通过或用户取消时调用。 */
    fun finish(result: ChallengeResult) {
        pending?.complete(result)
        pending = null
        targetUrl = null
    }
}
