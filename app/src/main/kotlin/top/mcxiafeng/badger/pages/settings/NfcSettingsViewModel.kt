package top.mcxiafeng.badger.pages.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import top.mcxiafeng.badger.data.repository.UserProfileRepository

/** [§14.2] Koin `inject()` 字段注入,移除 `@HiltViewModel`。 */
class NfcSettingsViewModel : ViewModel() {
    val userProfileRepository: UserProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get()
    init {
        Log.d("Tester", "NfcSettingsViewModel initialized")
    }
}
