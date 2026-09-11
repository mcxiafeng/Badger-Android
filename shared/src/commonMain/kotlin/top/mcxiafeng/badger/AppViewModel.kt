package top.mcxiafeng.badger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity as UserProfile
import top.mcxiafeng.badger.data.repository.AuthState
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.domain.ImportProfileFieldsUseCase
import top.mcxiafeng.badger.ocr.ExtractedContactInfo
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * App-level state and operations shared by the application composition root.
 *
 * UI observes state and calls intent-like methods; repository and network access stay here
 * or below the ViewModel/use-case boundary.
 */
class AppViewModel(
    userProfileRepository: UserProfileRepository,
    userProfileTicker: UserProfileTicker,
    userAuthRepository: UserAuthRepository,
    notificationRepository: NotificationRepository,
    contactRepository: ContactRepository,
    importProfileFieldsUseCase: ImportProfileFieldsUseCase,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    // [兼容]:dev 组合根(App.kt)仍直接读取这三个属性;待 UI 迁移到
    // authState/findContactIdByServerId 等 API 后再收紧为 private。
    val userProfileRepository: UserProfileRepository = userProfileRepository
    val userAuthRepository: UserAuthRepository = userAuthRepository
    val contactRepository: ContactRepository = contactRepository

    private val userProfileTicker: UserProfileTicker = userProfileTicker
    private val notificationRepository: NotificationRepository = notificationRepository
    private val importProfileFieldsUseCase: ImportProfileFieldsUseCase = importProfileFieldsUseCase

    val unreadNotificationCount: StateFlow<Int> = notificationRepository.unreadCount
    val authState: StateFlow<AuthState> = userAuthRepository.state
    val userProfileTick: StateFlow<Long> = userProfileTicker.tick

    init {
        viewModelScope.launch { userAuthRepository.bootstrap() }
        // 登录态进入 SignedIn（登录 / 注册自动登录 / 冷启恢复会话）后触发一轮 bootstrap 同步。
        // Android 启动链只有 Outbox push（OutboxWorker），没有任何 pull 路径；
        // 不补这个，登录后本地列表永远是空的，直到手动同步。
        viewModelScope.launch {
            userAuthRepository.state
                .drop(1)
                .filter { it is AuthState.SignedIn }
                .collect {
                    BadgerLog.d(TAG, "authState → SignedIn: bootstrap sync")
                    try {
                        // 用 syncOnce（非 IfIdle）：includeBackoff=true 会把退避中卡住的 op 一起重试。
                        // 旧用 syncOnceIfIdle 走 includeBackoff=false，且启动期 BadgerApplication 的预同步
                        // 占着 started 标志会让它 skip——卡在退避的 PATCH（如基础信息编辑遇 401）永远不被重试。
                        val result = syncEngine.syncOnce()
                        BadgerLog.d(TAG, "bootstrap sync done: $result")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        BadgerLog.w(TAG, "bootstrap sync failed (下次 SignedIn 转换或手动同步重试)", e)
                    }
                }
        }
    }

    fun refreshUserProfile() {
        userProfileTicker.tick()
    }

    /** Resolve a server-side contact id without exposing the repository to Compose. */
    suspend fun findContactIdByServerId(serverId: String): Long? =
        contactRepository.getContactByServerId(serverId)?.id

    /** Import scanner-discovered platform fields into the current user profile. */
    suspend fun importProfileFields(items: List<ExtractedContactInfo>): Int =
        importProfileFieldsUseCase(items)

    suspend fun reloadUserProfileNow(): UserProfile? =
        userProfileRepository.getUserProfileOnce()

    private companion object {
        const val TAG = "AppViewModel"
    }
}
