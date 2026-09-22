package top.mcxiafeng.badger

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch
//import top.mcxiafeng.badger.data.repository.SocialRepository
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.system.database.androidSystemDatabaseBuilder
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.database.androidCacheDatabaseBuilder
import top.mcxiafeng.badger.platform.initDeviceIdentity
//import top.mcxiafeng.badger.sync.SyncPullEngine

class BadgerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: 初始化 CacheDatabase / SystemDatabase / DeviceIdentity")
        CacheDbHolder.init(androidCacheDatabaseBuilder(this))
        SystemDbHolder.init(androidSystemDatabaseBuilder(this))
        initDeviceIdentity(this)
    }

    private companion object {
        const val TAG = "BadgerApplicationTester"
    }
}
