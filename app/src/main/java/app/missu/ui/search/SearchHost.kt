@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.VideoGridCard
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 搜索宿主：**一个** `SearchBar` 就地展开，而不是「首页假框 → 跳搜索页」。
 *
 * NOTE: M3 的搜索模式是同一个栏展开成全屏搜索视图。之前首页那只"输入框"其实是个 `Box`（44dp / 10dp 圆角 /
 * 无图标），搜索页才是真 `SearchBar`（56dp / 胶囊 / 带搜索图标），四项几何全不一样，切换时必然割裂。
 * 现在首页直接把这里吐出的折叠态当入口：点一下就在当前页展开，提交后结果出现在同一屏，
 * 栏收回去、不再有页面切换，键盘也不会重启。
 * NOTE: 展开/收起都是 suspend（`SearchBarState.animateToExpanded/Collapsed`），只能放进 `scope.launch`。
 * NOTE: 触摸模式下输入框获得焦点会自动展开，不需要手动调 `animateToExpanded()`。
 *
 * @param content 栏下面的内容：搜索态为 Idle 时由调用方放首页内容，否则放 [SearchBody]。
 */
@Composable
fun SearchHost(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onClear: () -> Unit,
    recent: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val searching = state !is SearchUiState.Idle

    // NOTE: foundation 1.13 的 TextFieldState 只有 edit { }（没有 setTextAndPlaceCursorAtEnd/clearText）。
    fun submit(keyword: String) {
        if (keyword.isBlank()) return
        scope.launch { searchBarState.animateToCollapsed() }
        onSearch(keyword)
    }

    fun pick(keyword: String) {
        textFieldState.edit { replace(0, length, keyword) }
        submit(keyword)
    }

    fun exit() {
        textFieldState.edit { replace(0, length, "") }
        scope.launch { searchBarState.animateToCollapsed() }
        onClear()
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { submit(textFieldState.text.toString().trim()) },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                // NOTE: 只有出结果之后才给「返回首页内容」的出口；Idle 时左侧就是一个纯搜索图标，
                // 别做成按钮——否则它会把「点一下就展开」的整块命中区截走一块。
                if (searching) {
                    IconButton(onClick = { exit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_exit))
                    }
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.edit { replace(0, length, "") } }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_clear))
                    }
                }
            },
        )
    }

    // NOTE: 浮动胶囊（不是把胶囊挤走，而是从内容上面滑走）：
    // - 内容在胶囊**下面**滚，靠 `contentPadding.top`（[LocalSearchBarInset]）让出静止时的位置；
    // - 胶囊**不占布局**，位移跟着手指 1:1 变化，松手（含 fling 结束）才弹簧归位到全露或全收。
    // 这样胶囊走开时内容一点都不用动——布局收缩那种「内容被抽一下」的观感就不存在了。
    val blockPx = with(LocalDensity.current) { SearchBarBlock.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    // NOTE: 归位**不能**用 expressive 的 spatial 弹簧：那档是带过冲的（阻尼 < 1），
    // 收完会先越过 0 再弹回来——看起来就是「归位成功后又抖一下」。边界动画不该有过冲，
    // 这里显式取 standard 方案（无过冲的那一套）。
    val settleSpec = MotionScheme.standard().fastSpatialSpec<Float>()
    val scrollConnection = remember(blockPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // NOTE: 只有列表**真的滚掉**的位移才驱动胶囊（`consumed`），下拉刷新的余量不参与。
                if (consumed.y != 0f) offset = (offset - consumed.y).coerceIn(0f, blockPx)
                return Offset.Zero
            }

            // NOTE: 松手才归位：过一半收完，否则回来——「擦一下」不会把搜索入口弄丢。
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val target = if (offset > blockPx * 0.5f) blockPx else 0f
                if (offset != target) {
                    settle.snapTo(offset)
                    settle.animateTo(target, settleSpec) { offset = value.coerceIn(0f, blockPx) }
                }
                return Velocity.Zero
            }
        }
    }
    // NOTE: 搜索态一变就把胶囊放回来——刚出结果却看不到搜索入口最容易迷路。
    LaunchedEffect(searching) { offset = 0f }

    // NOTE: 顶部状态栏内边距在这里（不在外层 Scaffold）：页面底色要铺到状态栏底下，
    // 页面缩小成卡片时才会把状态栏一起带上。
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .nestedScroll(scrollConnection),
    ) {
        CompositionLocalProvider(LocalSearchBarInset provides SearchBarBlock) {
            Column(Modifier.fillMaxSize()) { content() }
        }
        // NOTE: 完全收起后整层都不画。
        if (offset < blockPx) {
            // NOTE: 外面这层定高盒子是**裁剪边界**：胶囊滑出去时要在「页面顶部」被切掉，
            // 而不是画到状态栏上（实测未裁时胶囊会压住状态栏的时间）。
            // 它是浮层子项（Box 里并列），不参与内容布局。
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(SearchBarBlock)
                    .clipToBounds(),
            ) {
                // NOTE: 胶囊跟着位移走（`offset { }` 的 lambda 版本只走 layout 阶段，不触发重组）。
                // NOTE: 不用渐变挡板——挡板等于把内容洗成底色，压在图片上像一块脏污；改成给胶囊
                // 一层阴影表达「浮在内容之上」，内容照原样从底下穿过去，不改它的颜色。
                Box(Modifier.offset { IntOffset(0, -offset.roundToInt()) }) {
                    // NOTE: 左右 16dp 与内容栅格同一条边线（首页/列表的卡片留白也是 16dp）；之前是通屏，
                    // 药丸比下面所有卡片都宽出去 16dp，看起来像没做完。
                    // NOTE: 顶部 8dp 不是审美值，是 material3 展开态给输入框的落点（SearchBarVerticalPadding）：
                    // 折叠态也停在「状态栏 + 8dp」，展开全程输入框的 y 才是恒定的（少这 8dp 它会中途往下滑一次）。
                    SearchBar(
                        state = searchBarState,
                        inputField = inputField,
                        // NOTE: 底部 12dp 是**静止时**胶囊与第一行之间的留白。滚动中卡片从胶囊底下穿过去，
                        // 这里不再充当「裁剪边界」——胶囊浮在内容之上，不参与布局。
                        // NOTE: 阴影加在 `padding` 之后，套的是胶囊自己的形状（折叠态是通栏药丸，
                        // 用 `CircleShape` 就是 28dp 圆角）。6dp 在亮色主题下压着亮图片也读得出来；
                        // 暗色主题里阴影本来不明显，分层主要靠 `surfaceContainerHigh` 的色调差。
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape),
                    )
                }
            }
        }
    }

    // 展开态：全屏浮层里放最近搜索。
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        RecentSearches(
            keywords = recent,
            onPick = { pick(it) },
            onClear = onClearHistory,
        )
    }
}

