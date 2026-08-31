package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.UserProfileRepository

/**
 * [A5] 我的名片编辑：sex / birthday / country / region / backgroundURL 字段级更新。
 * [A6] 从平台解析导入：仅覆盖解析到的非空 name/bio/avatarPath。
 *
 * 复用 `saveUserProfile`（全量保存 + diff 防抖 + 直推），UI 层先用最新 DB 快照
 * copy 出目标字段的新值再落库，避免用陈旧 UI 快照覆盖并发修改（与 EditNameDialog 同模式）。
 */
class UserProfileDetailViewModel(
    val userProfileRepository: UserProfileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** [A5] 字段级写入：fieldKey ∈ sex/birthday/country/region/backgroundURL */
    fun updateProfileField(
        fieldKey: String,
        newValue: String?,
        onDone: (UserProfileCacheEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val current = withContext(ioDispatcher) {
                    userProfileRepository.getUserProfileOnce()
                        ?: UserProfileCacheEntity(name = "用户", updateTime = System.currentTimeMillis())
                }
                val updated = when (fieldKey) {
                    "sex" -> current.copy(sex = newValue?.ifBlank { null }, updateTime = System.currentTimeMillis())
                    "birthday" -> current.copy(birthday = newValue?.ifBlank { null }, updateTime = System.currentTimeMillis())
                    "country" -> current.copy(country = newValue?.ifBlank { null }, updateTime = System.currentTimeMillis())
                    "region" -> current.copy(region = newValue?.ifBlank { null }, updateTime = System.currentTimeMillis())
                    "backgroundURL" -> current.copy(backgroundURL = newValue?.ifBlank { null }, updateTime = System.currentTimeMillis())
                    else -> {
                        Log.w(TAG, "updateProfileField: 未知字段 $fieldKey")
                        return@launch
                    }
                }
                withContext(ioDispatcher) {
                    userProfileRepository.saveUserProfile(updated)
                }
                val fresh = withContext(ioDispatcher) { userProfileRepository.getUserProfileOnce() } ?: updated
                onDone(fresh)
            } catch (e: Exception) {
                Log.e(TAG, "updateProfileField($fieldKey) 失败", e)
            }
        }
    }

    /**
     * [A6] 将平台解析结果合并进当前名片并落库。
     *
     * 仅覆盖解析到的非空字段，不抹掉用户已填的其他字段。
     */
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
                        ?: UserProfileCacheEntity(name = "用户", updateTime = System.currentTimeMillis())
                }
                val updated = mergeImportedProfile(current, importedName, importedBio, importedAvatarPath)
                withContext(ioDispatcher) {
                    userProfileRepository.saveUserProfile(updated)
                }
                val fresh = withContext(ioDispatcher) { userProfileRepository.getUserProfileOnce() } ?: updated
                onDone(fresh)
            } catch (e: Exception) {
                Log.e(TAG, "importFromPlatform 失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "UserProfileDetailViewModel"

        /**
         * [A6] 纯函数：解析结果 → 名片字段合并。
         *
         * - name/bio 仅在非空且非 "未知" 时覆盖
         * - avatarPath 仅在非空时覆盖（下载失败时调用方传 null，保留原头像）
         */
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
                updateTime = System.currentTimeMillis(),
            )
        }
    }
}
