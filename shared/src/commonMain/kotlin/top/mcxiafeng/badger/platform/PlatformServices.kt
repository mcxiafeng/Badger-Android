package top.mcxiafeng.badger.platform

expect object PlatformClipboard {
    
    fun copy(text: String): Boolean
}

expect object SystemShare {
    
    fun shareText(title: String, text: String): Boolean

    

    fun shareFile(filePath: String, mimeType: String, title: String): Boolean
}

expect object UrlOpener {
    
    fun openUrl(url: String): Boolean
}
