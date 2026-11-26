package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.data.repository.GaodeDistrict
import top.mcxiafeng.badger.ui.components.BreadcrumbBar
import top.mcxiafeng.badger.ui.components.BreadcrumbItem
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.ui.designsystem.BadgerSize
import top.mcxiafeng.badger.utils.CountryCatalog
import top.yukonga.miuix.kmp.basic.Text
import top.mcxiafeng.badger.ui.components.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.mcxiafeng.badger.ui.glass.TextButton
import top.mcxiafeng.badger.ui.glass.WindowDialog

/**
 * 国家选择 Dialog
 *
 * **交互:直接选**。打开即展示内置国家目录(高德无全球国家列表端点),
 * 顶部搜索框用于过滤(支持中文子串)。点选即确认并关闭。
 */
@Composable
fun CountryPickerDialog(
    show: Boolean,
    current: String?,
    onDismiss: () -> Unit,
    onConfirm: (countryName: String) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }

    LaunchedEffect(show) {
        if (show) keyword = ""
    }

    if (!show) return
    WindowDialog(
        show = show,
        title = "选择国家/地区",
        onDismissRequest = onDismiss,
    ) {
        CountryPickerContent(
            keyword = keyword,
            onKeywordChange = { keyword = it },
            current = current,
            onPick = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CountryPickerContent(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val countries = remember(keyword) { CountryCatalog.filter(keyword) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        RegionSearchField(
            keyword = keyword,
            onKeywordChange = onKeywordChange,
            label = "搜索国家/地区",
        )
        Spacer(Modifier.height(8.dp))
        if (countries.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().height(LIST_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有匹配的国家/地区",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth().height(LIST_HEIGHT)) {
                items(countries, key = { it }) { name ->
                    CountryRow(
                        name = name,
                        selected = name == current,
                        onClick = { onPick(name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun RegionPickerDialog(
    show: Boolean,
    current: String?,
    countryName: String?,
    onDismiss: () -> Unit,
    onConfirm: (regionName: String, path: List<String>) -> Unit,
    contactId: Long? = null,
    viewModel: RegionPickerViewModel = hiltViewModel(),
) {
    if (!show) return
    val state by viewModel.state.collectAsState()
    val country = countryName?.trim()?.takeIf { it.isNotEmpty() }
    DisposableEffect(viewModel, contactId, country, current) { onDispose { viewModel.reset() } }
    LaunchedEffect(viewModel, contactId, country, current) {
        if (country != null) viewModel.loadInitial(country, current, contactId)
    }

    WindowDialog(show = true, title = "选择地区", onDismissRequest = onDismiss) {
        RegionPickerContent(state, country, current, onDismiss, onConfirm, viewModel)
    }
}

@Composable
private fun RegionPickerContent(
    state: RegionPickerState,
    country: String?,
    current: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit,
    viewModel: RegionPickerViewModel,
) {
    var manualValue by remember(country, current) { mutableStateOf(current.orEmpty()) }
    val manual = country == null || state.needsManualInput
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (country != null && state.path.isNotEmpty()) {
            BreadcrumbBar(
                items = state.path.map { BreadcrumbItem(path = it.name) },
                onItemClick = viewModel::goBackTo,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val contentModifier = Modifier.weight(1f, fill = false)
        when {
            manual -> ManualRegionContent(
                manualValue, { manualValue = it }, state.errorMsg, state.savedRegionUnresolved,
                viewModel::retry, contentModifier,
            )
            state.loading || state.path.isEmpty() -> LoadingBox(contentModifier)
            state.children.isNotEmpty() -> RegionChildList(state, viewModel::pickChild, contentModifier)
            else -> RegionPickerMessage("没有下级行政区")
        }
        DialogButtonRow(
            onNegative = onDismiss,
            onPositive = {
                val latest = viewModel.state.value
                if (manual) {
                    val name = manualValue.trim()
                    if (name.isNotEmpty() && (country == null || latest.needsManualInput)) onConfirm(name, emptyList())
                } else {
                    latest.selectedRegion?.let { onConfirm(it, latest.path.map(GaodeDistrict::pathKey)) }
                }
            },
            positiveEnabled = if (manual) manualValue.isNotBlank() else state.selectedRegion != null,
        )
    }
}

@Composable
private fun ManualRegionContent(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    savedRegionUnresolved: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) { RegionPickerMessage(error) }
                Box(
                    modifier = Modifier.sizeIn(
                        minWidth = BadgerSize.minimumTouchTarget,
                        minHeight = BadgerSize.minimumTouchTarget,
                    ).clickable(role = Role.Button, onClick = onRetry),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("重试", color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.body2)
                }
            }
        } else if (savedRegionUnresolved) {
            RegionPickerMessage("无法定位已保存的地区，可保留原值或点上方国家重新选择。")
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = "地区名称",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RegionPickerMessage(message: String) {
    Text(
        text = message,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun RegionChildList(
    state: RegionPickerState,
    onPick: (GaodeDistrict) -> Unit,
    modifier: Modifier,
) {
    var keyword by remember(state.path) { mutableStateOf("") }
    val filtered = remember(state.children, keyword) {
        val term = keyword.trim()
        state.children.filter { it.name.contains(term, ignoreCase = true) }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RegionSearchField(keyword = keyword, onKeywordChange = { keyword = it }, label = "搜索地区")
        if (filtered.isEmpty()) {
            RegionPickerMessage("无匹配结果")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = LIST_HEIGHT)) {
                items(filtered, key = GaodeDistrict::pathKey) { district ->
                    DistrictRow(district, onClick = { onPick(district) })
                }
            }
        }
    }
}
