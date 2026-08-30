package top.mcxiafeng.badger.ocr

import android.content.Intent

import top.mcxiafeng.badger.R
import top.mcxiafeng.badger.network.ContactType
import androidx.core.net.toUri

/**
 * 链接来源类型
 */
enum class LinkSource {
    /** 填账号→自动生成HTTPS链接；填链接→提取账号+直接用链接 */
    AUTO,
    /** 只接受链接粘贴（抖音/小红书/网站，ID非人类可输入） */
    LINK_ONLY,
    /** 无链接可生成（微信，只存ID） */
    NO_LINK,
}

/**
 * 平台字段注册表
 *
 * 集中管理所有联系方式平台的元数据。新增平台只需在 PLATFORM_FIELDS 列表中添加一条记录，
 * 全项目所有 UI 展示、字段映射、图标加载、链接构造、别名识别自动覆盖，无需逐文件修改。
 *
 * @property fieldKey    字段唯一标识，对应数据库 contact_fields_cache.fieldKey
 * @property displayName 显示名称（中文）
 * @property contactType 网络解析类型
 * @property iconRes     图标 drawable 资源 ID
 * @property linkTemplate 从 ID 构造平台链接的模板（%s 为占位符），null 表示直接用原值
 * @property deepLinkTemplate 从 ID 构造 APP 私有跳转链接的模板（%s 为占位符），null 表示无私有跳转
 * @property packageName APP 包名，用于 setPackage Intent
 * @property qrcodeToScan 微信等平台：保存二维码到相册 + 打开扫一扫
 * @property aliases     otherInfo 中识别该平台的别名列表
 * @property inputHint 输入框提示文字
 * @property linkSource 链接来源类型
 */
data class PlatformFieldDef(
    val fieldKey: String,
    val displayName: String,
    val contactType: ContactType,
    val iconRes: Int,
    val linkTemplate: String? = null,
    val deepLinkTemplate: String? = null,
    val packageName: String? = null,
    val qrcodeToScan: Boolean = false,
    val aliases: List<String> = emptyList(),
    val inputHint: String = "",
    val linkSource: LinkSource = LinkSource.AUTO,
)

/**
 * 系统预置字段（非社交平台，无网络解析）
 */
val SYSTEM_FIELDS = listOf(
    PlatformFieldDef("phone", "电话", ContactType.None, R.drawable.ic_phone),
    PlatformFieldDef("email", "邮箱", ContactType.None, R.drawable.ic_email),
    // ========== 联系人「基础信息」字段（PR2 详情页 2x2 网格用） ==========
    // iconRes 占位为 ic_phone 仅为通过 seedDefaults 的非空检查；UI 渲染用 Material Icons
    // 按 fieldKey 直接选图标，详见 ContactFieldComponents.BasicInfoCard。
    PlatformFieldDef("gender", "性别", ContactType.None, R.drawable.ic_phone),
    PlatformFieldDef("birthday", "生日", ContactType.None, R.drawable.ic_phone),
    PlatformFieldDef("country", "国家", ContactType.None, R.drawable.ic_phone),
    PlatformFieldDef("region", "地区", ContactType.None, R.drawable.ic_phone),
)

/**
 * 社交平台字段（有适配器、可网络解析）
 *
 * 按中国用户使用频率排序。
 * 新增平台只需在此列表中添加一条记录。
 */
