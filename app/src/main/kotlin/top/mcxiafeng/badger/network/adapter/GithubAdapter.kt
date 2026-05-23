package top.mcxiafeng.badger.network.adapter

import top.mcxiafeng.badger.network.ContactType
import top.mcxiafeng.badger.utils.HttpUtil

/**
 * GitHub 用户适配器
 *
 * 通过 GitHub 公开 API（api.github.com/users/{username}）获取用户信息。
 * 返回用户名、头像和简介。
 */
class GithubAdapter : PlatformAdapter {

    override val platformType = ContactType.GitHub
    override val label = "GitHub"
    override val tagColor = 0xFF24292EL // GitHub 深灰黑

    override suspend fun resolve(content: String): PlatformResolveResult? {
        val username = extractUsername(content) ?: return null

        // 调用 GitHub 公开 API
        val json = HttpUtil.get(
            "https://api.github.com/users/$username",
            headers = mapOf("Accept" to "application/vnd.github.v3+json")
        ) ?: return simpleResult(username)

        return try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(json, Map::class.java) as Map<*, *>

            val name = root["name"]?.toString()?.ifBlank { null }
                ?: root["login"]?.toString()?.ifBlank { null }
                ?: username
            val avatarUrl = root["avatar_url"]?.toString()?.ifBlank { null }
            val bio = root["bio"]?.toString()?.ifBlank { null }

            PlatformResolveResult(
                name = name,
                avatarUrl = avatarUrl,
                signature = bio,
                contactMap = mapOf("github" to username)
            )
        } catch (_: Exception) {
            simpleResult(username)
        }
    }

    /** 从 URL 中提取 GitHub 用户名 */
    private fun extractUsername(content: String): String? {
        val match = Regex("""github\.com/([a-zA-Z0-9_-]+)(?:/|$|\?)""").find(content)
        if (match != null) {
            val name = match.groupValues[1]
            if (name !in listOf("orgs", "repos", "settings", "notifications", "explore", "trending", "search", "features", "marketplace", "pricing", "login", "signup", "topics", "collections", "events")) {
                return name
            }
        }
        return null
    }

    /** API 调用失败时回退到简单结果 */
    private fun simpleResult(username: String): PlatformResolveResult {
        return PlatformResolveResult(
            name = username,
            avatarUrl = null,
            signature = null,
            contactMap = mapOf("github" to username)
        )
    }
}