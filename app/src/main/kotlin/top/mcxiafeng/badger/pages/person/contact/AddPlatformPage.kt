package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AddPlatformPage(contactId: Long, onBack: () -> Unit, onSaved: () -> Unit = onBack) {
    val viewModel: AddPlatformViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (!viewModel.back()) {
            if (state.hasDraft) showDiscard = true else onBack()
        }
    }
    BackHandler(onBack = requestBack)
    LaunchedEffect(contactId) { viewModel.load(contactId) }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            Toast.makeText(context, "已存本机 ${state.additions.size} 个平台", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }
    AddPlatformScreen(state, requestBack, viewModel::updateInput, viewModel::openManual,
        viewModel::updateQuery, viewModel::selectPlatform, viewModel::removePending,
        viewModel::updateAccount, viewModel::updateAuxiliary, viewModel::updateDisplayName,
        viewModel::updateCustomName, onSubmit = {
            if (state.step == AddPlatformStep.FORM) viewModel.addManualEntry()
            else viewModel.save()
        })
    if (showDiscard) {
        WindowDialog(show = true, title = "放弃添加？", summary = "当前输入尚未保存",
            onDismissRequest = { showDiscard = false }) {
            DialogButtonRow(negativeText = "继续填写", positiveText = "放弃添加",
                onNegative = { showDiscard = false }, onPositive = {
                    showDiscard = false
                    Log.d("AddPlatformPageTester", "用户放弃未保存的平台输入")
                    onBack()
                })
        }
    }
}

@Composable
internal fun AddPlatformScreen(
    state: AddPlatformUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onManual: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRemovePending: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onAuxiliaryChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.step) {
        focusManager.clearFocus()
        keyboard?.hide()
        Log.d("AddPlatformPageTester", "页面内切换: step=${state.step}")
    }
    Scaffold(topBar = {
        TopAppBar(title = "添加平台",
            navigationIcon = { IconButton(onClick = onBack, enabled = !state.isSaving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = BadgerSize.contentMaxWidth).fillMaxSize()
                .padding(horizontal = BadgerSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
                PlatformStepContent(state, Modifier.weight(1f)) { snapshot ->
                    AddPlatformBody(snapshot, onInputChange, onManual, onQueryChange, onSelect,
                        onRemovePending, onAccountChange, onAuxiliaryChange, onNameChange, onCustomNameChange)
                }
                state.error?.let { PlatformInputError(it) }
                AddPlatformSubmitButton(state, onSubmit, Modifier.padding(bottom = BadgerSpacing.lg))
            }
        }
    }
}

@Composable
private fun AddPlatformBody(
    state: AddPlatformUiState, onInputChange: (String) -> Unit, onManual: () -> Unit,
    onQueryChange: (String) -> Unit, onSelect: (String) -> Unit, onRemovePending: (String) -> Unit,
    onAccountChange: (String) -> Unit, onAuxiliaryChange: (String) -> Unit,
    onNameChange: (String) -> Unit, onCustomNameChange: (String) -> Unit
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    when (state.step) {
        AddPlatformStep.AUTOMATIC -> AutomaticPlatformContent(state, onInputChange, onManual,
            onRemovePending, Modifier.fillMaxSize())
        AddPlatformStep.MANUAL -> ManualPlatformList(state, onQueryChange, onSelect, Modifier.fillMaxSize())
        AddPlatformStep.FORM -> ManualPlatformForm(state, onAccountChange, onAuxiliaryChange,
            onNameChange, onCustomNameChange, Modifier.fillMaxSize())
    }
}

@Composable
private fun AddPlatformSubmitButton(state: AddPlatformUiState, onSubmit: () -> Unit, modifier: Modifier) {
    val isForm = state.step == AddPlatformStep.FORM
    val canSubmit = if (isForm) {
        (state.selectedKey.isNotBlank() || state.customName.isNotBlank()) &&
            (state.account.isNotBlank() || state.auxiliaryAccount.isNotBlank())
    } else state.additions.isNotEmpty() && state.candidates.none { it.message != null }
    Button(onClick = onSubmit, enabled = canSubmit && !state.isLoading && !state.isSaving && !state.isSaved,
        modifier = modifier.fillMaxWidth().heightIn(min = BadgerSize.minimumTouchTarget),
        colors = ButtonDefaults.buttonColorsPrimary()) {
        Text(when {
            state.isSaving -> "正在保存…"
            isForm -> "添加到列表"
            state.error != null && state.additions.isNotEmpty() -> "重试保存"
            state.additions.isNotEmpty() -> "保存 ${state.additions.size} 个平台"
            else -> "保存"
        })
    }
}
