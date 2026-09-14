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

private val LIST_PANE_WIDTH_MEDIUM = 360.dp
private val LIST_PANE_WIDTH_EXPANDED = 400.dp

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

@Composable
private fun MasterDetailEmptyPane(icon: ImageVector, title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BadgerEmptyStateSimple(icon = icon, title = title, subtitle = subtitle)
    }
}
