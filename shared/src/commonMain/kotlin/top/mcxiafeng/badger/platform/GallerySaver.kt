package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 系统相册保存边界（替代 Methods.saveBitmapToGallery 的平台半边）。
 *
 * Android actual = MediaStore.Images + Pictures/Badger（PNG，语义与原实现一致，
 * 失败时清理半成品 MediaStore 条目）；
 * iOS actual = 骨架（PhotoKit 加号授权 K16 接线，当前返回 false 并记日志）。
 */
expect object GallerySaver {
    /** 保存 PNG 字节到系统相册；返回是否成功。 */
    fun saveImagePng(bytes: ByteArray, displayName: String): Boolean
}