val PLATFORM_FIELDS = listOf(
    PlatformFieldDef("wechat", "微信", ContactType.WeChat, R.drawable.ic_wechat,
        qrcodeToScan = true,
        aliases = listOf("微信", "wechat"),
        inputHint = "微信号",
        linkSource = LinkSource.NO_LINK),
    PlatformFieldDef("qq", "QQ", ContactType.QQ, R.drawable.ic_qq,
        linkTemplate = "https://tool.gljlw.com/qq/?qq=%s",
        deepLinkTemplate = "mqq://card/show_pslcard?src_type=internal&version=1&uin=%s&card_type=person&source=sharecard",
        packageName = "com.tencent.mobileqq",
        aliases = listOf("qq"),
        inputHint = "QQ号或加好友链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("bilibili", "B站", ContactType.Bilibili, R.drawable.ic_bilibili,
        linkTemplate = "https://space.bilibili.com/%s",
        deepLinkTemplate = "bilibili://space/%s",
        packageName = "tv.danmaku.bili",
        aliases = listOf("bilibili", "b站"),
        inputHint = "B站UID或主页链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("xiaohongshu", "小红书", ContactType.Xiaohongshu, R.drawable.ic_xiaohongshu,
        linkTemplate = "https://www.xiaohongshu.com/user/profile/%s",
        packageName = "com.xingin.xhs",
        aliases = listOf("小红书", "xiaohongshu"),
        inputHint = "小红书主页链接",
        linkSource = LinkSource.LINK_ONLY),
    PlatformFieldDef("douyin", "抖音", ContactType.TikTok, R.drawable.ic_douyin,
        linkTemplate = "https://www.douyin.com/user/%s",
        deepLinkTemplate = "snssdk1128://user/profile/%s",
        packageName = "com.ss.android.ugc.aweme",
        aliases = listOf("抖音", "douyin"),
        inputHint = "抖音主页链接",
        linkSource = LinkSource.LINK_ONLY),
    PlatformFieldDef("weibo", "微博", ContactType.Weibo, R.drawable.ic_weibo,
        linkTemplate = "https://weibo.com/u/%s",
        deepLinkTemplate = "sinaweibo://userinfo?uid=%s",
        packageName = "com.sina.weibo",
        aliases = listOf("微博", "weibo"),
        inputHint = "微博UID或主页链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("github", "GitHub", ContactType.GitHub, R.drawable.ic_github,
        linkTemplate = "https://github.com/%s",
        packageName = "com.github.android",
        aliases = listOf("github"),
        inputHint = "GitHub用户名或主页链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("telegram", "Telegram", ContactType.Telegram, R.drawable.ic_telegram,
        linkTemplate = "https://t.me/%s",
        deepLinkTemplate = "tg://resolve?domain=%s",
        packageName = "org.telegram.messenger",
        aliases = listOf("telegram", "tg"),
        inputHint = "Telegram用户名或主页链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("facebook", "Facebook", ContactType.Facebook, R.drawable.ic_facebook,
        linkTemplate = "https://www.facebook.com/%s",
        deepLinkTemplate = "fb://profile/%s",
        packageName = "com.facebook.katana",
        aliases = listOf("facebook", "fb"),
        inputHint = "Facebook用户名或主页链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("x", "X", ContactType.X, R.drawable.ic_x,
        linkTemplate = "https://x.com/%s",
        deepLinkTemplate = "twitter://user?screen_name=%s",
        packageName = "com.twitter.android",
        aliases = listOf("x", "twitter"),
        inputHint = "X用户名或主页链接",
        linkSource = LinkSource.AUTO),
    PlatformFieldDef("website", "网站", ContactType.Website, R.drawable.ic_website,
        aliases = listOf("网站", "website"),
        inputHint = "网站链接",
        linkSource = LinkSource.LINK_ONLY),
    PlatformFieldDef("telegramGroup", "Telegram群", ContactType.TelegramGroup, R.drawable.ic_telegram,
        packageName = "org.telegram.messenger",
        aliases = listOf("telegram群", "tg群"),
        inputHint = ""),
    PlatformFieldDef("qqGroup", "QQ群", ContactType.QQGroup, R.drawable.ic_qq,
        packageName = "com.tencent.mobileqq",
        aliases = listOf("qq群"),
        inputHint = ""),
)

/** 所有可保存字段（系统 + 平台） */
val ALL_FIELDS = SYSTEM_FIELDS + PLATFORM_FIELDS

/** fieldKey → PlatformFieldDef 查找表 */
val FIELD_DEF_MAP = ALL_FIELDS.associateBy { it.fieldKey }

// [Phase 4 剩余] 原 `ADDABLE_PLATFORMS`（按 showInAddDialog 过滤）已退役 —— 「添加平台」网格
// 改由服务端 `/api/resolve/platforms` 清单驱动（见 network/PlatformManifestRepository.kt），
// 本地 PLATFORM_FIELDS 降级为服务端清单的 UI 标签映射（图标 / linkTemplate / inputHint 等）。

/** 系统字段 fieldKey 集合（联系方式字段 / 基础信息，非社交平台） */
val SYSTEM_FIELD_KEYS: Set<String> = SYSTEM_FIELDS.map { it.fieldKey }.toSet()

/** 社交平台 fieldKey 集合（用于区分平台字段和联系方式字段） */
val PLATFORM_FIELD_KEYS: Set<String> = PLATFORM_FIELDS.map { it.fieldKey }.toSet()

/** 别名 → fieldKey 查找表（用于 otherInfo 中识别平台） */
val ALIAS_TO_KEY_MAP: Map<String, String> = buildMap {
    for (def in ALL_FIELDS) {
        for (alias in def.aliases) {
            put(alias.lowercase(), def.fieldKey)
        }
    }
}

