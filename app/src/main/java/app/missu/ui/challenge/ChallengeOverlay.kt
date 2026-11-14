package app.missu.ui.challenge

import android.annotation.SuppressLint
import android.os.SystemClock
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.missu.R
import app.missu.data.api.ChallengeHandler
import app.missu.data.api.ChallengeResult
import app.missu.data.api.SourceConfig
import app.missu.ui.theme.LocalDarkTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** 与 `:core` 的判定同一个标题；用来确认页面已经离开挑战页。 */
private const val CHALLENGE_TITLE = "Just a moment"

private const val FADE_MS = 150
private const val SCRIM_ALPHA = 0.86f

/** 首次检查前的等待：页面开始加载后才可能有 `cf_clearance`，但没必要等 `onPageFinished`（会随重定向重复）。 */
private const val FIRST_CHECK_MS = 300L

/** 检查间隔。挑战通常 1–3 秒内自动放行，500ms 的粒度让「通过 → 关掉」几乎无感。 */
private const val POLL_MS = 500L

/** 上限 120 次 ≈ 60 秒：交互式挑战的「计算」可能十几秒，30 秒太短（实测 10 秒仍未放行）。 */
private const val MAX_ATTEMPTS = 120

/**
 * 「浏览器压根没被挑战」时的提前收工阈值（12 次 ≈ 6 秒）。
 *
 * NOTE: 这种情况下等 clearance 是等不到的，只能靠浏览器代取 HTML；连 HTML 都拿不到
 * （页面被导去别处，例如登录态失效 302 回首页）就没必要干等满 60 秒。
 */
private const val GIVE_UP_ATTEMPTS = 12

