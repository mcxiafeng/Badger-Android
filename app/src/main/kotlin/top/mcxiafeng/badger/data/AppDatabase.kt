package top.mcxiafeng.badger.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Badger 应用的主数据库定义
 *
 * 使用 Room 持久化库，包含 6 个实体表和对应的 6 个 DAO。
 * 全局类型转换通过 [Converters] 处理。
 *
 * 实体表：
 * - [Contact] - 联系人基本信息
 * - [ContactField] - 系统预置联系方式字段定义
 * - [CustomField] - 用户自定义字段定义
 * - [ContactFieldValue] - 联系人字段值（关联表）
 * - [CardCollection] - 名片夹/合集
 * - [ScanResult] - 扫描结果记录（联系人↔名片夹关联表）
 *
 * @see DatabaseProvider 数据库单例获取方式
 */
@Database(
    entities = [
        Contact::class,
        ContactField::class,
        CustomField::class,
        ContactFieldValue::class,
        CardCollection::class,
        ScanResult::class,
        UserProfile::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun contactFieldDao(): ContactFieldDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun contactFieldValueDao(): ContactFieldValueDao
    abstract fun cardCollectionDao(): CardCollectionDao
    abstract fun scanResultDao(): ScanResultDao
    abstract fun userProfileDao(): UserProfileDao
}
