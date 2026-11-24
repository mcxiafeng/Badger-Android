package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.GaodeDistrict
import top.mcxiafeng.badger.data.repository.GaodeException
import top.mcxiafeng.badger.data.repository.GaodeRegionRepository
import javax.inject.Inject

@Immutable
data class RegionPickerState(
    val loading: Boolean = false,
    val errorMsg: String? = null,
    val path: List<GaodeDistrict> = emptyList(),
    val children: List<GaodeDistrict> = emptyList(),
    val savedRegionUnresolved: Boolean = false,
) {
    val selectedRegion: String?
        get() = path.lastOrNull()?.name?.takeIf { path.size > 1 && !loading && errorMsg == null }

    val needsManualInput: Boolean
        get() = !loading && (errorMsg != null || savedRegionUnresolved || (path.size == 1 && children.isEmpty()))
}

@HiltViewModel
class RegionPickerViewModel @Inject constructor(
    private val repo: GaodeRegionRepository,
    private val fields: FieldRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RegionPickerState())
    val state: StateFlow<RegionPickerState> = _state.asStateFlow()
    private val levels = mutableListOf<Level>()
    private var requestJob: Job? = null
    private var retryLoad: (() -> Unit)? = null

    fun loadInitial(countryName: String, currentRegion: String? = null, contactId: Long? = null) {
        reset()
        loadLevels {
            val result = repo.queryCountry(countryName.trim())
            val root = result.top ?: throw GaodeException("高德没有返回该国家或地区的区划，请手动填写")
            val rootLevel = Level(root, result.children)
            val name = currentRegion?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null || rootLevel.children.isEmpty()) listOf(rootLevel)
            else restoreLevels(countryName.trim(), name, contactId, rootLevel)
        }
    }

    private suspend fun restoreLevels(country: String, region: String, contactId: Long?, root: Level): List<Level> {
        val saved = contactId?.let { fields.getContactRegionPath(it) }.orEmpty()
        restoreSavedPath(root, saved, region)?.let { return it }
        val legacy = repo.findSavedPath(country, region).mapNotNull { result ->
            result.top?.let { Level(it, result.children) }
        }
        val leaf = legacy.lastOrNull() ?: return listOf(root.copy(savedRegionUnresolved = true))
        return legacy.dropLast(1) + leaf.copy(children = repo.queryChildren(leaf.district))
    }

    private suspend fun restoreSavedPath(root: Level, keys: List<String>, name: String): List<Level>? {
        if (keys.size < 2 || keys.first() != root.district.pathKey) return null
        val restored = mutableListOf(root)
        for (key in keys.drop(1)) {
            val district = restored.last().children.singleOrNull { it.pathKey == key } ?: return null
            restored.add(Level(district, repo.queryChildren(district)))
        }
        return restored.takeIf { it.last().district.name == name }
    }

    fun pickChild(district: GaodeDistrict) {
        val current = state.value
        if (current.loading || current.errorMsg != null || district !in current.children) return
        loadLevels { listOf(Level(district, repo.queryChildren(district))) }
    }

    fun goBackTo(index: Int) {
        val current = state.value
        if (current.loading || index !in levels.indices) return
        if (index == levels.lastIndex && current.errorMsg == null && !current.savedRegionUnresolved) return
        levels.subList(index + 1, levels.size).clear()
        if (current.savedRegionUnresolved) levels[index] = levels[index].copy(savedRegionUnresolved = false)
        retryLoad = null
        showCurrentLevel()
    }

    fun retry() {
        if (!state.value.loading) retryLoad?.invoke()
    }

    fun reset() {
        requestJob?.cancel()
        requestJob = null
        retryLoad = null
        levels.clear()
        _state.value = RegionPickerState()
    }

    private fun loadLevels(load: suspend () -> List<Level>) {
        retryLoad = { loadLevels(load) }
        _state.update { it.copy(loading = true, errorMsg = null) }
        requestJob = viewModelScope.launch {
            try {
                val loaded = load()
                ensureActive()
                levels.addAll(loaded)
                retryLoad = null
                showCurrentLevel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                Log.w(TAG, "地区层级加载失败，类型=${e.javaClass.simpleName}")
                val message = (e as? GaodeException)?.message ?: "加载地区失败，请重试或手动填写"
                _state.update { it.copy(loading = false, errorMsg = message) }
            }
        }
    }

    private fun showCurrentLevel() {
        Log.d(TAG, "地区选择层级=${levels.size}")
        val current = levels.last()
        _state.value = RegionPickerState(
            path = levels.map { it.district },
            children = current.children,
            savedRegionUnresolved = current.savedRegionUnresolved,
        )
    }

    private data class Level(
        val district: GaodeDistrict,
        val children: List<GaodeDistrict>,
        val savedRegionUnresolved: Boolean = false,
    )

    private companion object {
        const val TAG = "RegionPickerViewModelTester"
    }
}
