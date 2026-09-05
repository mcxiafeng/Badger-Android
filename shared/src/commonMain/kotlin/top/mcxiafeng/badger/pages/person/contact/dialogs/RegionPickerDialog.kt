package top.mcxiafeng.badger.pages.person.contact.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.RegionNode
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.MapPin
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 国家选择 Dialog(无前置,直接选)
 *
 * 数据源 `dr5hn/countries-states-cities-database`。中文名优先。
 */
@Composable
fun CountryPickerDialog(
    show: Boolean,
    current: String?,
    onDismiss: () -> Unit,
    onConfirm: (countryName: String, countryId: Long) -> Unit,
    viewModel: CountryPickerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var manualFallback by remember { mutableStateOf(false) }
    var manualValue by remember { mutableStateOf(current.orEmpty()) }

    LaunchedEffect(show) {
        if (show) {
            manualFallback = false
            manualValue = current.orEmpty()
            viewModel.loadIfNeeded()
        }
    }

    if (!show) return
    WindowDialog(
        show = show,
        title = "选择国家",
        onDismissRequest = onDismiss,
    ) {
        when {
            manualFallback -> ManualFallbackColumn(
                value = manualValue,
                onValueChange = { manualValue = it },
                errorMsg = state.errorMsg,
                onCancel = onDismiss,
                onConfirm = { onConfirm(manualValue.trim(), -1L) },
            )
            state.loading -> LoadingBox()
            state.errorMsg != null -> {
                val errMsg = state.errorMsg ?: ""
                ErrorColumn(
                    errorMsg = errMsg,
                    onCancel = onDismiss,
                    onManual = { manualFallback = true },
                    onRetry = { viewModel.retry() },
                )
            }
            else -> RegionBrowser(
                breadcrumb = emptyList(),
                items = state.countries,
                onPick = { node -> viewModel.confirmCountry(node) },
                onBack = {},
                onCancel = onDismiss,
                onConfirm = { fullName -> /* 由 onPick 处理 */ },
                confirmEnabled = false,
            )
        }
        ConfirmHandler(viewModel = viewModel, onConfirmCountry = onConfirm, onDismiss = onDismiss)
    }
}

/** CountryPicker 用:接收 confirmEvent 一次性回调 */
@Composable
private fun ConfirmHandler(
    viewModel: CountryPickerViewModel,
    onConfirmCountry: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.confirmEvent.collect { pair ->
            pair?.let { (name, id) ->
                onConfirmCountry(name, id)
                viewModel.clearConfirmEvent()
            }
        }
    }
}

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class CountryPickerViewModel : ViewModel() {
    private val repo: WorldRegionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val _state = MutableStateFlow(RegionPickerState())
    val state: StateFlow<RegionPickerState> = _state.asStateFlow()

    /** 一次性事件:用户已确认国家 */
    private val _confirm = MutableStateFlow<Pair<String, Long>?>(null)
    val confirmEvent: StateFlow<Pair<String, Long>?> = _confirm.asStateFlow()
    fun clearConfirmEvent() { _confirm.value = null }

    fun loadIfNeeded() {
        if (_state.value.countries.isNotEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMsg = null) }
            try {
                val list = repo.loadCountries()
                _state.update { it.copy(loading = false, countries = list) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, errorMsg = "加载国家列表失败:${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            _state.update { it.copy(errorMsg = null, loading = true) }
            try {
                repo.invalidate()
            } catch (e: Exception) {
                // invalidate 失败有日志
                BadgerLog.e("RegionPickerVM", "invalidate failed", e)
            }
            loadIfNeeded()
        }
    }

    fun confirmCountry(node: RegionNode) {
        _confirm.value = node.name to node.externalId
    }
}

// ========== 共享 state ==========

data class RegionPickerState(
    val loading: Boolean = false,
    val errorMsg: String? = null,
    val breadcrumb: List<String> = emptyList(),
    val countries: List<RegionNode> = emptyList(),
    val states: List<RegionNode> = emptyList(),
    val path: List<RegionNode> = emptyList(),
)

// ========== RegionDialog(以 countryId 为前置) ==========

@Composable
fun RegionPickerDialog(
    show: Boolean,
    current: String?,
    countryId: Long?,        // 若 null 则弹提示让用户先选国家
    countryName: String?,    // 仅用于标题展示
    onDismiss: () -> Unit,
    onConfirm: (fullRegion: String) -> Unit,
    viewModel: RegionPickerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var manualFallback by remember { mutableStateOf(false) }
    var manualValue by remember { mutableStateOf(current.orEmpty()) }

    LaunchedEffect(show, countryId, countryName) {
        if (show) {
            manualFallback = false
            manualValue = current.orEmpty()
            // 换国家后清旧省份列表
            viewModel.reset()
            when {
                countryId != null -> viewModel.loadStatesIfNeeded(countryId)
                countryName != null -> viewModel.loadStatesByCountryName(countryName)
            }
        }
    }

    if (!show) return
    WindowDialog(
        show = show,
        title = if (countryName != null) "$countryName > 选择地区" else "选择地区",
        onDismissRequest = onDismiss,
    ) {
        when {
            countryId == null -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FirstTimeHint(
                        text = "请先选择国家,再选择地区",
                        hintKey = "region_no_country",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(text = "关闭", onClick = onDismiss, modifier = Modifier.weight(1f))
                    }
                }
            }
            manualFallback -> ManualFallbackColumn(
                value = manualValue,
                onValueChange = { manualValue = it },
                errorMsg = state.errorMsg,
                onCancel = onDismiss,
                onConfirm = { onConfirm(manualValue.trim()) },
            )
            state.loading -> LoadingBox()
            state.errorMsg != null -> {
                val errMsg = state.errorMsg ?: ""
                ErrorColumn(
                    errorMsg = errMsg,
                    onCancel = onDismiss,
                    onManual = { manualFallback = true },
                    onRetry = { viewModel.retry(countryId) },
                )
            }
            else -> RegionBrowser(
                breadcrumb = state.path.map { it.name },
                items = state.states,
                onPick = { node -> viewModel.pickRegion(node) },
                onBack = { viewModel.goBack() },
                onCancel = onDismiss,
                onConfirm = { /* 由 "用全称" 按钮触发 */ },
                confirmEnabled = false,
                extraActions = {
                    TextButton(
                        text = "用全称",
                        enabled = state.path.isNotEmpty(),
                        onClick = {
                            val full = state.path.joinToString("") { it.name }
                            onConfirm(full)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "用当前名",
                        enabled = state.path.isNotEmpty(),
                        onClick = {
                            onConfirm(state.path.last().name)
                        },
                        modifier = Modifier.weight(1f),
                    )
                },
            )
        }
    }
}

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class RegionPickerViewModel : ViewModel() {
    private val repo: WorldRegionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val _state = MutableStateFlow(RegionPickerState())
    val state: StateFlow<RegionPickerState> = _state.asStateFlow()
    private var countryId: Long? = null

    fun loadStatesIfNeeded(countryId: Long) {
        this.countryId = countryId
        if (_state.value.states.isNotEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMsg = null) }
            try {
                val list = repo.loadStatesByCountry(countryId)
                _state.update { it.copy(loading = false, states = list) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, errorMsg = "加载地区失败:${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    /**
     * 用国家中文名加载。
     * 内部走「加载全部国家 → 匹配 name → 拿到 id → 加载省」两步。
     * 仅在 Dialog 入参没有 externalId 但有 name 时使用。
     */
    fun loadStatesByCountryName(countryName: String) {
        if (_state.value.states.isNotEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMsg = null) }
            try {
                // **单次网络**:WorldRegionRepository.loadStatesByCountryName 内部保证
                // 只拉一次 states.json(700KB),失败抛异常给 UI 处理。
                // 不再依赖 countries 找 id 再拉 states,避免双网络串行失败。
                val list = repo.loadStatesByCountryName(countryName)
                _state.update { it.copy(loading = false, states = list) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, errorMsg = "加载地区失败:${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    fun retry(countryId: Long) {
        _state.update { it.copy(states = emptyList()) }
        loadStatesIfNeeded(countryId)
    }

    fun pickRegion(region: RegionNode) {
        val newPath = _state.value.path + region
        // 本数据集 states 不再细分,选中即确定
        _state.update { it.copy(path = newPath) }
    }

    fun goBack() {
        val newPath = _state.value.path.dropLast(1)
        _state.update { it.copy(path = newPath) }
    }

    fun reset() {
        _state.value = RegionPickerState()
        countryId = null
    }
}

// ========== 共享 Composables ==========

@Composable
internal fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ManualFallbackColumn(
    value: String,
    onValueChange: (String) -> Unit,
    errorMsg: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (errorMsg != null) {
            FirstTimeHint(
                text = errorMsg,
                hintKey = "region_manual_fallback",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = "地区",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
            TextButton(
                text = "确定",
                enabled = value.isNotBlank(),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun ErrorColumn(
    errorMsg: String,
    onCancel: () -> Unit,
    onManual: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FirstTimeHint(
            text = errorMsg,
            hintKey = "region_error",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
            TextButton(text = "手动输入", onClick = onManual, modifier = Modifier.weight(1f))
            TextButton(text = "重试", onClick = onRetry, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun RegionBrowser(
    breadcrumb: List<String>,
    items: List<RegionNode>,
    onPick: (RegionNode) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmEnabled: Boolean,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (breadcrumb.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.ArrowLeft,
                    contentDescription = "返回上级",
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "返回 ${breadcrumb.last()}",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            items(items) { region ->
                RegionRow(region = region, onClick = { onPick(region) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
            extraActions()
            TextButton(
                text = "确定",
                enabled = confirmEnabled,
                onClick = {
                    onConfirm(breadcrumb.joinToString(""))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun RegionRow(
    region: RegionNode,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = region.name,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}
