package top.mcxiafeng.badger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
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
}
