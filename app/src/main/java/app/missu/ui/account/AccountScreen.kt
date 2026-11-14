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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import app.missu.data.model.AccountUser
import app.missu.ui.components.EmptyHint
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
        PageTopBar(title = "我的")

        when (state) {
            AuthUiState.Checking -> LoadingHint()

            AuthUiState.LoggedOut -> EmptyHint(
                icon = Icons.Filled.AccountCircle,
                title = "未登录",
                subtitle = "登录后可以收藏影片、管理片单，并同步观看历史",
                actionLabel = "去登录",
                onAction = onLogin,
            )

            is AuthUiState.LoggedIn -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ProfileCard(user = state.user)
                Spacer(Modifier.height(16.dp))

                MenuGroup {
                    MenuRow(Icons.Filled.Favorite, "我的收藏", onSaved)
                    RowDivider()
                    MenuRow(Icons.AutoMirrored.Filled.List, "我的片单", onPlaylists)
                    RowDivider()
                    MenuRow(Icons.Filled.DateRange, "观看历史", onHistory)
                    RowDivider()
                    MenuRow(Icons.Filled.Settings, "设置", onSettings)
                }

                Spacer(Modifier.height(12.dp))

                MenuGroup {
                    MenuRow(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        label = "退出登录",
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
    val name = user.username ?: "已登录"
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

/** 分组容器：一张 `surfaceContainer` 的圆角面。 */
@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(content = { content() })
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

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
