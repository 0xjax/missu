package app.missu.ui.components

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.missu.data.model.VideoSummary
import app.missu.ui.theme.MissuTheme

@Preview(name = "Error — light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ErrorHintPreview() {
    MissuTheme(darkTheme = false) {
        ErrorHint(message = "The site could not be reached", onRetry = {})
    }
}

@Preview(
    name = "Empty — dark, large font",
    showBackground = true,
    widthDp = 360,
    heightDp = 640,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun EmptyHintPreview() {
    MissuTheme(darkTheme = true) {
        EmptyHint(
            icon = Icons.Outlined.Warning,
            title = "No results for this unusually long search query",
            subtitle = "Try another code, actress or keyword. Long supporting text should remain readable at larger font sizes.",
            actionLabel = "Try again",
            onAction = {},
        )
    }
}

@Preview(name = "Video card — dark", showBackground = true, widthDp = 220, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoGridCardPreview() {
    MissuTheme(darkTheme = true) {
        VideoGridCard(
            video = VideoSummary(
                id = "preview",
                title = "A long video title that wraps onto the second line without changing the card height",
                pageUrl = "/preview",
                duration = "24:16",
                badges = listOf("中文字幕"),
            ),
            onClick = {},
        )
    }
}
