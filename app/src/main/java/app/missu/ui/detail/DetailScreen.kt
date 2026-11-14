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
    // NOTE: 共享容器的 key 与卡片一侧一致（路由里的 id），详情数据还没回来时也拿得到。
    videoId: String = "",
    // NOTE: 卡片封面。详情解析出来之前先用它铺满播放器框——点卡片时那个框就是从这里长过来的。
    posterUrl: String? = null,
    // NOTE: 这个目的地还是不是导航栈顶。返回时它会先变 false，用来在**转场开始那一帧**
    // 把容器内容换回封面、停掉音频（比目的地被销毁早整整一个转场）。
    isCurrent: Boolean = true,
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
    // NOTE: 卡片带过来的那张优先，而不是详情页的 og:image。理由有两个：它和卡片上看到的
    // 是同一张（容器长到位时画面不会变），而且它已经在 Coil 的内存缓存里（不会从纯色淡入）。
    val poster = posterUrl ?: detail?.thumbnailUrl

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
            // NOTE: 正文（标题/信息行）和底部动作条本来只有在详情数据回来之后才存在。
            // 详情请求常在整页淡入窗口（90–300ms）**之后**才返回，那时把这两块直接摆上去
            // 就是一次硬切——而页签里的其它页面全都有 fade through 兜这一下。所以这里也走
            // `PageStateTransition`：同一个语言，快慢网的行为也一致（快网时它就是跟着整页淡入）。
            PageStateTransition(
                state = state,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { current ->
                when (current) {
                    // 加载态不在正文里放转圈——进度统一收在播放器框里（封面 + 延迟出现的
                    // 指示器），否则页面上会同时有两个"正在加载"。
                    DetailUiState.Loading -> Unit

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

/** 视频出画时封面淡出的时长；与控件出现是同一段，看起来像「画面显影」。 */
private const val POSTER_FADE_MS = 200

/** 转圈延迟出现的时长：快的时候（内存缓存 / 秒开）不该闪一下转圈。 */
private const val PROGRESS_DELAY_MS = 600L

/**
 * 封面虚焦的时序与强度。
 *
 * NOTE: 延迟必须 ≥ 容器转场时长（300ms）。飞行途中就开始虚化，等于把刚建立的
 * 「同一个东西」的连续性自己拆掉——先让它落位、被看清，再让它退到"未就绪"的状态里。
 * NOTE: 爬升时长按**典型的视频起步延迟（2–3 秒）**来定，不是按普通状态切换的 300ms：
 * 它要在整段等待里持续可见地变化，而不是"一闪就完成、然后一直锁死"。
 */
private const val DEFOCUS_DELAY_MS = 300L
private const val DEFOCUS_RAMP_MS = 1500
private const val POSTER_BLUR_DP = 12
private const val POSTER_SCRIM_ALPHA = 0.22f

/**
 * 播放器框——同时是卡片封面的**共享容器**（container transform 的落点）。
 *
 * 这里也是「静帧 → 动态画面」的接缝，四件事必须按顺序发生，否则接缝会露出来：
 * 1. 容器长大到位时，里面还是**同一张封面**（卡片那张，从内存缓存来），此时不虚化；
 * 2. 落位 300ms 后开始虚焦 + 轻压暗（600ms 渐变）：降低封面的"认知抓力"，
 *    让画面只有一个主角——还没出现的视频。等待中若一直清晰，用户会以为这就是内容；
 * 3. 视频第一帧真正出画之前，封面一直在；中途容器里不会空一下，也不会先出现控件；
 * 4. 第一帧出画 → 封面 200ms 淡出（画面在下面显影），控件同时出现，之后 3 秒无操作自动隐藏。
 *
 * NOTE: 虚化用**两层静态图交叉淡入**（清晰层 + 虚焦层），不逐帧改 blur 半径——
 * 后者每帧重建 RenderEffect，中端机会掉帧；前者只动 alpha，成本恒定且可逆。
 * `Modifier.blur` 在 API < 31 是空操作，那时只剩压暗，降级不崩。
 * NOTE: 封面画在播放器**之上**（含控件层之上），所以过渡只能是「封面淡出露出画面」，
 * 而不可能是「画面替换封面」那种硬切。
 * NOTE: 它必须在首帧就存在，Loading / Error 也不例外：点卡片时共享元素要有另一侧可长进去。
 * NOTE: 播放器本身固定在这一格（不进 LazyColumn），切全屏只是换尺寸、不会离开组合，
 * ExoPlayer 实例与播放进度都能保住。
 */
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

    // CRITICAL: 这里的状态**只能按 heroKey（路由 id）取 remember，不能按 playbackUrl**。
    // playbackUrl 会从 null 变成真正的地址，按它做 key 等于在详情数据回来那一刻重建状态：
    // Animatable 被重置成 0，正在跑的虚化动画作用在旧实例上、直接被丢掉——表现就是
    // "虚化一小段 → 弹回清晰封面 → 一直清晰等视频"。
    /** 封面已经可以让位给画面（视频出过画，或播放失败要让错误层露出来）。 */
    var revealed by remember(heroKey) { mutableStateOf(false) }
    /** 正在离开当前目的地（返回）。容器里要换回封面，缩回去的才不是一个活视频。 */
    var leaving by remember(heroKey) { mutableStateOf(false) }

    val coverVisible = leaving || !revealed
    val posterAlpha by animateFloatAsState(
        targetValue = if (coverVisible) 1f else 0f,
        animationSpec = tween(durationMillis = POSTER_FADE_MS),
        label = "posterFade",
    )

    // 还在等：要么详情没回来，要么播放地址有了但视频还没出画。
    val pending = detail == null || (playbackUrl != null && !revealed)
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (!pending) {
            showProgress = false
            return@LaunchedEffect
        }
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }

    // NOTE: 用 Animatable 而不是 animateFloatAsState：出画时要把当前值**冻住**让封面整体淡出，
    // 不能反向"重新对焦"（那会在淡出前先闪一下变清晰）。
    val defocus = remember(heroKey) { Animatable(0f) }
    LaunchedEffect(pending) {
        if (pending) {
            delay(DEFOCUS_DELAY_MS)
            defocus.animateTo(1f, tween(durationMillis = DEFOCUS_RAMP_MS))
        } else if (detail != null && playbackUrl == null) {
            // 站点没给 m3u8：不会再播放了，把封面恢复清晰（这里反向是对的）。
            defocus.animateTo(0f, tween(durationMillis = DEFOCUS_RAMP_MS))
        }
    }

    // NOTE: 离开（返回 / 被压栈）时容器里要换回封面：反向的 container transform 落点必须是
    // 卡片上那张封面，不能是一帧视频画面。用 `isCurrent` 而不是拦截返回键——它在 pop 开始那一帧
    // 就变 false（比目的地被销毁早整整一个转场），而且不参与返回决策，预测性返回的手势预览照旧。
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            // 预测性返回被取消：视频重新盖过封面（音频由播放器自己恢复）。
            leaving = false
        } else {
            leaving = true
            // 还在等待（封面是虚的）时离开：把它恢复清晰再飞回去。
            if (!revealed) defocus.snapTo(0f)
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
                // 播放失败：把封面撤掉让错误层露出来（否则它永远被压在最上面）。
                onPlaybackFailed = { revealed = true },
            )
        }

        // 封面盖在播放器之上：出画前它是容器内容，出画时它淡出。
        if (poster != null && sharpAlpha > 0.01f) {
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
        // 压暗：只压暗不虚化，封面的"同一张图"辨识度就还在，那正是要削掉的东西。
        if (defocus.value > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = POSTER_SCRIM_ALPHA * defocus.value)),
            )
        }

        if (showProgress) {
            // NOTE: 压在照片上必须自带底衬，否则浅色封面上的指示器会看不清。
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                // NOTE: 这里刻意**不用**页面级的 `LoadingIndicator`。视频缓冲和「页面在取数」是
                // 两种等待：一个是媒体流在续，一个是界面在等数据。形状家族不同（波浪环 vs 形变团块）
                // 才能一眼分清——同一个团块出现在两个地方，只会让人觉得"整个 App 都在转"。
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                    trackColor = Color(0x40FFFFFF),
                )
            }
        }

        if (playbackUrl == null && detail != null) {
            // 详情拿到了但站点没给 m3u8：压在封面上说一句，否则用户只看到一张静止的封面。
            Text(
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
 * 播放器下面的一切：标题 / 信息行 / 简介，以及底部的收藏与片单动作。
 *
 * NOTE: 播放器不在这里——它由 [DetailHero] 固定在页首，本函数只负责剩余内容。
 * NOTE: 这里自己开一个 Column：它是 [PageStateTransition] 的内容，拿不到外层的 ColumnScope，
 * 所以 `weight(1f)` 只能由这一层的 Column 提供。
 */
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
    // NOTE: 触觉只用来确认「状态改变」，不用在导航与滚动上（见 DESIGN.md「Motion」）。收藏要等一次
    // 网络请求，界面上的文字与颜色都要等到结果回来才变——按下去这一下是它唯一即时的回应。
    val haptics = LocalHapticFeedback.current

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
                    text = listOfNotNull(detail.duration, detail.releaseDate).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // NOTE: 收藏 / 片单两个动作搬到页面底部的 FlexibleBottomAppBar 里（SplitButton：
            // 左半是主操作「收藏」，右半是「加入片单」），内容区不再被一行按钮打断。
            message?.let { notice ->
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

        // NOTE: M3E 的底栏动作区：左半是主操作（收藏），右半是次要入口（加入片单）。
        // NOTE: 收藏状态来自 /api/items/{id}/view；401 会先自动重登一次（见 withAuthRetry）。
        // NOTE: 它和正文一起走 fade through——动作条属于「内容到达」，不该比文字先到或后到。
        FlexibleBottomAppBar {
            SplitButtonLayout(
                modifier = Modifier.weight(1f),
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = {
                            // 语义按「这次点击会把它变成什么」给：已收藏 → ToggleOff。
                            haptics.performHapticFeedback(
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

/** 加入片单的面板。NOTE: 只展示与勾选，新建片单要走站点的网页表单，暂未实现。 */
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
            // NOTE: 标签在页面里自带冒号（如「发行日期:」），这里只补一个空格。
            text = row.label + " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // NOTE: 带 path 的值（女优/类型/厂商…）可点，跳到对应的 /latest 列表页。
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.values.forEach { value ->
                // NOTE: 先取成局部变量——跨模块的 data class 属性在 lambda 里不会被智能转换。
                val path = value.path
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
