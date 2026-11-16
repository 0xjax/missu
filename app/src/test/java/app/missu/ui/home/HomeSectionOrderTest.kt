package app.missu.ui.home

import app.missu.data.api.VideoListOptions
import app.missu.data.model.HomeSection
import app.missu.data.model.PinnedHomeList
import app.missu.data.model.VideoListQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionOrderTest {
    private val sections = listOf(
        HomeSection(id = "release", title = "Release", videos = emptyList(), morePath = "/release"),
        HomeSection(id = "new", title = "New", videos = emptyList(), morePath = "/cn/new"),
        HomeSection(id = "random", title = "Random", videos = emptyList()),
    )

    @Test
    fun `first home section is the effective pin without an explicit preference`() {
        assertEquals(
            VideoListQuery("/release"),
            effectivePinnedQuery(null, HomeUiState.Content(sections)),
        )
    }

    @Test
    fun `default home list moves its existing section to the front`() {
        val pinned = PinnedHomeList("New", VideoListQuery("/en/new"))

        assertEquals(listOf("new", "release", "random"), orderHomeSections(sections, pinned).map { it.id })
    }

    @Test
    fun `random section can be pinned without a more link`() {
        val pinned = PinnedHomeList("Random", VideoListQuery("/cn/random"))

        assertEquals(listOf("random", "release", "new"), orderHomeSections(sections, pinned).map { it.id })
    }

    @Test
    fun `custom query does not reuse the unfiltered home preview`() {
        val pinned = PinnedHomeList(
            "New",
            VideoListQuery("/new", sort = VideoListOptions.Sort.VIEWS),
        )

        assertEquals(listOf("release", "new", "random"), orderHomeSections(sections, pinned).map { it.id })
    }
}
