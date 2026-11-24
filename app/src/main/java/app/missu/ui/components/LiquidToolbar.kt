@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.missu.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.missu.ui.search.LocalSearchGlassState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass

val LocalTabAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun LiquidToolbar(content: @Composable () -> Unit) {
    val glassState = LocalSearchGlassState.current
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalTabAnimatedVisibilityScope.current
    val sharedModifier = if (sharedScope != null && visibilityScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "liquid-top-toolbar"),
                animatedVisibilityScope = visibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                boundsTransform = { _, _ -> tween(durationMillis = 300) },
            )
        }
    } else {
        Modifier
    }
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .then(sharedModifier)
    if (glassState == null) {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            content()
        }
        return
    }

    val style = rememberLiquidGlassStyle(CircleShape)
    Box(
        modifier.hazeGlass(
            input = HazeInput.Sources(glassState),
            style = style,
        ),
    ) {
        content()
    }
}
