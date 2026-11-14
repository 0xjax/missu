@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.DetailInfoRow
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist
import app.missu.ui.player.ExoVideoPlayer
import app.missu.ui.player.FullscreenEffect
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.PageStateTransition
import app.missu.ui.common.UiMessage
import app.missu.ui.common.text
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.videoHero
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // NOTE: 鍏变韩瀹瑰櫒鐨?key 涓庡崱鐗囦竴渚т竴鑷达紙璺敱閲岀殑 id锛夛紝璇︽儏鏁版嵁杩樻病鍥炴潵鏃朵篃鎷垮緱鍒般€?    videoId: String = "",
    // NOTE: 鍗＄墖灏侀潰銆傝鎯呰В鏋愬嚭鏉ヤ箣鍓嶅厛鐢ㄥ畠閾烘弧鎾斁鍣ㄦ鈥斺€旂偣鍗＄墖鏃堕偅涓灏辨槸浠庤繖閲岄暱杩囨潵鐨勩€?    posterUrl: String? = null,
    // NOTE: 杩欎釜鐩殑鍦拌繕鏄笉鏄鑸爤椤躲€傝繑鍥炴椂瀹冧細鍏堝彉 false锛岀敤鏉ュ湪**杞満寮€濮嬮偅涓€甯?*
    // 鎶婂鍣ㄥ唴瀹规崲鍥炲皝闈€佸仠鎺夐煶棰戯紙姣旂洰鐨勫湴琚攢姣佹棭鏁存暣涓€涓浆鍦猴級銆?    isCurrent: Boolean = true,
    message: UiMessage? = null,
    onToggleSaved: () -> Unit = {},
    startPositionMs: Long = 0L,
    onPositionReport: (Long) -> Unit = {},
    playlists: PlaylistUiState = PlaylistUiState.Hidden,
    onOpenPlaylists: () -> Unit = {},
    onClosePlaylists: () -> Unit = {},
    onTogglePlaylist: (VideoPlaylist) -> Unit = {},
    onOpenPath: (path: String, title: String) -> Unit = { _, _ -> },
) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    FullscreenEffect(fullscreen)

    val detail = (state as? DetailUiState.Content)?.detail
    // NOTE: 鍗＄墖甯﹁繃鏉ョ殑閭ｅ紶浼樺厛锛岃€屼笉鏄鎯呴〉鐨?og:image銆傜悊鐢辨湁涓や釜锛氬畠鍜屽崱鐗囦笂鐪嬪埌鐨?    // 鏄悓涓€寮狅紙瀹瑰櫒闀垮埌浣嶆椂鐢婚潰涓嶄細鍙橈級锛岃€屼笖瀹冨凡缁忓湪 Coil 鐨勫唴瀛樼紦瀛橀噷锛堜笉浼氫粠绾壊娣″叆锛夈€?    val poster = posterUrl ?: detail?.thumbnailUrl

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!fullscreen) {
            PageTopBar(title = "", onBack = onBack)
        }

        DetailHero(
            state = state,
            poster = poster,
            heroKey = videoId.ifEmpty { detail?.id.orEmpty() },
            isCurrent = isCurrent,
            fullscreen = fullscreen,
            startPositionMs = startPositionMs,
            onPositionReport = onPositionReport,
            onToggleFullscreen = { fullscreen = !fullscreen },
        )

        if (!fullscreen) {
            // NOTE: 姝ｆ枃锛堟爣棰?淇℃伅琛岋級鍜屽簳閮ㄥ姩浣滄潯鏈潵鍙湁鍦ㄨ鎯呮暟鎹洖鏉ヤ箣鍚庢墠瀛樺湪銆?            // 璇︽儏璇锋眰甯稿湪鏁撮〉娣″叆绐楀彛锛?0鈥?00ms锛?*涔嬪悗**鎵嶈繑鍥烇紝閭ｆ椂鎶婅繖涓ゅ潡鐩存帴鎽嗕笂鍘?            // 灏辨槸涓€娆＄‖鍒団€斺€旇€岄〉绛鹃噷鐨勫叾瀹冮〉闈㈠叏閮芥湁 fade through 鍏滆繖涓€涓嬨€傛墍浠ヨ繖閲屼篃璧?            // `PageStateTransition`锛氬悓涓€涓瑷€锛屽揩鎱㈢綉鐨勮涓轰篃涓€鑷达紙蹇綉鏃跺畠灏辨槸璺熺潃鏁撮〉娣″叆锛夈€?            PageStateTransition(
                state = state,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { current ->
                when (current) {
                    // 鍔犺浇鎬佷笉鍦ㄦ鏂囬噷鏀捐浆鍦堚€斺€旇繘搴︾粺涓€鏀跺湪鎾斁鍣ㄦ閲岋紙灏侀潰 + 寤惰繜鍑虹幇鐨?                    // 鎸囩ず鍣級锛屽惁鍒欓〉闈笂浼氬悓鏃舵湁涓や釜"姝ｅ湪鍔犺浇"銆?                    DetailUiState.Loading -> Unit

                    is DetailUiState.Error -> ErrorHint(current.message.text(), onRetry)

                    is DetailUiState.Content -> DetailBody(
                        detail = current.detail,
                        message = message,
                        onToggleSaved = onToggleSaved,
                        playlists = playlists,
                        onOpenPlaylists = onOpenPlaylists,
                        onClosePlaylists = onClosePlaylists,
                        onTogglePlaylist = onTogglePlaylist,
                        onOpenPath = onOpenPath,
                    )
                }
            }
        }
    }
}

