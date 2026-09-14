package top.mcxiafeng.badger.shared.prefs

expect object PrefsPathFactory {
    
    fun storeAppDir(dir: String)

    
    fun prefsDir(): String
}
