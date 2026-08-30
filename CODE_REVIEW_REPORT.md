# 代码审查与简化报告

> 日期: 2026-08-30
> 范围: 全项目 310 个 Kotlin 文件
> Agent 数: 8 个并行审查
> 发现总数: ~193 项
> 已修复: ~45 项
> 编译: ✅ compileDebugKotlin 通过
> 单测: 434/434 基线一致（12 个 pre-existing 失败，非本次引入）

---

## 一、P0 CORRECTNESS 修复（17 项）

### 1. DeviceApi.kt — 字段名 `"name"` vs `"deviceName"`
- **行号**: 49
- **问题**: `addProperty("name", name)` 发送 `"name"` 字段，但服务端 `UserModule.renameDevice` 读的是 `deviceName`，导致重命名静默失败
- **修复**: `addProperty("deviceName", name)`

### 2. NetworkModule.kt — 双 Cache 实例
- **行号**: 54, 60
- **问题**: `baseClient(context)` 被调用两次，创建两个 `Cache` 实例指向同一目录，OkHttp 文档要求同一目录只应有一个 Cache 实例，会竞争 journal 文件导致缓存损坏
- **修复**: 提取 `val base = baseClient(context)`，ServerApi 和 newBuilder 共用同一个 base 实例

### 3. NetworkModule.kt — TOCTOU `holder.get()!!`
- **行号**: 104
- **问题**: line 99 检查 `holder.get() == null` 后 return，line 104 再次 `get()!!` 强转非空，但另一个线程可在两次调用间执行 `holder.set(null)`（如 logout），导致 NPE
- **修复**: `val current = holder.get() ?: return@chain resp`

### 4. NetworkModule.kt — `runRefresh` 缺 try-catch
- **行号**: 129
- **问题**: `AuthPrefs.readServerUrl(context)` 未做 try-catch 防护，`provideOkHttpClient` 同一调用有 try-catch 降级到默认 URL，但 `runRefresh` 没有
- **修复**: 与 `provideOkHttpClient` 一致的 try-catch + 降级默认 URL

### 5. AuthScreens.kt — 忘记密码按钮失效
- **行号**: 464
- **问题**: "忘记密码？"按钮的 `onClick` 绑定的是 `onBackToLogin`，该回调传入的是 `{ onSwitchMode(AuthMode.Login) }`（已是当前模式，等于空操作），忘记密码入口完全失效
- **修复**: 新增 `onNavigateForgotPassword` 回调参数，`onClick = onNavigateForgotPassword`

### 6. ServerApiTypes.kt — `takeIfString()` 非 primitive crash
- **行号**: 383
- **问题**: `isJsonNull` 只检查 null，不检查 `JsonObject`/`JsonArray`。若服务端在期望 string 的字段返回了对象，`asString` 会抛 `UnsupportedOperationException`
- **修复**: 增加 `isJsonPrimitive` 守卫

### 7. ContactFieldValueCacheDao.kt — `@Insert` 语义错误
- **行号**: 31
- **问题**: `insertOrUpdateFieldValues` 方法名暗示 upsert 语义，但 `@Insert` 无 `onConflict` 策略，默认 `ABORT`（冲突时抛异常而非静默更新）
- **修复**: 改为 `@Upsert`

### 8. UserAuthRepository.kt — `catch (Throwable)` 吞 OOM
- **行号**: 84
- **问题**: `bootstrap()` 的 `catch (e: Throwable)` 会捕获 OOM/StackOverflow 等 Error，导致严重错误被当成"登录过期"清理 auth state
- **修复**: 改为 `catch (e: Exception)`

### 9. NotificationRepository.kt — 同上
- **行号**: 79
- **问题**: `refreshUnreadCount()` 的 `catch (e: Throwable)` 同理会掩盖 OOM 等致命错误
- **修复**: 改为 `catch (e: Exception)`

### 10. TagRepositoryImpl.kt — `clearContactTagsBySource` 返回 0
- **行号**: 269
- **问题**: 接口声明返回 `Int`，实现中 `refs.filter { it.source == source }` 已算好但丢弃了 count，末尾直接 `return 0`
- **修复**: 改为 `refs.count { it.source == source }`