/** 瑙嗛鍑虹敾鏃跺皝闈㈡贰鍑虹殑鏃堕暱锛涗笌鎺т欢鍑虹幇鏄悓涓€娈碉紝鐪嬭捣鏉ュ儚銆岀敾闈㈡樉褰便€嶃€?*/
private const val POSTER_FADE_MS = 200

/** 杞湀寤惰繜鍑虹幇鐨勬椂闀匡細蹇殑鏃跺€欙紙鍐呭瓨缂撳瓨 / 绉掑紑锛変笉璇ラ棯涓€涓嬭浆鍦堛€?*/
private const val PROGRESS_DELAY_MS = 600L

/**
 * 灏侀潰铏氱劍鐨勬椂搴忎笌寮哄害銆? *
 * NOTE: 寤惰繜蹇呴』 鈮?瀹瑰櫒杞満鏃堕暱锛?00ms锛夈€傞琛岄€斾腑灏卞紑濮嬭櫄鍖栵紝绛変簬鎶婂垰寤虹珛鐨? * 銆屽悓涓€涓笢瑗裤€嶇殑杩炵画鎬ц嚜宸辨媶鎺夆€斺€斿厛璁╁畠钀戒綅銆佽鐪嬫竻锛屽啀璁╁畠閫€鍒?鏈氨缁?鐨勭姸鎬侀噷銆? * NOTE: 鐖崌鏃堕暱鎸?*鍏稿瀷鐨勮棰戣捣姝ュ欢杩燂紙2鈥? 绉掞級**鏉ュ畾锛屼笉鏄寜鏅€氱姸鎬佸垏鎹㈢殑 300ms锛? * 瀹冭鍦ㄦ暣娈电瓑寰呴噷鎸佺画鍙鍦板彉鍖栵紝鑰屼笉鏄?涓€闂氨瀹屾垚銆佺劧鍚庝竴鐩撮攣姝?銆? */
private const val DEFOCUS_DELAY_MS = 300L
private const val DEFOCUS_RAMP_MS = 1500
private const val POSTER_BLUR_DP = 12
private const val POSTER_SCRIM_ALPHA = 0.22f

