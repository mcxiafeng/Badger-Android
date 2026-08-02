# Badger - AI Agent 开发指南

## 项目概述

Badger 是一款 Android 电子名片夹应用，核心功能：扫码存联系人、管理数字名片、NFC 写入、WebDAV 云同步。全中文 UI，数据本地存储。

V2 重构版（[docs/BADGER_V2_CLIENT_PLAN.md](./docs/BADGER_V2_CLIENT_PLAN.md)）已切换到 NowInAndroid 风格 Repository = 薄协调层 + DataSource 双层结构。所有写操作走乐观更新 + PendingUpload 队列 + WorkManager 异步消费。

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 语言 | Kotlin 2.3.21，Java 17 |
| UI | Jetpack Compose + Miuix 0.9.3（MIUI 风格组件库） |
| 架构 | MVVM + Use Cases，Repository = 薄协调层 |
| DI | Koin 4.0.0（pure-Kotlin，替代 Hilt） |
| 数据库 | Room 2.8.4（**version = 6**，9 V1 entity + 8 V2 cache + 2 queue，含 FTS4 全文检索） |
| 同步 | WorkManager 2.10.3 + PendingUpload 队列 |
| 图片 | Coil 3 |
| 分页 | Paging 3 |
| 相机/扫码 | CameraX 1.3.4 + WeChatQRCode 2.5.0 + ML Kit Chinese 16.0.1 |
| 网络 | OkHttp 4.12 |
| NFC | ReaderMode（非 ForegroundDispatch） |
| 导航 | 自定义栈式导航器 `AppNavigator`（非 Jetpack Navigation） |
| 测试 | Robolectric 4.14 + MockK + Truth + Turbine + Koin-test |

## 项目结构

```
app/src/main/kotlin/top/mcxiafeng/badger/
├── BadgerApplication.kt      # 普通 Application + startKoin{} (无 @HiltAndroidApp)
├── MainActivity.kt            # ComponentActivity + edge-to-edge (无 @AndroidEntryPoint)
├── App.kt                     # HorizontalPager(4 tabs) + AnimatedContent 路由栈
├── AppViewModel.kt            # 顶层 VM:auth bootstrap + userProfile tick
├── AppTheme.kt                # Miuix 主题
├── NetworkModule.kt           # OkHttpClient + ServerApiFactory 顶层 lambda（Koin 不接管它）
├── LegacyTagFixup.kt          # 一次性 v4→v5 migration 收尾
├── ai/                        # AiTagGenerator
├── data/                      # 数据层 (V1 + V2 cache 共存，AppDatabase version=6)
│   ├── Models.kt              # V1 entity (ContactField/CustomField/...)
│   ├── Daos.kt                # V1 DAO (字段/扫码历史/平台兼容)
│   ├── AppDatabase.kt         # @Database(version=6) + 5 Migration
│   ├── AuthPrefs.kt           # 普通 SharedPreferences (refresh token 短期 + access token 仅内存)
│   ├── CloudSyncConfig.kt
│   ├── ShortLinkPrefs.kt
│   ├── OnboardingPrefs.kt
│   ├── DeveloperModePref.kt
│   ├── QAuxvFriendImporter.kt
│   ├── CollectionExporter.kt
│   ├── CollectionUtils.kt
│   ├── cache/
│   │   ├── entity/            # 8 个 V2 *CacheEntity (主路径)
│   │   └── dao/               # 8 个 V2 cache DAO
│   ├── queue/                 # PendingUpload + OperationHistory (V2-P2/P7)
│   │   ├── PendingUploadEntity
│   │   ├── PendingUploadDao
│   │   ├── OperationHistoryEntity
│   │   ├── OperationHistoryDao
│   │   └── OperationTypes.kt
│   ├── snapshot/              # ContactSnapshot + Snapshotter (撤销用)
│   └── repository/            # Repository 接口 + Impl
│       ├── ContactRepository + Impl
│       ├── FieldRepository + Impl
│       ├── CollectionRepository + Impl
│       ├── UserProfileRepository + Impl + Ticker
│       ├── TagRepository + Impl
│       ├── OperationHistoryRepository + Impl
│       ├── SyncStatusRepository + Impl
│       ├── UserAuthRepository
│       ├── WorldRegionRepository (80KB+700KB 国家/地区数据)
│       ├── ServerUrlHolder + ServerApiFactory
│       ├── ContactMapper
│       └── CommitResult
├── di/
│   ├── KoinModules.kt         # 6 个 module:database/repository/network/image/useCase/viewModel
│   └── KoinComponentBy.kt     # 静态 get<T>()（VM 字段初始化器用，避开 KoinComponent 双接收器歧义）
├── domain/                    # 8 个 UseCase (ParseQr / SaveScanned / Merge / Filter / Duplicate / PrepareNfc / SelectPlatform)
├── network/
│   ├── ContactNetworkResolver # HttpUtil 静态 compat 层入口
│   ├── LinkResolver
│   ├── ServerApi              # Retrofit 接口（实为 OkHttp + Gson 手写）
│   ├── PlatformAdapterRegistry # 13 个 PlatformAdapter 注册
│   └── ShortLinkService       # short.io API (更新链接用 POST 而非 PATCH)
├── ocr/
│   ├── AiOcrConfig            # DeepSeek / 通义千问 / 智谱 / 月之暗面 / 硅基流动 / 自定义
│   ├── AiOcrService
│   ├── ExtractedContactInfo
│   └── PlatformFields.kt      # 平台字段定义注册表 (PlatformFieldDef)
├── pages/                     # UI 页面（按功能分包，详见 routes）
│   ├── card/                  # 名片夹 (CardPage / CollectionDetailPage + Dialogs)
│   ├── person/                # 联系人列表 (PersonPage, Paging 3) + contact/* (创建/详情/平台选择等 20+ dialogs)
│   ├── scanner/               # 扫码 (ScannerPage + CameraX/OCR/Merge 子模块)
│   ├── settings/              # 设置 (15 个 sub-page + 各自 VM)
│   ├── setupguide/            # 首次引导
│   ├── social/                # 我的名片 + NFC (QrCodeCard / NfcHelper / NfcWriteDialog)
│   └── auth/                  # LoginScreen / RegisterScreen + AuthViewModel
├── sync/                      # V2 同步基础设施
│   ├── ContactSyncBootstrapper # 启动一次性主同步
│   ├── DeviceIdProvider
│   ├── PendingUploadScheduler  # 合并高频 kick + WorkManager 触发 (10s backoff)
│   ├── PendingUploadWorker     # 实际消费 PendingUpload DAO
│   ├── PendingUploadExecutor   # Executor 核心循环
│   ├── RevertStuckOpWorker     # 卡死的 IN_FLIGHT op 恢复
│   └── SyncWorkerFactory       # Koin 模式下手动构造 Worker
├── ui/                        # 通用 UI 组件
│   ├── LiquidGlassNavBar.kt   # 浮动导航栏
│   ├── components/            # Avatar / Dialog / EmptyState / PlatformIcon / ImageCrop / TagDialogs / FirstTimeHint / LaunchActionHandler / CollectionTheme
│   ├── blur/                  # BlurHelper / GpuCompat / LayerBackdrop / Vibrancy / SphereSurface / DampedDrag / LiquidWobble / InteractiveHighlight / Lens / InnerShadow / CombinedBackdrop
│   └── navigation/            # Route / AppNavigator (synchronized) / NavBarConfig / NavTransitionEasing
└── utils/                     # 工具类
    ├── HttpUtil.kt            # OkHttpClient 持有方 + Bitmap 内存缓存
    ├── HttpResult.kt / HttpException.kt / NetworkConstants.kt
    ├── SafeLog.kt             # 脱敏工具:phone/email/token/authHeader/url/apiKey
    ├── Methods.kt             # 通用扩展
    ├── PinyinUtils.kt         # 拼音排序 (NFD 去声调)
    ├── ShortLinkUtils.kt
    ├── QrUtils.kt             # ZXing 二维码生成
    ├── ColorExtractor.kt      # Palette.Swatch → Compose Color
    └── MiuixShape.kt
```

