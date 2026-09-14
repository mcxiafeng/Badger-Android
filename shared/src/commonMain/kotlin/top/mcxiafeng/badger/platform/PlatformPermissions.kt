package top.mcxiafeng.badger.platform

expect object PlatformPermissions {

    
    fun isCameraGranted(): Boolean

    
    suspend fun requestCamera(): Boolean

    

    fun openAppSettings()
}
