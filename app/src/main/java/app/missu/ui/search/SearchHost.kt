@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import app.missu.R
import app.missu.data.api.VideoListOptions
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.VideoGridCard
import app.missu.ui.list.VideoListControls
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 搜索宿主：全宽 `SearchBar` 或行内圆形按钮，就地展开为全屏搜索，不另跳路由。
 *
 * NOTE: 展开/收起都是 suspend（`SearchBarState.animateToExpanded/Collapsed`），只能放进 `scope.launch`。
 * 行内按钮没有可聚焦的折叠输入框，因此展开完成后显式请求焦点。
 *
 * @param content 搜索态为 Idle 时放原页面内容，否则放 [SearchBody]；`searchAction` 可嵌入原页面工具行。
 */
@Composable
fun SearchHost(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    initialKeyword: String = "",
    recent: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
    onExit: (() -> Unit)? = null,
    inlineTrigger: Boolean = false,
    content: @Composable ColumnScope.(searchAction: @Composable () -> Unit) -> Unit,
) {
    SearchHostShell(
        searching = state !is SearchUiState.Idle,
        onSearch = onSearch,
        onClear = onClear,
        modifier = modifier,
        initialKeyword = initialKeyword,
        placeholder = stringResource(R.string.search_placeholder),
        onExit = onExit,
        inlineTrigger = inlineTrigger,
        idleContent = { pick ->
            RecentSearches(
                keywords = recent,
                onPick = pick,
                onClear = onClearHistory,
            )
        },
        content = content,
    )
}

/** 影片与漫画共用的搜索按钮、输入态、工具框形变和返回规则。 */
@Composable
internal fun SearchHostShell(
    searching: Boolean,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    initialKeyword: String = "",
    placeholder: String,
    onExit: (() -> Unit)? = null,
    inlineTrigger: Boolean = false,
    onQueryChange: ((String) -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    idleContent: @Composable (onPick: (String) -> Unit) -> Unit,
    content: @Composable ColumnScope.(searchAction: @Composable () -> Unit) -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(initialText = initialKeyword)
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var triggerBounds by remember { mutableStateOf<Rect?>(null) }
    var expansionAnchor by remember { mutableStateOf<Rect?>(null) }
    val expansionProgress = remember { Animatable(0f) }
    var expansionVisible by remember { mutableStateOf(false) }
    val expansionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(textFieldState, onQueryChange) {
        if (onQueryChange == null) return@LaunchedEffect
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { onQueryChange(it) }
    }

    fun collapseSearch() {
        scope.launch { searchBarState.animateToCollapsed() }
        if (inlineTrigger) {
            scope.launch {
                expansionProgress.animateTo(0f, expansionSpec)
                expansionVisible = false
            }
        }
    }

    // NOTE: foundation 1.13 的 TextFieldState 只有 edit { }（没有 setTextAndPlaceCursorAtEnd/clearText）。
    fun submit(keyword: String) {
        if (keyword.isBlank()) return
        collapseSearch()
        onSearch(keyword)
    }

    fun pick(keyword: String) {
        textFieldState.edit { replace(0, length, keyword) }
        submit(keyword)
    }

    fun openSearch() {
        expansionAnchor = triggerBounds
        expansionVisible = true
        scope.launch {
            expansionProgress.snapTo(0f)
            expansionProgress.animateTo(1f, expansionSpec)
        }
        scope.launch {
            searchBarState.animateToExpanded()
            if (inlineTrigger) focusRequester.requestFocus()
        }
    }

    fun exit() {
        val wasExpanded = searchBarState.targetValue == SearchBarValue.Expanded
        if (onExit == null || !wasExpanded) textFieldState.edit { replace(0, length, "") }
        collapseSearch()
        if (onExit == null) onClear() else if (!wasExpanded) onExit()
    }

    fun back() {
        if (inlineTrigger && searching && searchBarState.targetValue != SearchBarValue.Expanded) {
            openSearch()
        } else {
            exit()
        }
    }

    BackHandler(
        enabled = searchBarState.targetValue == SearchBarValue.Expanded || (onExit == null && searching),
    ) {
        back()
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { submit(textFieldState.text.toString().trim()) },
            modifier = if (inlineTrigger) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier,
            placeholder = { Text(placeholder) },
            leadingIcon = {
                if (inlineTrigger || searching) {
                    IconButton(onClick = { back() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.search_exit))
                    }
                } else {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.edit { replace(0, length, "") } }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_clear))
                    }
                }
            },
        )
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
    val pillShape = RoundedCornerShape(SearchBarHeight / 2)
    val searchAction = @Composable {
        InlineSearchButton(
            searching = searching,
            onClick = { if (searching) back() else openSearch() },
        )
    }
    val contentInset = statusTop + if (inlineTrigger) InlineSearchBlock else SearchBarBlock
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSearchBarInset provides contentInset,
            LocalSearchAnchorCapture provides { bounds -> triggerBounds = bounds },
        ) {
            Column(Modifier.fillMaxSize()) { content(searchAction) }
        }
        if (!inlineTrigger) {
            SearchTopVeil(statusTop, veil, SearchBarHeight)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTop),
            ) {
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    shape = pillShape,
                    modifier = Modifier
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = SearchBarTopGap,
                            bottom = SearchBarBottomGap,
                        )
                        .shadow(elevation = 6.dp, shape = pillShape),
                )
            }
        }
    }

    if (inlineTrigger) {
        InlineSearchExpansion(
            visible = expansionVisible,
            progress = expansionProgress.value.coerceIn(0f, 1f),
            anchor = expansionAnchor,
            statusTop = statusTop,
            inputField = inputField,
            onDismiss = { exit() },
        ) {
            if (searching && expandedContent != null) expandedContent() else idleContent { pick(it) }
        }
    } else {
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            if (searching && expandedContent != null) expandedContent() else idleContent { pick(it) }
        }
    }
}

