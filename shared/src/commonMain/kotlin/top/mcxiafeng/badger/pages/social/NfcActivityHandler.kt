package top.mcxiafeng.badger.pages.social

/**
 * NFC 操作回调接口，封装需要 Activity 的 ReaderMode 操作，
 * 使 ViewModel 不直接持有 Activity 引用。
 */
interface NfcActivityHandler {
    fun startWriting(uri: String)
    fun stopWriting()
}
