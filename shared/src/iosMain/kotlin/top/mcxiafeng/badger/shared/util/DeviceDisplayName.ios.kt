package top.mcxiafeng.badger.shared.util

import platform.UIKit.UIDevice

actual fun deviceDisplayName(): String = UIDevice.currentDevice.name