/** 已知短链域名 → fieldKey 映射 */
val SHORT_LINK_DOMAINS: Map<String, String> = mapOf(
    "v.douyin.com" to "douyin",
    "www.iesdouyin.com" to "douyin",
    "xhslink.com" to "xiaohongshu",
    "b23.tv" to "bilibili",
    "t.cn" to "weibo",
    "qm.qq.com" to "qq",
    "tool.gljlw.com" to "qq",
    "u.wechat.com" to "wechat",
)

/**
 * 判断输入是否为 URL（http/https/weixin 等 scheme）。
 *
 * 统一散落在各处的 `startsWith("http")` 判断，避免逻辑不一致。
 */
fun isUrlInput(input: String): Boolean =
    input.startsWith("http://") || input.startsWith("https://")

/**
 * 根据 fieldKey 构造平台链接
 *
 * @param fieldKey 字段 key
 * @param value 字段值（如 QQ 号、B站 UID 等）
 * @return 构造的链接，无 linkTemplate 则返回原值
 */
fun buildPlatformLink(fieldKey: String, value: String): String {
    val def = FIELD_DEF_MAP[fieldKey] ?: return value
    // LINK_ONLY 平台的非 http 输入不触发链接生成
    if (def.linkSource == LinkSource.LINK_ONLY && !value.startsWith("http")) return value
    // NO_LINK 平台不触发链接生成
    if (def.linkSource == LinkSource.NO_LINK) return value
    val clean = value.removePrefix("@")
    return def.linkTemplate?.replace("%s", clean) ?: value
}

/**
 * 跳转动作类型
 */
sealed class LaunchAction {
    /** 按优先级尝试多个 Intent */
    data class Intents(val intents: List<Intent>) : LaunchAction()
    /** 微信：保存二维码到相册 + 打开扫一扫 */
    data class WechatQrScan(val qrContent: String) : LaunchAction()
    /** 复制到剪贴板 + 打开对应 APP（电话/邮箱等） */
    data class CopyAndOpen(val copyText: String, val intent: Intent) : LaunchAction()
    /** 无法跳转 */
    data object None : LaunchAction()
}

/**
 * 构建跳转动作
 *
 * - 有 deepLink/web 链接 → LaunchAction.Intents（三级回退）
 * - 微信 → LaunchAction.WechatQrScan（保存二维码+扫一扫）
 * - 电话 → LaunchAction.CopyAndOpen（复制+拨号）
 * - 邮箱 → LaunchAction.CopyAndOpen（复制+邮件）
 */
fun buildLaunchAction(fieldKey: String, value: String, jumpLink: String = ""): LaunchAction {
    val def = FIELD_DEF_MAP[fieldKey] ?: return LaunchAction.None

    // 微信特殊处理
    if (def.qrcodeToScan) {
        val content = jumpLink.ifBlank { value }
        return if (content.startsWith("http://") || content.startsWith("https://") || content.startsWith("weixin://")) {
            LaunchAction.WechatQrScan(qrContent = content)
        } else {
            LaunchAction.CopyAndOpen(
                copyText = content,
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage("com.tencent.mm")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    // 电话
    if (fieldKey == "phone") {
        val phone = value.trim()
        return LaunchAction.CopyAndOpen(
            copyText = phone,
            intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // 邮箱
    if (fieldKey == "email") {
        val email = value.trim()
        return LaunchAction.CopyAndOpen(
            copyText = email,
            intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // 通用：deepLink → setPackage+web → 裸 web
    val clean = value.removePrefix("@")
    val intents = mutableListOf<Intent>()

    // 1. deepLink
    def.deepLinkTemplate?.replace("%s", clean)?.let { uri ->
        intents.add(Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // 2. setPackage + web 链接
    val webUri = when {
        jumpLink.startsWith("http://") || jumpLink.startsWith("https://") ->
            jumpLink.replace(Regex("/@([^/]+)"), "/$1")
        clean.startsWith("http://") || clean.startsWith("https://") ->
            clean.replace(Regex("/@([^/]+)"), "/$1")
        clean.isNotBlank() -> {
            val link = def.linkTemplate?.replace("%s", clean) ?: clean
            if (link.startsWith("http")) link.replace(Regex("/@([^/]+)"), "/$1") else null
        }
        else -> null
    }
    if (webUri != null && def.packageName != null) {
        intents.add(Intent(Intent.ACTION_VIEW, webUri.toUri()).apply {
            setPackage(def.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // 3. 裸 web 链接
    if (webUri != null) {
        intents.add(Intent(Intent.ACTION_VIEW, webUri.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    return if (intents.isNotEmpty()) LaunchAction.Intents(intents) else LaunchAction.None
}
