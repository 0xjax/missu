package app.missu.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(
    state: AuthUiState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {

        when (state) {
            AuthUiState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AuthUiState.LoggedOut -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未登录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onLogin) { Text("去登录") }
                }
            }

            is AuthUiState.LoggedIn -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.user.username ?: "已登录",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onSaved) { Text("我的收藏") }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onPlaylists) { Text("我的片单") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onHistory) { Text("观看历史") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onSettings) { Text("设置") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onLogout) { Text("退出登录") }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
