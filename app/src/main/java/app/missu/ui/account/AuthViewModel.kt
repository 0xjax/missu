package app.missu.ui.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.AccountUser
import app.missu.data.store.CredentialStore
import app.missu.data.store.ProfileStore
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Checking : AuthUiState
    data object LoggedOut : AuthUiState
    data class LoggedIn(val user: AccountUser) : AuthUiState
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: UiMessage) : LoginUiState
}

/**
 * 账户状态。整个 App 共用一份（在 MissuApp 里创建），避免「我的」和「登录」两个页面各持一份状态。
 *
 * 记住账号：登录成功后把凭据用 Keystore 加密存起来；会话失效时自动拿它重登一次。
 * 这样用户只需要第一次手动输入，之后掉登录也能自己恢复。
 */
class AuthViewModel(
    private val api: MissavApi,
    private val store: ProfileStore,
    private val credentials: CredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()
    private var authJob: Job? = null

    init {
        restore()
    }

    /**
     * 启动时恢复登录态：有本地资料就直接显示已登录（乐观恢复）。
     *
     * WARNING: 这里**不**调用 `checkSession()`。实测当前站点对游客访问 `/playlists` 返回 200 而不是
     * 302 回首页，用它判断的结果永远是「会话有效」，据此做启动重登只会是死代码。
     * 真正的会话失效信号只会出现在需要登录的接口上，由 `MissavApi.withAuthRetry` 负责
     * 「401 → 用记住的凭据重登一次 → 重试」。
     */
    private fun restore() {
        val saved = store.read()
        if (saved == null) {
            Log.w(TAG, "restore: no local profile")
            _state.value = AuthUiState.LoggedOut
        } else {
            Log.w(TAG, "restore: local profile found, assuming logged in")
            _state.value = AuthUiState.LoggedIn(saved)
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (_loginState.value is LoginUiState.Loading) return
        _loginState.value = LoginUiState.Loading
        authJob?.cancel()
        authJob = viewModelScope.launch {
            try {
                val user = api.login(email.trim(), password)
                store.write(user)
                credentials.save(user.email, password)
                _state.value = AuthUiState.LoggedIn(user)
                _loginState.value = LoginUiState.Idle
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _loginState.value = LoginUiState.Error(error.toUiMessage())
            }
        }
    }

    fun logout() {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            api.logout()
            store.clear()
            credentials.clear()
            _state.value = AuthUiState.LoggedOut
        }
    }

    fun clearLoginError() {
        _loginState.value = LoginUiState.Idle
    }

    private companion object {
        // NOTE: 只记分支名，绝不打印邮箱或密码。
        const val TAG = "MissavAuth"
    }

}