@Composable
internal fun BoxScope.SearchTopVeil(
    statusTop: Dp,
    veil: Color,
    triggerHeight: Dp,
) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(statusTop)
            .background(veil),
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusTop)
            .fillMaxWidth()
            .height(SearchBarTopGap + triggerHeight / 2)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to veil,
                        0.45f to veil.copy(alpha = 0.52f),
                        0.75f to veil.copy(alpha = 0.24f),
                        1f to Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun InlineSearchExpansion(
    visible: Boolean,
    progress: Float,
    anchor: Rect?,
    statusTop: Dp,
    inputField: @Composable () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.setWindowAnimations(0)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val fullWidth = constraints.maxWidth.toFloat()
            val fullHeight = constraints.maxHeight.toFloat()
            val toolbarHeight = with(density) { InlineSearchBlock.toPx() }
            val fallback = with(density) {
                val margin = 16.dp.toPx()
                val top = statusTop.toPx() + SearchBarTopGap.toPx()
                Rect(margin, top, fullWidth - margin, top + toolbarHeight)
            }
            val start = anchor ?: fallback
            val left = start.left * (1f - progress)
            val top = start.top * (1f - progress)
            val width = start.width + (fullWidth - start.width) * progress
            val height = start.height + (fullHeight - start.height) * progress
            val containerShape = RoundedCornerShape(InlineSearchBlock / 2 * (1f - progress))

            Surface(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
                shape = containerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 1f - progress),
                ),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = progress },
                ) {
                    // 与原生 FullScreenSearchBarLayout 一致：顶部间距随进度增长，
                    // 输入框在收起终点与工具框顶部重合，展开终点才落到状态栏下方。
                    Spacer(Modifier.height((statusTop + SearchBarTopGap) * progress))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp * progress)) {
                        inputField()
                    }
                    Box(Modifier.weight(1f).padding(top = 8.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineSearchButton(
    searching: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(SearchTriggerWidth, SearchTriggerHeight),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = if (searching) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Search,
            contentDescription = stringResource(if (searching) R.string.search_edit else R.string.search_open),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 行内搜索按钮与同一行选项共用的高度。 */
internal val SearchTriggerHeight = 40.dp

/** 搜索托盘宽度为 40dp 高度的 1.5 倍。 */
private val SearchTriggerWidth = 60.dp

/** 胶囊上方的 8dp（material3 展开态给输入框的落点）。 */
internal val SearchBarTopGap = 8.dp

/** 折叠态胶囊的高度（material3 的 56dp）。 */
private val SearchBarHeight = 56.dp

/** 胶囊下方那 12dp 留白。 */
private val SearchBarBottomGap = 12.dp

/** 搜索胶囊这一条占的高度：8 + 56 + 12 = 76dp。 */
private val SearchBarBlock = SearchBarTopGap + SearchBarHeight + SearchBarBottomGap

/** 行内悬浮工具行占的高度：8 + 40 + 8 = 56dp。 */
private val InlineSearchBlock = SearchBarTopGap + SearchTriggerHeight + 8.dp

/**
 * 搜索宿主给滚动内容预留的顶部空间；内容首帧避开工具行，滚动后从其下方穿过。
 */
val LocalSearchBarInset = staticCompositionLocalOf { 0.dp }

/** 当前悬浮工具框把实际窗口边界交给搜索展开容器；非搜索页面不处理。 */
internal val LocalSearchAnchorCapture = staticCompositionLocalOf<(Rect) -> Unit> { {} }

/** 搜索结果区（Idle 不在这里渲染——那是原页面内容的地盘）。 */
@Composable
fun SearchBody(
    state: SearchUiState,
    keyword: String,
    sort: VideoListOptions.Sort?,
    filter: VideoListOptions.Filter?,
    detectedSort: VideoListOptions.Sort?,
    onSortChange: (VideoListOptions.Sort?) -> Unit,
    onFilterChange: (VideoListOptions.Filter?) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    modifier: Modifier = Modifier,
    searchAction: (@Composable () -> Unit)? = null,
    scrollToTopSignal: Int = 0,
) {
    val resultsContent: @Composable (Modifier) -> Unit = { bodyModifier ->
        PageStateTransition(state = state, modifier = bodyModifier) { current ->
            when (current) {
                SearchUiState.Idle -> Unit

                is SearchUiState.Loading -> LoadingHint()

                is SearchUiState.Empty -> EmptyHint(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.search_empty_title),
                    subtitle = stringResource(R.string.search_empty_subtitle),
                )

                is SearchUiState.Error -> ErrorHint(
                    message = current.message.text(),
                    onRetry = { onRetry(current.keyword) },
                )

                is SearchUiState.Content -> Results(
                    state = current,
                    scrollToTopSignal = scrollToTopSignal,
                    onLoadMore = onLoadMore,
                    onOpen = onOpen,
                    contentTopInset = if (searchAction == null) 0.dp else LocalSearchBarInset.current,
                )
            }
        }
    }
    val controls: @Composable () -> Unit = {
        VideoListControls(
            sort = sort,
            filter = filter,
            detectedSort = detectedSort,
            sorts = VideoListOptions.Sort.entries,
            filters = VideoListOptions.Filter.entries,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            controlsLeading = null,
            controlsTrailing = searchAction,
        )
    }

    if (searchAction == null) {
        Column(modifier.fillMaxSize().padding(top = LocalSearchBarInset.current)) {
            controls()
            resultsContent(Modifier.weight(1f))
        }
    } else {
        Box(modifier.fillMaxSize()) {
            resultsContent(Modifier.fillMaxSize())
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            SearchTopVeil(
                statusTop = statusTop,
                veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                triggerHeight = SearchTriggerHeight,
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = statusTop)) {
                controls()
            }
        }
    }
}

/** 最近搜索：点一下直接搜。 */
@Composable
internal fun RecentSearches(
    keywords: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (keywords.isEmpty()) {
        Hint(stringResource(R.string.search_hint_idle))
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keywords.forEach { keyword ->
                SuggestionChip(
                    onClick = { onPick(keyword) },
                    label = { Text(keyword) },
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Results(
    state: SearchUiState.Content,
    scrollToTopSignal: Int,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    contentTopInset: Dp,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) gridState.animateScrollToItem(0)
    }
    LoadMoreOnScroll(
        state = gridState,
        enabled = state.more == LoadMoreState.Idle,
        onLoadMore = onLoadMore,
    )
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(LocalVideoListColumns.current),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset + 12.dp,
            bottom = 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.search_results_count, state.keyword, state.videos.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(items = state.videos, key = { it.id + it.pageUrl }) { video ->
            VideoGridCard(video = video, onClick = { onOpen(video.id, video.thumbnailUrl) })
        }

        item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
            LoadMoreFooter(nextPage = state.nextPage, state = state.more, onRetry = onLoadMore)
        }
    }
}
