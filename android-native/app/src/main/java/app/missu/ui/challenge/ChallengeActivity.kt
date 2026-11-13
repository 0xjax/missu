package app.missu.ui.challenge

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.missu.data.api.ChallengeHost
import app.missu.data.api.ChallengeResult
import app.missu.data.api.SourceConfig
import app.missu.ui.theme.MissuTheme
import org.json.JSONTokener

/**
 * Cloudflare 人机验证页。
 *
 * NOTE: 站点对非浏览器客户端的任意路径都会返回 403 + `cf-mitigated: challenge`，
 * 唯一可靠的做法是让用户在真 WebView 里过一遍；`cf_clearance` 落在系统 Cookie 里，
 * OkHttp 通过 `AndroidCookieJar` 读同一份存储，重试即通。
 */
class ChallengeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MissuTheme {
                ChallengeScreen(
                    url = ChallengeHost.targetUrl ?: SourceConfig.localizedUrl("/"),
                    onFinish = { passed, html ->
                        ChallengeHost.finish(ChallengeResult(passed, html))
                        finish()
                    },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChallengeScreen(url: String, onFinish: (Boolean, String?) -> Unit) {
    var status by remember { mutableStateOf("正在打开站点…") }
    var attempts by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    fun readPage(view: WebView) {
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { raw ->
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            onFinish(true, html)
        }
    }

    fun check(view: WebView) {
        if (finished) return
        attempts += 1
        val cookie = CookieManager.getInstance().getCookie(view.url ?: url)
        when {
            cookie?.contains("cf_clearance") == true -> {
                finished = true
                status = "验证通过，正在读取页面…"
                readPage(view)
            }
            attempts >= 40 -> status = "仍未通过（已检查 $attempts 次）。可以点「取消」后重试。"
            else -> {
                status = "等待站点放行…（第 $attempts 次检查）"
                view.postDelayed({ check(view) }, 1500)
            }
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = SourceConfig.DEFAULT_USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, pageUrl: String) {
                                    view.postDelayed({ check(view) }, 1200)
                                }
                            }
                            loadUrl(url)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    if (!finished) {
                        finished = true
                        onFinish(false, null)
                    }
                }) { Text("取消") }
            }
        }
    }
}
