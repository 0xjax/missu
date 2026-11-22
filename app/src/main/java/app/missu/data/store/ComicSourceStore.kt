package app.missu.data.store

import android.content.Context
import app.missu.data.comic.ComicSource

class ComicSourceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("comics", Context.MODE_PRIVATE)

    fun source(): ComicSource = ComicSource.fromId(preferences.getString(KEY_SOURCE, null).orEmpty())

    fun setSource(source: ComicSource) {
        preferences.edit().putString(KEY_SOURCE, source.id).apply()
    }

    private companion object {
        const val KEY_SOURCE = "source"
    }
}
