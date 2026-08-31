package top.mcxiafeng.badger.pages.settings

import androidx.lifecycle.ViewModel
import top.mcxiafeng.badger.data.repository.UserProfileRepository

/** NFC settings state owner; dependencies are explicit for testability. */
class NfcSettingsViewModel(
    val userProfileRepository: UserProfileRepository,
) : ViewModel()