/**
 * 鎾斁鍣ㄦ鈥斺€斿悓鏃舵槸鍗＄墖灏侀潰鐨?*鍏变韩瀹瑰櫒**锛坈ontainer transform 鐨勮惤鐐癸級銆? *
 * 杩欓噷涔熸槸銆岄潤甯?鈫?鍔ㄦ€佺敾闈€嶇殑鎺ョ紳锛屽洓浠朵簨蹇呴』鎸夐『搴忓彂鐢燂紝鍚﹀垯鎺ョ紳浼氶湶鍑烘潵锛? * 1. 瀹瑰櫒闀垮ぇ鍒颁綅鏃讹紝閲岄潰杩樻槸**鍚屼竴寮犲皝闈?*锛堝崱鐗囬偅寮狅紝浠庡唴瀛樼紦瀛樻潵锛夛紝姝ゆ椂涓嶈櫄鍖栵紱
 * 2. 钀戒綅 300ms 鍚庡紑濮嬭櫄鐒?+ 杞诲帇鏆楋紙600ms 娓愬彉锛夛細闄嶄綆灏侀潰鐨?璁ょ煡鎶撳姏"锛? *    璁╃敾闈㈠彧鏈変竴涓富瑙掆€斺€旇繕娌″嚭鐜扮殑瑙嗛銆傜瓑寰呬腑鑻ヤ竴鐩存竻鏅帮紝鐢ㄦ埛浼氫互涓鸿繖灏辨槸鍐呭锛? * 3. 瑙嗛绗竴甯х湡姝ｅ嚭鐢讳箣鍓嶏紝灏侀潰涓€鐩村湪锛涗腑閫斿鍣ㄩ噷涓嶄細绌轰竴涓嬶紝涔熶笉浼氬厛鍑虹幇鎺т欢锛? * 4. 绗竴甯у嚭鐢?鈫?灏侀潰 200ms 娣″嚭锛堢敾闈㈠湪涓嬮潰鏄惧奖锛夛紝鎺т欢鍚屾椂鍑虹幇锛屼箣鍚?3 绉掓棤鎿嶄綔鑷姩闅愯棌銆? *
 * NOTE: 铏氬寲鐢?*涓ゅ眰闈欐€佸浘浜ゅ弶娣″叆**锛堟竻鏅板眰 + 铏氱劍灞傦級锛屼笉閫愬抚鏀?blur 鍗婂緞鈥斺€? * 鍚庤€呮瘡甯ч噸寤?RenderEffect锛屼腑绔満浼氭帀甯э紱鍓嶈€呭彧鍔?alpha锛屾垚鏈亽瀹氫笖鍙€嗐€? * `Modifier.blur` 鍦?API < 31 鏄┖鎿嶄綔锛岄偅鏃跺彧鍓╁帇鏆楋紝闄嶇骇涓嶅穿銆? * NOTE: 灏侀潰鐢诲湪鎾斁鍣?*涔嬩笂**锛堝惈鎺т欢灞備箣涓婏級锛屾墍浠ヨ繃娓″彧鑳芥槸銆屽皝闈㈡贰鍑洪湶鍑虹敾闈€嶏紝
 * 鑰屼笉鍙兘鏄€岀敾闈㈡浛鎹㈠皝闈€嶉偅绉嶇‖鍒囥€? * NOTE: 瀹冨繀椤诲湪棣栧抚灏卞瓨鍦紝Loading / Error 涔熶笉渚嬪锛氱偣鍗＄墖鏃跺叡浜厓绱犺鏈夊彟涓€渚у彲闀胯繘鍘汇€? * NOTE: 鎾斁鍣ㄦ湰韬浐瀹氬湪杩欎竴鏍硷紙涓嶈繘 LazyColumn锛夛紝鍒囧叏灞忓彧鏄崲灏哄銆佷笉浼氱寮€缁勫悎锛? * ExoPlayer 瀹炰緥涓庢挱鏀捐繘搴﹂兘鑳戒繚浣忋€? */