### 数据库 Schema 演进

`app/src/main/kotlin/top/mcxiafeng/badger/data/AppDatabase.kt` 中定义的迁移（导出 schema 在 `app/schemas/`）：
- MIGRATION_1_2: card_collections 加 backgroundImagePath + dominantColor
- MIGRATION_2_3: contact_platforms 表 + contacts.pinyinInitial + FTS4 虚表
- MIGRATION_3_4: tags 表 + 多对多 contact_tag_cross_ref + FTS4 扩展
- MIGRATION_4_5: 8 个 V2 cache 表 + groups / collection_lifecycle 字段
- MIGRATION_5_6: 把老 contacts/contact_fields 等 V1 表数据镜像到 V2 cache 表（isLocalOnly=1），pending_uploads/operation_history 空表创建

## 架构模式

### Koin DI（[§14.2] - 替换原 Hilt）

| 旧 Hilt                            | 新 Koin                                    |
|------------------------------------|--------------------------------------------|
| `@HiltAndroidApp BadgerApplication` | 普通 `Application` + `startKoin{}`         |
| `@HiltViewModel X*ViewModel`        | `viewModel { X() }` + `koinViewModel<X>()` |
| `@Inject constructor()`             | `singleOf(::Impl) { bind<Interface>() }`  |
| `@Module @Provides DatabaseModule`  | `databaseModule`                            |
| `@Module @Provides NetworkModule`   | `networkModule` (顶层 `NetworkModule.kt` 提供 OkHttpClient lambda) |
| `@Module @Provides AuthModule`      | `authModule` (合并入 useCaseModule)         |
| `@HiltWorker @AssistedInject`       | 删除注解，`SyncWorkerFactory` 手动构造从 Koin `GlobalContext.get()` 拉依赖 |

**6 个 Koin module**（装载顺序在 `BadgerApplication.onCreate`）：
1. `databaseModule` — `AppDatabase` + 所有 DAO
2. `repositoryModule` — `singleOf(::Impl) { bind<Iface>() }`
3. `useCaseModule` — 8 个 UseCase + Snapshotter + Scheduler + Bootstrapper + ServerUrlHolder + WorldRegionRepository + UserAuthRepository + AiTagGenerator + LegacyTagFixup
4. `networkModule` — ServerApiFactory + ServerApi + OkHttpClient + TokenHolder
5. `imageModule` — Coil `ImageLoader` (memory 25%, disk 2%)
6. `viewModelModule` — 20 个 ViewModel

