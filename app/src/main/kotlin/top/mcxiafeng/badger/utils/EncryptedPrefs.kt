package top.mcxiafeng.badger.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import top.mcxiafeng.badger.data.account.AccountContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一创建 EncryptedSharedPreferences，进程内缓存实例。
 *
 * security-crypto 每次创建都要走 MasterKey 构建 + Tink keyset 解密（Keystore binder），
 * 实测单次约 300ms；无缓存时设置页在主线程同步连开两次，直接把转场动画整帧吞掉
 * （ColorOS Quality 两次 UISlowBinder ~310ms，Choreographer 跳 87 帧）。
 *
 * 备份恢复或换机后，已存密文会与 Keystore 中重新生成的主密钥不匹配，直接创建会抛
 * AEADBadTagException 并沿依赖注入链在启动时崩溃成死循环。这里在解密失败时清空该
 * 存储自己的密文后用现有主密钥重建：已存数据丢失、需要重新登录或重新配置，但共享
 * 主密钥不受影响，其他加密偏好仍可解密。重建仍失败说明 Keystore 本身不可用，异常
 * 原样抛出，绝不降级到明文。
 */
object EncryptedPrefs {
    private const val TAG = "EncryptedPrefsTester"

    /** 键为账号作用域映射后的物理文件名，切换账号自然落到不同键，不会串号。 */
    private val CACHE = ConcurrentHashMap<String, SharedPreferences>()

    /** AndroidKeyStore 并非完全线程安全（如 issuetracker 176215143），所有创建路径共用一把锁串行。 */
    private val createLock = Any()

    fun open(context: Context, name: String): SharedPreferences {
        val cacheKey = cacheKeyFor(context, name)
        CACHE[cacheKey]?.let { return it }
        synchronized(createLock) {
            CACHE[cacheKey]?.let { return it }
            Log.d(TAG, "加密偏好 $name（物理文件 $cacheKey）首次创建")
            return try {
                create(context, name).also { CACHE[cacheKey] = it }
            } catch (e: GeneralSecurityException) {
                recreate(context, name, cacheKey, e)
            } catch (e: IOException) {
                recreate(context, name, cacheKey, e)
            }
        }
    }

    /** 启动预热：把首次创建的 Keystore 开销挪到后台线程，UI 路径读 Key 时直接命中缓存。 */
    fun warmUp(context: Context, names: Collection<String>) {
        names.forEach { name ->
            try {
                open(context, name)
            } catch (e: Exception) {
                Log.e(TAG, "加密偏好 $name 预热失败（UI 路径会退回首次同步创建）", e)
            }
        }
    }

    private fun cacheKeyFor(context: Context, name: String): String =
        AccountContext.resolvePrefsName(AccountContext.find(context)?.scope, name)

    private fun recreate(context: Context, name: String, cacheKey: String, cause: Exception): SharedPreferences {
        Log.e(TAG, "加密偏好 $name 无法解密（${cause.javaClass.simpleName}），清空密文后重建，已存数据丢失", cause)
        // 清空前必须先逐出缓存实例；框架层 SharedPreferences 也按物理文件缓存实例，
        // 只删文件不清内存中的旧密文，重试时仍会读到损坏的 keyset，必须通过实例本身清空。
        val cleared = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().clear().commit()
        if (!cleared) Log.e(TAG, "加密偏好 $name 密文清盘失败，重建大概率仍会失败")
        CACHE.remove(cacheKey)
        return create(context, name).also { CACHE[cacheKey] = it }
    }

    private fun create(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