@Composable
private fun DetailHero(
    state: DetailUiState,
    poster: String?,
    heroKey: String,
    isCurrent: Boolean,
    fullscreen: Boolean,
    startPositionMs: Long,
    onPositionReport: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val detail = (state as? DetailUiState.Content)?.detail
    val playbackUrl = detail?.playbackUrl

    // CRITICAL: 杩欓噷鐨勭姸鎬?*鍙兘鎸?heroKey锛堣矾鐢?id锛夊彇 remember锛屼笉鑳芥寜 playbackUrl**銆?    // playbackUrl 浼氫粠 null 鍙樻垚鐪熸鐨勫湴鍧€锛屾寜瀹冨仛 key 绛変簬鍦ㄨ鎯呮暟鎹洖鏉ラ偅涓€鍒婚噸寤虹姸鎬侊細
    // Animatable 琚噸缃垚 0锛屾鍦ㄨ窇鐨勮櫄鍖栧姩鐢讳綔鐢ㄥ湪鏃у疄渚嬩笂銆佺洿鎺ヨ涓㈡帀鈥斺€旇〃鐜板氨鏄?    // "铏氬寲涓€灏忔 鈫?寮瑰洖娓呮櫚灏侀潰 鈫?涓€鐩存竻鏅扮瓑瑙嗛"銆?    /** 灏侀潰宸茬粡鍙互璁╀綅缁欑敾闈紙瑙嗛鍑鸿繃鐢伙紝鎴栨挱鏀惧け璐ヨ璁╅敊璇眰闇插嚭鏉ワ級銆?*/
    var revealed by remember(heroKey) { mutableStateOf(false) }
    /** 姝ｅ湪绂诲紑褰撳墠鐩殑鍦帮紙杩斿洖锛夈€傚鍣ㄩ噷瑕佹崲鍥炲皝闈紝缂╁洖鍘荤殑鎵嶄笉鏄竴涓椿瑙嗛銆?*/
    var leaving by remember(heroKey) { mutableStateOf(false) }

    val coverVisible = leaving || !revealed
    val posterAlpha by animateFloatAsState(
        targetValue = if (coverVisible) 1f else 0f,
        animationSpec = tween(durationMillis = POSTER_FADE_MS),
        label = "posterFade",
    )

    // 杩樺湪绛夛細瑕佷箞璇︽儏娌″洖鏉ワ紝瑕佷箞鎾斁鍦板潃鏈変簡浣嗚棰戣繕娌″嚭鐢汇€?    val pending = detail == null || (playbackUrl != null && !revealed)
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (!pending) {
            showProgress = false
            return@LaunchedEffect
        }
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }

    // NOTE: 鐢?Animatable 鑰屼笉鏄?animateFloatAsState锛氬嚭鐢绘椂瑕佹妸褰撳墠鍊?*鍐讳綇**璁╁皝闈㈡暣浣撴贰鍑猴紝
    // 涓嶈兘鍙嶅悜"閲嶆柊瀵圭劍"锛堥偅浼氬湪娣″嚭鍓嶅厛闂竴涓嬪彉娓呮櫚锛夈€?    val defocus = remember(heroKey) { Animatable(0f) }
    LaunchedEffect(pending) {
        if (pending) {
            delay(DEFOCUS_DELAY_MS)
            defocus.animateTo(1f, tween(durationMillis = DEFOCUS_RAMP_MS))
        } else if (detail != null && playbackUrl == null) {
            // 绔欑偣娌＄粰 m3u8锛氫笉浼氬啀鎾斁浜嗭紝鎶婂皝闈㈡仮澶嶆竻鏅帮紙杩欓噷鍙嶅悜鏄鐨勶級銆?            defocus.animateTo(0f, tween(durationMillis = DEFOCUS_RAMP_MS))
        }
    }

    // NOTE: 绂诲紑锛堣繑鍥?/ 琚帇鏍堬級鏃跺鍣ㄩ噷瑕佹崲鍥炲皝闈細鍙嶅悜鐨?container transform 钀界偣蹇呴』鏄?    // 鍗＄墖涓婇偅寮犲皝闈紝涓嶈兘鏄竴甯ц棰戠敾闈€傜敤 `isCurrent` 鑰屼笉鏄嫤鎴繑鍥為敭鈥斺€斿畠鍦?pop 寮€濮嬮偅涓€甯?    // 灏卞彉 false锛堟瘮鐩殑鍦拌閿€姣佹棭鏁存暣涓€涓浆鍦猴級锛岃€屼笖涓嶅弬涓庤繑鍥炲喅绛栵紝棰勬祴鎬ц繑鍥炵殑鎵嬪娍棰勮鐓ф棫銆?    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            // 棰勬祴鎬ц繑鍥炶鍙栨秷锛氳棰戦噸鏂扮洊杩囧皝闈紙闊抽鐢辨挱鏀惧櫒鑷繁鎭㈠锛夈€?            leaving = false
        } else {
            leaving = true
            // 杩樺湪绛夊緟锛堝皝闈㈡槸铏氱殑锛夋椂绂诲紑锛氭妸瀹冩仮澶嶆竻鏅板啀椋炲洖鍘汇€?            if (!revealed) defocus.snapTo(0f)
        }
    }

    val sharpAlpha = posterAlpha * (1f - defocus.value)
    val softAlpha = posterAlpha * defocus.value

    Box(
        modifier = (if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        })
            .videoHero(heroKey, RectangleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (playbackUrl != null) {
            ExoVideoPlayer(
                url = playbackUrl,
                modifier = Modifier.fillMaxSize(),
                active = isCurrent,
                isFullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                initialPositionMs = startPositionMs,
                onPositionReport = onPositionReport,
                onFirstFrameRendered = { revealed = true },
                // 鎾斁澶辫触锛氭妸灏侀潰鎾ゆ帀璁╅敊璇眰闇插嚭鏉ワ紙鍚﹀垯瀹冩案杩滆鍘嬪湪鏈€涓婇潰锛夈€?                onPlaybackFailed = { revealed = true },
            )
        }

        // 灏侀潰鐩栧湪鎾斁鍣ㄤ箣涓婏細鍑虹敾鍓嶅畠鏄鍣ㄥ唴瀹癸紝鍑虹敾鏃跺畠娣″嚭銆?        if (poster != null && sharpAlpha > 0.01f) {
            AsyncImage(
                model = poster,
                contentDescription = detail?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(sharpAlpha),
            )
        }
        if (poster != null && softAlpha > 0.01f) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(POSTER_BLUR_DP.dp)
                    .alpha(softAlpha),
            )
        }
        // 鍘嬫殫锛氬彧鍘嬫殫涓嶈櫄鍖栵紝灏侀潰鐨?鍚屼竴寮犲浘"杈ㄨ瘑搴﹀氨杩樺湪锛岄偅姝ｆ槸瑕佸墛鎺夌殑涓滆タ銆?        if (defocus.value > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = POSTER_SCRIM_ALPHA * defocus.value)),
            )
        }

        if (showProgress) {
            // NOTE: 鍘嬪湪鐓х墖涓婂繀椤昏嚜甯﹀簳琛紝鍚﹀垯娴呰壊灏侀潰涓婄殑鎸囩ず鍣ㄤ細鐪嬩笉娓呫€?            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                // NOTE: 杩欓噷鍒绘剰**涓嶇敤**椤甸潰绾х殑 `LoadingIndicator`銆傝棰戠紦鍐插拰銆岄〉闈㈠湪鍙栨暟銆嶆槸
                // 涓ょ绛夊緟锛氫竴涓槸濯掍綋娴佸湪缁紝涓€涓槸鐣岄潰鍦ㄧ瓑鏁版嵁銆傚舰鐘跺鏃忎笉鍚岋紙娉㈡氮鐜?vs 褰㈠彉鍥㈠潡锛?                // 鎵嶈兘涓€鐪煎垎娓呪€斺€斿悓涓€涓洟鍧楀嚭鐜板湪涓や釜鍦版柟锛屽彧浼氳浜鸿寰?鏁翠釜 App 閮藉湪杞?銆?                CircularWavyProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                    trackColor = Color(0x40FFFFFF),
                )
            }
        }

        if (playbackUrl == null && detail != null) {
            // 璇︽儏鎷垮埌浜嗕絾绔欑偣娌＄粰 m3u8锛氬帇鍦ㄥ皝闈笂璇翠竴鍙ワ紝鍚﹀垯鐢ㄦ埛鍙湅鍒颁竴寮犻潤姝㈢殑灏侀潰銆?            Text(
                text = stringResource(R.string.detail_no_playback),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xCC000000), MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 鎾斁鍣ㄤ笅闈㈢殑涓€鍒囷細鏍囬 / 淇℃伅琛?/ 绠€浠嬶紝浠ュ強搴曢儴鐨勬敹钘忎笌鐗囧崟鍔ㄤ綔銆? *
 * NOTE: 鎾斁鍣ㄤ笉鍦ㄨ繖閲屸€斺€斿畠鐢?[DetailHero] 鍥哄畾鍦ㄩ〉棣栵紝鏈嚱鏁板彧璐熻矗鍓╀綑鍐呭銆? * NOTE: 杩欓噷鑷繁寮€涓€涓?Column锛氬畠鏄?[PageStateTransition] 鐨勫唴瀹癸紝鎷夸笉鍒板灞傜殑 ColumnScope锛? * 鎵€浠?`weight(1f)` 鍙兘鐢辫繖涓€灞傜殑 Column 鎻愪緵銆? */
