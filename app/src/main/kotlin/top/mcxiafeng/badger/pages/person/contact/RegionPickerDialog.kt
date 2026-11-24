package top.mcxiafeng.badger.pages.person.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.mcxiafeng.badger.data.repository.GaodeDistrict
import top.mcxiafeng.badger.ui.components.BreadcrumbBar
import top.mcxiafeng.badger.ui.components.BreadcrumbItem
import top.mcxiafeng.badger.ui.components.DialogButtonRow
import top.mcxiafeng.badger.utils.CountryCatalog
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

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
    onConfirm: (regionName: String) -> Unit,
    viewModel: RegionPickerViewModel = hiltViewModel(),
) {
    if (!show) return
    val state by viewModel.state.collectAsState()
    val country = countryName?.trim()?.takeIf { it.isNotEmpty() }
    var browsing by remember(country) { mutableStateOf(false) }
    var manualValue by remember(current) { mutableStateOf(current.orEmpty()) }
    DisposableEffect(viewModel, country) { onDispose { viewModel.reset() } }

    WindowDialog(show = true, title = "编辑地区", onDismissRequest = onDismiss) {
        if (browsing) {
            RegionPickerContent(state, onDismiss, onConfirm, viewModel, onManual = {
                viewModel.reset()
                browsing = false
            })
        } else {
            ManualRegionContent(manualValue, { manualValue = it }, country, onDismiss, onConfirm, onBrowse = {
                viewModel.loadInitial(it)
                browsing = true
            })
        }
    }
}

@Composable
private fun ManualRegionContent(
    value: String,
    onValueChange: (String) -> Unit,
    country: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onBrowse: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                label = "地区名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (country != null && country in CountryCatalog.ALL) {
                RegionPickerMessage("联网选择会向高德发送所选国家和行政区划编码。")
                TextButton(text = "从高德选择", onClick = { onBrowse(country) })
            }
        }
        DialogButtonRow(
            onNegative = onDismiss,
            onPositive = { onConfirm(value.trim()) },
            positiveEnabled = value.isNotBlank(),
        )
    }
}

@Composable
private fun RegionPickerContent(
    state: RegionPickerState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    viewModel: RegionPickerViewModel,
    onManual: () -> Unit,
) {
    var keyword by remember(state.path) { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(text = "手动填写", onClick = onManual)
        if (state.path.isNotEmpty()) {
            BreadcrumbBar(
                items = state.path.map { BreadcrumbItem(path = it.name) },
                onItemClick = viewModel::goBackTo,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            state.loading -> LoadingBox(Modifier.weight(1f, fill = false))
            state.errorMsg != null -> RegionLoadError(
                state.errorMsg,
                viewModel::retry,
                Modifier.weight(1f, fill = false),
            )
            state.children.isEmpty() -> RegionPickerMessage(
                if (state.selectedRegion == null) "没有可选地区，可手动填写" else "没有下级行政区",
            )
            else -> RegionChildList(state, keyword, { keyword = it }, viewModel::pickChild, Modifier.weight(1f, fill = false))
        }
        DialogButtonRow(
            onNegative = onDismiss,
            onPositive = { viewModel.state.value.selectedRegion?.let(onConfirm) },
            positiveEnabled = state.selectedRegion != null,
        )
    }
}

@Composable
private fun RegionLoadError(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = message, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
        TextButton(text = "重试", onClick = onRetry)
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
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onPick: (GaodeDistrict) -> Unit,
    modifier: Modifier,
) {
    val filtered = remember(state.children, keyword) {
        val term = keyword.trim()
        state.children.filter { it.name.contains(term, ignoreCase = true) }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RegionSearchField(keyword = keyword, onKeywordChange = onKeywordChange, label = "搜索地区")
        if (filtered.isEmpty()) {
            RegionPickerMessage("无匹配结果")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = LIST_HEIGHT)) {
                items(filtered, key = { "${it.adcode}:${it.name}" }) { district ->
                    DistrictRow(district, onClick = { onPick(district) })
                }
            }
        }
    }
}
