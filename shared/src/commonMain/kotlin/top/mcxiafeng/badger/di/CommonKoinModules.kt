package top.mcxiafeng.badger.di

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.LegacyTagFixup
import top.mcxiafeng.badger.data.repository.CollectionRepository
import top.mcxiafeng.badger.data.repository.CollectionRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactRepository
import top.mcxiafeng.badger.data.repository.ContactRepositoryImpl
import top.mcxiafeng.badger.data.repository.ContactWriter
import top.mcxiafeng.badger.data.repository.FieldRepository
import top.mcxiafeng.badger.data.repository.FieldRepositoryImpl
import top.mcxiafeng.badger.data.repository.OperationHistoryRepository
import top.mcxiafeng.badger.data.repository.OperationHistoryRepositoryImpl
import top.mcxiafeng.badger.data.repository.ServerUrlHolder
import top.mcxiafeng.badger.data.repository.SyncStatusRepository
import top.mcxiafeng.badger.data.repository.SyncStatusRepositoryImpl
import top.mcxiafeng.badger.data.repository.TagRepository
import top.mcxiafeng.badger.data.repository.TagRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.repository.UserProfileRepositoryImpl
import top.mcxiafeng.badger.data.repository.UserProfileTicker
import top.mcxiafeng.badger.data.repository.DeviceRepository
import top.mcxiafeng.badger.data.repository.NotificationRepository
import top.mcxiafeng.badger.data.repository.UserAuthRepository
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.domain.DuplicateDetectionUseCase
import top.mcxiafeng.badger.domain.ImportProfileFieldsUseCase
import top.mcxiafeng.badger.domain.PrepareNfcWriteUseCase
import top.mcxiafeng.badger.domain.SelectPlatformUseCase
import top.mcxiafeng.badger.network.PlatformManifestRepository
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.OutboxQueue
import top.mcxiafeng.badger.sync.OutboxStore
import top.mcxiafeng.badger.sync.SyncEngine

/**
 * [KMP K16] 双端共用的 Koin 模块（自 app KoinModules 上移）。
 *
 * 平台差异收口为三类注入点，由各平台 module 补齐：
 * - [daoModule]：AppDatabase 实例本身由平台 module 提供（Android=AppDatabaseHost.build /
 *   iOS=iosAppDatabaseBuilder），DAO 单例与 OutboxStore 双端一致；
 * - [commonRepositoryModule]：avatarFetcher lambda 平台实现注入；
 * - [commonAppStateModule]：OutboxScheduler（Android WorkManager）/ SyncDispatcher（iOS）与
 *   AppLinkHandler 绑定由平台 module 追加。
 */

/**
 * UseCase — 对应原 Hilt 自动 `@Inject constructor`(无显式 Provider)。
 * 原代码里 8 个 UseCase 都是无依赖(`@Inject constructor()`);Koin 这里简化成
 * `factoryOf(::UseCase)`,按需创建。
 */
val useCaseModule = module {
    factoryOf(::DuplicateDetectionUseCase)
    // [迁移] 扫码导入档案字段的编排用例（AppViewModel 消费）
    factoryOf(::ImportProfileFieldsUseCase)
    factoryOf(::PrepareNfcWriteUseCase)
    // [迁移] singleOf：debounce/短链更新状态必须跨调用方共享（5829aa7）
    singleOf(::SelectPlatformUseCase)
}

/** ViewModel registrations consumed by Compose `koinViewModel()`. */
val viewModelModule = module {
    viewModel {
        // [迁移] AppViewModel 改为构造器注入（App.kt 的 koinViewModel() 调用面不变）
        top.mcxiafeng.badger.AppViewModel(
            userProfileRepository = get(),
            userProfileTicker = get(),
            userAuthRepository = get(),
            notificationRepository = get(),
            contactRepository = get(),
            importProfileFieldsUseCase = get(),
        )
    }
    viewModel { top.mcxiafeng.badger.pages.auth.AuthViewModel() }
    viewModel { top.mcxiafeng.badger.pages.card.CardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.PersonViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.detail.ContactDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.CreateContactViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.UserProfileDetailViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.dialogs.CountryPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.person.contact.dialogs.RegionPickerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.scanner.ScannerViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.account.AccountSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.notification.NotificationViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.devices.DeviceViewModel() }
    viewModel { top.mcxiafeng.badger.pages.dashboard.DashboardViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.NfcSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.history.OperationHistoryViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.SettingsHomeViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.sync.SyncStatusViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.tags.TagManagerSettingsViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.PlatformListViewModel() }
    viewModel { top.mcxiafeng.badger.pages.social.SocialViewModel() }
    viewModel { top.mcxiafeng.badger.pages.setupguide.SetupGuideViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.account.ChangePasswordViewModel() }
    viewModel { top.mcxiafeng.badger.pages.settings.sync.ServerShortLinkViewModel() }
}

