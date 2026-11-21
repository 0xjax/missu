package app.missu.ui.study

import androidx.annotation.StringRes
import app.missu.R
import app.missu.ui.home.Guide
import app.missu.ui.home.guideCatalog

data class Vocabulary(
    val word: String,
    @param:StringRes val meaningRes: Int,
    @param:StringRes val exampleRes: Int,
)

data class EnglishArticle(
    val guide: Guide,
    val vocabulary: List<Vocabulary>,
)

val englishCatalog = listOf(
    EnglishArticle(
        guide = guideCatalog.first { it.id == "desire" },
        vocabulary = listOf(
            Vocabulary("urge", R.string.vocab_urge, R.string.vocab_urge_example),
            Vocabulary("conscious", R.string.vocab_conscious, R.string.vocab_conscious_example),
            Vocabulary("inertia", R.string.vocab_inertia, R.string.vocab_inertia_example),
            Vocabulary("recognise", R.string.vocab_recognise, R.string.vocab_recognise_example),
        ),
    ),
    EnglishArticle(
        guide = guideCatalog.first { it.id == "attention" },
        vocabulary = listOf(
            Vocabulary("consumption", R.string.vocab_consumption, R.string.vocab_consumption_example),
            Vocabulary("concrete", R.string.vocab_concrete, R.string.vocab_concrete_example),
            Vocabulary("prestigious", R.string.vocab_prestigious, R.string.vocab_prestigious_example),
            Vocabulary("nourish", R.string.vocab_nourish, R.string.vocab_nourish_example),
        ),
    ),
)

fun englishArticleById(id: String): EnglishArticle? = englishCatalog.firstOrNull { it.guide.id == id }
