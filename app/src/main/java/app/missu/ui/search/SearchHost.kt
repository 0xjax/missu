@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.VideoGridCard
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
    modifier: Modifier = Modifier,
    recent: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
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

    // NOTE: 搜索态是**同一个目的地里的第二层**（栏就地展开、结果是同一屏的内容替换），返回栈上
    // 没有它的条目。不接管返回键的话，系统返回会一路走到 Activity，也就是「从搜索结果页直接退出
    // 应用」。这里让它退一层：先回到首页内容，首页再按才是退出。
    // NOTE: 展开但还没提交查询也算一层——栏开着就说明用户进入了搜索模式，返回应当先收起。
    BackHandler(enabled = searching || searchBarState.targetValue == SearchBarValue.Expanded) {
        exit()
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

    // NOTE: 搜索胶囊**固定不动**：它就是页面顶部一层浮起的栏，内容在它底下滚过去。
    // 不跟随滚动、不收起、不归位——这是最简也最可预期的一版。
    // NOTE: 首页是唯一让内容从 y=0 开始的页面（`PageSurface(contentInset = false)`）：
    // 卡片能从状态栏底下滚过去，状态栏那块压一层**纯色半透明**，图标才不会被亮图吃掉。
    // 其它页面照旧让开状态栏，所以它们的顶部一个像素都不变。
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 状态栏那块纯色、以及下面那段渐隐的起点，共用这一个 70% 的页面底色。
    val veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
    // NOTE: 胶囊的形状只定义一次，`SearchBar` 与阴影共用——以后改高度不会有一处漏改。
    val pillShape = RoundedCornerShape(SearchBarHeight / 2)
    Box(modifier.fillMaxSize()) {
        // NOTE: 内边距给的是「状态栏 + 胶囊那一条」：内容从状态栏底下开始滚，
        // 但第一行落在胶囊下面，不会被胶囊压住。
        CompositionLocalProvider(LocalSearchBarInset provides statusTop + SearchBarBlock) {
            Column(Modifier.fillMaxSize()) { content() }
        }
        // NOTE: 状态栏那一块**保持纯色半透明**（`veil` = 页面底色 70%），高度正好是状态栏。
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop)
                .background(veil),
        )
        // NOTE: 从**状态栏下沿**到**胶囊中线**再接一段渐隐（70% → 全透明），把两段接缝抹平；
        // 状态栏本身不参与渐变。
        // NOTE: 曲线用 1 - t²（下面几档 stop 是它的近似）：上面掉得慢、下面掉得快，
        // 这样它读起来是「内容逐渐消散」，而不是一条边界明显的带子。
        // NOTE: 画在胶囊**之前**——胶囊自己保持清晰，只有它背后的内容被压暗。
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusTop)
                .fillMaxWidth()
                .height(SearchBarTopGap + SearchBarHeight / 2)
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
        // 胶囊：停在「状态栏下沿 + 8dp」，浮在内容之上。
        // NOTE: 它自己**没有**任何渐变挡板（挡板等于把内容洗成底色，压在图片上像一块脏污）；
        // 上面那两段渐隐属于系统栏，只铺在胶囊之下、不覆盖胶囊。
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusTop),
        ) {
            // NOTE: 左右 16dp 与内容栅格同一条边线（首页/列表的卡片留白也是 16dp）。
            // NOTE: 顶部 8dp 不是审美值，是 material3 展开态给输入框的落点（SearchBarVerticalPadding）：
            // 折叠态也停在「状态栏 + 8dp」，展开全程输入框的 y 才是恒定的（少这 8dp 它会中途往下滑一次）。
            // NOTE: 阴影加在 `padding` 之后，套的是胶囊自己的形状（`pillShape` = 半高圆角 = 药丸），
            // 与传给 `SearchBar` 的是同一个值，避免两处形状假设。
            // 6dp 在亮色主题下压着亮图片也读得出来；暗色主题里阴影本来不明显，分层主要靠
            // `surfaceContainerHigh` 的色调差。
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
                    // NOTE: 层级只靠阴影。1dp 描边试过并否掉——它会把胶囊读成一张贴纸。
                    .shadow(elevation = 6.dp, shape = pillShape),
            )
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

/** 胶囊上方的 8dp（material3 展开态给输入框的落点）。 */
private val SearchBarTopGap = 8.dp

/** 折叠态胶囊的高度（material3 的 56dp）。 */
private val SearchBarHeight = 56.dp

/** 胶囊下方那 12dp 留白。 */
private val SearchBarBottomGap = 12.dp

/** 搜索胶囊这一条占的高度：8 + 56 + 12 = 76dp。 */
private val SearchBarBlock = SearchBarTopGap + SearchBarHeight + SearchBarBottomGap

/**
 * 有搜索胶囊的页面，内容顶部要让出来的高度（= 状态栏 + 胶囊那一条，见 [SearchBarBlock]）。
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

            is SearchUiState.Loading -> LoadingHint()

            is SearchUiState.Empty -> EmptyHint(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.search_empty_title),
                subtitle = stringResource(R.string.search_empty_subtitle),
            )

            is SearchUiState.Error -> ErrorHint(
                message = current.message.text(),
                onRetry = { onRetry(current.keyword) },
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
    val gridState = rememberLazyGridState()
    LoadMoreOnScroll(
        state = gridState,
        enabled = state.more == LoadMoreState.Idle,
        onLoadMore = onLoadMore,
    )
    LazyVerticalGrid(
        state = gridState,
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

        item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
            LoadMoreFooter(nextPage = state.nextPage, state = state.more, onRetry = onLoadMore)
        }
    }
}
