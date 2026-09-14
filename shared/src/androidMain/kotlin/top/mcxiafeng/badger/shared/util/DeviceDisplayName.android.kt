package top.mcxiafeng.badger.shared.util

import android.os.Build

actual fun deviceDisplayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }
