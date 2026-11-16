package app.missu.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import app.missu.R
import app.missu.ui.common.LoadMoreState
import app.missu.ui.theme.MissuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Windows SAC 会拦 Robolectric 解压的未签名 Conscrypt DLL；本类不测 TLS。
@ConscryptMode(ConscryptMode.Mode.OFF)
class PageStatesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun errorRetryInvokesAction() {
        var retries = 0
        composeRule.setContent {
            MissuTheme {
                ErrorHint(message = "Network unavailable", onRetry = { retries += 1 })
            }
        }

        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.common_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun emptyActionInvokesAction() {
        var actions = 0
        composeRule.setContent {
            MissuTheme {
                EmptyHint(
                    icon = Icons.Filled.Warning,
                    title = "No results",
                    actionLabel = "Reload",
                    onAction = { actions += 1 },
                )
            }
        }

        composeRule.onNodeWithText("No results").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").performClick()
        assertEquals(1, actions)
    }

    @Test
    fun failedPageCanRetryAndEndPageDoesNotOfferRetry() {
        var retries = 0
        val nextPage = mutableStateOf<Int?>(2)
        composeRule.setContent {
            MissuTheme {
                LoadMoreFooter(
                    nextPage = nextPage.value,
                    state = if (nextPage.value == null) LoadMoreState.Idle else LoadMoreState.Failed,
                    onRetry = { retries += 1 },
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.common_load_failed)).performClick()
        assertEquals(1, retries)

        composeRule.runOnIdle { nextPage.value = null }
        composeRule.onNodeWithText(context.getString(R.string.common_load_end)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.common_load_failed)).assertDoesNotExist()
    }
}
