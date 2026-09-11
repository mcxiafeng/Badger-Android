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
import androidx.compose.runtime.Immutable
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
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.ui.components.FirstTimeHint
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
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
            viewModel.resetQuery()
            viewModel.loadIfNeeded()
        }
    }

    if (!show) return
    WindowDialog(
        show = true,
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
            else -> Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = state.countryQuery,
                    onValueChange = viewModel::onCountryQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = "搜索国家（中文名/英文名）",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val shown = remember(state.countries, state.countryQuery) {
                    filterSortCountries(state.countries, state.countryQuery)
                }
                RegionBrowser(
                    breadcrumb = emptyList(),
                    items = shown,
                    onPick = { node -> viewModel.confirmCountry(node) },
                    onBack = {},
                    onCancel = onDismiss,
                    onConfirm = { fullName -> /* 由 onPick 处理 */ },
                    confirmEnabled = false,
                )
            }
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
                BadgerLog.e("RegionPickerVM", "loadCountries failed", e)
                _state.update {
                    it.copy(loading = false, errorMsg = "加载国家列表失败:${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    fun onCountryQuery(query: String) {
        _state.update { it.copy(countryQuery = query) }
    }

    fun resetQuery() {
        _state.update { it.copy(countryQuery = "") }
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

@Immutable
data class RegionPickerState(
    val loading: Boolean = false,
    val errorMsg: String? = null,
    val breadcrumb: List<String> = emptyList(),
    val countries: List<RegionNode> = emptyList(),
    val states: List<RegionNode> = emptyList(),
    val path: List<RegionNode> = emptyList(),
    val countryQuery: String = "",
)

/** 国家列表过滤+排序：中国置顶，其余按逐字拼音首字母序列排序，英文原名作次序。 */
private fun filterSortCountries(list: List<RegionNode>, query: String): List<RegionNode> {
    val q = query.trim()
    val matched = if (q.isEmpty()) list else list.filter { node ->
        node.name.contains(q) || node.cname?.contains(q, ignoreCase = true) == true
    }
    return matched.sortedWith(
        compareByDescending<RegionNode> { it.name == CHINA_COUNTRY_NAME }
            .thenBy { node -> node.name.map(PinyinUtils::getPinyinInitial).joinToString("") }
            .thenBy { it.cname ?: it.name }
    )
}

private const val CHINA_COUNTRY_NAME = "中国"

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
            BadgerLog.d("RegionPickerTester", "dialog opened: countryId=$countryId countryName=$countryName")
            manualFallback = false
            manualValue = current.orEmpty()
            // 换国家后清旧省份列表
            viewModel.reset()
            if (countryId != null || !countryName.isNullOrBlank()) {
                viewModel.loadByCountry(countryId, countryName)
            }
        }
    }

    // 中国级联选到区(叶子)时的一次性自动确认
    LaunchedEffect(viewModel) {
        viewModel.confirmEvent.collect { full ->
            if (full != null) {
                BadgerLog.d("RegionPickerTester", "auto-confirm (district leaf): regionLen=${full.length}")
                onConfirm(full)
                viewModel.clearConfirmEvent()
            }
        }
    }

    if (!show) return
    WindowDialog(
        show = true,
        title = if (countryName != null) "$countryName > 选择地区" else "选择地区",
        onDismissRequest = onDismiss,
    ) {
        when {
            countryId == null && countryName.isNullOrBlank() -> {
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
                onConfirm = {
                    BadgerLog.d("RegionPickerTester", "manual confirm: regionLen=${manualValue.trim().length}")
                    onConfirm(manualValue.trim())
                },
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
                breadcrumb = state.path.map { it.name },
                items = state.states,
                onPick = { node -> viewModel.pickRegion(node) },
                onBack = { viewModel.goBack() },
                onCancel = onDismiss,
                onConfirm = { _ ->
                    BadgerLog.d("RegionPickerTester", "确定 clicked → confirmPath")
                    onConfirm(viewModel.confirmPath())
                },
                confirmEnabled = state.path.isNotEmpty(),
                extraActions = {},
            )
        }
    }
}

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class RegionPickerViewModel : ViewModel() {
    private val repo: WorldRegionRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val _state = MutableStateFlow(RegionPickerState())
    val state: StateFlow<RegionPickerState> = _state.asStateFlow()

    /** 一次性事件:中国级联选到区(叶子)时自动确认,值为拼接好的 region 串。 */
    private val _confirm = MutableStateFlow<String?>(null)
    val confirmEvent: StateFlow<String?> = _confirm.asStateFlow()
    fun clearConfirmEvent() { _confirm.value = null }

    private var countryId: Long? = null
    private var countryName: String? = null

    /** true = 当前国家为中国:走高德行政区划级联(省→市→区,最多到区);false = dr5hn states 单级。 */
    private var chinaMode = false

    fun loadByCountry(countryId: Long?, countryName: String?) {
        this.countryId = countryId
        this.countryName = countryName
        if (_state.value.states.isNotEmpty() || _state.value.loading) return
        chinaMode = countryName == CHINA_NAME
        BadgerLog.d("RegionPickerTester", "loadByCountry: countryId=$countryId countryName=$countryName chinaMode=$chinaMode")
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMsg = null) }
            try {
                val list = when {
                    chinaMode -> repo.loadChinaDistricts(null)
                    countryId != null -> repo.loadStatesByCountry(countryId)
                    else -> repo.loadStatesByCountryName(countryName.orEmpty())
                }
                BadgerLog.d("RegionPickerTester", "loadByCountry done: states=${list.size} chinaMode=$chinaMode")
                _state.update { it.copy(loading = false, states = list) }
            } catch (e: Exception) {
                BadgerLog.e("RegionPickerTester", "loadByCountry failed china=$chinaMode", e)
                _state.update {
                    it.copy(loading = false, errorMsg = "加载地区失败:${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    fun retry() {
        _state.update { it.copy(states = emptyList(), errorMsg = null) }
        loadByCountry(countryId, countryName)
    }

    fun pickRegion(region: RegionNode) {
        BadgerLog.d("RegionPickerTester", "pickRegion: name=${region.name} level=${region.level} adcode=${region.externalId} chinaMode=$chinaMode")
        if (chinaMode && region.level == LEVEL_DISTRICT) {
            // 精度封顶到区:选区即确认
            val joined = joinPath(_state.value.path + region)
            BadgerLog.d("RegionPickerTester", "district leaf → auto-confirm: regionLen=${joined.length}")
            _confirm.value = joined
            return
        }
        val newPath = _state.value.path + region
        _state.update { it.copy(path = newPath) }
        if (!chinaMode) {
            BadgerLog.d("RegionPickerTester", "non-china state picked, path=${newPath.size}, wait for 确定")
            return // dr5hn states 不再细分,由"确定"按钮提交
        }
        BadgerLog.d("RegionPickerTester", "drill into ${region.name} adcode=${region.externalId}")
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMsg = null) }
            try {
                val children = repo.loadChinaDistricts(region.externalId.toString())
                BadgerLog.d("RegionPickerTester", "drill done: ${region.name} → ${children.size} children")
                _state.update { it.copy(loading = false, states = children) }
            } catch (e: Exception) {
                BadgerLog.e("RegionPickerTester", "pickRegion 下钻失败 adcode=${region.externalId}", e)
                _state.update {
                    it.copy(loading = false, errorMsg = "加载下级区划失败:${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    fun goBack() {
        val newPath = _state.value.path.dropLast(1)
        _state.update { it.copy(path = newPath) }
        if (!chinaMode) return
        viewModelScope.launch {
            try {
                val list = if (newPath.isEmpty()) {
                    repo.loadChinaDistricts(null)
                } else {
                    repo.loadChinaDistricts(newPath.last().externalId.toString())
                }
                _state.update { it.copy(states = list) }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "goBack 加载区划失败", e)
                _state.update { it.copy(errorMsg = "加载区划失败:${e.message ?: e::class.simpleName}") }
            }
        }
    }

    /** "确定"按钮提交:路径拼接为 region 值。 */
    fun confirmPath(): String {
        val joined = joinPath(_state.value.path)
        BadgerLog.d("RegionPickerTester", "confirmPath (确定 button): regionLen=${joined.length}")
        return joined
    }

    fun reset() {
        _state.value = RegionPickerState()
        countryId = null
        countryName = null
        chinaMode = false
        _confirm.value = null
    }

    /** 路径拼接为 region 值；相邻同名节点(直辖市省/市同名)只保留一个。 */
    private fun joinPath(path: List<RegionNode>): String {
        val sb = StringBuilder()
        var last: String? = null
        for (node in path) {
            if (node.name == last) continue
            sb.append(node.name)
            last = node.name
        }
        return sb.toString()
    }

    private companion object {
        const val TAG = "RegionPickerVM"
        const val CHINA_NAME = "中国"
        const val LEVEL_DISTRICT = "district"
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
        // [AGENTS.md] 最多 2 个按钮；"手动输入"降级为文字链接
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "手动输入",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onManual() },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(text = "取消", onClick = onCancel, modifier = Modifier.weight(1f))
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
