package app.missu.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize

@Composable
internal fun <T> VideoOptionMenu(
    text: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    var anchorTopLeft by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableMapOf<Int, Rect>() }
    val currentOptions by rememberUpdatedState(options)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(
        modifier
            .onGloballyPositioned { anchorTopLeft = it.positionOnScreen() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val wasOpen = menuOpen
                    var dragged = false
                    menuOpen = true
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragged = true
                        }
                        val optionIndex = itemBounds.entries.firstOrNull { (_, bounds) ->
                            bounds.contains(anchorTopLeft + change.position)
                        }?.key ?: -1
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            menuOpen = if (optionIndex >= 0 || dragged) false else !wasOpen
                            highlightedIndex = -1
                            currentOptions.getOrNull(optionIndex)?.let(currentOnSelect)
                            break
                        }
                        if (!change.pressed) {
                            menuOpen = false
                            break
                        }
                        highlightedIndex = optionIndex
                    }
                    highlightedIndex = -1
                }
            },
    ) {
        VideoControlButton(
            text = text,
            expanded = menuOpen,
            onClick = { menuOpen = !menuOpen },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = {
                menuOpen = false
                highlightedIndex = -1
            },
        ) {
            options.forEachIndexed { index, option ->
                val highlighted = index == highlightedIndex
                DropdownMenuItem(
                    modifier = Modifier
                        .onGloballyPositioned { itemBounds[index] = Rect(it.positionOnScreen(), it.size.toSize()) }
                        .background(
                            if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
                            CircleShape,
                        ),
                    text = {
                        Text(
                            text = optionLabel(option),
                            color = when {
                                highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                                option == selected -> MaterialTheme.colorScheme.primary
                                else -> Color.Unspecified
                            },
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
