package top.mcxiafeng.badger.pages.setupguide

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.setServerUrlConfigured
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.IdentifyResponse
import top.mcxiafeng.badger.network.UserProfileResponse
import top.mcxiafeng.badger.sync.SyncRepository

/** Setup guide state and orchestration. */
class SetupGuideViewModel(
    val userProfileRepository: UserProfileRepository,
    private val syncRepository: SyncRepository,
    private val context: Context,
    private val serverUrlHolder: ServerUrlHolder,
    private val serverApiFactory: ServerApiFactory,
    private val httpClient: OkHttpClient,
    private val networkResolver: ContactNetworkResolver,
) : ViewModel() {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    val currentServerUrl: StateFlow<String> = serverUrlHolder.url
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
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Request.Builder().url(url).head().build().let { request ->
                        httpClient.newCall(request).execute().use { it.code }
                    }
                }
            }
            _testState.value = result.fold(
                onSuccess = { TestState.Success(it) },
                onFailure = { TestState.Failed(it.message ?: "无法连接到服务器") },
            )
        }
    }

    fun resetTestState() {
        _testState.value = TestState.Idle
    }

    fun updateServerUrl(newUrl: String, defaultUrl: String) {
        val normalized = newUrl.trim().trimEnd('/')
        if (normalized.isBlank()) return
        serverUrlHolder.set(normalized)
        serverApiFactory.updateBaseUrl(normalized)
        setServerUrlConfigured(context, normalized != defaultUrl)
    }

    fun resetServerUrlToDefault(defaultUrl: String) {
        serverUrlHolder.set(defaultUrl)
        serverApiFactory.updateBaseUrl(defaultUrl)
        setServerUrlConfigured(context, false)
    }

    fun runSync(reason: String = "sync", block: suspend () -> Unit) {
        if (_isSyncing.value) {
            Log.w(TAG, "[SYNC] ignored re-entry reason=$reason")
            return
        }
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "[SYNC] failed reason=$reason", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun bootstrapPostLogin() {
        viewModelScope.launch {
            runCatching {
                val resp = withContext(Dispatchers.IO) { serverApiFactory.get().getProfile() }
                mergeProfile(resp)
            }.onFailure { Log.w(TAG, "[POSTLOGIN] profile fetch failed", it) }
            runCatching { syncRepository.pullOnceIfIdle() }
                .onFailure { Log.w(TAG, "[POSTLOGIN] sync failed", it) }
        }
    }

    suspend fun getUserProfileOnce(): UserProfileCacheEntity? = userProfileRepository.getUserProfileOnce()
    suspend fun saveUserProfile(profile: UserProfileCacheEntity) = userProfileRepository.saveUserProfile(profile)
    suspend fun updatePlatformField(
        fieldKey: String,
        jumpLink: String,
        value: String?,
        displayName: String?,
        avatarUrl: String?,
        originalLink: String?,
    ) = userProfileRepository.updatePlatformField(
        fieldKey,
        jumpLink,
        value,
        displayName,
        avatarUrl,
        originalLink,
    )

    suspend fun removePlatform(fieldKey: String) = userProfileRepository.removePlatform(fieldKey)
    fun identifyPlatform(input: String): IdentifyResponse? = networkResolver.identify(input)

    private suspend fun mergeProfile(resp: UserProfileResponse) {
        val now = System.currentTimeMillis()
        val existing = userProfileRepository.getUserProfileOnce()
        val merged = (existing ?: UserProfileCacheEntity(name = "", updateTime = now)).copy(
            name = resp.name ?: resp.displayName ?: existing?.name ?: "",
            bio = resp.profile?.description ?: existing?.bio,
            avatarPath = resp.profile?.avatarURL ?: existing?.avatarPath,
            sex = resp.profile?.sex ?: existing?.sex,
            country = resp.profile?.country ?: existing?.country,
            region = resp.profile?.region ?: existing?.region,
            birthday = resp.profile?.birthday ?: existing?.birthday,
            backgroundURL = resp.profile?.backgroundURL ?: existing?.backgroundURL,
            extra = resp.profile?.extra?.toString()?.takeIf(String::isNotBlank) ?: existing?.extra,
            updateTime = now,
        )
        if (existing != null && existing == merged) return
        userProfileRepository.saveUserProfile(merged)
    }

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
