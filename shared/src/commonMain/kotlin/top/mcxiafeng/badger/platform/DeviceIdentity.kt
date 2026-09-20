package top.mcxiafeng.badger.platform

/**
 * 本机设备身份：deviceId/deviceName 由平台推算（本机不可变值），不落库、不持久化。
 * 登录请求的 deviceId/deviceName 唯一来源。
 */
data class DeviceIdentity(val deviceId: String, val deviceName: String)

expect fun deviceIdentity(): DeviceIdentity
