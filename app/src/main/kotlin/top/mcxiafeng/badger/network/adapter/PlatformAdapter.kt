package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType

/**
 * 平台适配器接口
 *
 * 每个社交平台（QQ、B站、微信、抖音等）实现此接口，
 * 统一对外输出：名字、头像 URL、签名/简介。
 *
 * 无法获取的字段返回 null，由调用方决定是否使用默认值。
 */
interface PlatformAdapter {

    /** 适配器对应的平台类型 */
    val platformType: ContactType

    /** 平台显示标签（如 "QQ"、"B站"） */
    val label: String

    /** 平台标签颜色（ARGB） */
    val tagColor: Long

    /**
     * 该适配器是否能够同步有效信息（名字、头像）
     *
     * 对于没有公开 API 的平台（如微信、小红书），resolve() 返回的结果
     * name=null、avatarUrl=null，同步操作对用户无意义，应隐藏同步按钮。
     * 默认为 true，不可同步的平台需 override 为 false。
     */
    val canSync: Boolean
        get() = true

    /**
     * 从原始链接/内容中解析出平台信息
     *
     * @param content 扫描到的原始内容（URL 或协议链接）
     * @return 解析结果，失败返回 null
     */
    suspend fun resolve(content: String): PlatformResolveResult?
}

/**
 * 平台解析结果
 *
 * @property name 名字/昵称（null 表示无法获取）
 * @property avatarUrl 头像 URL（null 表示无法获取）
 * @property signature 签名/简介（null 表示无法获取）
 * @property contactMap 结构化字段（如 qq=12345, bilibili=uid456）
 */
data class PlatformResolveResult(
    val name: String?,
    val avatarUrl: String?,
    val signature: String?,
    val contactMap: Map<String, String>
)
