package app.missu.ui.challenge

import android.annotation.SuppressLint
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
 * 「浏览器压根没被挑战」时的收工阈值（12 次 ≈ 6 秒）。
 *
 * NOTE: 这种情况下等 clearance 是等不到的，只能靠浏览器代取 HTML；连 HTML 都拿不到
 * （页面被导去别处，例如登录态失效 302 回首页，或者浏览器自己卡住）就没必要干等满 60 秒。
 * WARNING: 这类请求**全程不显形**——正常页面上绝不该弹出防护层。所以到点就交回 OkHttp
 * 重取，别留一个看不见、又在吃触摸的模态窗口。
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
    class Request(
        val path: String,
        val deferred: CompletableDeferred<ChallengeResult>,
        /** 非空 = 预览：直接加载这个 URL（本地 fixture 或抓下来的真挑战页），不判通过、不碰网络。 */
        val previewUrl: String? = null,
    )

    override suspend fun verify(path: String): ChallengeResult {
        val running = current
        if (running != null) {
            // NOTE: 预览占着浮层时不让真实验证抢槽位——否则预览会被应用的请求顶掉。
            if (running.previewUrl != null) return ChallengeResult(passed = false, html = null)
            // NOTE: 同一时刻只服务一个验证，但后到的**跟着等**这一次，不再把前一次判成取消。
            // 以前是 `current.deferred.cancel()`：被取消那次请求的界面会冒出一句「Job was cancelled」，
            // 而且用户明明过完了验证它照样失败。这里把 passed 传下去、HTML 不带（那是别人那条路径的
            // 页面），调用方拿到 passed 后自己重取——此时 clearance 已经在共享 Cookie 里了。
            return running.deferred.await().copy(html = null)
        }
        val request = Request(path, CompletableDeferred())
        current = request
        return try {
            request.deferred.await()
        } finally {
            if (current === request) current = null
        }
    }

    /**
     * 把浮层抬起来展示 [url]，**不触发任何真实请求**（只用于预览配色与三种状态）。
     *
     * 调用方必须是 debuggable 构建（见 `MainActivity` 里的 `challenge_preview` 入口）；
     * 关掉浮层的唯一方式是「取消」或返回键。
     */
    fun preview(url: String) {
        current?.deferred?.cancel()
        current = Request(path = "", CompletableDeferred(), previewUrl = url)
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
    val a = actual.toUri().path.orEmpty()
    val b = expected.toUri().path.orEmpty()
    return a.replace(Regex("/dm\\d+(?=/)"), "") == b.replace(Regex("/dm\\d+(?=/)"), "")
}

/**
 * 让**站点自己的页面**跟随应用明暗。
 *
 * 实测（2026-11-15 05:35）：站点的 `html` / `body` 背景都是 `rgba(0, 0, 0, 0)`，`body` 也没有显式 `color`——
 * 默认文字颜色由浏览器的 `color-scheme` 决定：设成 `dark` 时 `color` 变成 `rgb(255, 255, 255)`，
 * 设成 `light` 时是 `rgb(0, 0, 0)`。所以「底色我们给 + 文字/控件颜色交给 color-scheme」就能两全。
 *
 * WARNING: 这一招对 **Cloudflare 挑战页无效**——它有自己写死的 `html{color:#313131}`，见 [PAGE_THEME_JS]。
 */
private fun applyColorScheme(view: WebView, dark: Boolean) {
    val scheme = if (dark) "dark" else "light"
    view.evaluateJavascript(
        "(function(){var e=document.documentElement;if(e){e.style.colorScheme='$scheme'}})()",
        null,
    )
}

/**
 * 把 Cloudflare 挑战页自带的深色样式「解包」成无条件规则，并把画布交回主题色。
 *
 * 证据（2026-11-15 06:11 现场，见 docs/android-native.md）：
 * - 挑战页的浅色版**根本没给 `body` 背景**（只有 `body{display:flex…}`，文字色来自 fallback 的
 *   `html{color:#313131}`），它假设画布是白的；放进我们的深色容器就是深底深字（实测对比度 2.15:1）。
 * - CF 自己写好了深色版：`@media (prefers-color-scheme: dark){body{background-color:#000;color:#f2f2f2}…}`
 *   （链接 `#82b6ff`、页脚分隔线、提示框 `#313131` 等一整套）。但 WebView 的 prefers-color-scheme
 *   恒为 light（app 的 `uiMode` 已经是 night 时也报 false），媒体查询永远不成立。
 *
 * 所以从 CSSOM 里把这些规则取出来、用同样的选择器与声明重放一遍：配色仍是 CF 自己的，
 * 只是不再受媒体查询限制。画布用 `transparent` 交回 Compose 的容器色，免得出现第二种底色。
 *
 * @return 解包到的规则条数；0 = CF 的样式还没注入（调用方下一轮再试），-1 = 文档还没准备好
 */
