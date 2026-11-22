package app.missu.data.api

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.missu.data.comic.createComicRepository
import app.missu.data.literature.LiteratureDatabase
import app.missu.data.literature.LiteratureRepository
import okhttp3.OkHttpClient

class AppDependencies(context: Context, challenge: ChallengeHandler) {
    val httpClient = OkHttpClient.Builder()
        .addInterceptor(ComicImageInterceptor())
        .build()
    val api: MissavApi = createMissavApi(context, challenge, httpClient)
    val comicRepository = createComicRepository(httpClient, AndroidCookieJar(), challenge)
    val literatureRepository = LiteratureRepository(LiteratureDatabase.create(context))
}

val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("AppDependencies is not provided")
}
