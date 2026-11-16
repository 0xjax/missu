package app.missu.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.missu.R
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.InvalidCredentialsException
import app.missu.data.api.SessionExpiredException
import java.io.IOException

/**
 * 界面要显示的一句话 = **类型 + 参数**，不是一段拼好的文案。
 *
 * NOTE: 以前每个 ViewModel 各有一份 `describe(error)`，把中文文案直接塞进状态
 * （`Error(val message: String)`）。那样有两个问题：文案跟着界面语言变不了（状态里存的是旧语言），
 * 而且同一套映射在 10 个 ViewModel 里各抄了一遍。现在数据层只给类型，文案在渲染时按当前资源取。
 */
sealed interface UiMessage {
    data object Network : UiMessage
    data object HomeEmpty : UiMessage
    data class Http(val status: Int) : UiMessage
    data object Challenge : UiMessage
    data object SessionExpired : UiMessage
    data object InvalidCredentials : UiMessage
    data object LoginFailed : UiMessage
    data object NoVideoId : UiMessage
    data object PlaylistNameEmpty : UiMessage
    data class PartialRemoveFailed(val count: Int) : UiMessage
    data object Unknown : UiMessage
}

/**
 * 数据层异常 → 界面消息。
 *
 * NOTE: 401 的重试已经在 api 层（`withAuthRetry`）做过一次，走到这里仍是 [SessionExpiredException]
 * 才能确定是真失效。
 * NOTE: OkHttp 的连接类异常 message 是英文原文，不直接甩给用户，统一归到 [UiMessage.Network]。
 */
fun Throwable.toUiMessage(): UiMessage = when (this) {
    is ChallengeException -> UiMessage.Challenge
    is HttpStatusException -> UiMessage.Http(status)
    is SessionExpiredException -> UiMessage.SessionExpired
    is InvalidCredentialsException -> UiMessage.InvalidCredentials
    is IOException -> UiMessage.Network
    else -> UiMessage.Unknown
}

/** 按当前界面语言取文案。所有显示消息的地方都必须经过它。 */
@Composable
fun UiMessage.text(): String = when (this) {
    UiMessage.Network -> stringResource(R.string.error_network)
    UiMessage.HomeEmpty -> stringResource(R.string.error_home_empty)
    is UiMessage.Http -> stringResource(R.string.error_http, status)
    UiMessage.Challenge -> stringResource(R.string.error_challenge)
    UiMessage.SessionExpired -> stringResource(R.string.error_session_expired)
    UiMessage.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    UiMessage.LoginFailed -> stringResource(R.string.error_login_failed)
    UiMessage.NoVideoId -> stringResource(R.string.error_no_video_id)
    UiMessage.PlaylistNameEmpty -> stringResource(R.string.error_playlist_name_empty)
    is UiMessage.PartialRemoveFailed -> pluralStringResource(R.plurals.error_partial_remove, count, count)
    UiMessage.Unknown -> stringResource(R.string.error_unknown)
}