private const val PAGE_THEME_JS = """
(function(dark){
  var doc=document, root=doc.documentElement;
  if(!root){return -1;}
  var st=doc.getElementById('missu-theme');
  if(!st){
    st=doc.createElement('style');
    st.id='missu-theme';
    (doc.head||root).appendChild(st);
  }
  if(dark&&st.dataset.rules==='1'){return Number(st.dataset.count||0);}
  var collected=[];
  if(dark){
    var walk=function(list){
      for(var i=0;i<list.length;i++){
        var r=list[i];
        if(r.type===4&&/prefers-color-scheme:\s*dark/i.test(r.conditionText||'')){
          for(var j=0;j<r.cssRules.length;j++){collected.push(r.cssRules[j].cssText);}
        }else if(r.cssRules){
          try{walk(r.cssRules);}catch(e){}
        }
      }
    };
    var sheets=doc.styleSheets;
    for(var k=0;k<sheets.length;k++){
      var rules=null;
      try{rules=sheets[k].cssRules;}catch(e){continue;}
      if(rules){walk(rules);}
    }
  }
  var css=collected.join('\n');
  if(dark){
    css+='\nhtml,body{background-color:transparent!important}';
    css+='\nhtml{color:#F2F2F2!important}';
  }
  st.textContent=css;
  st.dataset.rules=(dark&&collected.length)?'1':'0';
  st.dataset.count=String(collected.length);
  return collected.length;
})(%s)
"""

private fun applyPageTheme(view: WebView, dark: Boolean, onRules: (Int) -> Unit = {}) {
    view.evaluateJavascript(PAGE_THEME_JS.format(if (dark) "true" else "false")) { raw ->
        onRules(raw.trim().toIntOrNull() ?: -1)
    }
}

/**
 * 页面探针：标题 / 就绪状态 / 是不是挑战页 / 正文长度 / 明暗 / body 实际计算色。
 *
 * `c` 是**挑战页自己的身份标记 `window._cf_chl_opt`**：CF 的 chl 脚本一定会在页面上挂它
 * （实测：抓下来的英文 403 正文里 7 处；中文挑战页 `请稍候…` 的实时 DOM 里同样成立）。
 *
 * 为什么不能只靠标题（2026-11-15 06:52 实测）：CF 的挑战页标题**会被本地化**——同一个站点，
 * 我们的 WebView 收到 `Just a moment...`，无头 Chrome 收到的是 `请稍候…`。旧代码只认英文标题，
 * 遇到后者就会把**挑战页当正常页**、把它的 HTML 交回请求（05:02 那次 `html=44698` 就是这个坑）。
 *
 * NOTE: 只用 `_cf_chl_opt` 一个标记。它在两种挑战页上都验过成立（英文 403 正文、中文 `请稍候…`），
 * 在真正的站点正常页上验过为 false（`t="MissAV | 免费高清AV在线看"`、`c=false`，2026-11-15 06:54 真机）。
 * 没有再加「资源里出现 `/cdn-cgi/challenge-platform`」这条：它覆盖的资源面比一个标记宽，
 * 而 CF 正常页面是否也拉那套脚本我们没验过——既然一个已验证足够的信号已经覆盖两种标题变体，
 * 就不引入第二个面更大、还没验证的信号。
 */
private const val PROBE_JS =
    "JSON.stringify({t:document.title,r:document.readyState," +
        "c:(typeof window._cf_chl_opt!=='undefined')," +
        "n:(document.body?document.body.innerText.trim().length:0)," +
        "d:matchMedia('(prefers-color-scheme: dark)').matches," +
        "bg:getComputedStyle(document.body).backgroundColor,fg:getComputedStyle(document.body).color})"

