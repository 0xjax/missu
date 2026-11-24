package app.missu.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.missu.ui.theme.LocalDarkTheme
import dev.chrisbanes.haze.glass.GlassStyle

@Composable
internal fun rememberLiquidGlassStyle(
    shape: RoundedCornerShape,
    tint: Color? = null,
): GlassStyle {
    val darkTheme = LocalDarkTheme.current
    val background = MaterialTheme.colorScheme.surface
    val resolvedTint = tint ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.32f)
    return remember(darkTheme, background, resolvedTint, shape) {
        GlassStyle.regular.then {
            backgroundColor(background)
            tint(resolvedTint)
            shape(shape)
            specularIntensity(if (darkTheme) 0.38f else 0.62f)
            edgeShadow(Color.Black.copy(alpha = if (darkTheme) 0.32f else 0.14f))
            ambientResponse(if (darkTheme) 0.08f else 0.28f)
            chromaticAberrationStrength(if (darkTheme) 0.08f else 0.12f)
            contrast(if (darkTheme) -0.31f else 0f)
            whitePoint(if (darkTheme) -0.23f else 0.55f)
            chromaMultiplier(if (darkTheme) 1.25f else 1.5f)
        }
    }
}