/**
 * 搜索胶囊这一条占的高度：8dp 顶 + 56dp 胶囊 + 12dp 底留白 = 76dp。
 * NOTE: 内容的 `contentPadding.top` 用它（见 [LocalSearchBarInset]），
 * 那份内边距跟着内容一起滚掉，所以滚动时卡片是从胶囊底下穿过去的。
 */
private val SearchBarBlock = 76.dp

/**
 * 有搜索胶囊的页面，内容顶部要让出来的高度。
 * NOTE: 胶囊浮在内容之上、不占布局，内容只能靠这个内边距避开它；没有胶囊的页面是 0。
 */
val LocalSearchBarInset = staticCompositionLocalOf { 0.dp }

/** 搜索结果区（Idle 不在这里渲染——那是首页内容的地盘）。 */
@Composable
fun SearchBody(
    state: SearchUiState,
    keyword: String,
    onRetry: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageStateTransition(state = state, modifier = modifier.fillMaxSize()) { current ->
        when (current) {
            SearchUiState.Idle -> Unit

            SearchUiState.Loading -> LoadingHint()

            SearchUiState.Empty -> EmptyHint(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.search_empty_title),
                subtitle = stringResource(R.string.search_empty_subtitle),
            )

            is SearchUiState.Error -> ErrorHint(
                message = current.message.text(),
                onRetry = { onRetry(keyword) },
            )

            is SearchUiState.Content -> Results(
                state = current,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
            )
        }
    }
}

/** 最近搜索：点一下直接搜。 */
@Composable
private fun RecentSearches(
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
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        // NOTE: 顶部让出搜索胶囊那一条（见 [LocalSearchBarInset]），静止时第一行落在胶囊下面；
        // 滚动时这份内边距跟着内容滚掉，卡片从胶囊底下穿过去。
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = LocalSearchBarInset.current,
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

        if (state.nextPage != null) {
            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreFooter(loading = state.loadingMore, onLoadMore = onLoadMore)
            }
        } else {
            item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