#### Koin 注入模式

```kotlin
// VM 不再继承 KoinComponent（避免 +ComponentCallbacks 双接收器歧义）
// 字段初始化器通过静态 KoinComponentBy.get<T>() 拿依赖
class AppViewModel : ViewModel() {
    val userProfileRepository = top.mcxiafeng.badger.di.KoinComponentBy.get<UserProfileRepository>()
}

// 普通类用构造器注入
class PendingUploadExecutor(
    private val dao: PendingUploadDao,
    private val serverApi: ServerApi,
    ...
) { ... }
```

#### Robolectric 测试 Koin 启动

每个 ViewModel/Repository 测试 `setUp()` 必须强制 stop+start 一次 Koin（防止 JVM 残留 GlobalContext 撞 `KoinApplicationAlreadyStartedException`）：

```kotlin
runCatching { GlobalContext.stopKoin() }
GlobalContext.startKoin { modules(module { single { ... } }) }
```

### MVVM + Use Cases

- **UI 层**：Compose Screen + ViewModel（`koinViewModel()`）
- **ViewModel** 暴露 `StateFlow<UiState>`，`UiState` 必须 `@Immutable data class`
- **Domain 层**：Use Case 类，`operator fun invoke()`，纯业务逻辑
- **Data 层**：Repository 接口在 `data/repository/`，Impl 通过 Koin `singleOf + bind` 注入
- **DataSource**：V1 保留 entity 在 `data/Models.kt`，V2 cache entity 在 `data/cache/entity/`（主路径）

### 架构边界（红线）

- **UI 层禁止直接访问 Repository**：必须通过 ViewModel（`koinViewModel`），不得绕 Koin
- **ViewModel 禁止暴露 Repository 为 public**：必须 `private`，UI 只看 ViewModel 公开接口
- **Composable 禁止包含业务逻辑**：DB / 网络 / 数据转换必须在 ViewModel/UseCase
- **ViewModel 禁止持有 Activity 引用**：方法参数不得为 `Activity`

### 导航

- `Route` sealed class：`MainTabs / Login / Register / Scanner(mode, targetCollectionId) / ContactDetail(id) / CollectionDetail(id) / CreateContact / SettingsSubPage(SettingsPage)`
- `SettingsPage` sealed class：`NfcSettings / UiSettings / About / OpenSourceLicense / AppLog / ContactUs / TagManager / PlatformList / OperationHistory / AccountProfile / ServerSettings / SyncStatus / CloudBackup`
- `AppNavigator` **synchronized 锁**（check+removeAt 原子）：栈底 `MainTabs`，push/pop，二级页覆盖一级
- `AnimatedContent` + `NavTransitionEasing` 弹簧阻尼（**已识别为 jank: tween(500) 振荡过久，应改 300ms**）
- `HorizontalPager` 4 Tab：我的名片 / 联系人 / 名片夹 / 设置

### 平台适配器模式

新增社交平台需要：
1. `ocr/PlatformFields.kt` 添加 `PlatformFieldDef`
2. 创建 `PlatformAdapter` 实现
3. `network/PlatformAdapterRegistry` 注册
4. 添加图标 drawable 到 `res/drawable/`

### 同步架构（V2-P2 / P4 / P7 / P9）

写操作走乐观更新三阶段：
1. **Optimistic update**（立即改本地 cache + 渲染）
2. **Snapshot 写入**（`ContactSnapshotter` 留撤销入口）
3. **Enqueue PendingUpload**（DAO 落盘 + `PendingUploadScheduler.kick()` 触发 WorkManager）

WorkManager 配置：`NetworkType.CONNECTED`、10s 指数 backoff、`APPEND_OR_REPLACE`（不丢新 op）。`SyncWorkerFactory` 在 `BadgerApplication` 实现 `Configuration.Provider` 接管初始化。`RevertStuckOpWorker` 每 30s 检查 `IN_FLIGHT` 卡死 op 重置为 `PENDING`。

## 构建配置

- `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`
- Kotlin `2.3.21` + KSP `2.3.6`，Room 是唯一 KSP 处理器（**Hilt KSP 已移除**，`ksp.incremental=true` 恢复增量）
- 三种构建类型：`debug`（`applicationIdSuffix=.debug`, `versionNameSuffix=-dev`，无混淆）/ `beta`（`applicationIdSuffix=.beta`, 混淆）/ `release`（混淆）
- ABI 分包：`arm64-v8a`、`armeabi-v7a`、`x86_64`，`isUniversalApk=false`
- APK 输出文件名：`Badger-{versionName}-{versionCode}-{abi}-{yyyyMMdd-HHmm}.apk`
- 签名从环境变量读取：`KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
- JDK 路径锁定 `C:\Program Files\Java\jdk-17`（`gradle.properties` 含 `org.gradle.java.home`；CI 用 `sed -i '/org.gradle.java.home/d'` 删除）

### 测试 MockK 配置

`build.gradle.kts` 的 `tasks.withType<Test>()` 必须设置 `systemProperty("jdk.attach.allowAttachSelf", "true")` —— JDK 17+ 默认禁止 self-attach 但 MockK 用 ByteBuddy 安装 javaagent，否则整套 mockk 用例在 `setUp` 阶段抛 `IllegalStateException`。

---

- LinkTemplate 必须是 HTTPS URL（不能是 `mqq://`、`weixin://`、`tg://` 等自定义 scheme）。iOS 后台 NFC 仅识别 HTTPS URI record，自定义 scheme 写入 NFC 后在 iOS 不会跳转
- AI OCR 仅做 DeepSeek 预设；云端备份不做服务商预设，用户自填 NAS/服务器地址
- HTTP 写 URL 不能泄 token：用 `SafeLog.url(u)` / `SafeLog.authHeader(v)` / `SafeLog.token(t)` 脱敏（详情见 `utils/SafeLog.kt`）

