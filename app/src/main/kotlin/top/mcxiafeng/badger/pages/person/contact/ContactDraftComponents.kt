package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.components.SkeletonBox
import top.mcxiafeng.badger.ui.components.rememberSkeletonDelayVisible
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 编辑器正文：一张卡片内三个文本框（姓名 / 备注 / 个人介绍），介绍放大以容纳长文。 */
@Composable
internal fun ContactDraftBody(
    state: ContactDraftUiState, viewModel: CreateContactViewModel, padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
        .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(state.draft.name, viewModel::updateName, label = "姓名 / 昵称（必填）",
                    enabled = state.editable, modifier = Modifier.fillMaxWidth())
                Text("${state.draft.name.codePointCount(0, state.draft.name.length)}/$CONTACT_NAME_LIMIT",
                    style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                TextField(state.draft.note, viewModel::updateNote, label = "备注（选填）", singleLine = false,
                    enabled = state.editable, modifier = Modifier.fillMaxWidth())
                Text("${state.draft.note.codePointCount(0, state.draft.note.length)}/$CONTACT_NOTE_LIMIT",
                    style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                TextField(state.draft.bio, viewModel::updateBio, label = "个人介绍（选填，可以写长一些）",
                    singleLine = false, minLines = BIO_MIN_LINES, maxLines = BIO_MAX_LINES,
                    enabled = state.editable, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.isLoading) ContactDraftLoadingHint()
        state.error?.let { Text(it, color = MiuixTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) }
        if (!state.isLoading && state.draft.collectionIds.any { id -> state.collections.none { it.id == id } }) {
            Text("所选名片夹已失效，请重新选择", color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 28.dp))
        }
    }
}

/** 介绍文本框行数：内容普遍较长，默认 6 行起步。 */
internal const val BIO_MIN_LINES = 6
internal const val BIO_MAX_LINES = 14

/** 可选资料骨架线高度：与 body1 单行文字高度接近 */
private val DRAFT_SKELETON_HEIGHT = 14.dp

/** 可选资料按 id 异步载入：骨架线占位替代文字提示；延迟显示避免快速查库闪屏 */
@Composable
private fun ContactDraftLoadingHint() {
    if (!rememberSkeletonDelayVisible()) return
    SkeletonBox(
        modifier = Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth(0.5f)
            .height(DRAFT_SKELETON_HEIGHT)
            .clip(RoundedCornerShape(BadgerRadius.chip))
    )
}
