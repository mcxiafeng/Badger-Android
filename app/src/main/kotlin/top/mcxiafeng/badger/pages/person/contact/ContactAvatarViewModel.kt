package top.mcxiafeng.badger.pages.person.contact

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.utils.Methods
import javax.inject.Inject

/** 头像页 ViewModel 向 UI 层发出的一次性事件 */
sealed class ContactAvatarEvent {
    data class ShowToast(val message: String) : ContactAvatarEvent()
    /** 头像已写入（更新/清空），通知外部刷新列表与"我的名片" */
    data object AvatarChanged : ContactAvatarEvent()
}

/**
 * 联系人头像页 ViewModel。
 *
 * 只承担头像的读写：加载联系人（姓名 + 头像）、保存裁剪后的本地头像、
 * 清空头像（删除本地头像文件并移除 avatarPath/avatarUrl）。
 */
@HiltViewModel
class ContactAvatarViewModel @Inject constructor(
    private val repository: ContactRepository,
) : ViewModel() {

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _events = Channel<ContactAvatarEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        Log.d("ContactAvatarPageTester", "ContactAvatarViewModel initialized")
    }

    fun loadContact(contactId: Long) {
        viewModelScope.launch {
            try {
                _contact.value = repository.getContactById(contactId)
                Log.d("ContactAvatarPageTester", "loadContact: id=$contactId name=${_contact.value?.name}")
            } catch (e: Exception) {
                Log.e("ContactAvatarPageTester", "loadContact failed, id=$contactId", e)
            }
        }
    }

    /** 更新联系人头像路径（文件已由 UI 层保存） */
    fun applyAvatarUpdate(contactId: Long, avatarPath: String) {
        viewModelScope.launch {
            try {
                // 写前重读：不从 UI 快照直接 copy，避免覆盖并发修改
                val currentContact = repository.getContactById(contactId) ?: return@launch
                val updated = currentContact.copy(
                    avatarPath = avatarPath,
                    updateTime = System.currentTimeMillis()
                )
                repository.updateContact(updated)
                _contact.value = updated
                _events.send(ContactAvatarEvent.ShowToast("头像已更新"))
                _events.send(ContactAvatarEvent.AvatarChanged)
                Log.d("ContactAvatarPageTester", "Avatar updated: contactId=$contactId path=$avatarPath")
            } catch (e: Exception) {
                Log.e("ContactAvatarPageTester", "设置头像失败", e)
                _events.send(ContactAvatarEvent.ShowToast("设置头像失败"))
            }
        }
    }

    /** 清空头像：删除本地头像文件并移除 avatarPath/avatarUrl（不含平台内的头像 URL） */
    fun clearAvatar(contactId: Long) {
        viewModelScope.launch {
            try {
                val currentContact = repository.getContactById(contactId) ?: return@launch
                Methods.deleteAvatarFile(currentContact.avatarPath)
                val updated = currentContact.copy(
                    avatarPath = null,
                    avatarUrl = null,
                    updateTime = System.currentTimeMillis()
                )
                repository.updateContact(updated)
                _contact.value = updated
                _events.send(ContactAvatarEvent.ShowToast("头像已清空"))
                _events.send(ContactAvatarEvent.AvatarChanged)
                Log.d("ContactAvatarPageTester", "Avatar cleared: contactId=$contactId")
            } catch (e: Exception) {
                Log.e("ContactAvatarPageTester", "清空头像失败", e)
                _events.send(ContactAvatarEvent.ShowToast("清空头像失败"))
            }
        }
    }
}