---

## 开发规范（必须遵守）

### 工作流程
1. **先看后做**：操作前先查看 `libdocs/`（Miuix 源码 + 优秀示例）和项目已有代码
2. **理解后重写**：参考 libdocs 示例时理解原理后用自己的方式重写
3. **复用优先**：优先 Miuix 组件 + `Methods.kt`/`SafeLog.kt` 等已有工具
4. **不动现有库**：不修改 `libdocs/` 下的图标库 / 组件库 / 第三方项目源码
5. **保持简洁**：单 Composable 文件 ≤ 500 行（超 700 必须拆为 Page+Components+Dialogs）；单函数 ≤ 50 行；相同模式出现 2 次以上必须抽取
6. **日志调试**：关键逻辑加 `Log.d("当前类名Tester", ...)`，敏感值必须经 `SafeLog` 脱敏

### 空状态 UI 规范
- 空列表页面居中提示文字，**禁止空列表可滚动**
- 模板："还没有XXX" + 主题色可点击的"点击添加"链接
- 不要使用灰色占位符、复杂的空状态插图

### 创建联系人规范
- 创建联系人应复用 `ContactDetailPage` 的添加字段/平台能力（create-then-edit 模式），不要新建独立表单页

### 数据库迁移规范
- **重构代码时不得升级 Room 版本号**，确保现有用户数据兼容。仅在模型字段变更时才升级版本（每条 Migration 都要在 `app/schemas/` 导出 JSON）
- **模糊搜索必须使用 FTS4**：`LIKE '%' || :query || '%'` 阻止索引使用，搜索查询走 FTS 表而非 LIKE fallback
- **新增 cache entity** 必须同步加到 `AppDatabase.@Database(entities=...)` + 写 Migration + 加 DAO + 在 `databaseModule` 注册

### 日志规范
- 所有新增逻辑必须加 `Log.d("当前类名Tester", ...)` 日志
- 关键分支、状态变化、异常捕获都要记录
- **禁止 `e.printStackTrace()`**：release 构建中 stderr 输出丢失，必须用 `Log.e("当前类名Tester", "xxx失败", e)` 替代
- **敏感字段必须经 `SafeLog` 脱敏**：`phone/email/token/authHeader/url/apiKey` 都有对应函数（详见 `utils/SafeLog.kt`）
- **Why:** 用户要求"只要是你做的，全部都加日志方便排查问题"

### 自检清单（功能完成后）
1. 点击流程是否正常（modifier 冲突可能导致点击失效）
2. 导航是否正常（页面跳转后导航栏/返回键行为）
3. 对话框 UI 是否与项目风格一致
4. 排版/文字是否正确

### 代码风格
- 禁止全限定类名调用，必须先 import 再用短名
- 改完代码不要自动 commit；完成完整功能后主动问用户是否 commit
- CI workflow 只负责构建 APK，不需要跑测试
- **魔法数字必须提取为命名常量**：防抖间隔、超时时间、HTTP 状态码等不得写裸数字，如 `SCAN_DEBOUNCE_MS = 500L`
- **错误处理禁止吞异常**：`catch (_: Exception)` 或 `catch` 返回 `null/false` 而不记录日志的行为禁止；至少 `Log.e` 记录

### 版本发布
- 用户希望自动规划 Dev 和 Beta 版本的发布流程，并编写 changelog
- 上传 artifact 时按 ABI 分割（arm64-v8a、armeabi-v7a、x86_64），CI workflow 见 `.github/workflows/release.yml`

---

## Miuix 组件使用规范

### 点击/反馈
- Card 自带 `onClick`/`onLongPress` 参数，**不要在 modifier 上加 `combinedClickable`/`clickable`**，会冲突导致点击无响应
- 内部有可交互子组件时必须用无点击版 Card
- `LocalIndication` 全局注入 `MiuixIndication`（按压/悬浮/聚焦半透明覆盖层）
- `pressable` Modifier 整合 indication，内置 150ms 防误触延迟
- `SinkFeedback`（按压缩小）、`TiltFeedback`（3D 倾斜）可选反馈

### ArrowPreference
- **不可点击的纯信息展示行不得用 ArrowPreference**（箭头暗示可点击）
- 纯信息展示（版本号、构建日期等）用 `BasicComponent`
- 需要导航/弹窗/操作的行才用 ArrowPreference
- ArrowPreference 不可嵌套 combinedClickable