/**
 * V2 cache DAO 单例 + OutboxStore（K16 自 app databaseModule 抽出；AppDatabase 由
 * 平台 module 提供——Koin include 语义，装载时先解析平台 module 的 AppDatabase）。
 */
val daoModule = module {
    single { get<AppDatabase>().contactCacheDao() }
    single { get<AppDatabase>().contactFieldCacheDao() }
    single { get<AppDatabase>().contactFieldValueCacheDao() }
    single { get<AppDatabase>().contactPlatformCacheDao() }
    single { get<AppDatabase>().tagCacheDao() }
    single { get<AppDatabase>().cardCollectionCacheDao() }
    single { get<AppDatabase>().userProfileCacheDao() }
    single { get<AppDatabase>().contactTagCacheDao() }
    single { get<AppDatabase>().syncCursorDao() }
    single { get<AppDatabase>().personProfileCacheDao() }
    single { get<AppDatabase>().customFieldCacheDao() }
    single { get<AppDatabase>().collectionMemberCacheDao() }
    single { get<AppDatabase>().operationHistoryDao() }
    single { get<AppDatabase>().outboxDao() }
    // [K16] OutboxStore 上移 common（dbTransaction 跨端事务边界）；bind OutboxQueue：
    // ContactRepositoryImpl 注入 common 契约接口，缺绑定运行期 NoDefinitionFoundException（K3 冒烟修复）
    singleOf(::OutboxStore) { bind<OutboxQueue>() }
}

/**
 * 仓库 / 单例绑定 — 对应原 Hilt `DataModule` 的 `@Binds`。
 * avatarFetcher 是唯一平台差异点（Android=HttpUtil 下载+Bitmap 落盘 / iOS=Ktor 下载+UIImage 落盘）。
 */
fun commonRepositoryModule(
    avatarFetcher: suspend (String, Long) -> String?,
) = module {
    // [KMP K08-B] avatarFetcher 注入点（实现内封装下载/缩放/编码/落盘与回收）
    single<suspend (String, Long) -> String?> { avatarFetcher }
    singleOf(::ContactRepositoryImpl) { bind<ContactRepository>() }
    singleOf(::FieldRepositoryImpl) { bind<FieldRepository>() }
    singleOf(::CollectionRepositoryImpl) { bind<CollectionRepository>() }
    singleOf(::UserProfileRepositoryImpl) { bind<UserProfileRepository>() }
    singleOf(::TagRepositoryImpl) { bind<TagRepository>() }
    singleOf(::OperationHistoryRepositoryImpl) { bind<OperationHistoryRepository>() }
    singleOf(::SyncStatusRepositoryImpl) { bind<SyncStatusRepository>() }
    singleOf(::ContactWriter)

    single { UserProfileTicker() }
}

/**
 * Application 单例 — Repository / StateHolder / 引导期 Fixup / 后台轮询器等。
 * 必须先于 viewModelModule 装载（VM 字段注入要拉这些）。
 * Outbox 调度器（Android=OutboxScheduler / iOS=SyncDispatcher）与 AppLinkHandler 绑定在平台侧追加。
 */
val commonAppStateModule = module {
    singleOf(::ServerUrlHolder)
    singleOf(::WorldRegionRepository)
    singleOf(::UserAuthRepository)
    singleOf(::AiTagGenerator)
    // [Phase 3] 双向同步引擎（CreateOnPush + PushLoop + PullLoop）
    singleOf(::SyncEngine)
    singleOf(::DeviceIdProvider)
    singleOf(::LegacyTagFixup)
    // [Phase 4 剩余] 服务端平台清单缓存
    singleOf(::PlatformManifestRepository)
    // [B1] 站内通知：未读 60s 轮询。createdAtStart：无 UI 时也要在 SignedIn 后开始轮询。
    single(createdAtStart = true) { NotificationRepository(serverApi = get(), userAuthRepository = get()) }
    // [B3] 设备管理：无需轮询，UI 主动 refresh。
    single { DeviceRepository(serverApi = get(), userAuthRepository = get()) }
    // [KMP K16] NfcWriter（expect class）实例化在平台 module——Android=原 NfcHelper / iOS=CoreNFC
}
