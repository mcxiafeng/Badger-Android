package top.mcxiafeng.badger.pages.server

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar

private const val TAG = "ChoiceServerPageTester"

/** 选择服务器页（移植自 dev 分支 page/server/ChoiceServerPage）。 */
@Composable
fun ChoiceServerPage(
    onBack: () -> Unit,
    onProceedToAuth: () -> Unit,
    viewModel: ChoiceServerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { Log.d(TAG, "进入选择服务器页") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "服务器",
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d(TAG, "返回上一页")
                        onBack()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        ChoiceServerContent(
            uiState = uiState,
            modifier = Modifier.padding(padding),
            onProceed = {
                val error = viewModel.proceed()
                if (error == null) {
                    Log.d(TAG, "服务器就绪，前往登录")
                    onProceedToAuth()
                } else {
                    Log.w(TAG, "服务器不可用：$error")
                    snackbarHostState.showSnackbar(error)
                }
            },
            onInput = viewModel::onInput,
            onHttps = viewModel::onHttpsChange,
            onRemoveHistory = viewModel::removeHistory,
            snackbarHost = { hostModifier ->
                SnackbarHost(state = snackbarHostState, modifier = hostModifier)
            },
        )
    }
}

@Composable
private fun ChoiceServerContent(
    uiState: ChoiceServerUiState,
    modifier: Modifier = Modifier,
    onProceed: suspend () -> Unit,
    onInput: (String) -> Unit,
    onHttps: (Boolean) -> Unit,
    onRemoveHistory: (String) -> Unit,
    snackbarHost: @Composable (androidx.compose.ui.Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val proceed: () -> Unit = {
        scope.launch { onProceed() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BadgerSpacing.lg),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ServerHero()
                Spacer(modifier = Modifier.height(BadgerSpacing.xl))
                ServerEntryCard(
                    input = uiState.input,
                    https = uiState.https,
                    reason = uiState.urlReason.takeIf { uiState.input.isNotBlank() },
                    onInput = onInput,
                    onHttps = onHttps,
                    onImeAction = { if (uiState.canProceed) proceed() },
                )
                HistorySection(
                    history = uiState.history,
                    onPick = onInput,
                    onRemove = onRemoveHistory,
                )
                Spacer(modifier = Modifier.height(BadgerSpacing.xl))
            }
            snackbarHost(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = SNACKBAR_BOTTOM_CLEARANCE),
            )
        }
        LoginEntryButton(
            resolving = uiState.resolving,
            enabled = uiState.canProceed,
            onClick = proceed,
        )
    }
}

@Composable
private fun ServerEntryCard(
    input: String,
    https: Boolean,
    reason: String?,
    onInput: (String) -> Unit,
    onHttps: (Boolean) -> Unit,
    onImeAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.card,
    ) {
        ServerUrlInput(value = input, onValueChange = onInput, reason = reason, onImeAction = onImeAction)
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        HttpsSwitchRow(checked = https, onChange = onHttps)
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (history.isEmpty()) return
    Spacer(modifier = Modifier.height(BadgerSpacing.lg))
    SmallTitle(text = "历史")
    Column(verticalArrangement = Arrangement.spacedBy(BadgerSpacing.md)) {
        history.forEach { entry ->
            HistoryRow(entry = entry, onClick = { onPick(entry) }, onRemove = { onRemove(entry) })
        }
    }
}