### 文字样式
- 优先用 `MiuixTheme.textStyles` 替代硬编码 `fontSize`+`fontWeight`
- 层级：title1(32sp) > title2(24sp) > title3(20sp) > title4(18sp) > subtitle(14sp Bold) > body1(16sp) > body2(14sp) > footnote1(13sp) > footnote2(11sp)

### 卡片间距
- 纵向间距不低于 12.dp，横向不低于 8.dp
- 优先用 `Arrangement.spacedBy(X.dp)` 或容器级 `padding`

### 弹窗/下拉
- **WindowDialog 必须用 Pattern A**：`if (showXxx) { WindowDialog(show = true, ...) }`，外层 `if` 控制挂载
- **不得用 Pattern B**：`WindowDialog(show = showXxx, ...)` 会导致首帧不渲染
- `DialogLayout` 需手动套 `Box(fillMaxSize, contentAlignment = Center)` 居中
- `OverlayListPopup` 需放在 Scaffold 内部；菜单项禁用 `BasicComponent`，改用 `Box+clickable+Text`
- WindowDialog 内部无 Scaffold，需用 `WindowDropdownPreference` 替代 `OverlayDropdownPreference`

### 其他
- 禁用 `MiuixIcons`，统一使用 Material Icons
- `Color.Transparent` 的 RGB 是 (0,0,0)，`copy(alpha)` 渲染为半透明黑色。需要半透明主题色用 `MiuixTheme.colorScheme.surfaceContainer.copy(alpha=...)`
- `MiuixTextButton` 没有 content lambda，需要加载动画时改用 `Button`
- `MiuixCircularProgressIndicator` 没有 color 参数，用 `colors: ProgressIndicatorColors`
- `ToggleableState` 包路径：`androidx.compose.ui.state.ToggleableState`
- Miuix 0.9.0 已自带 `ColorPalette`（HSV 色格）和 `ColorPicker`（色相环+滑块）

---

## 对话框规范

### 按钮
- 最多 2 个按钮（取消+确认），3 个太丑
- 单个按钮必须 `Modifier.fillMaxWidth()` 顶满宽度

### flag 重置
- 所有 `showXxxDialog` 状态必须在三条路径都重置为 `false`：`onDismissRequest`、取消按钮、确认按钮
- 选项型对话框每个选项的 `onClick` 也必须重置 flag

### 选项型对话框
- 用 `BasicComponent` 列表 + `WindowDialog`，不需要 `DialogButtonRow`
- 点选项直接执行，点外部关闭

### 拍照处理中 Dialog
- `isProcessingPhoto` 时 Dialog `onDismissRequest` 置空 + `BackHandler` 拦截，防止误触关闭丢失拍照结果

---

## 布局规范

### 不得重叠
- 所有 UI 组件之间绝对不得出现视觉重叠
- 浮动组件必须考虑与其他组件的偏移
- 使用 `LocalFloatingBarBottomPadding` 等 CompositionLocal 传递高度信息

### FloatingToolbar 适配
- 主 Tab 页面的 `floatingToolbar` 槽必须用 `Box(modifier = Modifier.padding(bottom = LocalFloatingBarBottomPadding.current))` 包裹
- 二级页面不受影响

---

## Android/Compose 关键模式

### BackHandler
- 所有多选/编辑/特殊模式页面必须添加 `BackHandler(enabled = isInSpecialMode) { exitSpecialMode() }`
- 适用：多选模式、编辑模式、搜索展开状态、拍照处理中 Dialog

### CameraPreview
- 用 `rememberUpdatedState(isScanningPaused)` 缓存扫描暂停状态，不要作为 `LaunchedEffect` key
- `cameraProviderFuture`（lambda）作为 key 不稳定，Dialog 重组会导致相机重绑定

### NFC
- **必须用 ReaderMode**，不要用 ForegroundDispatch（Android 12+ BAL 限制会清空 Tag 数据）
- 写入成功后延迟 3s 再 `disableReaderMode`

### WorkManager
- **`BadgerApplication` 实现 `Configuration.Provider`**，`workManagerConfiguration` 内构造 `SyncWorkerFactory(this)`，让 Koin 能注入到 Worker
- `PendingUploadScheduler` 监听 `ProcessLifecycleOwner` + `ConnectivityManager` 在合适时机 `kick()`
- 重复 `kick()` 用 `MutableSharedFlow(replay=0, extraBufferCapacity=N)` 合并去抖

### API 陷阱
- short.io 更新链接用 POST（非 PATCH）
- Gson 解析 JSON 数字默认为 Double：`code: 0` → `0.0`
- B 站 API 返回结构是 `data.card.face`（非 `data.face`）；CDN 头像需 `Referer` 头
- `LaunchedEffect` 中 `Flow.collect` 是无限挂起，会阻塞后续调用；拆分为独立 `LaunchedEffect`

### Bitmap 内存管理
- **不能手动 recycle 展示中的 Bitmap**（被 `asImageBitmap()` 引用的 bitmap）
- 只有临时 bitmap（缩放中间产物、保存前的编码 bitmap）才 recycle，且必须在 `try/finally` 中回收
- **状态变量中的 Bitmap 旧引用必须在赋值前回收**：`val old = state.value; state.value = newBitmap; old?.recycle()`
- **ML Kit TextRecognizer 必须复用实例**：不得在每帧创建新 `TextRecognition.getClient()`，模型加载开销 50-100ms，页面级 `remember` 创建一次

