@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import app.missu.R

import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist

import app.missu.ui.player.FullscreenEffect
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.GroupDivider
import app.missu.ui.components.GroupSurface

import app.missu.ui.components.PageStateTransition
import app.missu.ui.common.UiMessage
import app.missu.ui.common.text
import app.missu.ui.components.heroKeyFor

import androidx.media3.common.Player

import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    // NOTE: 共享容器的 key 与卡片一侧一致（路由里的 id），详情数据还没回来时也拿得到。
    videoId: String = "",
    // NOTE: 卡片的来源标签：首页同一部影片可能出现在两个板块里，光靠 id 会和另一张卡片配对
    // （封面往屏外飞）。导航参数原样带过来，两侧用 heroKeyFor 算出同一个 key。
    heroTag: String = "",
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
    onSearch: (String) -> Unit = {},
) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    FullscreenEffect(fullscreen)
    // NOTE: 全屏时返回键/侧滑**先退全屏**，不退出详情：横屏里返回行被藏起来，播放器是唯一的画面，
    // 侧滑一下就把整部片连同这一页丢掉太狠；而且退出全屏要由我们主动发 `requestedOrientation`，
    // 交给导航走的话页面直接没了、横屏状态还得靠 onDispose 兜底。
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val detail = (state as? DetailUiState.Content)?.detail
    var player by remember { mutableStateOf<Player?>(null) }
    // NOTE: 卡片带过来的那张优先，而不是详情页的 og:image。理由有两个：它和卡片上看到的
    // 是同一张（容器长到位时画面不会变），而且它已经在 Coil 的内存缓存里（不会从纯色淡入）。
    val poster = posterUrl ?: detail?.thumbnailUrl

    // 上滑进全屏 / 下滑退全屏：跟手进度 0..1。转屏本身没法跟手（那是另一套窗口配置），
    // 所以松手过阈值之后再交给系统转，跟手这一段负责「越拖越大 / 越拖越缩」。
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val animationScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val statusBarInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    // NOTE: 播放器必须和正文**并列**在一个 Box 里，不能只是 Column 的兄弟：上滑放大时它要长到
    // 正文上面去，而 Column 中后一个孩子会把前一个盖住。所以正文让出播放器高度的占位，
    // 播放器自己绝对定位在顶部、zIndex 抬到最上面。
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        // 内嵌播放器的高度：整宽 16:9。正文从这个高度之下开始。
        val videoHeight = maxWidth * 9f / 16f
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // 提交阈值：竖屏浅、横屏深（理由见 DragCommitPortrait / Landscape）。
        val commitThresholdPx = screenHeightPx * if (fullscreen) DragCommitLandscape else DragCommitPortrait

        val dragModifier = Modifier.pointerInput(fullscreen, commitThresholdPx) {
            var accumulated = 0f
            fun amountOf(): Float = if (fullscreen) accumulated else -accumulated
            detectVerticalDragGestures(
                onDragStart = { accumulated = 0f },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    accumulated += dy
                    // 竖屏只认上滑，横屏只认下滑；反方向一律不跟手。
                    val amount = amountOf()
                    dragProgress = if (amount > 0f) {
                        (amount / commitThresholdPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                onDragEnd = {
                    if (amountOf() >= commitThresholdPx) {
                        animationScope.launch {
                            animate(initialValue = dragProgress, targetValue = 1f, animationSpec = tween(DragCommitMs)) { value, _ ->
                                dragProgress = value
                            }
                            // 交出去：转屏由系统做，布局换成全屏后进度归零。
                            fullscreen = !fullscreen
                            dragProgress = 0f
                        }
                    } else {
                        animationScope.launch {
                            animate(initialValue = dragProgress, targetValue = 0f, animationSpec = tween(DragCancelMs)) { value, _ ->
                                dragProgress = value
                            }
                        }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        animate(initialValue = dragProgress, targetValue = 0f, animationSpec = tween(DragCancelMs)) { value, _ ->
                            dragProgress = value
                        }
                    }
                },
            )
        }

        if (!fullscreen) {
            Column(Modifier.fillMaxSize().padding(top = statusBarInset)) {
                // 播放器的位置占位（播放器本身在下面绝对定位，见上）。
                Spacer(Modifier.height(videoHeight))

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
                            onSeekBy = player?.let { currentPlayer ->
                                { deltaMs ->
                                    currentPlayer.seekTo((currentPlayer.currentPosition + deltaMs).coerceAtLeast(0L))
                                }
                            },
                            playlists = playlists,
                            onOpenPlaylists = onOpenPlaylists,
                            onClosePlaylists = onClosePlaylists,
                            onTogglePlaylist = onTogglePlaylist,
                            onOpenPath = onOpenPath,
                            onSearch = onSearch,
                        )
                    }
                }
            }
        }

        // 上滑跟手时整页压暗（YouTube：视频在放大、后面那页变暗）。全屏那边底本来就是黑的。
        if (!fullscreen && dragProgress > 0.001f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * dragProgress)))
        }

        // NOTE: 详情页**没有返回行**（2026-11-16 定的）：这一页的全部内容就是视频，返回行是纯
        // chrome，白占掉播放器上方 56dp。退出一律用系统返回（侧滑 / 三键 / 预测性返回的手势预览）。
        // NOTE: 也不做「浮在画面左上角」的那颗键——它会实实在在挡住画面（2026-11-15 实机否掉），
        // 所以那次是整行去掉，不是把它挪进画面里。
        DetailHero(
            state = state,
            poster = poster,
            heroKey = heroKeyFor(heroTag, videoId.ifEmpty { detail?.id.orEmpty() }),
            isCurrent = isCurrent,
            fullscreen = fullscreen,
            startPositionMs = startPositionMs,
            onPositionReport = onPositionReport,
            onPlayerAvailable = { player = it },
            onToggleFullscreen = { fullscreen = !fullscreen },
            dragProgress = dragProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // CRITICAL: 这里只能 offset，**不能** padding。padding 在 modifier 链上会缩掉
                // 播放器自己的可用高度（16:9 的框被压成 16:9 - 状态栏高，实机量出来是 2.07:1），
                // offset 只是整体下移，框本身还是严丝合缝的 16:9。
                // CRITICAL: 只有内嵌才让出状态栏。全屏时系统栏是隐藏的，可它一旦被算进 inset
                // （比如全屏里开了抽屉、那个窗口抢走焦点），这里再 offset 就会把整块画面顶下去。
                .offset {
                    val top = if (fullscreen) 0.dp else statusBarInset
                    IntOffset(0, top.toPx().roundToInt())
                }
                .zIndex(1f)
                .then(dragModifier),
        )
    }
}

