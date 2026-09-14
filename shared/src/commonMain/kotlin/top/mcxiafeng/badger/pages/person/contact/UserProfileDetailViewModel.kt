package top.mcxiafeng.badger.pages.person.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.shared.util.nowMs

class UserProfileDetailViewModel(
    private val ioDispatcher: CoroutineDispatcher = BadgerDispatchers.io,
    val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get(),
) : ViewModel() {

    
    fun updateProfileField(
        fieldKey: String,
        newValue: String?,
        onDone: (UserProfileCacheEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val current = withContext(ioDispatcher) {
                    userProfileRepository.getUserProfileOnce()
                        ?: UserProfileCacheEntity(name = "用户", updateTime = nowMs())
                }
                val updated = when (fieldKey) {
                    "sex" -> current.copy(sex = newValue?.ifBlank { null }, updateTime = nowMs())
                    "birthday" -> current.copy(birthday = newValue?.ifBlank { null }, updateTime = nowMs())
                    "country" -> current.copy(country = newValue?.ifBlank { null }, updateTime = nowMs())
                    "region" -> current.copy(region = newValue?.ifBlank { null }, updateTime = nowMs())
                    "backgroundURL" -> current.copy(backgroundURL = newValue?.ifBlank { null }, updateTime = nowMs())
                    else -> {
                        BadgerLog.w(TAG, "updateProfileField: 未知字段 $fieldKey")
                        return@launch
                    }
                }
                withContext(ioDispatcher) {
                    userProfileRepository.saveUserProfile(updated)
                }
                val fresh = withContext(ioDispatcher) { userProfileRepository.getUserProfileOnce() } ?: updated
                onDone(fresh)
            } catch (e: Exception) {
                BadgerLog.e(TAG, "updateProfileField($fieldKey) 失败", e)
            }
        }
    }

    

    fun importFromPlatform(
        importedName: String?,
        importedBio: String?,
        importedAvatarPath: String?,
        onDone: (UserProfileCacheEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val current = withContext(ioDispatcher) {
                    userProfileRepository.getUserProfileOnce()
                        ?: UserProfileCacheEntity(name = "用户", updateTime = nowMs())
                }
                val updated = mergeImportedProfile(current, importedName, importedBio, importedAvatarPath)
                withContext(ioDispatcher) {
                    userProfileRepository.saveUserProfile(updated)
                }
                val fresh = withContext(ioDispatcher) { userProfileRepository.getUserProfileOnce() } ?: updated
                onDone(fresh)
            } catch (e: Exception) {
                BadgerLog.e(TAG, "importFromPlatform 失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "UserProfileDetailViewModel"

        

        fun mergeImportedProfile(
            current: UserProfileCacheEntity,
            importedName: String?,
            importedBio: String?,
            importedAvatarPath: String?,
        ): UserProfileCacheEntity {
            val name = importedName?.takeIf { it.isNotBlank() && it != "未知" }
            val bio = importedBio?.takeIf { it.isNotBlank() }
            return current.copy(
                name = name ?: current.name,
                bio = bio ?: current.bio,
                avatarPath = importedAvatarPath ?: current.avatarPath,
                updateTime = nowMs(),
            )
        }
    }
}