### Color ↔ ARGB Long 转换
- Compose `Color` → Long 存入数据库时统一用无符号格式：`color.toArgb().toLong() and 0xFFFFFFFFL`
- Long → Color：`Color(longValue)` 两种格式都正确
- 平台品牌色（如 `0xFF12B7F5L`）是正数 Long，`Palette.Swatch.rgb` 是负数 Long

### 通用陷阱
- `hashCode() % array.size` 可能返回负数索引 → 加 `abs()`
- 协程线程：Toast 需在主线程，IO 操作切 `Dispatchers.IO`
- WindowDialog 切换（两个交替显示）会导致闪烁 → 在同一个内根据状态切换内容
- 拼音排序：Han-Latin Transliterator 返回带声调拼音，需 `Normalizer.NFD` 去声调
- `HttpUtil.downloadBitmap` 有额外 headers 时不使用内存缓存
- `aspectRatio` + `padding` 交互陷阱：`fillMaxWidth().aspectRatio(1f).padding(horizontal=16.dp)` 会使内部容器变矩形

### CameraX 资源清理
- 所有 CameraX 资源（flashlight、camera、analyzer）离开页面时必须通过 `DisposableEffect` 清理
- 手电筒在离开扫描页后不关是已知 bug，需 `DisposableEffect(Unit) { onDispose { camera?.cameraControl?.enableTorch(false) } }`
- **`Tasks.await()` 禁止阻塞 CameraX analyzer 线程**：ML Kit 文字识别必须用 `suspendCancellableCoroutine` 包装异步 API

### Compose 状态管理
- **单 Composable 函数 `mutableStateOf` 不超过 10 个**：超 10 个必须拆分子组件
- **无关状态必须隔离到独立 Composable**：相机预览、扫描结果、对话框等各自成子组件
- **Regex、Color 转换等计算必须在 `remember` 中**
- **`UiState` 必须标注 `@Immutable`**：确保 Compose 重组优化生效

---

## 协程与数据一致性

### Mutex 保护
- Repository 中的 read-modify-write 操作必须用 Mutex 保护：`contactMutex`、`userProfileMutex`、`collectionMutex`
- 多个协程同时修改同一数据时可能竞态导致覆盖

### 写前重读（防 stale snapshot）
- 更新 Contact/UserProfile/CardCollection 前，必须从 DB 重新读取最新数据，不能直接使用 UI state 中的快照

### 对话框状态管理
- 编辑对话框需要独立的状态变量（`showEditPlatformDialog` + `editingPlatform`），不要和新增对话框共用 `showAddPlatformDialog`
- 编辑初始化需要用一次性 flag（`editInitialized`），不能用 `mainInput.isEmpty()` 条件判断
- `remember { mutableStateMapOf(...) }` 需要传入 key（如 `remember(currentCollectionIds)`），否则切换页面时不会重置

### PendingUpload 队列契约
- 状态机：`PENDING → IN_FLIGHT → (DONE | FAILED_PERMANENT | FAILED_RETRY)`
- `markInFlight` 双 Worker 抢锁用 CAS 语义
- `recoverFromDirect` 把卡死的 `IN_FLIGHT → PENDING`（30s 兜底窗口）

---

## AI OCR API 陷阱

- ModelScope API 默认 SSE 流式响应，`HttpUtil` 无法解析。必须在请求体明确 `"stream": false`
- AI OCR 文字模式的超时时间必须和视觉模式一致（60s），否则慢模型（Qwen3-235B）会超时
- Gson `getAsJsonArray("choices")` 遇到 `"choices": null`（JsonNull）会抛 ClassCastException。必须先检查 `isJsonNull`

---

## 安全已知问题

- AI OCR API key 和 short.io API key 存储在明文 SharedPreferences（`ai_ocr_config.xml`、`short_link_settings`）。`AuthPrefs` 也使用明文 SharedPreferences（refresh token 由服务端短期下发 + access token 仅内存持有）
- `network_security_config.xml` 中 `cleartextTrafficPermitted=true` 在 `<base-config>` 中，对所有构建类型生效
- CloudSyncManager 备份 SharedPreferences 到 WebDAV 时 API key 以明文 JSON 上传
- `allowBackup=true` 可通过 ADB 导出完整数据库和 SharedPreferences
- ProGuard 规则缺少 Gson TypeToken、OkHttp 的反射 keep 规则，release 构建可能崩溃

## Android 系统兼容性

- Android 16 "减弱模糊效果" 无障碍设置会静默禁用跨窗口模糊。NavBarConfig 必须在 API 31+ 检查 `WindowManager.isCrossWindowBlurEnabled`
- 模糊/液态玻璃效果可能导致 LazyColumn 无法滚动到底部（PersonPage、设置页、CardPage）
- `BadgerApplication.onCreate()` 通过 `Build.FINGERPRINT.equals("robolectric", ignoreCase = true)` 跳过 OpenCV/WeChatQRCode 初始化（Robolectric 测试环境无 native 库）

---

## 已知 UI 问题

