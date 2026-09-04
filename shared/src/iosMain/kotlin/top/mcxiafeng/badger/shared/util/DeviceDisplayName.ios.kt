package top.mcxiafeng.badger.shared.util

import platform.UIKit.UIDevice

/**
 * [KMP K08-B] iOS actual：UIDevice 型号标识。
 */
actual fun deviceDisplayName(): String = UIDevice.currentDevice.name
