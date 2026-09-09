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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import top.mcxiafeng.badger.data.model.ContactLocation
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

// 魔法数字收口
/** 搜索输入防抖间隔。 */
private const val SEARCH_DEBOUNCE_MS = 500L
/** 结果列表最大高度。 */
private val RESULTS_MAX_HEIGHT = 320.dp
/** 单页搜索条数（服务端上限 25）。 */
private const val SEARCH_PAGE_SIZE = 15

/**
 * 位置选择 Dialog（联系人详情页「位置」字段入口）。
 *
 * 三条选点路径：
 * 1. 关键字搜索（有定位中心时走周边搜索按距离排序，否则全国关键字搜索）；
 * 2. 「使用当前位置」→ 设备定位 + 逆地理，结果首条标记为当前位置；
 * 3. 手动填写（代理不可用/搜索无结果的兜底，无坐标纯地址）。
 *
 * 契约：[onConfirm] 传 null 表示清除位置；选中结果立即回调并关闭。
 */
@Composable
fun LocationPickerDialog(
    show: Boolean,
    current: ContactLocation?,
    onDismiss: () -> Unit,
    onConfirm: (ContactLocation?) -> Unit,
    viewModel: LocationPickerViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(show) {
        if (show) viewModel.reset()
    }

    if (!show) return
    WindowDialog(
        show = true,
        title = "选择位置",
        onDismissRequest = onDismiss,
    ) {
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

            // 当前定位入口行
            if (state.locating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在获取当前位置…", style = MiuixTheme.textStyles.body2)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "使用当前位置",
                        onClick = { viewModel.useCurrentLocation() },
                    )
                    if (state.locationUnsupported) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "设备不支持定位",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            // 错误提示（主题色提示，非错误红——多为可操作的引导文案）
            state.errorMsg?.let { msg ->
                Text(
                    text = msg,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 结果 / 手动兜底 / 加载 / 空态
            Box(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                when {
                    state.manualFallback -> ManualLocationColumn(
                        name = state.manualName,
                        address = state.manualAddress,
                        onNameChange = { name -> viewModel.updateManual(name, state.manualAddress) },
                        onAddressChange = { address -> viewModel.updateManual(state.manualName, address) },
                        onCancel = { viewModel.exitManual() },
                        onConfirm = {
                            val location = viewModel.confirmManual()
                            if (location != null) onConfirm(location) else viewModel.enterManualError()
                        },
                    )

                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.results.isEmpty() -> EmptyResultsColumn(
                        onManual = { viewModel.enterManual() },
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        // [修复防御]: key 必须全列表唯一——兜底来源（手动/OSM）可能无 poiId 且
                        // 同名同坐标，补 index 维度杜绝重复 key 崩溃
                        itemsIndexed(
                            state.results,
                            key = { index, item ->
                                "${index}:${item.poiId ?: "n"}:${item.name}:${item.longitude}"
                            },
                        ) { _, item ->
                            BasicComponent(
                                title = if (item.source == ContactLocation.SOURCE_CURRENT) {
                                    "当前位置 · ${item.displayTitle()}"
                                } else {
                                    item.displayTitle()
                                },
                                summary = item.displaySubtitle(),
                                onClick = { onConfirm(item) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current != null) {
                    TextButton(
                        text = "清除位置",
                        onClick = { onConfirm(null) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun EmptyResultsColumn(onManual: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有找到相关地点",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            text = "手动填写位置",
            onClick = onManual,
        )
    }
}

@Composable
private fun ManualLocationColumn(
    name: String,
    address: String,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = "位置名称（如：公司）",
            useLabelAsPlaceholder = true,
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = "详细地址",
            useLabelAsPlaceholder = true,
            singleLine = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            TextButton(text = "取消", onClick = onCancel)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(text = "确定", onClick = onConfirm)
        }
    }
}

/** 位置选择器状态。 */
@Immutable
data class LocationPickerUiState(
    val searchInput: String = "",
    val loading: Boolean = false,
    val locating: Boolean = false,
    val locationUnsupported: Boolean = false,
    val results: List<ContactLocation> = emptyList(),
    val errorMsg: String? = null,
    val manualFallback: Boolean = false,
    val manualName: String = "",
    val manualAddress: String = "",
)

/** [§14.2] Koin 字段注入（KoinComponentBy 静态 get，测试经 mock 模块覆盖）。 */
class LocationPickerViewModel : ViewModel() {

    private val locationRepository: LocationRepository = KoinComponentBy.get()

    private val _state = MutableStateFlow(LocationPickerUiState())
    val state: StateFlow<LocationPickerUiState> = _state.asStateFlow()

    /** 最近一次定位中心（有中心 → 搜索走周边排序）。 */
    private var centerPoint: top.mcxiafeng.badger.platform.GeoPoint? = null
    private var searchJob: Job? = null

    fun reset() {
        searchJob?.cancel()
        centerPoint = null
        _state.value = LocationPickerUiState(
            locationUnsupported = !locationRepository.isLocationSupported(),
        )
    }

    /** 搜索输入（防抖后触发搜索；空输入清结果）。 */
    fun onSearchInput(raw: String) {
        _state.update { it.copy(searchInput = raw, errorMsg = null, manualFallback = false) }
        searchJob?.cancel()
        val keyword = raw.trim()
        if (keyword.isEmpty()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            search(keyword)
        }
    }

    /** 「使用当前位置」：权限 → 定位 → 逆地理 → 当前位置置顶 + 周边候选。 */
    fun useCurrentLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true, errorMsg = null, manualFallback = false) }
            try {
                if (!locationRepository.isLocationPermissionGranted()) {
                    val granted = locationRepository.requestLocationPermission()
                    if (!granted) {
                        _state.update {
                            it.copy(
                                locating = false,
                                errorMsg = "未授予定位权限，可在系统设置中开启，或手动填写位置",
                            )
                        }
                        return@launch
                    }
                }
                val point = locationRepository.currentPosition()
                if (point == null) {
                    _state.update {
                        it.copy(locating = false, errorMsg = "定位失败，请重试或手动填写位置")
                    }
                    return@launch
                }
                centerPoint = point
                val regeo = locationRepository.reverseGeocode(point)
                val currentLocation = regeo.toCurrentLocation(point)
                val around = runCatching {
                    locationRepository.searchAround(point, pageSize = SEARCH_PAGE_SIZE)
                }.onFailure { e ->
                    BadgerLog.w(TAG, "useCurrentLocation: 周边搜索失败(定位结果保留)", e)
                }.getOrNull()
                val candidates = buildList {
                    currentLocation?.let { add(it) }
                    around?.pois?.forEach { poi ->
                        ContactLocation.fromPoi(poi.toJsonObject(), ContactLocation.SOURCE_POI)
                            ?.takeIf { candidate -> candidate.poiId != currentLocation?.poiId }
                            ?.let { add(it) }
                    }
                }
                _state.update {
                    it.copy(
                        locating = false,
                        results = candidates,
                        errorMsg = if (candidates.isEmpty()) "附近没有可用的地点信息" else null,
                    )
                }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "useCurrentLocation 失败", e)
                _state.update {
                    it.copy(locating = false, errorMsg = "定位异常：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /** 手动兜底入口。 */
    fun enterManual() {
        _state.update { it.copy(manualFallback = true, errorMsg = null) }
    }

    fun updateManual(name: String, address: String) {
        _state.update { it.copy(manualName = name, manualAddress = address, errorMsg = null) }
    }

    fun exitManual() {
        _state.update { it.copy(manualFallback = false, manualName = "", manualAddress = "") }
    }

    /** 手动提交校验失败提示（名称/地址至少一项）。 */
    fun enterManualError() {
        _state.update { it.copy(errorMsg = "请至少填写位置名称或地址") }
    }

    /** 手动位置提交（无坐标，纯地址；name/address 至少一项）。 */
    fun confirmManual(): ContactLocation? {
        val s = _state.value
        val name = s.manualName.trim()
        val address = s.manualAddress.trim()
        if (name.isBlank() && address.isBlank()) return null
        return ContactLocation(name = name, address = address, source = ContactLocation.SOURCE_MANUAL)
    }

    private suspend fun search(keyword: String) {
        _state.update { it.copy(loading = true, errorMsg = null, manualFallback = false) }
        try {
            // LocationRepository 内部已切 IO，这里不再包一层
            val page = centerPoint?.let { center ->
                locationRepository.searchAround(center, keywords = keyword, pageSize = SEARCH_PAGE_SIZE)
            } ?: locationRepository.searchKeyword(keyword, pageSize = SEARCH_PAGE_SIZE)
            val candidates = page.pois.mapNotNull {
                ContactLocation.fromPoi(it.toJsonObject(), ContactLocation.SOURCE_POI)
            }
            _state.update {
                it.copy(
                    loading = false,
                    results = candidates,
                    errorMsg = if (candidates.isEmpty()) "没有找到相关地点，可尝试手动填写" else null,
                )
            }
        } catch (e: Exception) {
            BadgerLog.e(TAG, "search 失败 keywordLen=${keyword.length}", e)
            _state.update {
                it.copy(
                    loading = false,
                    results = emptyList(),
                    errorMsg = "搜索失败：${e.message ?: "网络异常"}，可尝试手动填写",
                )
            }
        }
    }

    /** 逆地理结果 → 当前位置（取最近 POI 名，退化到 township/district）。 */
    private fun RegeoResult.toCurrentLocation(point: top.mcxiafeng.badger.platform.GeoPoint): ContactLocation? {
        val nearest = pois.firstOrNull()
        val name = nearest?.name ?: township ?: district ?: return null
        val address = nearest?.address ?: formattedAddress ?: ""
        return ContactLocation(
            name = name,
            address = address,
            longitude = point.longitude,
            latitude = point.latitude,
            province = province,
            city = city,
            district = district,
            poiId = nearest?.id,
            source = ContactLocation.SOURCE_CURRENT,
        )
    }

    private fun AmapPoi.toJsonObject(): JsonObject = buildJsonObject {
        id?.let { put("id", JsonPrimitive(it)) }
        put("name", JsonPrimitive(name))
        address?.let { put("address", JsonPrimitive(it)) }
        longitude?.let { put("longitude", JsonPrimitive(it)) }
        latitude?.let { put("latitude", JsonPrimitive(it)) }
        province?.let { put("province", JsonPrimitive(it)) }
        city?.let { put("city", JsonPrimitive(it)) }
        district?.let { put("district", JsonPrimitive(it)) }
        distance?.let { put("distance", JsonPrimitive(it)) }
    }

    private companion object {
        const val TAG = "LocationPickerViewModel"
    }
}