@Composable
private fun DetailBody(
    detail: VideoDetail,
    message: UiMessage?,
    onToggleSaved: () -> Unit,
    playlists: PlaylistUiState,
    onOpenPlaylists: () -> Unit,
    onClosePlaylists: () -> Unit,
    onTogglePlaylist: (VideoPlaylist) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
) {
    // NOTE: 瑙﹁鍙敤鏉ョ‘璁ゃ€岀姸鎬佹敼鍙樸€嶏紝涓嶇敤鍦ㄥ鑸笌婊氬姩涓婏紙瑙?DESIGN.md銆孧otion銆嶏級銆傛敹钘忚绛変竴娆?    // 缃戠粶璇锋眰锛岀晫闈笂鐨勬枃瀛椾笌棰滆壊閮借绛夊埌缁撴灉鍥炴潵鎵嶅彉鈥斺€旀寜涓嬪幓杩欎竴涓嬫槸瀹冨敮涓€鍗虫椂鐨勫洖搴斻€?    val haptics = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "meta") {
                Text(
                    text = listOfNotNull(detail.duration, detail.releaseDate).joinToString(" 路 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // NOTE: 鏀惰棌 / 鐗囧崟涓や釜鍔ㄤ綔鎼埌椤甸潰搴曢儴鐨?FlexibleBottomAppBar 閲岋紙SplitButton锛?            // 宸﹀崐鏄富鎿嶄綔銆屾敹钘忋€嶏紝鍙冲崐鏄€屽姞鍏ョ墖鍗曘€嶏級锛屽唴瀹瑰尯涓嶅啀琚竴琛屾寜閽墦鏂€?            message?.let { notice ->
                item(key = "message") {
                    Text(
                        text = notice.text(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            items(detail.info, key = { row -> row.label }) { row ->
                InfoRow(row = row, onOpenPath = onOpenPath)
            }

            detail.description?.let { description ->
                item(key = "description") {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        // NOTE: M3E 鐨勫簳鏍忓姩浣滃尯锛氬乏鍗婃槸涓绘搷浣滐紙鏀惰棌锛夛紝鍙冲崐鏄瑕佸叆鍙ｏ紙鍔犲叆鐗囧崟锛夈€?        // NOTE: 鏀惰棌鐘舵€佹潵鑷?/api/items/{id}/view锛?01 浼氬厛鑷姩閲嶇櫥涓€娆★紙瑙?withAuthRetry锛夈€?        // NOTE: 瀹冨拰姝ｆ枃涓€璧疯蛋 fade through鈥斺€斿姩浣滄潯灞炰簬銆屽唴瀹瑰埌杈俱€嶏紝涓嶈姣旀枃瀛楀厛鍒版垨鍚庡埌銆?        FlexibleBottomAppBar {
            SplitButtonLayout(
                modifier = Modifier.weight(1f),
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = {
                            // 璇箟鎸夈€岃繖娆＄偣鍑讳細鎶婂畠鍙樻垚浠€涔堛€嶇粰锛氬凡鏀惰棌 鈫?ToggleOff銆?                            haptics.performHapticFeedback(
                                if (detail.saved) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                            )
                            onToggleSaved()
                        },
                    ) {
                        Text(if (detail.saved) stringResource(R.string.detail_saved) else stringResource(R.string.detail_save))
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(onClick = onOpenPlaylists) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.detail_add_to_playlist))
                    }
                },
            )
        }
    }

    if (playlists !is PlaylistUiState.Hidden) {
        PlaylistSheet(state = playlists, onDismiss = onClosePlaylists, onToggle = onTogglePlaylist)
    }
}

