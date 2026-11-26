package top.mcxiafeng.badger.pages.person.contact

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.ui.glass.IconButton
import top.mcxiafeng.badger.ui.glass.Scaffold
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.TopAppBar
import top.mcxiafeng.badger.ui.glass.WindowDialog
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check

/**
 * 名片编辑页（新建 / 编辑共用）。只编辑文本三件套：姓名、备注、个人介绍。
 * 头像、基础信息、标签、平台、名片夹全部在详情页直接编辑。
 */
@Composable
fun CreateContactPage(
    contactId: Long? = null,
    targetCollectionId: Long? = null,
    onBack: () -> Unit = {},
    onNavigateToContactDetail: (Long) -> Unit = {},
    viewModel: CreateContactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (!state.isSaving) {
            if (state.dirty) showDiscard = true else onBack()
            Log.d("CreateContactPageTester", "请求退出${if (contactId != null) "编辑" else "新建"}名片，已检查草稿")
        }
    }
    BackHandler(enabled = state.dirty || state.isSaving) { requestBack() }
    LaunchedEffect(targetCollectionId) { viewModel.initializeTarget(targetCollectionId) }
    LaunchedEffect(contactId) { viewModel.initializeEditing(contactId) }
    LaunchedEffect(state.savedContactId, state.accountChanged) {
        state.savedContactId?.let { if (viewModel.canNavigate()) onNavigateToContactDetail(it) }
    }
    Scaffold(topBar = { ContactDraftTopBar(state, contactId != null, requestBack, viewModel::save) }) { padding ->
        ContactDraftBody(state, viewModel, padding)
    }
    if (showDiscard) ContactDraftDiscardDialog(contactId != null,
        onDismiss = { showDiscard = false }, onDiscard = {
            showDiscard = false; onBack()
        })
}

@Composable
private fun ContactDraftTopBar(state: ContactDraftUiState, isEditing: Boolean, onBack: () -> Unit, onSave: () -> Unit) {
    TopAppBar(title = if (isEditing) "编辑名片" else "新建名片", navigationIcon = {
        IconButton(onClick = onBack, enabled = !state.isSaving) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
        }
    }, actions = {
        IconButton(onClick = onSave, enabled = state.canSave) {
            if (state.isSaving) CircularProgressIndicator()
            else Icon(Icons.Default.Check, "保存名片")
        }
    })
}

@Composable
private fun ContactDraftDiscardDialog(isEditing: Boolean, onDismiss: () -> Unit, onDiscard: () -> Unit) {
    WindowDialog(show = true, title = if (isEditing) "放弃编辑？" else "放弃新建名片？",
        summary = if (isEditing) "本次对名片的修改尚未保存。" else "填写的内容尚未保存。",
        onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("继续编辑", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton("放弃", onClick = onDiscard, modifier = Modifier.weight(1f))
        }
    }
}
