package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.ocr.LaunchAction

expect suspend fun executeLaunchAction(action: LaunchAction): Boolean
