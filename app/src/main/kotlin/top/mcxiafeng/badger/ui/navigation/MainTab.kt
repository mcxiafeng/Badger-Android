package top.mcxiafeng.badger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class MainTab(val label: String, val icon: ImageVector) {
    MY_CARD("我的名片", Icons.Outlined.QrCodeScanner),
    CONTACTS("联系人", Icons.Outlined.Person),
    MY_PAGE("我的", Icons.Outlined.AccountCircle),
}