/** 鍔犲叆鐗囧崟鐨勯潰鏉裤€侼OTE: 鍙睍绀轰笌鍕鹃€夛紝鏂板缓鐗囧崟瑕佽蛋绔欑偣鐨勭綉椤佃〃鍗曪紝鏆傛湭瀹炵幇銆?*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    state: PlaylistUiState,
    onDismiss: () -> Unit,
    onToggle: (VideoPlaylist) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.detail_add_to_playlist),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            when (state) {
                PlaylistUiState.Hidden -> Unit
                PlaylistUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                is PlaylistUiState.Error -> Text(
                    text = state.message.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )

                is PlaylistUiState.Content -> {
                    if (state.items.isEmpty()) {
                        Text(
                            text = stringResource(R.string.detail_no_playlists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    state.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.added,
                                onCheckedChange = { onToggle(item) },
                                enabled = state.pendingKey == null || state.pendingKey == item.key,
                            )
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun InfoRow(row: DetailInfoRow, onOpenPath: (path: String, title: String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            // NOTE: 鏍囩鍦ㄩ〉闈㈤噷鑷甫鍐掑彿锛堝銆屽彂琛屾棩鏈?銆嶏級锛岃繖閲屽彧琛ヤ竴涓┖鏍笺€?            text = row.label + " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // NOTE: 甯?path 鐨勫€硷紙濂充紭/绫诲瀷/鍘傚晢鈥︼級鍙偣锛岃烦鍒板搴旂殑 /latest 鍒楄〃椤点€?        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.values.forEach { value ->
                // NOTE: 鍏堝彇鎴愬眬閮ㄥ彉閲忊€斺€旇法妯″潡鐨?data class 灞炴€у湪 lambda 閲屼笉浼氳鏅鸿兘杞崲銆?                val path = value.path
                Text(
                    text = value.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (path != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = if (path != null) {
                        Modifier.clickable { onOpenPath(path, value.text) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