- 扫码添加的 QR 码在浅色模式下与卡片背景色有明显色差（环形边框），需在渲染时消除背景色差异
- `MiuixTextButton` 没有 `color` 参数，使用 `ButtonDefaults.textButtonColorsPrimary()` 获取主题色
- `MiuixTheme.colorScheme.*` 属性只能在 `@Composable` 作用域内调用
- Miuix 的 colorScheme 使用 `onSurfaceVariantSummary` / `onSurfaceVariantActions`，不是 Material3 的 `onSurfaceVariant`

---

## 构建环境

- KSP/Dagger 跨盘符构建可能失败（Gradle 缓存在 C: 但项目在 F:）。错误信息："this and base files have different roots"
- JDK 必须 17 且路径 `C:\Program Files\Java\jdk-17`（`gradle.properties` 锁）
- CI workflow 用 `sed -i '/org.gradle.java.home/d' gradle.properties` 移除本地 Java 锁定

### 构建命令

```bash
# 本地调试
./gradlew assembleDebug                            # 输出三个 ABI 的 debug APK

# 测试
./gradlew test                                     # 全部单元测试 (含 Robolectric JVM)
./gradlew testDebugUnitTest                        # 仅 debug variant
./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.pages.scanner.*"  # 子集

# 静态检查
./gradlew lintDebug                                # Android Lint
./gradlew lint                                      # 所有 variant（lint-baseline.xml 抑制旧问题）

# 发布（需环境变量 + release.keystore）
./gradlew assembleRelease                          # 三个 ABI 的 release APK
./gradlew assembleBeta                             # 三个 ABI 的 beta APK

# 清理
./gradlew clean                                    # 删除 build/
./gradlew cleanKspCaches                           # KSP 缓存独立清理

# 强制重建 schema（新增 Migration 后）
rm -rf app/schemas/top.mcxiafeng.badger.data.AppDatabase/*.json
./gradlew assembleDebug    # 重新生成最新 schema
```

### CI workflows

- `.github/workflows/ci.yml` — push master / PR → `assembleDebug`（仅构建，不测）
- `.github/workflows/release.yml` — push tag `v*` → `assembleRelease + assembleBeta`，按 ABI 上传 6 个 artifact 并触发 `softprops/action-gh-release`
- `.github/workflows/qq-push.yml` — QQ 渠道通知（可选）

---

## 已知性能问题

### AnimatedContent 转场 jank
- `App.kt` 使用 `tween(500)` + `NavTransitionEasing(0.8f, 0.95f)`，弹簧过冲振荡 500ms。应改为 300ms

### HttpUtil Bitmap 缓存无上限
- `ConcurrentHashMap<String, Bitmap>` 无 eviction/size limit。应改 LruCache 或 WeakReference

### AppNavigator 竞态（已修复）
- ~~`synchronizedList.isNotEmpty()` + `removeAt()` 不原子~~ → 现已 `synchronized(lock)` 包裹

### material-icons-extended 膨胀
- 引入 2000+ 图标但实际只用到 4 个（QrCodeScanner、Person、CreditCard、Settings），约 2MB class loading 开销

### 扫码预处理 GC 压力
- `QrCodeUtils.detectQrCodesFromBitmap()` 每帧创建 6+ 预处理变体 x 9 网格区域 x 2x 缩放

### WebDavClient 错误吞噬
- 所有方法 catch 通用 Exception 返回 null/false

### e.printStackTrace() 在 release 中丢失
- 部分历史代码仍有 `e.printStackTrace()`，应用 `Log.e()` + `SafeLog` 替代

### 无 Baseline Profile
- 无 `BaselineProfile` 或 `Macrobenchmark` 模块

---

## 代码质量红线（新增代码必须检查）

| 红线 | 说明                                                                  |
|------|---------------------------------------------------------------------|
| UI 层直接访问 Repository | 必须通过 ViewModel（`koinViewModel()`），不得绕过 Koin                   |
| ViewModel 暴露 Repository 为 public | Repository 必须 `private`                                                |
| Composable 中 `scope.launch(IO)` 执行 DB/网络 | 业务逻辑必须在 ViewModel/UseCase                                          |
| `e.printStackTrace()` | 必须用 `Log.e("当前类名Tester", msg, e)`                                 |
| `catch` 返回 null/false 不记录日志 | 至少 `Log.e`                                                                |
| 裸数字（500L、2000L） | 必须提取为命名常量                                                           |
| 单文件超 700 行 | 必须拆分为 Page+Components+Dialogs                                       |
| 单函数超 50 行 | 必须拆分或提取子函数                                                          |
| 相同代码模式出现 3 次 | 必须抽取通用函数                                                            |
| `Tasks.await()` 阻塞 CameraX 线程 | 必须用协程包装异步 API                                                       |
| 临时 Bitmap 未在 finally 中回收 | `try/finally` 包裹                                                         |
| ML Kit 每帧创建新实例 | 必须页面级 `remember` 复用                                                 |
| `LIKE '%' || query || '%'` 搜索 | 必须走 FTS4 全文检索 |
| ViewModel 方法接收 Activity 参数 | 改用接口/callback 回传 UI 层操作                                             |
| 敏感值明文 log | 用户名/手机号/邮箱/token/auth header/URL 必须经 `SafeLog` 脱敏              |
| VM 字段用 `org.koin...inject` | 用 `top.mcxiafeng.badger.di.KoinComponentBy.get<T>()` 避免双接收器歧义 |
| 跨盘符 Gradle 缓存 | Gradle 缓存与项目必须在同一盘符，否则 `this and base files have different roots` |

