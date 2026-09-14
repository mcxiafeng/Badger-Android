package top.mcxiafeng.badger.pages.setupguide

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.utils.KtorHttpCore
import top.mcxiafeng.badger.utils.HttpResult
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.prefs.setServerUrlConfigured
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.network.kindCanSync
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

class SetupGuideViewModel : ViewModel() {
    private val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val syncEngine: SyncEngine = top.mcxiafeng.badger.di.KoinComponentBy.get()

    private val serverUrlHolder: ServerUrlHolder = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val serverApiFactory: ServerApiFactory = top.mcxiafeng.badger.di.KoinComponentBy.get()
    private val http = KtorHttpCore()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    
    val currentServerUrl: StateFlow<String> = serverUrlHolder.url

    

    val profile: StateFlow<UserProfileCacheEntity?> = userProfileRepository.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    
    

    private val _pageValidity = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val pageValidity: StateFlow<Map<Int, Boolean>> = _pageValidity.asStateFlow()

    fun setPageValid(page: Int, valid: Boolean) {
        if (_pageValidity.value[page] != valid) {
            _pageValidity.value = _pageValidity.value + (page to valid)
        }
    }

    
    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    

    fun testServerConnection(url: String) {
        if (_testState.value is TestState.Testing) return
        _testState.value = TestState.Testing
        viewModelScope.launch {
            val result = withContext(BadgerDispatchers.io) {
                runCatching {
                    
                    when (val r = http.get(url, timeoutMs = 15_000)) {
                        
                        is HttpResult.Success -> 200
                        is HttpResult.Failure -> r.code
                    }
                }
            }
            _testState.value = result.fold(
                onSuccess = { code ->
                    BadgerLog.d(TAG, "testServerConnection: $url → HTTP $code (≤15s)")
                    TestState.Success(code)
                },
                onFailure = { e ->
                    BadgerLog.w(TAG, "testServerConnection: $url → ${e::class.simpleName}: ${e.message} (≤15s)")
                    TestState.Failed(e.message ?: "无法连接到服务器")
                },
            )
        }
    }

    
    fun resetTestState() {
        _testState.value = TestState.Idle
    }

    

