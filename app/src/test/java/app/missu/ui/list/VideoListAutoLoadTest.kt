package app.missu.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.missu.data.model.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
class VideoListAutoLoadTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `initial loading starts its request`() {
        var loads = 0
        composeRule.setContent {
            AutoLoadVideoList(Any(), MutableStateFlow(VideoListUiState.Loading)) { loads += 1 }
        }

        composeRule.waitUntil { loads == 1 }
        assertEquals(1, loads)
    }

    @Test
    fun `switching from content to a new loading view model starts its request`() {
        val loaded = MutableStateFlow<VideoListUiState>(
            VideoListUiState.Content(
                videos = listOf(VideoSummary(id = "one", title = "One", pageUrl = "/one")),
                nextPage = null,
            ),
        )
        val current = mutableStateOf("old" to loaded)
        var loads = 0
        composeRule.setContent {
            Text(current.value.first)
            AutoLoadVideoList(current.value.first, current.value.second) { loads += 1 }
        }
        composeRule.waitForIdle()
        assertEquals(0, loads)

        composeRule.runOnIdle {
            current.value = "new" to MutableStateFlow(VideoListUiState.Loading)
        }

        composeRule.onNodeWithText("new").assertIsDisplayed()
        composeRule.waitUntil { loads == 1 }
        assertEquals(1, loads)
    }
}