/** 挑战页判定：CF 自己的标记优先，标题只作辅助（标题会改版、会本地化）。 */
private fun JSONObject.isChallengePage(): Boolean =
    optBoolean("c") || optString("t").contains(CHALLENGE_TITLE, ignoreCase = true)

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
    // 预览时加载的是本地 fixture（或抓下来的真挑战页），其余情况都是站点的真实路径。
    val targetUrl = request.previewUrl ?: SourceConfig.localizedUrl(request.path)
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    // 页面自己不带背景（实测 html/body 的 background 都是 rgba(0,0,0,0)），底色由这层给；
    // 明暗再通过 `color-scheme` 交回浏览器的默认值（见 applyColorScheme）。
    val darkTheme = LocalDarkTheme.current
    val title = stringResource(R.string.challenge_title)
    val cancel = stringResource(R.string.common_cancel)
    val passedText = stringResource(R.string.challenge_passed)
    val openingText = stringResource(R.string.challenge_opening)
    val waitingText = stringResource(R.string.challenge_waiting)

    // NOTE: 先不显形——只有确认「这是真挑战页」才淡入。见 poll 循环里的 revealed 判定。
    var revealed by remember(request) { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(FADE_MS),
        label = "challengeAlpha",
    )
    var status by remember(request, openingText) { mutableStateOf(openingText) }
    var attempts by remember(request) { mutableIntStateOf(0) }
    var finished by remember(request) { mutableStateOf(false) }
    // WebView 在 factory 里创建、在 LaunchedEffect 里被轮询读：用普通引用而不是 Compose 状态，
    // 免得在组合期写状态。销毁交给 AndroidView 的 onRelease。
    val webView = remember(request) { AtomicReference<WebView?>(null) }
    // 挑战页自带的深色样式可能比 onPageFinished 更晚注入：解包到 0 条就下一轮重试（见 applyPageTheme）。
    val themeRules = remember(request) { AtomicInteger(0) }
    // 挑战页的 DOM 只存一次：下次要看 CF 到底怎么写的、Turnstile 带什么参数，就不必再等一次现场。
    val htmlDumped = remember(request) { AtomicBoolean(false) }

    // 取证：抬起即记一行，并在页面刚开始加载时留一张（此时通常是白底或正在加载）。
    LaunchedEffect(request) {
        ChallengeDiagnostics.event(context, "shown path=${request.path}")
        delay(OPENING_SHOT_MS)
        shot("opening")
    }

    /**
     * 跑一次 [PROBE_JS]，返回解析好的对象（拿到什么算什么，页面没准备好时是 null）。
     *
     * WARNING: 用 JSON 而不是拼分隔符——站点标题里本来就有 `|`（`MissAV | 免费高清AV在线看`），
     * 按分隔符切会把 readyState 切错（实测被这条卡住：判定永远不成立，浮层干等到超时）。
     */
    suspend fun probePage(view: WebView): JSONObject? = suspendCancellableCoroutine { cont ->
        view.evaluateJavascript(PROBE_JS) { raw ->
            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            cont.resume(runCatching { JSONObject(text) }.getOrNull())
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

    LaunchedEffect(request, waitingText) {
        delay(FIRST_CHECK_MS)
        while (!finished) {
            attempts += 1
            val view = webView.get()
            if (view == null) {
                delay(POLL_MS)
                continue
            }
            val page = probePage(view)
            val ready = page?.optString("r").orEmpty()
            // NOTE: 只看 cookie 会误判——cookie 可能由别的出口 IP 签发、或刚被站点作废，
            // 此时页面还停在挑战页，照读 HTML 会把挑战页当结果。所以还要看页面身份（见 isChallengePage）。
            val clearance = CookieManager.getInstance()
                .getCookie(view.url ?: targetUrl)
                ?.contains("cf_clearance") == true
            val challengePage = page?.isChallengePage() == true
            val onTarget = samePath(view.url.orEmpty(), targetUrl)
            val contentLength = page?.optInt("n") ?: 0
            val blankPage = page != null && ready == "complete" && contentLength == 0
            // 正常且有正文的目标页不打扰用户；真挑战、空白外壳和暂时无法识别的页面必须给出等待状态。
            if (challengePage || blankPage || page == null || request.previewUrl != null) revealed = true
            // 深色主题 + 挑战页：CF 的深色样式可能晚于 onPageFinished 注入，没抓到就每轮补一次。
            if (challengePage && darkTheme && themeRules.get() <= 0) {
                applyPageTheme(view, darkTheme) { n -> themeRules.set(n) }
            }
            // 预览：页面是本地 fixture，永远等不到 clearance，也不该自己关掉——只做样式注入与取证。
            if (request.previewUrl != null) {
                status = waitingText
                delay(POLL_MS)
                continue
            }
            when {
                // 首选结局：浏览器已经把有正文的目标页渲染完，就把这份 HTML 直接交回请求。
                // 空 body 的 document 外壳不是结果（实测只有 39 字符），显示等待并继续等，不能交回重取。
                // WARNING: 这一条必须排在 clearance 之前。以前是 clearance 优先，于是浏览器手里明明有整页，
                // 也要放它走、让 OkHttp 再取一次；那次要是又被挡（CF 按 TLS 指纹判、或 cookie 还没落到
                // 共享存储），界面看到的就是一句「站点要求人机验证，请重试」，浮层不会再来第二遍。
                !challengePage && ready == "complete" && onTarget && contentLength > 0 -> {
                    // NOTE: 空串当没有——`html=""` 会让解析直接失败，而重取一次反而可能成。
                    readHtml(view) { html ->
                        finish(passed = true, html = html?.takeIf { it.isNotBlank() })
                    }
                    return@LaunchedEffect
                }
                // 页面还停在中间态：clearance 到手就够了，交给 OkHttp 重试，之后所有请求都通。
                clearance && !challengePage -> finish(passed = true, html = null)
                // 页面被导去了别处（例如登录态失效 302 回首页），又没有 clearance：没东西可等。
                !challengePage && ready == "complete" && !onTarget && attempts >= GIVE_UP_ATTEMPTS ->
                    finish(passed = false, html = null)
                // 已识别的非空普通页面迟迟不 complete：到点交回 OkHttp 重取。
                // 空白或无法识别的页面不算通过，继续显示等待，直到 clearance、有效正文或最终失败。
                !challengePage && !blankPage && page != null && attempts >= GIVE_UP_ATTEMPTS ->
                    finish(passed = true, html = null)
                attempts >= MAX_ATTEMPTS -> finish(passed = false, html = null)
                else -> {
                    status = waitingText
                    delay(POLL_MS)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // NOTE: alpha 只在绘制层生效——窗口照常存在，Chromium 仍认为它可见，挑战照常跑，
            // 但用户什么都看不到。这样「正常首页也弹出防护层」在观感上就不存在了。
            .alpha(contentAlpha)
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
                        // NOTE: 挑战页自身不带背景（浅色版连 `body` 背景都没有），底色由这层给；
                        // 深色主题下再由 applyPageTheme 把 CF 的深色样式解包进来，文字才不会落在深底上。
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = SourceConfig.DEFAULT_USER_AGENT
                                // NOTE: 预览要加载 file:// 的 fixture（或 adb push 进来的真挑战页），
                                // 而 targetSdk 30+ 默认禁止 file 访问——只在 debuggable 构建里放开。
                                if (ChallengeDiagnostics.enabled(ctx)) settings.allowFileAccess = true
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
                                        // prefers-color-scheme 与 body 的计算色是「页面为什么读不清」的直接证据。
                                        view.evaluateJavascript(PROBE_JS) { raw ->
                                            val text =
                                                runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
                                            ChallengeDiagnostics.event(context, "page=$text url=$pageUrl")
                                            val probe = runCatching { JSONObject(text) }.getOrNull()
                                            if (probe?.isChallengePage() == true) {
                                                // 挑战页自己写死浅色文字，深色主题下必须靠解包 CF 的深色样式。
                                                if (darkTheme) applyPageTheme(view, darkTheme) { n -> themeRules.set(n) }
                                                if (htmlDumped.compareAndSet(false, true)) {
                                                    readHtml(view) { html ->
                                                        if (html != null) ChallengeDiagnostics.saveHtml(context, html, "challenge")
                                                    }
                                                }
                                            }
                                        }
                                        shot("page")
                                    }
                                }
                                webView.set(this)
                                // NOTE: WebView 自己发的 `Accept-Language` 是 `en-US,en`（实测），
                                // 与 OkHttp、与界面语言都不一致——显式覆盖成同一份派生值（见 SourceConfig.acceptLanguage）。
                                loadUrl(targetUrl, mapOf("Accept-Language" to SourceConfig.acceptLanguage()))
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