---

## 禁止截图

用户明确禁止使用截图/截屏工具读取设备 UI。使用以下替代方案：
- `mobile_list_elements_on_screen` — 获取 UI 元素文字列表
- `adb shell dumpsys` — 获取系统状态
- `adb shell logcat` — 读取日志
- accessibility tree dump — 获取无障碍树

永远不要使用 `mobile_take_screenshot` 或 `mobile_save_screenshot`。

---

## 测试

- 框架：Robolectric 4.14.1 + MockK 1.13.16 + Truth 1.4.4 + Turbine 1.2.0 + Coroutines Test 1.10.2 + Koin-test 4.0.0
- 自定义 `InMemoryDatabaseRule`（在 `testutil/`）创建 Room 内存数据库
- 测试文件在 `app/src/test/`，全部 `KoinTest` 自动管理 startKoin / stopKoin
- WorkManager 测试用 `PendingUploadSchedulerTest` / `PendingUploadWorkerTest` / `RevertStuckOpWorkerTest`
- JVM 跑 `MockK` 必须允许 self-attach：`-Djdk.attach.allowAttachSelf=true`（已在 `build.gradle.kts` 配置）
- 测试环境跳过 OpenCV/WeChatQRCode 初始化：`Build.FINGERPRINT == "robolectric"`

### ViewModel 测试模式

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FooViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        runCatching { GlobalContext.stopKoin() }
        GlobalContext.startKoin { modules(module { ... }) }
        Dispatchers.setMain(testDispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        GlobalContext.stopKoin()
    }

    @Test fun foo() = runTest { ... }
}
```

---

## 重要参考文件

| 文件 | 用途 |
|------|------|
| `app/build.gradle.kts` | 构建配置、依赖、签名、ABI 分包、APK 命名 |
| `gradle/libs.versions.toml` | 版本目录（Koin 4.0, Room 2.8.4, Miuix 0.9.3, WorkManager 2.10.3） |
| `gradle.properties` | KSP 增量、Gradle configuration-cache、JDK 17 路径锁定 |
| `.github/workflows/release.yml` | tag → 多 ABI APK 上传 + GitHub Release |
| `app/src/main/kotlin/top/mcxiafeng/badger/data/Models.kt` | V1 Room 实体定义（Contact/CustomField 等） |
| `app/src/main/kotlin/top/mcxiafeng/badger/data/Daos.kt` | V1 DAO |
| `app/src/main/kotlin/top/mcxiafeng/badger/data/AppDatabase.kt` | `@Database(version=6)` + 5 Migration + 所有 DAO 抽象方法 |
| `app/src/main/kotlin/top/mcxiafeng/badger/data/cache/entity/` | V2 8 个 CacheEntity（主路径） |
| `app/src/main/kotlin/top/mcxiafeng/badger/data/queue/` | PendingUploadEntity/Dao + OperationHistoryEntity/Dao + OperationTypes |
| `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinModules.kt` | 6 个 Koin module 定义 + Koin/Hilt 对照表 |
| `app/src/main/kotlin/top/mcxiafeng/badger/di/KoinComponentBy.kt` | 静态 `get<T>()` 助手（解决双接收器歧义） |
| `app/src/main/kotlin/top/mcxiafeng/badger/sync/PendingUploadScheduler.kt` | kick() 触发器（去抖 + WorkManager） |
| `app/src/main/kotlin/top/mcxiafeng/badger/sync/SyncWorkerFactory.kt` | Koin 模式下手动构造 Worker |
| `app/src/main/kotlin/top/mcxiafeng/badger/ui/navigation/Route.kt` | 路由 sealed class（MainTabs/Scanner/ContactDetail/... + SettingsPage） |
| `app/src/main/kotlin/top/mcxiafeng/badger/ui/navigation/AppNavigator.kt` | 同步锁路由栈 |
| `app/src/main/kotlin/top/mcxiafeng/badger/ui/navigation/NavBarConfig.kt` | 浮动导航栏 + 模糊配置 |
| `app/src/main/kotlin/top/mcxiafeng/badger/utils/SafeLog.kt` | 日志脱敏：user/phone/email/token/authHeader/url/apiKey |
| `app/src/main/kotlin/top/mcxiafeng/badger/ocr/PlatformFields.kt` | 平台字段定义注册表（PlatformFieldDef） |
| `app/src/main/kotlin/top/mcxiafeng/badger/network/PlatformAdapterRegistry.kt` | 平台适配器注册 |
| `App.kt` | 主入口、Tab + 路由组合 + GPU 兼容 + HazeState + 生命周期 |
| `BadgerApplication.kt` | 普通 Application + startKoin + Configuration.Provider(WorkManager) + 跳过 Robolectric 的 OpenCV/WeChatQRCode |
| `libdocs/框架/miuix-main` | Miuix 框架源码和示例（参考用，不要改） |
| `docs/BADGER_V2_CLIENT_PLAN.md` | V2 重写版的客户端协议规约 |
| `docs/V2_P1_HANDOFF.md` | V2 阶段交付清单 |
| `R8_Configuration_Analysis.md` | ProGuard 规则审计报告（当前未补 keep 规则） |
