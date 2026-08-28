package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.repository.UserProfileRepository

/**
 * [A5] 我的名片编辑：sex / birthday / country / region / backgroundURL 字段级更新。
 *
 * 复用 `saveUserProfile`（全量保存 + diff 防抖 + 直推），UI 层先用最新 DB 快照
 * copy 出目标字段的新值再落库，避免用陈旧 UI 快照覆盖并发修改（与 EditNameDialog 同模式）。
 */
class UserProfileDetailViewModel : ViewModel() {
    val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()

    init {
    }

    /** [A5] 字段级写入：fieldKey ∈ sex/birthday/country/region/backgroundURL */
    fun updateProfileField(
        fieldKey: String,
        newValue: String?,
        onDone: (UserProfileCacheEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val current = withContext(Dispatchers.IO) {
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
                withContext(Dispatchers.IO) {
                    userProfileRepository.saveUserProfile(updated)
                }
                val fresh = withContext(Dispatchers.IO) { userProfileRepository.getUserProfileOnce() } ?: updated
                onDone(fresh)
            } catch (e: Exception) {
                Log.e(TAG, "updateProfileField($fieldKey) 失败", e)
            }
        }
    }

    private companion object {
        const val TAG = "UserProfileDetailViewModel"
    }
}
