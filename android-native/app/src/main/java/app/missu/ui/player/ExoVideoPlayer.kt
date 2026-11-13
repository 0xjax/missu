package app.missu.ui.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.missu.data.api.AndroidCookieJar
import app.missu.data.api.SourceConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

private const val TAG = "MissavPlayer"

/**
 * 最小可用播放器：ExoPlayer + Media3 自带控件（播放/暂停 + 进度条）。
 *
 * NOTE: 媒体主机对裸请求返回 403（实测：旧播放地址带不带 Referer 都是 403，因为地址是临时的），
 * 因此显式补上站点的 Referer 与 UA，和页面内 play() 的行为保持一致。
 * NOTE: 播放失败必须显示出来——之前 HLS 模块缺失时播放器只是停在 00:00，界面上看不出任何原因。
 *
 * TODO: 全屏、倍速、快进退手势、锁横屏都还没做。
 * TODO: 离开页面只 release，没做失焦/后台暂停，接详情页返回时要补生命周期处理。
 */
@Composable
fun ExoVideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val player = remember {
        // NOTE: 数据源必须用 OkHttp：媒体主机对 HttpURLConnection 的 TLS 指纹返回 403（实测）。
        // Cookie 与站点请求共用系统 CookieManager，媒体主机若下发 Cookie 也能带上。
        val callFactory = OkHttpClient.Builder()
            .cookieJar(AndroidCookieJar())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val httpFactory = OkHttpDataSource.Factory(callFactory)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to SourceConfig.BASE_URL + "/",
                    "User-Agent" to SourceConfig.DEFAULT_USER_AGENT,
                ),
            )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playback failed: ${error.errorCodeName} / ${error.message}", error)
                errorMessage = "${error.errorCodeName}: ${error.message}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        errorMessage = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
        )

        errorMessage?.let { message ->
            Text(
                text = "播放失败\n$message",
                color = Color.White,
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(12.dp),
            )
        }
    }
}
