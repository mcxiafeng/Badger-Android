package top.mcxiafeng.badger.pages.social

interface NfcActivityHandler {
    fun startWriting(uri: String)
    fun stopWriting()
}
