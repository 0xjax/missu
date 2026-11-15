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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.AccountUser
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.GroupDivider
import app.missu.ui.components.GroupSurface
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageTopBar

/**
 * 「我的」页：账号资料 + 二三级入口。
 *
 * NOTE: 这是三个一级页签里唯一需要「账号」的页面，所以顶栏没有返回键（[PageTopBar] 的 onBack 传空）。
 * NOTE: 结构改成 M3E 的分组列表——资料卡（extraLarge 圆角的大容器）在上，下面一张
 * `surfaceContainer` 的分组容器装四个入口，退出登录单独一张容器并挂 error 色：
 * 破坏性动作不能和普通入口混在同一条视觉层里。
 */
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
        PageTopBar(title = stringResource(R.string.account_title))

        when (state) {
            AuthUiState.Checking -> LoadingHint()

            AuthUiState.LoggedOut -> Column(Modifier.fillMaxSize()) {
                // NOTE: 未登录也要够得到设置（主题 / 语言 / 播放），所以空态只占中间那块，
                // 入口与登录后那张分组容器同一套样式——同一个入口不该因为有没有登录而换样子。
                EmptyHint(
                    icon = Icons.Filled.AccountCircle,
                    title = stringResource(R.string.account_logged_out_title),
                    subtitle = stringResource(R.string.account_logged_out_subtitle),
                    actionLabel = stringResource(R.string.account_go_login),
                    onAction = onLogin,
                    modifier = Modifier.weight(1f),
                )
                GroupSurface {
                    MenuRow(Icons.Filled.Settings, stringResource(R.string.account_settings), onSettings)
                }
                Spacer(Modifier.height(16.dp))
            }

            is AuthUiState.LoggedIn -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ProfileCard(user = state.user)
                Spacer(Modifier.height(16.dp))

                GroupSurface {
                    MenuRow(Icons.Filled.Favorite, stringResource(R.string.account_saved), onSaved)
                    GroupDivider(start = 56.dp)
                    MenuRow(Icons.AutoMirrored.Filled.List, stringResource(R.string.account_playlists), onPlaylists)
                    GroupDivider(start = 56.dp)
                    MenuRow(Icons.Filled.DateRange, stringResource(R.string.account_history), onHistory)
                    GroupDivider(start = 56.dp)
                    MenuRow(Icons.Filled.Settings, stringResource(R.string.account_settings), onSettings)
                }

                Spacer(Modifier.height(12.dp))

                GroupSurface {
                    MenuRow(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
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
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(text = label, color = tint)
    }
}
