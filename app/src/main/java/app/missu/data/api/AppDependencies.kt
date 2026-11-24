package app.missu.data.api

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.missu.data.article.ArticleRepository
import app.missu.data.comic.createComicRepository
import app.missu.data.literature.LiteraturePackManager
import app.missu.data.literature.LiteratureRepository
import app.missu.data.store.UsageStatsStore
import okhttp3.OkHttpClient

class AppDependencies(context: Context, challenge: ChallengeHandler) {
    val httpClient = OkHttpClient.Builder()
        .cookieJar(AndroidCookieJar())
        .addInterceptor(ComicImageInterceptor())
        .build()
    val api: MissavApi = createMissavApi(context, challenge, httpClient)
    val comicRepository = createComicRepository(httpClient, AndroidCookieJar(), challenge)
    val literatureRepository = LiteratureRepository(LiteraturePackManager.openInitialDatabase(context))
    val literaturePackManager = LiteraturePackManager(context, httpClient, literatureRepository)
    val articleRepository = ArticleRepository(context.applicationContext)
    val usageStats = UsageStatsStore(context.applicationContext)
}

val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("AppDependencies is not provided")
}
