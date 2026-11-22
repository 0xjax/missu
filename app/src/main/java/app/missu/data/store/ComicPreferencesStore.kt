package app.missu.data.store

import android.content.Context

class ComicPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("comics", Context.MODE_PRIVATE)

    fun chapterDescending(): Boolean = preferences.getBoolean(KEY_CHAPTER_DESCENDING, false)

    fun setChapterDescending(value: Boolean) {
        preferences.edit().putBoolean(KEY_CHAPTER_DESCENDING, value).apply()
    }

    private companion object {
        const val KEY_CHAPTER_DESCENDING = "chapter_descending"
    }
}
