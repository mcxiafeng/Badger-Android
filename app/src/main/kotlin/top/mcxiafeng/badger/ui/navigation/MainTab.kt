package top.mcxiafeng.badger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class MainTab(val label: String, val icon: ImageVector) {
    MY_CARD("首页", Icons.Outlined.Home),
    CONTACTS("名片夹", Icons.Outlined.FolderOpen),
    TODOS("待办", Icons.Outlined.CheckCircle),
    MY_PAGE("我的", Icons.Outlined.AccountCircle),
}
