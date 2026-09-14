package top.mcxiafeng.badger.platform

expect object LogCollector {

    
    fun collectRecentLogs(): String

    
    fun cacheDirPath(): String

    
    fun deviceAbiLine(): String
}
