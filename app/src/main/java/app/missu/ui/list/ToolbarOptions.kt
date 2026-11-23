package app.missu.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.components.MissuBottomSheet
import app.missu.ui.search.LocalSearchGlassState
import app.missu.ui.search.SearchTriggerHeight

@Composable
internal fun ToolbarOptionsButton(
    content: @Composable ColumnScope.(onDismiss: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { open = true },
        modifier = Modifier.size(SearchTriggerHeight),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (LocalSearchGlassState.current == null) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = stringResource(R.string.toolbar_options),
            modifier = Modifier.size(24.dp),
        )
    }
    if (open) {
        MissuBottomSheet(
            onDismissRequest = { open = false },
            skipPartiallyExpanded = true,
        ) {
            content { open = false }
        }
    }
}

@Composable
internal fun ToolbarOptionsSheet(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : Any> ToolbarRadioGroup(
    title: String,
    options: List<T?>,
    selected: T?,
    onSelect: (T?) -> Unit,
    optionLabel: @Composable (T) -> String,
    defaultLabel: String? = null,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
    FlowRow(modifier = Modifier.fillMaxWidth(), maxItemsInEachRow = 2) {
        options.forEach { option ->
            val checked = option == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = checked,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option?.let { optionLabel(it) } ?: defaultLabel ?: stringResource(R.string.list_filter_all),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                RadioButton(selected = checked, onClick = null)
            }
        }
    }
}
