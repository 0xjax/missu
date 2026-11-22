@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle

import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.AccountUser
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.GroupSurface
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageTopBar

/**
 * 影片账号页：登录、资料与退出登录；全局设置由首页直接进入。
 */
@Composable
fun AccountScreen(
    state: AuthUiState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        onBack?.let {
            PageTopBar(title = stringResource(R.string.account_title), onBack = it)
        }
        when (state) {
            AuthUiState.Checking -> LoadingHint()

            AuthUiState.LoggedOut -> Column(Modifier.weight(1f)) {
                EmptyHint(
                    icon = Icons.Outlined.AccountCircle,
                    title = stringResource(R.string.account_logged_out_title),
                    subtitle = stringResource(R.string.account_logged_out_subtitle),
                    actionLabel = stringResource(R.string.account_go_login),
                    onAction = onLogin,
                    modifier = Modifier.weight(1f),
                )
            }

            is AuthUiState.LoggedIn -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                ProfileCard(user = state.user)
                Spacer(Modifier.height(16.dp))

                GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                    MenuRow(
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        label = stringResource(R.string.account_logout),
                        onClick = onLogout,
                        destructive = true,
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 资料卡：账号首字母头像 + 昵称 + 邮箱。 */
@Composable
private fun ProfileCard(user: AccountUser) {
    val name = user.username ?: stringResource(R.string.account_logged_in)
    val initial = (user.username ?: user.email).trim().take(1).uppercase().ifEmpty { "?" }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.size(16.dp))

            Column {
                Text(text = name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/**
 * 分组内的一行入口。
 *
 * NOTE: 用官方 [ListItem] 而不是自己拼 Row——行高、图标位宽、标题字号都由 M3E 的列表项 token 决定，
 * 和「设置」页的分组列表保持一致。行本身可点，所以图标与箭头对读屏都是装饰（contentDescription = null）。
 * WARNING: `containerColor` 必须显式给 Transparent。M3E 的列表项默认容器色取的是主题的 `surface`
 * （本工程是纯黑），它会整行盖掉分组容器的底色——实测分组容器会「消失」，只剩分隔线浮在黑底上。
 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = tint) },
        trailingContent = {
            if (!destructive) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(text = label, color = tint)
    }
}
