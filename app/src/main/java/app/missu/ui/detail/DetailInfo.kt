package app.missu.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.DetailInfoRow

@Composable
fun DescriptionCard(description: String) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun InfoRow(
    row: DetailInfoRow,
    videoId: String,
    onOpenPath: (path: String, title: String) -> Unit,
    onSearch: (String) -> Unit,
) {
    var expanded by rememberSaveable(row.label) { mutableStateOf(false) }
    var overflowing by remember(row.label) { mutableStateOf(false) }
    val label = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (overflowing) {
                    Modifier.clickable(onClickLabel = label) { expanded = !expanded }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.label + " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f),
        )
        FlowRow(
            modifier = Modifier.weight(0.66f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            row.values.forEach { value ->
                val path = value.path
                val isCode = path == null && value.text.equals(videoId, ignoreCase = true)
                Text(
                    text = value.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (path != null || isCode) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (expanded) {
                            if (result.lineCount > 1) overflowing = true
                        } else {
                            overflowing = result.hasVisualOverflow
                        }
                    },
                    modifier = when {
                        path != null -> Modifier.clickable { onOpenPath(path, value.text) }
                        isCode -> Modifier.clickable { onSearch(value.text) }
                        else -> Modifier
                    },
                )
            }
        }
        if (overflowing) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}
