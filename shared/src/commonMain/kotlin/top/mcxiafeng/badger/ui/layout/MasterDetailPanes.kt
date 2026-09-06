package top.mcxiafeng.badger.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import top.mcxiafeng.badger.pages.card.CardRoute
import top.mcxiafeng.badger.pages.card.CollectionDetailPage
import top.mcxiafeng.badger.pages.person.PersonRoute
import top.mcxiafeng.badger.pages.person.contact.detail.ContactDetailPage
import top.mcxiafeng.badger.platform.BackHandler
import top.mcxiafeng.badger.ui.components.BadgerEmptyStateSimple
import top.mcxiafeng.badger.ui.windowsize.LocalBadgerWindowSizeClass
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.VerticalDivider

private const val TAG = "MasterDetailPane"

// [KMP K18] 左栏宽度：Medium 窄一点（给详情留空间），Expanded 用标准 400dp
private val LIST_PANE_WIDTH_MEDIUM = 360.dp
private val LIST_PANE_WIDTH_EXPANDED = 400.dp

/**
 * 联系人「列表-详情」双栏（K18，仅 Medium/Expanded 启用）。
 *
 * 左栏常驻 [PersonRoute] 列表；点击联系人不再 push 二级路由，而是选中并在右栏
 * 内嵌渲染 [ContactDetailPage]（embedded=true，无返回箭头）。选中态可跨 Tab 切换、
 * 跨二级路由 push/pop 保留（rememberSaveable + SaveableStateHolder("MainTabs")）。
 * 系统返回键语义：有选中 → 取消选中回到列表；无选中 → 交给系统默认。
 * 我的名片（id=-1L）仍走全屏编辑页。
 */
@Composable
fun PersonMasterDetailPane(
    onScanContact: () -> Unit,
    onCreateContact: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    var selectedContactId by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(enabled = selectedContactId != null) {
        BadgerLog.d(TAG, "PersonPane: back clears selection=$selectedContactId")
        selectedContactId = null
    }
    MasterDetailRow(
        listPane = {
            PersonRoute(
                onScanContact = onScanContact,
                onCreateContact = onCreateContact,
                onContactClick = { id ->
                    if (id == -1L) {
                        // 我的名片 → 全屏编辑页（UserProfileDetailPage 表单量大，不适合嵌入右栏）
                        onOpenProfile()
                    } else {
                        BadgerLog.d(TAG, "PersonPane: select contact=$id")
                        selectedContactId = id
                    }
                },
            )
        },
        detailPane = {
            val id = selectedContactId
            if (id != null) {
                ContactDetailPage(
                    contactId = id,
                    onBack = { selectedContactId = null },
                    onRefreshData = null,
                    embedded = true,
                )
            } else {
                MasterDetailEmptyPane(
                    icon = Lucide.User,
                    title = "选择联系人",
                    subtitle = "在左侧列表中选择联系人查看详情",
                )
            }
        },
    )
}

/**
 * 名片夹「网格-详情」双栏（K18，仅 Medium/Expanded 启用）。
 *
 * 左栏常驻 [CardRoute] 网格（列数由 gridColumns 决定）；点击名片夹在右栏内嵌
 * [CollectionDetailPage]（embedded=true）。扫码/联系人详情/新建联系人等重操作
 * 仍走全屏路由。返回键语义同 [PersonMasterDetailPane]。
 */
@Composable
fun CardMasterDetailPane(
    columns: Int,
    onScanToCollection: (Long) -> Unit,
    onContactClick: (Long) -> Unit,
    onOpenCreateContact: (Long) -> Unit,
) {
    var selectedCollectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(enabled = selectedCollectionId != null) {
        BadgerLog.d(TAG, "CardPane: back clears selection=$selectedCollectionId")
        selectedCollectionId = null
    }
    MasterDetailRow(
        listPane = {
            CardRoute(
                onScanToCollection = onScanToCollection,
                onContactClick = onContactClick,
                onNavigateToCollectionDetail = { id ->
                    BadgerLog.d(TAG, "CardPane: select collection=$id")
                    selectedCollectionId = id
                },
                columns = columns,
            )
        },
        detailPane = {
            val id = selectedCollectionId
            if (id != null) {
                CollectionDetailPage(
                    collectionId = id,
                    onBack = { selectedCollectionId = null },
                    onNavigateToScanner = onScanToCollection,
                    onNavigateToContactDetail = onContactClick,
                    onNavigateToCreateContact = onOpenCreateContact,
                    embedded = true,
                )
            } else {
                MasterDetailEmptyPane(
                    icon = Lucide.Folder,
                    title = "选择名片夹",
                    subtitle = "在左侧选择一个名片夹查看联系人",
                )
            }
        },
    )
}

/**
 * 双栏骨架：左栏固定宽度 + 分隔线 + 右栏自适应。
 */
@Composable
private fun MasterDetailRow(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    val widthClass = LocalBadgerWindowSizeClass.current.widthSizeClass
    val listPaneWidth: Dp = if (widthClass == WindowWidthSizeClass.Medium) {
        LIST_PANE_WIDTH_MEDIUM
    } else {
        LIST_PANE_WIDTH_EXPANDED
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(listPaneWidth).fillMaxHeight()) {
            listPane()
        }
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            detailPane()
        }
    }
}

/**
 * 右栏空态占位（无选中时提示）。
 */
@Composable
private fun MasterDetailEmptyPane(icon: ImageVector, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BadgerEmptyStateSimple(icon = icon, title = title, subtitle = subtitle)
    }
}
