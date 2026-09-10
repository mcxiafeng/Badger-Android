package top.mcxiafeng.badger.pages.person.contact.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.LocationRepository
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.network.AmapPoi
import top.mcxiafeng.badger.network.RegeoResult
import top.mcxiafeng.badger.utils.BadgerLog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val SEARCH_DEBOUNCE_MS = 500L
private val RESULTS_MAX_HEIGHT = 320.dp
private const val SEARCH_PAGE_SIZE = 15

/**
 * 位置选择 Dialog（联系人详情页「国家」字段入口）。
 *
 * 选点后提取 country + region 写入已有的国家/地区字段——不再有独立"位置"字段。
 * 三路径：POI 搜索（高德→Photon 兜底，国内外通吃）、当前定位（逆地理）、手动填写。
 *
 * @param onConfirm (country, region) — 填入国家+地区；null/null = 清除
 */
@Composable
fun LocationPickerDialog(
    show: Boolean,
    hasCurrent: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (country: String?, region: String?) -> Unit,
    viewModel: LocationPickerViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(show) { if (show) viewModel.reset() }

    if (!show) return
    WindowDialog(show = true, title = "选择地区", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = state.searchInput,
                onValueChange = { viewModel.onSearchInput(it) },
                modifier = Modifier.fillMaxWidth(),
                label = "搜索地点名称或地址",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (state.locating) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在获取当前位置…", style = MiuixTheme.textStyles.body2)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = "使用当前位置", onClick = { viewModel.useCurrentLocation() })
                    if (state.locationUnsupported) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("设备不支持定位", style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }

            state.errorMsg?.let { msg ->
                Text(msg, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                when {
                    state.manualFallback -> ManualLocationColumn(
                        country = state.manualCountry,
                        region = state.manualRegion,
                        onCountryChange = { viewModel.updateManual(it, state.manualRegion) },
                        onRegionChange = { viewModel.updateManual(state.manualCountry, it) },
                        onCancel = { viewModel.exitManual() },
                        onConfirm = {
                            val (c, r) = viewModel.confirmManual()
                            if (c != null) onConfirm(c, r) else viewModel.enterManualError()
                        },
                    )
                    state.loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    state.results.isEmpty() -> EmptyResultsColumn(onManual = { viewModel.enterManual() })
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(state.results, key = { i, p -> "$i:${p.id ?: p.name}:${p.longitude}" }) { _, poi ->
                            BasicComponent(
                                title = poi.name.ifBlank { "未命名地点" },
                                summary = poi.address,
                                onClick = { onConfirm(poi.country, poi.address) },
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasCurrent) { TextButton(text = "清除", onClick = { onConfirm(null, null) }) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun EmptyResultsColumn(onManual: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("没有找到相关地点", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(text = "手动填写", onClick = onManual)
    }
}

@Composable
private fun ManualLocationColumn(
    country: String, region: String,
    onCountryChange: (String) -> Unit, onRegionChange: (String) -> Unit,
    onCancel: () -> Unit, onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(value = country, onValueChange = onCountryChange, modifier = Modifier.fillMaxWidth(),
            label = "国家", useLabelAsPlaceholder = true, singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(value = region, onValueChange = onRegionChange, modifier = Modifier.fillMaxWidth(),
            label = "地区（省市区）", useLabelAsPlaceholder = true, singleLine = true)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(text = "取消", onClick = onCancel)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(text = "确定", onClick = onConfirm)
        }
    }
}

@Immutable
data class LocationPickerUiState(
    val searchInput: String = "",
    val loading: Boolean = false,
    val locating: Boolean = false,
    val locationUnsupported: Boolean = false,
    val results: List<AmapPoi> = emptyList(),
    val errorMsg: String? = null,
    val manualFallback: Boolean = false,
    val manualCountry: String = "",
    val manualRegion: String = "",
)

class LocationPickerViewModel : ViewModel() {
    private val locationRepository: LocationRepository = KoinComponentBy.get()
    private val _state = MutableStateFlow(LocationPickerUiState())
    val state: StateFlow<LocationPickerUiState> = _state.asStateFlow()
    private var centerPoint: top.mcxiafeng.badger.platform.GeoPoint? = null
    private var searchJob: Job? = null

    fun reset() {
        searchJob?.cancel()
        centerPoint = null
        _state.value = LocationPickerUiState(locationUnsupported = !locationRepository.isLocationSupported())
    }

    fun onSearchInput(raw: String) {
        _state.update { it.copy(searchInput = raw, errorMsg = null, manualFallback = false) }
        searchJob?.cancel()
        val keyword = raw.trim()
        if (keyword.isEmpty()) { _state.update { it.copy(results = emptyList(), loading = false) }; return }
        searchJob = viewModelScope.launch { delay(SEARCH_DEBOUNCE_MS); search(keyword) }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true, errorMsg = null, manualFallback = false) }
            try {
                if (!locationRepository.isLocationPermissionGranted()) {
                    val granted = locationRepository.requestLocationPermission()
                    if (!granted) {
                        _state.update { it.copy(locating = false, errorMsg = "未授予定位权限，可手动填写") }
                        return@launch
                    }
                }
                val point = locationRepository.currentPosition()
                if (point == null) {
                    _state.update { it.copy(locating = false, errorMsg = "定位失败，请重试或手动填写") }
                    return@launch
                }
                centerPoint = point
                val regeo = locationRepository.reverseGeocode(point)
                val candidates = buildList {
                    // 当前位置作为首条：country + region（逆地理组合的省市区）
                    regeo.formattedAddress?.let { addr ->
                        add(AmapPoi(name = "当前位置", address = addr, longitude = point.longitude,
                            latitude = point.latitude, country = regeo.country,
                            province = regeo.province, city = regeo.city, district = regeo.district,
                            distance = 0, id = "current"))
                    }
                    // 附近 POI
                    runCatching { locationRepository.searchAround(point, pageSize = SEARCH_PAGE_SIZE) }
                        .onFailure { BadgerLog.w(TAG, "useCurrentLocation: 周边搜索失败(定位结果保留)", it) }
                        .getOrNull()?.pois?.forEach { add(it) }
                }
                _state.update { it.copy(locating = false, results = candidates,
                    errorMsg = if (candidates.isEmpty()) "附近没有可用的地点信息" else null) }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "useCurrentLocation 失败", e)
                _state.update { it.copy(locating = false, errorMsg = "定位异常：${e.message ?: "未知错误"}") }
            }
        }
    }

    fun enterManual() { _state.update { it.copy(manualFallback = true, errorMsg = null) } }
    fun updateManual(country: String, region: String) { _state.update { it.copy(manualCountry = country, manualRegion = region, errorMsg = null) } }
    fun exitManual() { _state.update { it.copy(manualFallback = false, manualCountry = "", manualRegion = "") } }
    fun enterManualError() { _state.update { it.copy(errorMsg = "请至少填写国家或地区") } }

    /** 手动提交：country/region 至少一项非空 → 返回 (country, region)；否则返回 (null, null)。 */
    fun confirmManual(): Pair<String?, String?> {
        val s = _state.value
        val c = s.manualCountry.trim()
        val r = s.manualRegion.trim()
        if (c.isBlank() && r.isBlank()) return null to null
        return c.ifBlank { null } to r.ifBlank { null }
    }

    private suspend fun search(keyword: String) {
        _state.update { it.copy(loading = true, errorMsg = null, manualFallback = false) }
        try {
            val page = centerPoint?.let { center ->
                locationRepository.searchAround(center, keywords = keyword, pageSize = SEARCH_PAGE_SIZE)
            } ?: locationRepository.searchKeyword(keyword, pageSize = SEARCH_PAGE_SIZE)
            _state.update { it.copy(loading = false, results = page.pois,
                errorMsg = if (page.pois.isEmpty()) "没有找到相关地点，可尝试手动填写" else null) }
        } catch (e: Exception) {
            BadgerLog.e(TAG, "search 失败 keywordLen=${keyword.length}", e)
            _state.update { it.copy(loading = false, results = emptyList(),
                errorMsg = "搜索失败：${e.message ?: "网络异常"}，可尝试手动填写") }
        }
    }

    private companion object { const val TAG = "LocationPickerViewModel" }
}
