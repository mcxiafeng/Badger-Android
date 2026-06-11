package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import javax.inject.Inject

@HiltViewModel
class UserProfileDetailViewModel @Inject constructor(
    val userProfileRepository: UserProfileRepository
) : ViewModel() {
    init {
        Log.d("Tester", "UserProfileDetailViewModel initialized")
    }
}