/** 抬起后多久补一张「刚打开」的截图：给 WebView 一点出画面的时间，又不至于错过白屏阶段。 */
private const val OPENING_SHOT_MS = 400L

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

    /** [path] 是触发挑战的站点相对路径；浮层加载它，浏览器要是直接拿到了页面就能把 HTML 带回。 */
    class Request(val path: String, val deferred: CompletableDeferred<ChallengeResult>)

    override suspend fun verify(path: String): ChallengeResult {
        // NOTE: 同一时刻只服务一个验证。后到的把前一个判成取消——让它挂在那里等更糟。
        current?.deferred?.cancel()
        val request = Request(path, CompletableDeferred())
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
        onDismissRequest = {
            ChallengeDiagnostics.event(outerContext, "cancelled (back)")
            state.finish(request, passed = false)
        },
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

/** 站点的镜像段 `/dmNNN` 会插在语言段前（`/dm247/cn`），比较路径前先剥掉。 */
private fun samePath(actual: String, expected: String): Boolean {
    val a = android.net.Uri.parse(actual).path.orEmpty()
    val b = android.net.Uri.parse(expected).path.orEmpty()
    return a.replace(Regex("/dm\\d+(?=/)"), "") == b.replace(Regex("/dm\\d+(?=/)"), "")
}

/**
 * 让页面跟随应用明暗。
 *
 * 实测（2026-11-15 05:35）：站点的 `html` / `body` 背景都是 `rgba(0, 0, 0, 0)`，`body` 也没有显式 `color`——
 * 默认文字颜色由浏览器的 `color-scheme` 决定：设成 `dark` 时 `color` 变成 `rgb(255, 255, 255)`，
 * 设成 `light` 时是 `rgb(0, 0, 0)`。所以「底色我们给 + 文字/控件颜色交给 color-scheme」就能两全：
 * 暗色下是站点原生的深色皮肤，浅色下也不会出现「白底白字」。
 */
private fun applyColorScheme(view: WebView, dark: Boolean) {
    val scheme = if (dark) "dark" else "light"
    view.evaluateJavascript(
        "(function(){var e=document.documentElement;if(e){e.style.colorScheme='$scheme'}})()",
        null,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChallengeContent(request: ChallengeState.Request, onFinish: (Boolean, String?) -> Unit) {
    // NOTE: 文案一律走 LocalContext：它是外层显式提供进来的界面语言 context（见 ChallengeOverlayHost）。
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val startedAt = remember(request) { SystemClock.elapsedRealtime() }

    /** 取证截图：主线程画、IO 编码（见 [ChallengeDiagnostics]，只在 debuggable 构建里有产物）。 */
    fun shot(tag: String) {
        scope.launch {
            val frame = ChallengeDiagnostics.render(view.rootView) ?: return@launch
            withContext(Dispatchers.IO) { ChallengeDiagnostics.save(context, frame, tag) }
        }
    }
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    // 页面自己不带背景（实测 html/body 的 background 都是 rgba(0,0,0,0)），底色由这层给；
    // 明暗再通过 `color-scheme` 交回浏览器的默认值（见 applyColorScheme）。
    val darkTheme = LocalDarkTheme.current
    val title = context.getString(R.string.challenge_title)
    val cancel = context.getString(R.string.common_cancel)
    val passedText = context.getString(R.string.challenge_passed)

    var status by remember(request) { mutableStateOf(context.getString(R.string.challenge_opening)) }
    var attempts by remember(request) { mutableIntStateOf(0) }
    var finished by remember(request) { mutableStateOf(false) }
    // WebView 在 factory 里创建、在 LaunchedEffect 里被轮询读：用普通引用而不是 Compose 状态，
    // 免得在组合期写状态。销毁交给 AndroidView 的 onRelease。
    val webView = remember(request) { AtomicReference<WebView?>(null) }

    // 取证：抬起即记一行，并在页面刚开始加载时留一张（此时通常是白底或正在加载）。
    LaunchedEffect(request) {
        ChallengeDiagnostics.event(context, "shown path=${request.path}")
        delay(OPENING_SHOT_MS)
        shot("opening")
    }

    /**
     * 标题 + 就绪状态。标题能区分「挑战页（Just a moment...）」与「真页面」。
     *
     * WARNING: 用 JSON 而不是拼分隔符——站点标题里本来就有 `|`（`MissAV | 免费高清AV在线看`），
     * 按分隔符切会把 readyState 切错（实测被这条卡住：判定永远不成立，浮层干等到超时）。
     */
    suspend fun pageInfo(view: WebView): Pair<String, String> = suspendCancellableCoroutine { cont ->
        view.evaluateJavascript("JSON.stringify({t: document.title, r: document.readyState})") { raw ->
            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            val obj = runCatching { JSONObject(text) }.getOrNull()
            cont.resume(obj?.optString("t").orEmpty() to obj?.optString("r").orEmpty())
        }
    }

    fun readHtml(view: WebView, onHtml: (String?) -> Unit) {
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { raw ->
            onHtml(runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull())
        }
    }

    fun finish(passed: Boolean, html: String?) {
        if (finished) return
        finished = true
        if (passed) status = passedText
        if (html != null) ChallengeDiagnostics.saveHtml(context, html, if (passed) "passed" else "failed")
        ChallengeDiagnostics.event(
            context,
            "finish passed=$passed attempts=$attempts in ${SystemClock.elapsedRealtime() - startedAt}ms" +
                " html=${html?.length ?: 0}",
        )
        shot(if (passed) "passed" else "blocked")
        onFinish(passed, html)
    }

    LaunchedEffect(request) {
        delay(FIRST_CHECK_MS)
        while (!finished) {
            attempts += 1
            val view = webView.get()
            if (view == null) {
                delay(POLL_MS)
                continue
            }
            val (pageTitle, ready) = pageInfo(view)
            // NOTE: 只看 cookie 会误判——cookie 可能由别的出口 IP 签发、或刚被站点作废，
            // 此时页面还停在挑战页，照读 HTML 会把挑战页当结果。所以标题必须一起看。
            val clearance = CookieManager.getInstance()
                .getCookie(view.url ?: SourceConfig.localizedUrl(request.path))
                ?.contains("cf_clearance") == true
            val challengePage = pageTitle.contains(CHALLENGE_TITLE, ignoreCase = true)
            val onTarget = samePath(view.url.orEmpty(), SourceConfig.localizedUrl(request.path))
            when {
                // 首选结局：clearance 到手，交给 OkHttp 重试，之后所有请求都通。
                clearance && !challengePage -> finish(passed = true, html = null)
                // 浏览器根本没被挑战、直接把目标页渲染出来了：把这份 HTML 交回去，
                // 正好治「OkHttp 被挡、浏览器没被挡」那种等不到 cookie 的卡死。
                !challengePage && ready == "complete" && onTarget -> {
                    readHtml(view) { html -> finish(passed = true, html = html) }
                    return@LaunchedEffect
                }
                // 页面被导去了别处（例如登录态失效 302 回首页），又没有 clearance：没东西可等。
                !challengePage && ready == "complete" && !onTarget && attempts >= GIVE_UP_ATTEMPTS ->
                    finish(passed = false, html = null)
                attempts >= MAX_ATTEMPTS -> finish(passed = false, html = null)
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
                        // NOTE: 站点页面是亮色皮肤（WebView 的 `prefers-color-scheme` 恒为 light，改不动），
                        // 但加载前那一两秒不该是一块刺眼的白——先铺主题容器色，页面画出来自然盖上去。
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = SourceConfig.DEFAULT_USER_AGENT
                                setBackgroundColor(surfaceLow.toArgb())

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                // 挑战页里可能带链接；留住站内跳转，别把用户丢给系统浏览器。
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                        applyColorScheme(view, darkTheme)
                                    }

                                    override fun onPageFinished(view: WebView, pageUrl: String) {
                                        applyColorScheme(view, darkTheme)
                                        // 取证：标题能独立证明收到的是挑战页（Just a moment...）还是正常页；
                                        // prefers-color-scheme 是「页面为什么是亮色」的唯一直接证据（站点按它切深色变体）。
                                        view.evaluateJavascript(
                                            "[document.title, matchMedia('(prefers-color-scheme: dark)').matches].join('|')",
                                        ) { probe ->
                                            ChallengeDiagnostics.event(context, "page=$probe url=$pageUrl")
                                        }
                                        shot("page")
                                    }
                                }
                                webView.set(this)
                                loadUrl(SourceConfig.localizedUrl(request.path))
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
                    TextButton(onClick = {
                        ChallengeDiagnostics.event(context, "cancelled (button) attempts=$attempts")
                        shot("cancelled")
                        onFinish(false, null)
                    }) { Text(cancel) }
                }
            }
        }
    }
}
