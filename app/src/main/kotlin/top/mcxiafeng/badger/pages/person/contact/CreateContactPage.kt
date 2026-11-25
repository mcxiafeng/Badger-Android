package top.mcxiafeng.badger.pages.person.contact

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Composable
fun CreateContactPage(
    targetCollectionId: Long? = null,
    onBack: () -> Unit = {},
    onNavigateToContactDetail: (Long) -> Unit = {},
    viewModel: CreateContactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (!state.isSaving) {
            if (state.draft.hasChanges || state.avatar != null) showDiscard = true else onBack()
            Log.d("CreateContactPageTester", "请求退出新建名片，已检查草稿")
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { cropUri = it }
    BackHandler(enabled = !state.editable || (cropUri == null && editor == null)) { requestBack() }
    LaunchedEffect(targetCollectionId) { viewModel.initializeTarget(targetCollectionId) }
    LaunchedEffect(state.savedContactId, state.accountChanged) {
        state.savedContactId?.let { if (viewModel.canNavigate()) onNavigateToContactDetail(it) }
    }
    Scaffold(topBar = { ContactDraftTopBar(state, requestBack, viewModel::save) }) { padding ->
        ContactDraftBody(state, viewModel, padding, onAvatar = { picker.launch("image/*") },
            onEdit = { editor = it })
    }
    if (state.editable) {
        ContactDraftEditors(editor, state, viewModel, onDismiss = { editor = null })
        if (cropUri != null) ContactDetailCropDialog(true, cropUri, onCropConfirm = {
            viewModel.setAvatar(it); cropUri = null
        }, onDismiss = { cropUri = null })
    }
    if (showDiscard) ContactDraftDiscardDialog(onDismiss = { showDiscard = false }, onDiscard = {
        showDiscard = false; onBack()
    })
}

@Composable
private fun ContactDraftTopBar(state: ContactDraftUiState, onBack: () -> Unit, onSave: () -> Unit) {
    TopAppBar(title = "新建名片", navigationIcon = {
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
private fun ContactDraftDiscardDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    WindowDialog(show = true, title = "放弃新建名片？", summary = "填写的内容尚未保存。",
        onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("继续填写", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton("放弃", onClick = onDiscard, modifier = Modifier.weight(1f))
        }
    }
}
