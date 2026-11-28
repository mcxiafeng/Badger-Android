package top.mcxiafeng.badger.pages.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.ui.components.QrShareDialog
import top.mcxiafeng.badger.ui.components.QrSharePlatform

/**
 * 联系人分享弹窗：平台数据经 PersonViewModel 从数据库读取，
 * 展示与首页出示二维码共用的 [QrShareDialog]。
 */
@Composable
internal fun PersonContactShareDialog(contact: Contact, viewModel: PersonViewModel, onDismiss: () -> Unit) {
    val platformsResult by produceState<Result<List<QrSharePlatform>>?>(null, contact.id, viewModel) {
        value = viewModel.loadContactSharePlatforms(contact.id)
    }
    QrShareDialog(
        name = contact.name.ifBlank { "未命名联系人" },
        avatarUrl = contact.avatarUrl,
        avatarPath = contact.avatarPath,
        platforms = platformsResult?.getOrNull(),
        onDismiss = onDismiss,
        paneTitle = "分享联系人",
        pagerTestTag = "contact_share_platform_pager",
        emptyText = if (platformsResult?.isFailure == true) "无法读取联系人平台资料，请重试"
        else "此联系人暂无可分享的平台资料",
    )
}