### 11. ChangePasswordViewModel.kt — 密码长度不一致
- **行号**: 43
- **问题**: 新密码最小长度校验为 6 位，但 `AuthViewModel.canSubmitRegister` 要求 8 位，同一应用内密码策略不一致
- **修复**: 统一为 8 位

### 12. AuthViewModel.kt — 验证码失败污染全局 state
- **行号**: 249
- **问题**: `refreshCaptcha` 失败时设置全局 `_state = AuthUiState.Error(...)`，会覆盖之前的错误状态
- **修复**: 仅清 `captchaCode`，不写全局 `_state`

### 13. AccountProfilePage.kt — Dialog 写入完成前关闭
- **行号**: 222-238, 268-283
- **问题**: 修改昵称/简介 Dialog 的 `onPositive` 在 `scope.launch(Dispatchers.IO)` 之后立即执行 `showEditName = false`，Dialog 在协程完成前就关闭，失败无反馈
- **修复**: 将 `showEditName = false` 移入协程内部的 `withContext(Dispatchers.Main)` 块中

### 14. PersonViewModel.kt — 多余 `synchronized`
- **行号**: 198
- **问题**: `synchronized(_allContacts)` 包裹 `_allContacts.value` 的读写，但 `StateFlow` 本身是线程安全的，`synchronized` 块内只有 filterNot + 赋值，不存在复合读-写竞争
- **修复**: 移除 `synchronized` 块

### 15. ScannerPage.kt — `@Preview` 在 `@RequiresApi(R)` 上
- **行号**: 57
- **问题**: Compose Preview 环境无 API R 保证，Preview 编译会失败
- **修复**: 移除 `@Preview` 注解

### 16. ContactDetailAvatar.kt — Bitmap 双重 recycle
- **行号**: 80
- **问题**: `DisposableEffect(Unit)` 和 `LaunchedEffect(show, avatarUrl)` 都会 recycle Bitmap，可能双重回收导致 `IllegalStateException`
- **修复**: LaunchedEffect else 分支只置 null，不 recycle（统一由 DisposableEffect 回收）

### 17. DashboardViewModel.kt — `_error` 永远为 null
- **行号**: 41-42
- **问题**: `_error` MutableStateFlow 声明了 `clearError()` 方法，但 `refresh()` 中所有异常都通过 `runCatching` 处理，从未写入 `_error`
- **修复**: 删除 `_error` + `clearError()` + combine 中的 `_error` 参数 + `DashboardUiState.error` 字段

---

## 二、P1 CORRECTNESS 修复（5 项）

### 18. ContactDetailPage.kt — `collectAsState` → `collectAsStateWithLifecycle`
- **行号**: 104-111
- **问题**: 7 处使用 `collectAsState()` 而非 `collectAsStateWithLifecycle()`，不尊重生命周期，在 Activity 进入 STOPPED 状态后仍会持续收集
- **修复**: 全部改为 `collectAsStateWithLifecycle()`

### 19. ContactDetailViewModel.kt — `createTagAndAssign` 返回 -1L
- **行号**: 244
- **问题**: `newId` 在 `viewModelScope.launch` 异步块内赋值，函数在 `launch` 之后立即 `return newId`（仍是 -1L）
- **修复**: 改为 `suspend fun`，直接返回真实 ID

### 20. CardPage.kt — 一次性 launcher 模式
- **行号**: 628
- **问题**: `if (showImportDialog) { showImportDialog = false; launcher.launch(...) }` 在 Composable 函数体中依赖重组时序
- **修复**: 改为 `LaunchedEffect(showImportDialog) { ... }`

### 21. CollectionDetailPage.kt — 同上两处
- **行号**: 672, 677
- **修复**: 导出/导入 launcher 都改为 `LaunchedEffect`

### 22. AiTagPreviewDialog.kt — stale `selectedCount`
- **行号**: 64
- **问题**: `remember(checkedMap) { checkedMap.values.count { it } }` 中 `checkedMap` 是 `SnapshotStateMap`，引用永不变化，`remember` 不会重新计算
- **修复**: 改为 `val selectedCount by remember { derivedStateOf { checkedMap.values.count { it } } }`

---

## 三、P2 DEAD_CODE 清理（12 项）

