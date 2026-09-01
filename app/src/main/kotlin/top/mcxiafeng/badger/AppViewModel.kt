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
    private val userProfileRepository: UserProfileRepository,
    private val userProfileTicker: UserProfileTicker,
    private val userAuthRepository: UserAuthRepository,
    private val notificationRepository: NotificationRepository,
    private val contactRepository: ContactRepository,
    private val importProfileFieldsUseCase: ImportProfileFieldsUseCase,
) : ViewModel() {

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
