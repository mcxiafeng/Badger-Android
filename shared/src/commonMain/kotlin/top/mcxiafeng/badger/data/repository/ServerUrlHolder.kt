package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.mcxiafeng.badger.data.prefs.AuthPrefs

private const val TAG = "ServerUrlHolder"

class ServerUrlHolder() {
    private val _url = MutableStateFlow(AuthPrefs.readServerUrl())
    val url: StateFlow<String> = _url.asStateFlow()

    

    private val _isUrlVerified = MutableStateFlow(false)
    val isUrlVerified: StateFlow<Boolean> = _isUrlVerified.asStateFlow()

    

    fun set(newUrl: String) {
        AuthPrefs.writeServerUrl(newUrl)
        _url.value = newUrl
        if (_isUrlVerified.value) {
            BadgerLog.d(TAG, "set: URL changed → isUrlVerified reset false")
            _isUrlVerified.value = false
        }
    }

    

    fun markUrlVerified() {
        if (!_isUrlVerified.value) {
            _isUrlVerified.value = true
            BadgerLog.d(TAG, "markUrlVerified: server verified gate cleared")
        }
    }
}