    fun updateServerUrl(newUrl: String, defaultUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) {
            BadgerLog.w(TAG, "updateServerUrl: blank input ignored")
            return
        }
        
        
        serverUrlHolder.set(normalized)             
        serverApiFactory.updateBaseUrl(normalized)   
        setServerUrlConfigured(normalized != defaultUrl)
        BadgerLog.d(TAG, "Server URL updated: $normalized (hot-applied, configured=${normalized != defaultUrl})")
    }

    
    fun resetServerUrlToDefault(defaultUrl: String) {
        serverUrlHolder.set(defaultUrl)
        serverApiFactory.updateBaseUrl(defaultUrl)
        setServerUrlConfigured(false)
        BadgerLog.d(TAG, "Server URL reset to default: $defaultUrl")
    }

    

    fun runSync(reason: String = "sync", block: suspend () -> Unit) {
        if (_isSyncing.value) {
            BadgerLog.w(TAG, "[SYNC] runSync(reason=$reason) re-entered while syncing, ignored")
            return
        }
        BadgerLog.d(TAG, "[SYNC] runSync start reason=$reason")
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                BadgerLog.e(TAG, "[SYNC] sync block failed reason=$reason", e)
            } finally {
                _isSyncing.value = false
                BadgerLog.d(TAG, "[SYNC] runSync end reason=$reason")
            }
        }
    }

    

    fun bootstrapPostLogin() {
        BadgerLog.d(TAG, "[POSTLOGIN] bootstrap start")
        viewModelScope.launch {
            runCatching {
                val resp = withContext(BadgerDispatchers.io) { serverApiFactory.get().getProfile() }
                
                
                userProfileRepository.applyRemoteProfile(resp)
                BadgerLog.d(TAG, "[POSTLOGIN] profile merged")
            }.onFailure { BadgerLog.w(TAG, "[POSTLOGIN] profile fetch failed", it) }

            
            runCatching {
                val r = syncEngine.syncOnceIfIdle()
                BadgerLog.d(TAG, "[POSTLOGIN] sync result: $r")
            }.onFailure { BadgerLog.w(TAG, "[POSTLOGIN] sync failed", it) }

            BadgerLog.d(TAG, "[POSTLOGIN] bootstrap done")
        }
    }

    suspend fun getUserProfileOnce() = userProfileRepository.getUserProfileOnce()

    suspend fun saveUserProfile(entity: UserProfileCacheEntity) = userProfileRepository.saveUserProfile(entity)

    suspend fun updatePlatformField(
        fieldKey: String,
        jumpLink: String,
        value: String?,
        displayName: String?,
        avatarUrl: String?,
        originalLink: String?,
    ) = userProfileRepository.updatePlatformField(fieldKey, jumpLink, value, displayName, avatarUrl, originalLink)

    suspend fun removePlatform(fieldKey: String) = userProfileRepository.removePlatform(fieldKey)

    suspend fun savePlatformAndMaybeSync(
        fieldKey: String,
        jumpLink: String,
        value: String?,
        displayName: String?,
        avatarUrl: String?,
        originalLink: String?,
        shouldSync: Boolean,
        contactType: top.mcxiafeng.badger.network.ContactType?,
    ) {
        val preProfile = userProfileRepository.getUserProfileOnce()
        val nameWasAutoFilled = isNameAutoFilled(preProfile)

        userProfileRepository.updatePlatformField(
            fieldKey, jumpLink, value, displayName, avatarUrl, originalLink,
        )

        if (shouldSync) {
            try {
                val resolveContent = jumpLink.ifBlank { value ?: "" }
                val result = KoinComponentBy.get<ContactNetworkResolver>().identify(resolveContent)
                if (result != null) {
                    userProfileRepository.updatePlatformField(
                        fieldKey, jumpLink, value,
                        result.nickname ?: displayName,
                        result.avatarUrl ?: avatarUrl,
                        originalLink,
                    )
                    BadgerLog.d(TAG, "Auto-fetched info for $fieldKey: name=${result.nickname}")
                }
            } catch (e: Exception) {
                BadgerLog.e(TAG, "Auto-fetch failed for $fieldKey", e)
            }
        }

        if (nameWasAutoFilled) autoFillProfileName()
    }

    private fun isNameAutoFilled(profile: UserProfileCacheEntity?): Boolean {
        if (profile == null) return true
        if (profile.name.isBlank() || profile.name == "用户") return true
        return top.mcxiafeng.badger.data.repository.ContactMapper.decodePlatformsMap(profile.platformsJson)
            ?.entries?.any { (_, e) ->
                !e.displayName.isNullOrBlank() && e.displayName == profile.name
            } == true
    }

    private suspend fun autoFillProfileName() {
        val p = userProfileRepository.getUserProfileOnce() ?: return
        val platformMap = top.mcxiafeng.badger.data.repository.ContactMapper.decodePlatformsMap(p.platformsJson) ?: return
        val canSyncEntry = platformMap.entries.firstOrNull { e ->
            e.key.kindCanSync && !e.value.displayName.isNullOrBlank()
        }
        val fallbackEntry = platformMap.entries.firstOrNull { !it.value.displayName.isNullOrBlank() }
        val chosen = canSyncEntry ?: fallbackEntry ?: return
        val chosenName = chosen.value.displayName ?: return
        userProfileRepository.saveUserProfile(
            p.copy(name = chosenName, updateTime = nowMs()),
        )
        BadgerLog.d(TAG, "Profile name auto-filled: $chosenName from ${chosen.key}")
    }

    @Immutable
    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data class Success(val httpCode: Int) : TestState
        data class Failed(val message: String) : TestState
    }

    private companion object {
        const val TAG = "SetupGuideViewModel"
    }
}
