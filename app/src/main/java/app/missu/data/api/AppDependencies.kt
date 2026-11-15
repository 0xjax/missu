package app.missu.data.api

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import okhttp3.OkHttpClient

class AppDependencies(context: Context, challenge: ChallengeHandler) {
    val httpClient = OkHttpClient()
    val api: MissavApi = createMissavApi(context, challenge, httpClient)
}

val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("AppDependencies is not provided")
}