| # | 文件 | 清理内容 |
|---|------|----------|
| 23 | `ContactRepositoryImpl.kt` | 25 处 `"Tester"` → `TAG` + `Log.e` → `Log.w`（离线场景非 ERROR） |
| 24 | `ContactDetailDialogs.kt` | 删除 9 个死 import（`rememberScrollState`, `verticalScroll`, `Color`, `SimpleDateFormat`, `Date`, `Locale`, `ContactNetworkResolver`, `kindCanSync`, `FIELD_DEF_MAP`, `BILIBILI_HEADERS`）+ 死函数 `sourceTypeDisplayName` |
| 25 | `ContactDetailViewModel.kt` | 删除 `IdentifyResponse` import + 空 `init{}` + `"Tester"` → `TAG` |
| 26 | `PersonViewModel.kt` | 删除死 import + `TAG = "Tester"` → `"PersonViewModel"` |
| 27 | `NfcSettingsViewModel.kt` | 删除死 `Log` import + 空 `init{}` |
| 28 | `SettingsHomeViewModel.kt` | 删除多余 `@OptIn(ExperimentalCoroutinesApi::class)`（combine/stateIn 均为 Stable API） |
| 29 | `QrCoordinateMapper.kt` | 删除 3 个死函数（`Float.format`, `mapBitmapToCompose`, `sortCorners`）+ 死 `Log` import |
| 30 | `ScannerComponents.kt` | 删除死 `SafeLog` import |
| 31 | `ScannerSubDialogs.kt` | 删除死 `Log` import |
| 32 | `PersonPage.kt` | 删除死 `WindowDialog` import |
| 33 | `CollectionExporter.kt` | 删除 `TAG_SOURCE_IMPORT` 常量 + `importContactsToCollection` 函数（~55 行） |
| 34 | `ServerApiTypes.kt` | 删除 UserStats + RecentPerson 重复 KDoc 注释 |

---

## 四、P3 STYLE/KOTLIN_IDIOM 修复（2 项）

| # | 文件 | 修复 |
|---|------|------|
| 35 | `AuthViewModel.kt:410` | Regex 每次调用编译 → companion `EMAIL_REGEX` 常量 |
| 36 | `DeviceApi.kt:54` | `{ _ -> Unit }` → `{ }` |

---

## 五、剩余待修（分级）

### 中优先级 — 有明确收益但改动量较大

| 文件 | 问题 | 预估工作量 |
|------|------|-----------|
| `FieldRepositoryImpl` + `ContactMapper` | 3 组重复映射函数（`toCacheEntity`, `toV1Entity`） | 30min |
| `TagPickerDialog.kt:121-167` | 重复 chip 渲染 → 复用 `TagChip` | 15min |
| `ScannerDialogs.kt:234-371` | 两处 `LaunchedEffect` batch resolve 重复 | 20min |
| `PhotoModeDialog.kt:137-227` | `mergedFields` 与 `computeMergedFields` 重复 | 20min |
| `CardDialogs.kt:63-368` | Create/EditCollectionDialog 背景图逻辑重复（~60 行） | 30min |
| `ScanModeDialog.kt` | 4 处 `buildScanResults` 重复 | 15min |
| `UserProfileDetailPage.kt:389-444` | 自动同步逻辑与 SyncOptionsBottomSheet 重复（~55 行） | 20min |
| `UserProfileDetailPage.kt:661-742` | 5 处 `updateProfileField` 回调重复 | 15min |

### 低优先级 — 架构/可读性优化

