package top.mcxiafeng.badger.pages.setupguide

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class SetupGuideViewModel @Inject constructor(
    val userProfileRepository: UserProfileRepository
) : ViewModel() {
    init {
        Log.d("Tester", "SetupGuideViewModel initialized")
    }
}
