package app.missu.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.InvalidCredentialsException
import app.missu.data.api.MissavApi
import app.missu.data.model.AccountUser
import app.missu.data.store.CredentialStore
import app.missu.data.store.ProfileStore
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
    data class Error(val message: String) : LoginUiState
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

    init {
        restore()
    }

    /**
     * 启动时恢复登录态：
     * 1. 没有本地资料 → 未登录；
     * 2. 会话仍有效 → 直接用；
     * 3. 会话失效 → 用记住的凭据自动重登，失败（改密码等）才清凭据回落到未登录。
     * NOTE: 只有「受保护页面被 302 回首页」才判会话失效；网络/挑战失败维持原状，避免误登出/误重登。
     */
    private fun restore() {
        viewModelScope.launch {
            val saved = store.read()
            if (saved == null) {
                _state.value = AuthUiState.LoggedOut
                return@launch
            }

            if (api.checkSession()) {
                _state.value = AuthUiState.LoggedIn(saved)
                return@launch
            }

            val remembered = credentials.load()
            val user = remembered?.let { (email, password) ->
                runCatching { api.login(email, password) }.getOrNull()
            }
            if (user != null) {
                store.write(user)
                _state.value = AuthUiState.LoggedIn(user)
            } else {
                store.clear()
                // NOTE: 凭据已经失效（大概率改了密码），删掉避免每次启动都白试一次。
                credentials.clear()
                _state.value = AuthUiState.LoggedOut
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (_loginState.value is LoginUiState.Loading) return
        _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val user = api.login(email.trim(), password)
                store.write(user)
                credentials.save(user.email, password)
                _state.value = AuthUiState.LoggedIn(user)
                _loginState.value = LoginUiState.Idle
                onSuccess()
            } catch (error: Exception) {
                _loginState.value = LoginUiState.Error(describe(error))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            api.logout()
            store.clear()
            credentials.clear()
            _state.value = AuthUiState.LoggedOut
        }
    }

    fun clearLoginError() {
        _loginState.value = LoginUiState.Idle
    }

    private fun describe(error: Exception): String = when (error) {
        is InvalidCredentialsException -> "邮箱或密码错误"
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "登录失败（HTTP ${error.status}）"
        is java.io.IOException -> "网络连接中断，请重试"
        else -> error.message ?: error::class.simpleName ?: "登录失败"
    }
}
