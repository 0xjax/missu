package app.missu.ui.study

import androidx.annotation.StringRes
import app.missu.R

data class Poem(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val authorRes: Int,
    @param:StringRes val themeRes: Int,
    @param:StringRes val textRes: Int,
    @param:StringRes val translationRes: Int,
    @param:StringRes val appreciationRes: Int,
)

val poemCatalog = listOf(
    Poem(
        id = "quiet-night",
        titleRes = R.string.poem_quiet_night_title,
        authorRes = R.string.poem_quiet_night_author,
        themeRes = R.string.poem_theme_longing,
        textRes = R.string.poem_quiet_night_text,
        translationRes = R.string.poem_quiet_night_translation,
        appreciationRes = R.string.poem_quiet_night_appreciation,
    ),
    Poem(
        id = "drinking-wine",
        titleRes = R.string.poem_drinking_wine_title,
        authorRes = R.string.poem_drinking_wine_author,
        themeRes = R.string.poem_theme_self,
        textRes = R.string.poem_drinking_wine_text,
        translationRes = R.string.poem_drinking_wine_translation,
        appreciationRes = R.string.poem_drinking_wine_appreciation,
    ),
    Poem(
        id = "calming-waves",
        titleRes = R.string.poem_calming_waves_title,
        authorRes = R.string.poem_calming_waves_author,
        themeRes = R.string.poem_theme_life,
        textRes = R.string.poem_calming_waves_text,
        translationRes = R.string.poem_calming_waves_translation,
        appreciationRes = R.string.poem_calming_waves_appreciation,
    ),
)

fun poemById(id: String): Poem? = poemCatalog.firstOrNull { it.id == id }
