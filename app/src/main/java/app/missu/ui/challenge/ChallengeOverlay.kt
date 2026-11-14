package app.missu.ui.challenge

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.missu.R
import app.missu.data.api.ChallengeHandler
import app.missu.data.api.ChallengeResult
import app.missu.data.api.SourceConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicReference

private const val FADE_MS = 150
private const val SCRIM_ALPHA = 0.86f

/** 首次检查前的等待：页面开始加载后才可能有 `cf_clearance`，但没必要等 `onPageFinished`（会随重定向重复）。 */
private const val FIRST_CHECK_MS = 300L

/** 检查间隔。挑战通常 1–3 秒内自动放行，500ms 的粒度让「通过 → 关掉」几乎无感。 */
private const val POLL_MS = 500L

/** 上限 60 次 ≈ 30 秒；到点仍未放行就交给用户决定（取消后由页面自己重试）。 */
private const val MAX_ATTEMPTS = 60

/**
 * 人机验证的宿主状态，同时就是 `:core` 要的那个 [ChallengeHandler]。
 *
 * NOTE: 站点对非浏览器客户端的任意路径都会返回 403 + `cf-mitigated: challenge`，
 * 唯一可靠的做法是让用户在真 WebView 里过一遍；`cf_clearance` 落在系统 Cookie 里，
 * OkHttp 通过 `AndroidCookieJar` 读同一份存储，重试即通。
 * NOTE: 浮层挂在 `MainActivity` 的组合里（同 [app.missu.ui.preview.VideoPreviewHost]），
 * 不再单开 Activity——验证面属于应用的一层，主题、语言、色板、返回键全都自动继承。
 */
@Stable
class ChallengeState : ChallengeHandler {
    /** 正在进行的验证；`null` = 没有浮层。 */
    var current by mutableStateOf<Request?>(null)
        private set

    class Request(val url: String, val deferred: CompletableDeferred<ChallengeResult>)

    override suspend fun verify(url: String): ChallengeResult {
        // NOTE: 同一时刻只服务一个验证。后到的把前一个判成取消——让它挂在那里等更糟。
        current?.deferred?.cancel()
        val request = Request(url, CompletableDeferred())
        current = request
        return try {
            request.deferred.await()
        } finally {
            if (current === request) current = null
        }
    }

    /** 由浮层在判定通过或用户取消时调用。重复调用是空操作（[CompletableDeferred] 只认第一次）。 */
    fun finish(request: Request, passed: Boolean, html: String? = null) {
        request.deferred.complete(ChallengeResult(passed, html))
        if (current === request) current = null
    }
}

/**
 * 人机验证浮层：整屏暗底 + 居中的卡片，卡片里是真正在跑挑战的 WebView。
 *
 * 关键取舍：
 * - 用 [Dialog] 而不是页面里的 Box：它盖得住底栏与系统栏，自带返回键关闭，也不参与导航转场。
 * - 关闭必须能淡出。Dialog 从组合里消失就是硬切，所以留住最后一个目标，等淡出走完再拿掉。
 * - 每轮验证新建一个 WebView、关闭即销毁：复用省下的只是一次初始化（0.1–0.3 秒），
 *   代价是一个常驻在树里的全屏 WebView（绘制、触摸、内存），不值。
 */
@Composable
fun ChallengeOverlayHost(state: ChallengeState) {
    var shown by remember { mutableStateOf<ChallengeState.Request?>(null) }
    val transition = remember { MutableTransitionState(false) }
    val open = state.current

    LaunchedEffect(open) {
        if (open != null) {
            shown = open
            transition.targetState = true
        } else {
            transition.targetState = false
            if (shown != null) {
                delay(FADE_MS.toLong())
                shown = null
            }
        }
    }

    val request = shown ?: return

    // NOTE: [Dialog] 会用自己的窗口 context 重新起一段组合，`LocalContext` 会被换成系统语言的
    // 那个——直接在里面 `getString` 就会掉回英文（实测：界面简体中文，验证层显示 "Verification"）。
    // 所以在进入弹层之前把这三个取出来，再显式提供进去。
    val outerContext = LocalContext.current
    val outerConfiguration = LocalConfiguration.current
    val outerResources = LocalResources.current

    Dialog(
        onDismissRequest = { state.finish(request, passed = false) },
        properties = DialogProperties(
            // 整屏铺满，底由我们自己画——窗口自带的那层暗底盖不到系统栏，留着会双份叠黑。
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            scrimAlpha = 0f,
            // 验证不能靠点外面关掉：那会把「还在跑」误判成取消。
            dismissOnClickOutside = false,
        ),
    ) {
        CompositionLocalProvider(
            LocalContext provides outerContext,
            LocalConfiguration provides outerConfiguration,
            LocalResources provides outerResources,
        ) {
            AnimatedVisibility(
                visibleState = transition,
                enter = fadeIn(tween(FADE_MS)) + scaleIn(initialScale = 0.92f, animationSpec = tween(FADE_MS)),
                exit = fadeOut(tween(FADE_MS)),
            ) {
                ChallengeContent(
                    request = request,
                    onFinish = { passed, html -> state.finish(request, passed, html) },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChallengeContent(request: ChallengeState.Request, onFinish: (Boolean, String?) -> Unit) {
    // NOTE: 文案一律走 LocalContext：它是外层显式提供进来的界面语言 context（见 ChallengeOverlayHost）。
    val context = LocalContext.current
    val title = context.getString(R.string.challenge_title)
    val cancel = context.getString(R.string.common_cancel)
    val passedText = context.getString(R.string.challenge_passed)

    var status by remember(request) { mutableStateOf(context.getString(R.string.challenge_opening)) }
    var attempts by remember(request) { mutableIntStateOf(0) }
    var finished by remember(request) { mutableStateOf(false) }
    // WebView 在 factory 里创建、在 LaunchedEffect 里被轮询读：用普通引用而不是 Compose 状态，
    // 免得在组合期写状态。销毁交给 AndroidView 的 onRelease。
    val webView = remember(request) { AtomicReference<WebView?>(null) }

    fun readPage(view: WebView) {
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { raw ->
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            onFinish(true, html)
        }
    }

    LaunchedEffect(request) {
        delay(FIRST_CHECK_MS)
        while (!finished) {
            attempts += 1
            val view = webView.get()
            val cookie = view?.let { CookieManager.getInstance().getCookie(it.url ?: request.url) }
            when {
                cookie?.contains("cf_clearance") == true -> {
                    finished = true
                    status = passedText
                    // 有 cookie 就说明 WebView 一定在（cookie 只从它身上读），这里不必再判空。
                    readPage(view)
                    return@LaunchedEffect
                }
                attempts >= MAX_ATTEMPTS -> {
                    status = context.getString(R.string.challenge_blocked, attempts)
                    return@LaunchedEffect
                }
                else -> {
                    status = context.getString(R.string.challenge_waiting)
                    delay(POLL_MS)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            // 系统栏（状态栏、手势条）也要压在暗底里，卡片内容再避开它们。
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        // 站点页面是浅色的，垫白底避免加载期间露出一块深色。
                        .background(Color.White),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = SourceConfig.DEFAULT_USER_AGENT
                                setBackgroundColor(android.graphics.Color.WHITE)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                // 挑战页里可能带链接；留住站内跳转，别把用户丢给系统浏览器。
                                webViewClient = WebViewClient()
                                webView.set(this)
                                loadUrl(request.url)
                            }
                        },
                        onRelease = { view ->
                            webView.compareAndSet(view, null)
                            view.stopLoading()
                            view.destroy()
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onFinish(false, null) }) { Text(cancel) }
                }
            }
        }
    }
}
