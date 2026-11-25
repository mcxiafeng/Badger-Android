package top.mcxiafeng.badger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class MainTab(val label: String, val icon: ImageVector) {
    MY_CARD("首页", Icons.Outlined.Home),
    CONTACTS("联系人", Icons.Outlined.Person),
    TODOS("待办", Icons.Outlined.CheckCircle),
    MY_PAGE("我的", Icons.Outlined.AccountCircle),
}
