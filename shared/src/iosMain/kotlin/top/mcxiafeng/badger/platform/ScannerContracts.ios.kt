package top.mcxiafeng.badger.platform

actual suspend fun loadOrientedImage(bytes: ByteArray): PlatformImage? =
    ImageCodec.decode(bytes)

actual fun notifyScannerDialogDismissed() {
}

actual object QrEngineBootstrap {
    actual suspend fun ensureReady() {
        
    }
}
