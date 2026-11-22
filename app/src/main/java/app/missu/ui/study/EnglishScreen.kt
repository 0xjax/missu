package app.missu.ui.study

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ArrayRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.components.MissuBottomSheet
import app.missu.ui.components.PageTopBar
import java.util.Locale

@Composable
fun EnglishList(
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.english_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        items(englishCatalog, key = { it.guide.id }) { article ->
            Card(
                onClick = { onOpen(article.guide.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.english_bilingual_reading),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(article.guide.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(article.guide.summaryRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun EnglishArticleScreen(
    article: EnglishArticle,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bodyRes = article.guide.bodyRes
    val english = remember(context, bodyRes) { localizedArray(context, bodyRes, Locale.ENGLISH) }
    val chinese = remember(context, bodyRes) { localizedArray(context, bodyRes, Locale.SIMPLIFIED_CHINESE) }
    var selectedWord by remember { mutableStateOf<Vocabulary?>(null) }

    Column(modifier.fillMaxSize()) {
        PageTopBar(title = stringResource(article.guide.titleRes), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.english_tap_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(english.size) { index ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VocabularyText(
                        text = english[index],
                        vocabulary = article.vocabulary,
                        onWord = { selectedWord = it },
                    )
                    chinese.getOrNull(index)?.let { translation ->
                        Text(
                            text = translation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    selectedWord?.let { word ->
        MissuBottomSheet(onDismissRequest = { selectedWord = null }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = word.word, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(word.meaningRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.english_example),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(word.exampleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VocabularyText(
    text: String,
    vocabulary: List<Vocabulary>,
    onWord: (Vocabulary) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val content = remember(text, vocabulary, linkColor, onWord) {
        val words = vocabulary.associateBy { it.word.lowercase() }
        buildAnnotatedString {
            Regex("[A-Za-z]+(?:['’][A-Za-z]+)?|[^A-Za-z]+").findAll(text).forEach { match ->
                val token = match.value
                val word = words[token.lowercase()]
                val start = length
                append(token)
                if (word != null) {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "vocabulary",
                            styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                            linkInteractionListener = { onWord(word) },
                        ),
                        start,
                        length,
                    )
                }
            }
        }
    }
    Text(
        text = content,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun localizedArray(
    context: Context,
    @ArrayRes bodyRes: Int,
    locale: Locale,
): Array<String> {
    val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
    return context.createConfigurationContext(configuration).resources.getStringArray(bodyRes)
}
