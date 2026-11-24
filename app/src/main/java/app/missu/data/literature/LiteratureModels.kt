package app.missu.data.literature

enum class LiteraryKind {
    POEM,
    CI,
    QU,
    PROSE,
    BOOK,
}

enum class LiteratureFilter {
    ALL,
    POEM,
    CI,
    QU,
    PROSE,
    BOOK,
    FAVORITES,
}

enum class LiteratureSort {
    PROMINENCE,
    TITLE,
    AUTHOR,
}

data class LiteraryChapter(
    val id: String,
    val title: String,
    val text: String,
)

data class LiteraryWorkSummary(
    val id: String,
    val kind: LiteraryKind,
    val title: String,
    val author: String,
    val themes: Map<String, String>,
) {
    fun theme(locale: String): String = themes.localized(locale)
}

data class LiteraryWork(
    val id: String,
    val kind: LiteraryKind,
    val title: String,
    val author: String,
    val text: String,
    val chapters: List<LiteraryChapter> = emptyList(),
    val source: String,
    val prominence: Int,
    val themes: Map<String, String>,
    val notes: Map<String, List<String>>,
    val translations: Map<String, String>,
    val appreciations: Map<String, String>,
) {
    fun theme(locale: String): String = themes.localized(locale)
    fun notes(locale: String): List<String> = notes[locale] ?: notes["en"].orEmpty()
    fun translation(locale: String): String = translations.localized(locale)
    fun appreciation(locale: String): String = appreciations.localized(locale)
}

data class LiteraturePage(
    val works: List<LiteraryWorkSummary>,
    val nextOffset: Int?,
)

private fun Map<String, String>.localized(locale: String): String =
    get(locale) ?: get("en").orEmpty()
