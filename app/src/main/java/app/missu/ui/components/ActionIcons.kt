package app.missu.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Material `push_pin`；单个字形不值得引入 icons-extended。 */
internal val PushPinIcon: ImageVector = ImageVector.Builder(
    name = "PushPin",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 9f); verticalLineTo(4f); horizontalLineToRelative(1f)
        curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
        curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
        horizontalLineTo(7f)
        curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
        curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
        horizontalLineToRelative(1f); verticalLineToRelative(5f)
        curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
        verticalLineToRelative(2f); horizontalLineToRelative(5.97f); verticalLineToRelative(7f)
        lineToRelative(1f, 1f); lineToRelative(1f, -1f); verticalLineToRelative(-7f)
        horizontalLineTo(19f); verticalLineToRelative(-2f)
        curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
        close()
    }
}.build()
