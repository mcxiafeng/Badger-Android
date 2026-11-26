package top.mcxiafeng.badger.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * 统一创建 EncryptedSharedPreferences。
 *
 * 备份恢复或换机后，已存密文会与 Keystore 中重新生成的主密钥不匹配，直接创建会抛
 * AEADBadTagException 并沿依赖注入链在启动时崩溃成死循环。这里在解密失败时清空该
 * 存储自己的密文后用现有主密钥重建：已存数据丢失、需要重新登录或重新配置，但共享
 * 主密钥不受影响，其他加密偏好仍可解密。重建仍失败说明 Keystore 本身不可用，异常
 * 原样抛出，绝不降级到明文。
 */
object EncryptedPrefs {
    private const val TAG = "EncryptedPrefsTester"

    fun open(context: Context, name: String): SharedPreferences = try {
        create(context, name)
    } catch (e: GeneralSecurityException) {
        recreate(context, name, e)
    } catch (e: IOException) {
        recreate(context, name, e)
    }

    private fun recreate(context: Context, name: String, cause: Exception): SharedPreferences {
        Log.e(TAG, "加密偏好 $name 无法解密（${cause.javaClass.simpleName}），清空密文后重建，已存数据丢失", cause)
        // 首次 create 失败前，同一进程内已缓存该文件的 SharedPreferences 实例；
        // 只删文件不清内存中的旧密文，重试时仍会读到损坏的 keyset，必须通过实例本身清空。
        val cleared = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit().clear().commit()
        if (!cleared) Log.e(TAG, "加密偏好 $name 密文清盘失败，重建大概率仍会失败")
        return create(context, name)
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
