package top.mcxiafeng.badger

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.SocialRepository
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.database.androidSystemDatabaseBuilder
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.database.androidCacheDatabaseBuilder
import top.mcxiafeng.badger.platform.initDeviceIdentity
import top.mcxiafeng.badger.sync.SyncPullEngine
import top.mcxiafeng.badger.sync.SyncScope

class BadgerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: 初始化 CacheDatabase / SystemDatabase / DeviceIdentity")
        CacheDbHolder.init(androidCacheDatabaseBuilder(this))
        SystemDbHolder.init(androidSystemDatabaseBuilder(this))
        initDeviceIdentity(this)
        // 启动即重放上次未推完的 pending（离线写入→被杀→下次启动补推），成功后自动拉取
        SyncScope.scope.launch {
            val report = SocialRepository(CacheDbHolder.get(), SystemDbHolder.get()).pushPending()
            Log.d(TAG, "启动推送完成: ${report.summary()}")
        }
        // 增量拉取：未登录时引擎内部直接跳过；登录后按游标续拉
        SyncScope.scope.launch {
            runCatching { SyncPullEngine().pullAll() }
                .onSuccess { report -> Log.d(TAG, "启动增量拉取: ${report?.summary() ?: "未登录跳过"}") }
                .onFailure { Log.e(TAG, "启动增量拉取失败", it) }
        }
    }

    private companion object {
        const val TAG = "BadgerApplicationTester"
    }
}
