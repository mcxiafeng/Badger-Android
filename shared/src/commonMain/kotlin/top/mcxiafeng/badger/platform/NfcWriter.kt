package top.mcxiafeng.badger.platform

import kotlinx.coroutines.flow.StateFlow

data class NfcWriteResult(val success: Boolean, val message: String)

expect class NfcWriter {
    
    val isWriting: Boolean

    
    val writeResult: StateFlow<NfcWriteResult?>

    
    fun isSupported(): Boolean

    
    fun openNfcSettings(): Boolean

    
    fun startWriting(uri: String)

    
    fun stopWriting()
}
