package app.missu.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.missu.data.model.VideoSummary
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.theme.MissuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Windows SAC 会拦 Robolectric 解压的未签名 Conscrypt DLL；本类不测 TLS。
@ConscryptMode(ConscryptMode.Mode.OFF)
class VideoListLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `column preference relayouts the existing video list`() {
        val columns = mutableStateOf(2)
        val videos = listOf(video("First"), video("Second"))
        composeRule.setContent {
            MissuTheme {
                CompositionLocalProvider(LocalVideoListColumns provides columns.value) {
                    VideoListBody(
                        state = VideoListUiState.Content(videos, nextPage = null),
                        onRetry = {},
                        onLoadMore = {},
                        onOpen = { _, _ -> },
                    )
                }
            }
        }

        val doubleFirst = composeRule.onNodeWithText("First").getUnclippedBoundsInRoot()
        val doubleSecond = composeRule.onNodeWithText("Second").getUnclippedBoundsInRoot()
        assertTrue(doubleSecond.left > doubleFirst.left)
        assertEquals(doubleFirst.top, doubleSecond.top)

        composeRule.runOnIdle { columns.value = 1 }

        val singleFirst = composeRule.onNodeWithText("First").getUnclippedBoundsInRoot()
        val singleSecond = composeRule.onNodeWithText("Second").getUnclippedBoundsInRoot()
        assertEquals(singleFirst.left, singleSecond.left)
        assertTrue(singleSecond.top > singleFirst.top)
    }
}

private fun video(title: String) = VideoSummary(id = title, title = title, pageUrl = "/$title")