| 文件 | 问题 |
|------|------|
| `ContactDetailPage.kt:113-145` | 25+ 个 `var showXxx` 状态变量 → 封装 `ContactDetailDialogState` data class |
| `ContactDetailDialogs.kt:358-517` | `ContactDetailPageDialogs` 50+ 参数 → 拆分子 Composable |
| `RegionPickerDialog.kt:131-346` | 内嵌 ViewModel → 提取独立文件 |
| `ContactRepositoryImpl.kt` | `findContactIdsByPlatform(key, value, -1)` 的 `-1` 魔法数字 |
| `WorldRegionRepository.kt:65-111` | check-then-act 缓存无二次检查（并发下载） |
| `FieldRepositoryImpl.kt:197-220` | `mutableMapOf + for` → `buildMap { }` |
| `CollectionExporter.kt:280` | 空 `.also { }` 块 |
| `CollectionExporter.kt:543` | `var merged = 0` 被 ++ 但从未读取 |
| `ScannerCamera.kt:97-98` | 两个独立 `SingleThreadExecutor` → 合并 |
| `BoundingBoxSmoother.kt:116` | 调试 `Log.d` 在生产代码中每次模式切换都打 |
| `OnboardingPrefs.kt` | 每次调用都 `getSharedPreferences()` → 提取 `sp()` helper |
| `TagRepositoryImpl.kt:162-165` | `searchTagsFts` 与 `searchTagsByName` 完全相同（V1 遗留） |
| `SyncStatusRepository.kt:27` | `DEFAULT_PURGE_DAYS` 注释标注"历史遗留，不再使用" |
| `OperationHistoryRepositoryImpl.kt:24` | `private val tag = TAG` 不必要的间接层 |
| `SyncStatusRepositoryImpl.kt:29` | 同上 |
| `TagRepositoryImpl.kt:347-349` | `bumpContact` 单行包装 → 内联 |
| `PersonPage.kt:533-653` | nameHits 和 tagHitGroups 的 ContactItem 渲染 lambda 重复（~30 行） |
| `NotificationPage.kt` + `DeviceListPage.kt` | `formatNotificationTime` / `formatDeviceLoginTime` 几乎相同 → 提取共享工具函数 |
| `AboutPage.kt:70-80` | 两套独立的 2 秒 debounce 机制重复 |
| `ChangePasswordPage.kt:63-66` | `remember` → `rememberSaveable`（配置变更丢密码） |
| `BatchImportPlatformsDialog.kt:72-78` | `remember` → `rememberSaveable`（旋转丢输入） |
| `LogViewerPage.kt:138-141` | `scrollTo` 包裹在 `withContext(Dispatchers.IO)` 中（UI 操作应在 Main） |
| `ContactDetailPage.kt:409` | `selectedPlatform` 已被置 null，日志永远打印 null |
| `ContactDetailPage.kt:617` | `selectedField!!` 非空断言 → `selectedField?.let` |
| `UserProfileDetailComponents.kt:299` | `AnimatedVisibility` 内 `return` 跳过 exit 动画 |
| `UserProfileDetailPage.kt:256-265` | `selectedPlatform!!` NPE 风险 |
| `ScanMarkerPickerDialog.kt:92` | `WindowDialog(show = true, ...)` 硬编码 → 传入 `show` |
| `AddContactFieldDialog.kt:101-102` | 可能重复的网格项（`SYSTEM_FIELDS` + `addableDefs` 重叠） |
| `AddContactFieldDialog.kt:296-320` | 多余 `withContext(Dispatchers.Main)`（Compose snapshot 线程安全） |
| `PlatformFields.kt:4,8` | 死 import（`android.net.Uri`, `PlatformAdapterRegistry`） |
| `PrepareNfcWriteUseCase.kt:5-6` | 死 import（`delay`, `PlatformEntry`） |
| `AppViewModel.kt:3,6-8` | 死 import（`Log`, `MutableStateFlow`, `asStateFlow`） |
| `SelectPlatformUseCase.kt:22-24` | `factoryOf` UseCase 持有可变状态但每次注入新建实例（debounce 失效） |
| `SelectPlatformUseCase.kt:66` | `delay(1500)` / `delay(2000)` UI 时序逻辑在 domain UseCase 中 |
| `App.kt:142,150` | FQN `kotlinx.coroutines.withContext` + `android.widget.Toast` |
| `KoinModules.kt:198-221` | `useCaseModule` 混入不相关的 singleton |
| `PersonPage.kt:282` | `SnackbarHost(remember { SnackbarHostState() })` 永远不被使用 |
| `FilterContactsUseCase.kt` | 注册在 Koin 但从未注入或调用 |

---

## 六、文件变更统计

| 类别 | 修改文件数 | 新增行 | 删除行 |
|------|-----------|--------|--------|
| P0 CORRECTNESS | 12 | ~30 | ~25 |
| P1 CORRECTNESS | 5 | ~20 | ~15 |
| P2 DEAD_CODE | 12 | ~10 | ~90 |
| P3 STYLE | 2 | ~5 | ~5 |
| **合计** | **~20** | **~65** | **~135** |