/**
 * 切换全屏的提交阈值：整屏高度的几分之几。
 *
 * NOTE: 两个方向**不是**同一个值：竖屏是"想看大画面"的主动动作，给低一点（滑一点就进）；
 * 横屏的下滑要更刻意才退出——否则看片时手一动就掉回竖屏。
 */
private const val DragCommitPortrait = 0.12f
private const val DragCommitLandscape = 0.26f
private const val DetailSeekStepShortMs = 60 * 1000L
private const val DetailSeekStepLongMs = 10 * 60 * 1000L

/** 过阈值后先把跟手进度补完再交出去；没到阈值就回弹。 */
private const val DragCommitMs = 120
private const val DragCancelMs = 180





/**
 * 标题：固定在动作条下面，不随信息卡滚动。
 *
 * NOTE: 最多 3 行、超出折成省略号：站点标题常常是整句番号 + 剧情梗概，不限行时一屏会被它吃掉
 * （2026-11-15 实机）。装不下的可点展开/收起，判断和信息行同一套（看实测排版，不猜内容长度）。
 * NOTE: 时长不在这里重复——播放器控件条右侧已经显示总时长，两处一样的信息。
 */
@Composable
private fun DetailTitle(detail: VideoDetail, interactionEnabled: Boolean = true) {
    var expanded by rememberSaveable(detail.id) { mutableStateOf(false) }
    var overflowing by remember(detail.id) { mutableStateOf(false) }
    val label = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand)
    Text(
        text = detail.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (expanded) {
                if (result.lineCount > 3) overflowing = true
            } else {
                overflowing = result.hasVisualOverflow
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // NOTE: clickable 在 padding 之后：命中区只覆盖文字本身，和它当初在列表里时一致。
            .then(
                if (overflowing && interactionEnabled) {
                    Modifier.clickable(onClickLabel = label) { expanded = !expanded }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * 播放器下面的一切：标题 / 信息行 / 简介，以及底部的收藏与片单动作。
 *
 * NOTE: 播放器不在这里——它由 [DetailHero] 固定在页首，本函数只负责剩余内容。
 * NOTE: 这里自己开一个 Column：它是 [PageStateTransition] 的内容，拿不到外层的 ColumnScope，
 * 所以 `weight(1f)` 只能由这一层的 Column 提供。
 * NOTE: 版式跟设置页 / 我的同一套：标题是 `titleMedium`，其余内容装进**分组容器**
 * （`GroupSurface`），行间用 `outlineVariant` 分隔线，内外边距 16dp。之前这里是一串裸
 * `bodySmall` 文字，密度和其余页面差一档，读起来像另一个 App。
 * NOTE: 收藏 / 片单那一行固定在**本函数最上面**（也就是视频正下方），不随正文滚动。
 * NOTE: 标题**也是固定的**（2026-11-16 定的）：它和那两个动作是同一块「这个视频」的决策区，
 * 跟着信息卡滚走之后，滚到页尾就再也看不到自己在看哪一部。滚动的只剩信息卡与简介。
 */
@Composable
private fun DetailBody(
    detail: VideoDetail,
    message: UiMessage?,
    onToggleSaved: () -> Unit,
    onSeekBy: ((Long) -> Unit)?,
    playlists: PlaylistUiState,
    onOpenPlaylists: () -> Unit,
    onClosePlaylists: () -> Unit,
    onTogglePlaylist: (VideoPlaylist) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
    onSearch: (String) -> Unit,
) {
    // NOTE: 触觉只用来确认「状态改变」，不用在导航与滚动上（见 DESIGN.md「Motion」）。收藏要等一次
    // 网络请求，界面上的文字与颜色都要等到结果回来才变——按下去这一下是它唯一即时的回应。
    val haptics = LocalHapticFeedback.current
    // 读屏用的「已收藏」说明：按钮上的文字固定是「收藏」，状态只能从这里出去。
    val savedText = stringResource(R.string.detail_saved)
    // 每部影片默认收起；旋转或全屏往返仍保留用户当前选择。
    var seekControlsOpen by rememberSaveable(detail.id) { mutableStateOf(false) }
    val seekScaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val seekEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val titleVisibility by animateFloatAsState(
        targetValue = if (seekControlsOpen) 0f else 1f,
        animationSpec = seekEffectsSpec,
        label = "detailTitleVisibility",
    )
    val titleThreeLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.titleMedium.lineHeight.toDp() * 3
    }

    // NOTE: 固定区（动作条 + 标题）**浮在列表上面**，不是和列表上下拼接。拼接时内容滚到标题
    // 下面会被一条看不见的边硬切断（2026-11-16 反馈「直接硬切割」）；浮起来之后卡片是从标题
    // 底下滑进去的，再按滚动状态给 2dp 浅阴影。不滚动时没有阴影，不留痕迹。
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
    val headerShadow by animateDpAsState(
        targetValue = if (scrolled) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "headerShadow",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = headerHeight + 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // NOTE: 收藏 / 片单两个动作在固定区里（视频正下方那行 SplitButton），标题也是；
            // 这里只剩可以滚的内容。
            message?.let { notice ->
                item(key = "message") {
                    Text(
                        text = notice.text(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (detail.info.isNotEmpty()) {
                item(key = "info") {
                    GroupSurface(modifier = Modifier) {
                        detail.info.forEachIndexed { index, row ->
                            if (index > 0) GroupDivider()
                            InfoRow(
                                row = row,
                                videoId = detail.id,
                                onOpenPath = onOpenPath,
                                onSearch = onSearch,
                            )
                        }
                    }
                }
            }

            detail.description?.let { description ->
                item(key = "description") {
                    GroupSurface(modifier = Modifier) {
                        DescriptionCard(description = description)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .onSizeChanged { headerHeightPx = it.height },
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = headerShadow,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            // NOTE: 动作条贴在**视频正下方**（视频和它都是固定不滚的），不是常驻底栏。
            // 理由：这两个动作属于「这个视频」，玩家和它们是同一个决策块；而视频本身不滚，
            // 贴着它同样一屏可见，还省掉了底部那条栏：信息卡可以一直滚到屏幕底，也不存在
            // 「底栏容器色 vs 卡片容器色」的色阶问题（2026-11-15 实机）。
            // NOTE: 收藏状态来自 /api/items/{id}/view；401 会先自动重登一次（见 withAuthRetry）。
            // NOTE: 它和正文一起走 fade through——动作条属于「内容到达」，不该比文字先到或后到。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 展开面板经过标题上方时，原按钮仍必须在最上层，连续点击才不会落进 +10 分钟。
                    .zIndex(1f)
                    // NOTE: 上面 12dp、下面只有 6dp，**看起来**才是等距：标题是文字，字身框自带一截
                    // 上边距（titleMedium 24sp 行高里，字形还没开始就已经占了几 dp），按 12/12 排下来
                    // 标题那一侧的视觉间距会明显宽一档（2026-11-16 反馈）。
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            ) {
                SplitButtonLayout(
                    modifier = Modifier.weight(1f),
                    leadingButton = {
                        // NOTE: 用 **tonal** 变体而不是默认填充：容器色落成 `secondaryContainer`
                        // （这套色板里是柔和玫瑰），和页面其余部分（surface 系 + 色阶）同一档，
                        // 不会出现一块高饱和的蓝压在内容中间（DESIGN.md 的 Anti-references）。
                        SplitButtonDefaults.TonalLeadingButton(
                            onClick = {
                                // 语义按「这次点击会把它变成什么」给：已收藏 → ToggleOff。
                                haptics.performHapticFeedback(
                                    if (detail.saved) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                )
                                onToggleSaved()
                            },
                        ) {
                            // NOTE: 文字固定「收藏」不随状态变（`detail_saved` 会多一个字，左右半的宽度
                            // 会跟着跳、右半跟着位移）；状态由**爱心**表达：空心 = 未收藏，实心红 = 已收藏。
                            // NOTE: 图标自己不带标签（文字已经是动作名），收藏态通过 `stateDescription`
                            // 交给读屏，否则“已收藏”这个状态对无障碍用户不可见。
                            Row(
                                modifier = Modifier.semantics {
                                    if (detail.saved) stateDescription = savedText
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (detail.saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (detail.saved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.detail_save))
                            }
                        }
                    },
                    trailingButton = {
                        // NOTE: `SplitButtonDefaults` 没有「tonal + 普通点击」的尾半（TonalTrailingButton
                        // 只有开关变体，带 checked/onCheckedChange），所以尾半保留默认按钮、只把
                        // 颜色换成 tonal——两半的容器色就一致了。
                        SplitButtonDefaults.TrailingButton(
                            onClick = onOpenPlaylists,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            // NOTE: 右半也带文字：两半都只有文字或都只有图标才"整齐"；左半有字、右半裸图标
                            // 会读成两个不同层级的控件。图标 18dp 与左半对齐，标签就是动作名，图标不再另挂
                            // contentDescription（按钮会把文字作为无障碍名）。
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                // NOTE: 标签是「片单」两个字，不是「加入片单」——和左半的「收藏」等长，
                                // 两半才齐整；动词由图标 + 位置表达。完整的「加入片单」留给片单面板标题。
                                Text(stringResource(R.string.detail_playlist))
                            }
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(
                            if (seekControlsOpen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                        )
                        seekControlsOpen = !seekControlsOpen
                    },
                    enabled = onSeekBy != null || seekControlsOpen,
                    colors = if (seekControlsOpen) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    },
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .size(width = 64.dp, height = 40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = stringResource(
                            if (seekControlsOpen) R.string.player_hide_seek_controls else R.string.player_show_seek_controls,
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = titleThreeLineHeight)) {
                Box(
                    Modifier
                        .graphicsLayer {
                            alpha = titleVisibility
                            scaleX = 0.96f + 0.04f * titleVisibility
                            scaleY = 0.96f + 0.04f * titleVisibility
                            translationY = (1f - titleVisibility) * 8.dp.toPx()
                        }
                        .then(
                            if (seekControlsOpen) Modifier.clearAndSetSemantics { } else Modifier,
                        ),
                ) {
                    DetailTitle(detail, interactionEnabled = !seekControlsOpen)
                }
                // NOTE: 标题始终参与测量，面板用 matchParentSize 覆盖；快速反向时两边也只反转、不重排。
                androidx.compose.animation.AnimatedVisibility(
                    visible = seekControlsOpen,
                    modifier = Modifier.matchParentSize(),
                    // scale 不裁剪 Surface：托盘、内容和轮廓始终同一层做弹簧变换。
                    enter = fadeIn(seekEffectsSpec) +
                        scaleIn(
                            animationSpec = seekScaleSpec,
                            initialScale = 0.85f,
                            transformOrigin = TransformOrigin(1f, 0f),
                        ),
                    exit = fadeOut(seekEffectsSpec) +
                        scaleOut(
                            animationSpec = seekScaleSpec,
                            targetScale = 0.85f,
                            transformOrigin = TransformOrigin(1f, 0f),
                        ),
                    label = "seekControlsPanel",
                ) {
                    val settled = transition.currentState == EnterExitState.Visible &&
                        transition.targetState == EnterExitState.Visible
                    SeekControlPanel(onSeekBy = onSeekBy, interactionsEnabled = settled)
                }
            }
        }
        }
    }

    if (playlists !is PlaylistUiState.Hidden) {
        PlaylistSheet(
            state = playlists,
            onDismiss = onClosePlaylists,
            onRetry = onOpenPlaylists,
            onToggle = onTogglePlaylist,
        )
    }
}

@Composable
private fun SeekControlPanel(
    onSeekBy: ((Long) -> Unit)?,
    interactionsEnabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeekControlButton(
                deltaMs = -DetailSeekStepLongMs,
                sign = "−",
                minutes = 10,
                description = stringResource(R.string.player_rewind_10_minutes),
                onSeekBy = onSeekBy,
                interactionsEnabled = interactionsEnabled,
                modifier = Modifier.weight(1f),
            )
            SeekControlButton(
                deltaMs = -DetailSeekStepShortMs,
                sign = "−",
                minutes = 1,
                description = stringResource(R.string.player_rewind_1_minute),
                onSeekBy = onSeekBy,
                interactionsEnabled = interactionsEnabled,
                modifier = Modifier.weight(1f),
            )
            SeekControlButton(
                deltaMs = DetailSeekStepShortMs,
                sign = "+",
                minutes = 1,
                description = stringResource(R.string.player_forward_1_minute),
                onSeekBy = onSeekBy,
                interactionsEnabled = interactionsEnabled,
                modifier = Modifier.weight(1f),
            )
            SeekControlButton(
                deltaMs = DetailSeekStepLongMs,
                sign = "+",
                minutes = 10,
                description = stringResource(R.string.player_forward_10_minutes),
                onSeekBy = onSeekBy,
                interactionsEnabled = interactionsEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SeekControlButton(
    deltaMs: Long,
    sign: String,
    minutes: Int,
    description: String,
    onSeekBy: ((Long) -> Unit)?,
    interactionsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = { if (interactionsEnabled) onSeekBy?.invoke(deltaMs) },
        enabled = onSeekBy != null,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
            .height(ButtonDefaults.MinHeight)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = stringResource(R.string.player_seek_minutes_compact, sign, minutes),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
