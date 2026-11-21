package app.missu.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiteratureStore(context: Context) {
    private val preferences = context.getSharedPreferences("literature", Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun toggleFavorite(id: String) {
        val updated = _favorites.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }.toSet()
        preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
        _favorites.value = updated
    }

    private fun loadFavorites(): Set<String> =
        preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    private companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
