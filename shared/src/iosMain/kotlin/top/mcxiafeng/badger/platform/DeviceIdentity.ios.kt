package top.mcxiafeng.badger.platform

import platform.UIKit.UIDevice

/** deviceId = identifierForVendor（同厂商 App 域内稳定）；deviceName = 用户命名的本机名。 */
actual fun deviceIdentity(): DeviceIdentity {
    val device = UIDevice.currentDevice
    val vendorId = device.identifierForVendor?.UUIDString
        ?: error("identifierForVendor 不可用")
    return DeviceIdentity(deviceId = vendorId, deviceName = device.name)
}
