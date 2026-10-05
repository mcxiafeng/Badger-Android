package top.mcxiafeng.badger

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.mcxiafeng.badger.data.repository.SystemRepository
import top.mcxiafeng.badger.data.repository.UserRepository
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.database.androidSystemDatabaseBuilder
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.database.androidCacheDatabaseBuilder
import top.mcxiafeng.badger.platform.initDeviceIdentity
import top.mcxiafeng.badger.sync.CollectionSyncer
import top.mcxiafeng.badger.sync.PersonSyncer
import top.mcxiafeng.badger.sync.ProfileSyncer
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.sync.SyncEngineHolder
import top.mcxiafeng.badger.sync.TagsSyncer

class BadgerApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: 初始化 CacheDatabase / SystemDatabase / DeviceIdentity / SyncEngine")
        CacheDbHolder.init(androidCacheDatabaseBuilder(this))
        SystemDbHolder.init(androidSystemDatabaseBuilder(this))
        initDeviceIdentity(this)
        initSyncEngine()
        kickSync("startup")
        observeForeground()
        observeNetworkRestore()
    }

    /** 组合根：引擎单例装配（各实体同步器内部自取仓储与 API），iOS 侧后续接线时同型复制。 */
    private fun initSyncEngine() {
        SyncEngineHolder.init(
            SyncEngine(
                systemRepository = SystemRepository(),
                userRepository = UserRepository(),
                personSyncer = PersonSyncer(),
                profileSyncer = ProfileSyncer(),
                collectionSyncer = CollectionSyncer(),
                tagSyncer = TagsSyncer(),
            )
        )
    }

    /**
     * 全局时刻只推送离线队列（拉取是页面打开的事）：队列空 = 零网络；
     * 引擎有防重入，重复调用安全；预期失败（离线/未登录）折叠进 outcome，bug 直接崩。
     */
    private fun kickSync(reason: String) {
        appScope.launch {
            Log.d(TAG, "kick($reason): ${SyncEngineHolder.get().pushQueue()}")
        }
    }

    /** 前台回切 kick（lifecycle-process 已在依赖里）。 */
    private fun observeForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                Log.d(TAG, "回到前台，kick 同步")
                kickSync("foreground")
            }
        })
    }

    /** 网络恢复 kick：registerDefaultNetworkCallback 要求 API 24+（minSdk 26 满足）。 */
    private fun observeNetworkRestore() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "网络恢复，kick 同步")
                kickSync("network-restore")
            }
        })
    }

    private companion object {
        const val TAG = "BadgerApplicationTester"
    }
}
