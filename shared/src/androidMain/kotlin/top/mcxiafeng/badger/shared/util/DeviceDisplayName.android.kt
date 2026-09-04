package top.mcxiafeng.badger.shared.util

import android.os.Build

/**
 * [KMP K08-B] Android actual：Build.MANUFACTURER + Build.MODEL。
 */
actual fun deviceDisplayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }
