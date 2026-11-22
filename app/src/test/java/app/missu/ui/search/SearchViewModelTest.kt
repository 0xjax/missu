package app.missu.ui.search

import app.missu.MainDispatcherRule
import app.missu.data.model.VideoList
import app.missu.data.model.VideoSummary
import app.missu.data.store.SearchHistory
import app.missu.ui.common.LoadMoreState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `new search wins after the previous request is cancelled`() = runTest {
        val oldResponse = CompletableDeferred<VideoList>()
        val newResponse = CompletableDeferred<VideoList>()
        val viewModel = SearchViewModel(FakeSearchHistory()) { keyword, _, _, _ ->
            if (keyword == "old") oldResponse.await() else newResponse.await()
        }

        viewModel.search("old")
        viewModel.search("new")
        newResponse.complete(page("new"))
        advanceUntilIdle()

        val state = viewModel.state.value as SearchUiState.Content
        assertEquals("new", state.keyword)
        assertEquals(listOf("new"), state.videos.map { it.id })
    }

    @Test
    fun `pagination failure keeps content and retry removes duplicates`() = runTest {
        var secondPageAttempts = 0
        val viewModel = SearchViewModel(FakeSearchHistory()) { _, page, _, _ ->
            if (page == 1) return@SearchViewModel VideoList(listOf(video("one")), nextPage = 2)
            secondPageAttempts += 1
            if (secondPageAttempts == 1) throw IOException("offline")
            VideoList(listOf(video("one"), video("two")))
        }

        viewModel.search("query")
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        var state = viewModel.state.value as SearchUiState.Content
        assertEquals(LoadMoreState.Failed, state.more)
        assertEquals(listOf("one"), state.videos.map { it.id })

        viewModel.loadMore()
        advanceUntilIdle()

        state = viewModel.state.value as SearchUiState.Content
        assertEquals(LoadMoreState.Idle, state.more)
        assertEquals(listOf("one", "two"), state.videos.map { it.id })
        assertEquals(null, state.nextPage)
    }

    @Test
    fun `blank search clears results without changing history`() = runTest {
        val history = FakeSearchHistory()
        val viewModel = SearchViewModel(history) { _, _, _, _ -> page("one") }
        viewModel.search("one")
        advanceUntilIdle()

        viewModel.search("   ")

        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals(listOf("one"), viewModel.recent.value)
        assertTrue(viewModel.state.value !is SearchUiState.Loading)
    }
}

private class FakeSearchHistory : SearchHistory {
    private val entries = mutableListOf<String>()

    override fun recent(): List<String> = entries.toList()

    override fun record(keyword: String) {
        entries.removeIf { it.equals(keyword, ignoreCase = true) }
        entries.add(0, keyword)
    }

    override fun clear() = entries.clear()
}

private fun page(id: String, nextPage: Int? = null) = VideoList(listOf(video(id)), nextPage)

private fun video(id: String) = VideoSummary(id = id, title = id, pageUrl = "/$id")
