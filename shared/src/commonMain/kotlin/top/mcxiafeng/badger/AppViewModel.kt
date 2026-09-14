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

class AppViewModel(
    userProfileRepository: UserProfileRepository,
    userProfileTicker: UserProfileTicker,
    userAuthRepository: UserAuthRepository,
    notificationRepository: NotificationRepository,
    contactRepository: ContactRepository,
    importProfileFieldsUseCase: ImportProfileFieldsUseCase,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    
    
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
        
        
        
        viewModelScope.launch {
            userAuthRepository.state
                .drop(1)
                .filter { it is AuthState.SignedIn }
                .collect {
                    BadgerLog.d(TAG, "authState → SignedIn: bootstrap sync")
                    try {
                        
                        
                        
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

    
    suspend fun findContactIdByServerId(serverId: String): Long? =
        contactRepository.getContactByServerId(serverId)?.id

    
    suspend fun importProfileFields(items: List<ExtractedContactInfo>): Int =
        importProfileFieldsUseCase(items)

    private companion object {
        const val TAG = "AppViewModel"
    }
}
