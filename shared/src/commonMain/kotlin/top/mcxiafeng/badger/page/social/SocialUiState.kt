package top.mcxiafeng.badger.page.social

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.system.entity.UserInfo
import top.mcxiafeng.badger.data.user.entity.Platform
import top.mcxiafeng.badger.data.user.entity.Profile
import top.mcxiafeng.badger.data.user.entity.User

@Immutable
sealed interface SocialUiState {
    data object Loading : SocialUiState

    data class Success(
        val userInfo: UserInfo,
        val user: User,
        val profile: Profile,
    ) : SocialUiState

    data class Error(val message: String) : SocialUiState
}
