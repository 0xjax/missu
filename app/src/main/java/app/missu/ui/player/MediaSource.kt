package app.missu.ui.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import app.missu.data.api.AndroidCookieJar
import app.missu.data.api.SourceConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 媒体流的数据源工厂：详情页播放器与卡片长按预览共用。
 *
 * NOTE: 必须走 OkHttp —— 媒体主机对 `HttpURLConnection` 的 TLS 指纹返回 403。
 * NOTE: `Referer` / `User-Agent` 也是必须的。预览片在站点自己的 CDN（`fourhoi.com`），
 * 不带这两个头同样被拒；两个播放器都在同一个函数里取，就不存在「一个能放一个放不了」。
 */
@UnstableApi
fun mediaDataSourceFactory(baseClient: OkHttpClient): DataSource.Factory {
    val callFactory = baseClient.newBuilder()
        .cookieJar(AndroidCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    return OkHttpDataSource.Factory(callFactory)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to SourceConfig.BASE_URL + "/",
                "User-Agent" to SourceConfig.DEFAULT_USER_AGENT,
            ),
        )
}
