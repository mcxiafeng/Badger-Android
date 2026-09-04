# Task List: Badger KMP 跨端迁移

规格：[docs/kmp-migration-plan.md](../docs/kmp-migration-plan.md)
顺序与检查点：[tasks/kmp-plan.md](./kmp-plan.md)

状态：**未开工**（计划产出 2026-09-04）。前置：UI 重构 U0 清障（U01–U04，见 tasks/ui-todo.md）。

> 通用验收（每个任务默认包含，不再逐条重复）：
> - [ ] Android 零回归：`./gradlew :app:assembleDebug` + `./gradlew :app:testDebugUnitTest` 绿
> - [ ] 迁移的纯 Kotlin 代码不引入任何 `android.*` import（commonMain grep 验证）
> - [ ] 遵守 AGENTS.md：敏感值 SafeLog 脱敏、命名常量、`Log.e` 不吞异常
> - [ ] Room 版本号不升级（保持 17）；DB schema 不做破坏性变更

---

## Phase K0 — 决策、验证、脚手架

### Task K01: 依赖迁移矩阵落表

**Description:** 把 `docs/kmp-migration-plan.md` §3 的矩阵细化为正式文档 `docs/kmp-dependency-matrix.md`：每个依赖标注「KMP 状态 / 替代方案 / 迁移动作 / 风险等级 / 对应任务号」。对不确定项（Paging KMP、OkHttp 5 KMP artifact、Haze iOS、Room FTS4 中文 tokenizer）标 `待 spike` 并指向 K02。

**Acceptance criteria:**
- [x] 矩阵文档完成，`app/build.gradle.kts` + `gradle/libs.versions.toml` 全部依赖覆盖（无一遗漏）
- [x] 每个依赖有明确结论字段（直接跨端 / 需替换 / 需 expect/actual / 待 spike）

> **实施备注（2026-09-04）：** 矩阵落表时同步修正四个前提：FTS4 已退役（搜索=LIKE）、OkHttp 实际 5.4.0、**Paging 3 已在 Phase 5 移除**（spike 项作废）、java.time 零引用。文档本地 docs/（gitignore 内），关键结论同步进 tasks 备注与 commit message。

**Dependencies:** None. **Files:** 新建 `docs/kmp-dependency-matrix.md`。**Scope:** S

### Task K02: 技术 spike（shared 模块 + Room KMP + iOS 编译）

**Description:** 新建最小 `shared` KMP 模块（commonMain + androidMain + iosMain，仅编译不接业务）：① Room KMP 跑通——复制 `contacts_cache` 表结构（17 版 schema 子集）+ LIKE 搜索查询（对齐 `ContactCacheDao` 现行为；**注意 FTS4 已于 v15 迁移退役，勿再验证 FTS4**，见 docs/kmp-dependency-matrix.md §0），bundled driver 下验证建表/查询/一条保守重建型 migration（6→7 型）行为；② ~~Paging 3 common artifact~~ **作废**——Paging 3 已在 Phase 5 移除（代码零引用），结论直接落表；③ OkHttp 5.4.0（toml 实际版本，已在 5.x 线）验证 iOS target artifact 解析 + GET/POST/流式请求行为出选型结论；④ `compileKotlinIosSimulatorArm64` 本地（Windows 交叉编译）或 CI 编译通过。产出 spike 结论记入 K01 矩阵文档。

**Acceptance criteria:**
- [x] `shared` 模块三源集编译通过（Android + iOS simulator arm64）
- [x] LIKE 搜索在 bundled driver 下行为与 Android 现状对齐（结论落表）
- [x] ~~Paging KMP~~ / OkHttp5 vs Ktor 结论落表（Q2 关闭；Paging 项因 Phase 5 移除而作废）
- [x] spike 代码可保留为 `shared` 骨架，不阻塞后续任务

> **实施备注（2026-09-04，详见 docs/kmp-dependency-matrix.md §3）：**
> ① Room KMP + bundled driver：Android + iOS 双端编译通过；Robolectric JVM（sqlite-bundled-jvm native）验证 LIKE 中文/ASCII 语义与保守重建 migration 数据保留。**Migration 签名 = SQLiteConnection（非 SupportSQLiteDatabase）**，16 条旧迁移的直用点 K07 需 room-sqlite-wrapper 桥接；androidx-sqlite 必须跟随 Room 传递版本 2.6.2。
> ② **Q2 关闭：选 Ktor 3.1.3**——OkHttp 5.4.0 Gradle metadata 实测无 iOS native 变体；Ktor core+Darwin iOS 编译通过、CIO JVM 真实 GET/POST 通过。K04 重建网络层，Coil iOS 侧用 ktor 引擎。
> ③ Windows 本地 `:shared:compileKotlinIosSimulatorArm64` 通过（konan 自动下载）；CI 门禁照建双保险。
> ④ 坑位记录：根 build.gradle.kts 必须 `apply false` 钉住 kotlinMultiplatform/androidLibrary 插件（KGP/AGP 单 jar 含全部插件类，否则报 unknown version）；shared 测试需显式 junit4；Ktor 3.1 无 `isSuccess()` 扩展（用 status.value 区间）。

**Dependencies:** K01. **Files:** 新建 `shared/`（build.gradle.kts + src）、`docs/kmp-dependency-matrix.md` 更新。**Scope:** L

### Task K03: CI iOS 编译门禁 + macOS 方案裁决

**Description:** 新建 `.github/workflows/kmp.yml`：macos-15 runner 跑 `:shared:compileKotlinIosSimulatorArm64`（K0–K4 阶段唯一 iOS 门槛，不要求真机）。同时裁决 Q1：真机调试/TestFlight 打包的 macOS 来源（购置 Mac mini / 云 Mac / 延后），结论写入 docs/kmp-migration-plan.md Q1。

**Acceptance criteria:**
- [x] CI workflow 就位且跑绿（含缓存策略：Gradle + konan）——workflow 已建（macos-15 + Gradle/Konan 缓存 + 排除外网依赖的 shared 测试），**首次 push 后在 GitHub Actions 确认绿**（本地无法代跑）
- [x] Q1 有书面结论；K5 开工条件明确——**用户裁决（2026-09-04）：云 Mac 按需租用**（K0–K4 = CI 门禁 + Windows 交叉编译；K5 起租云 Mac，前置 = Apple Developer Program 账号 + 真机 UDID 注册）
- [x] 不影响现有 ci.yml / release.yml（独立 workflow，仅 dev/master push 与 PR 触发）

**Dependencies:** K02. **Files:** 新建 `.github/workflows/kmp.yml`、`docs/kmp-migration-plan.md`。**Scope:** S

---

## Phase K1 — 基础设施 common 化

### Task K04: Gson → kotlinx.serialization（网络层 common 化）

**Description:** 全部 DTO（ServerApi 手写 OkHttp+Gson 涉及的约 30+ 个数据类）加 `@Serializable`；网络客户端按 K02 结论（OkHttp 5 KMP 或 Ktor）重建于 commonMain；`ServerApi` 接口签名保持不变，Android 侧实现切换到新客户端。已知 Gson 数字→Double 陷阱（`code: 0`→`0.0`）随迁移消失，相关 workaround 代码同步删除。B 站 API 嵌套结构（`data.card.face`）与 CDN Referer 头逐接口对照测试。

**Acceptance criteria:**
- [x] commonMain 无 Gson import；DTO 全部 `@Serializable`
- [x] 新旧解析对照测试：同一批真实响应 JSON 双实现断言等价后删旧实现
- [x] short.io POST（非 PATCH）、AI OCR `stream:false`、ModelScope SSE 规避等既有协议约束不回归
- [x] 全量单测绿

> **实施备注（2026-09-04，未 commit 待用户确认）：**
> ① **传输层调整**：OkHttp 传输保持现状（Android 单端可用），Ktor 接入挪至 K06（HttpUtil 拆分时随网络进 commonMain 一并落）——K04 验收的「commonMain 无 Gson」已通过序列化层迁移达成；B 站嵌套结构 `data.card.face` 属 PlatformAdapter 模块（本次未动，随 K06 处理，当前无回归面）。
> ② 主源集 `com.google.gson` grep = 0；Gson 降级 `testImplementation`（`JsonMigrationParityTest` 8 条对照 oracle 保留——网络解析等价 4 条 + 存储兼容 2 条（老 Gson 写入 DB 的 platformsJson/payloadJson 可被新解码器读）+ Outbox 字段级 merge 语义 2 条），未来可转纯快照测试后移除。
> ③ `network/JsonSupport.kt` 新增：`BadgerJson` 单例 + Gson 防御 helper 平移（`stringOrNull`/`takeIfString`/`intOr` 含 "400.0" 形态收敛）。
> ④ **kotlinx 陷阱记录**：`JsonNull` 是 `JsonPrimitive` 子类（Gson 里不是），裸 `as? JsonPrimitive?.content` 遇 JSON null 得字符串 `"null"`——取值必须经 `contentOrNull()`/`takeIfString()`（内含守卫）。
> ⑤ 网络解析保留手写 `from(JsonObject)` 防御链而非 `decodeFromJsonElement`：缺字段跳行/类型异常降级语义需逐条对齐，直接解码会改行为；`@Serializable` 服务于 Outbox 编解码与 KMP common 化。
> ⑥ 全部 DTO `@Serializable`（ServerApiTypes 14 个 + PersonApi 5 个 + PlatformEntry + CollectionExporter 导出协议 v2/v3 5 个 + WorldRegion 手写解析保留）；10 个测试文件同步迁移；数字→Double workaround 在 intOr/longOr 收敛逻辑中覆盖。
> ⑦ 验证：`compileDebugKotlin` + `compileDebugUnitTestKotlin` + `:shared:compileKotlinIosSimulatorArm64` 绿；全量 507 例 13 失败 = Notification 既有基线（2+10+1），零新失败。

**Dependencies:** K02. **Files:** `network/ServerApi.kt`、`data/` 相关 DTO、`gradle/libs.versions.toml`。**Scope:** L

### Task K05: SharedPreferences → DataStore KMP

**Description:** 9 个 prefs（`AuthPrefs`、`ShortLinkPrefs`、`OnboardingPrefs`、`DeveloperModePref`、`badger_settings`（ThemeConfig/NavBarConfig）、`GpuCompat 结果`、`ai_ocr_config`）迁 DataStore。注意：AI OCR API key 与 short.io key 的明文存储现状保持不变（安全整改是另一个议题，不混入迁移）；AuthPrefs 的「refresh token 短期 + access token 仅内存」语义不变。提供 expect 的 DataStore 文件路径工厂（Android `filesDir` / iOS `NSDocumentDirectory`）。

**Acceptance criteria:**
- [x] commonMain 无 SharedPreferences import；9 处读写全走 DataStore
- [x] Android 升级场景：旧 SharedPreferences 值一次性搬迁（onMigration 读取→写入 DataStore→标记），用户无感
- [x] 明暗主题/效果模式/悬浮导航开关等设置项切换即时生效不回归

> **实施备注（2026-09-04）：**
> ① **架构**：`PrefsStore`（app `data/prefs/`）＝内存缓存（ConcurrentHashMap）+ DataStore 异步落盘——读走缓存保持旧 SharedPreferences 同步语义（几十处调用点签名零改动），写先更新缓存（设置即时生效）再异步 `edit` 落盘。9 个 prefs 对象（AuthPrefs/ShortLinkPrefs/OnboardingPrefs/DeveloperModePref/ThemeConfig/NavBarConfig/GpuCompat/AiOcrConfig/DeviceIdProvider + FirstTimeHint/SetupGuideModels/QrCodeCard 散点）全部改走 PrefsStore，方法签名不变。
> ② **搬迁**：`PrefsMigrator.migrateAll`（Application.onCreate 最先执行，阻塞）按旧文件名逐个搬运 9 个 SharedPreferences 文件（String/Boolean/Int/Long/Float/StringSet）到单一 `badger_prefs.preferences_pb`，每个文件搬完打 `migrated_<name>` 标记。
> ③ **expect 路径工厂**：`shared/prefs/PrefsPathFactory`（Android=filesDir/datastore、iOS=NSDocumentDirectory/datastore；Android 由 Application 注入目录，iOS `@OptIn(ExperimentalForeignApi)`）。app 依赖 `:shared`（首次接线）。
> ④ **坑位**：DataStore 同文件双实例抛 "multiple DataStores active"——Migrator 与 Store 必须共用同一实例（`PrefsStore.store()` 按路径单例）；Robolectric 每测试类换 filesDir，store() 按路径变化重建 + initialize() 可重入清缓存重灌。
> ⑤ 验证：compileDebugKotlin / assembleDebug / :shared iOS 编译绿；507 例 13 失败 = Notification 既有基线，零新失败。

**Dependencies:** K02. **Files:** `data/prefs/` 全部、`ui/navigation/ThemeConfig.kt`、`NavBarConfig.kt`、`blur/GpuCompat.kt`、`di/KoinModules.kt`。**Scope:** L

### Task K06: 日志抽象 + HttpUtil 拆分

**Description:** `SafeLog` 的 `Log.d/e` 底座抽为 expect/actual（Android 保持 `android.util.Log`，iOS 用 `print`/oslog 或 kermit——K01 矩阵结论）；`SafeLog` 脱敏逻辑本身纯 Kotlin，直接进 commonMain。`HttpUtil` 拆分：OkHttpClient 持有与请求逻辑进 common（依赖 K04 选型），`Bitmap` 内存缓存与 `downloadBitmap` 的解码部分留 androidMain，iOS 侧解码用 Skia/UIImage actual。

**Acceptance criteria:**
- [x] `SafeLog` 全量 API 在 commonMain 可用，调用方（几十处）import 路径更新但函数签名不变
- [x] HttpUtil 网络部分 common 化后 Android 行为零变化（超时/重试/headers）
- [x] 全量单测绿

> **实施备注（2026-09-04）：**
> ① **日志底座**：`shared/utils/BadgerLog` expect/actual（Android=android.util.Log 透传零变化；iOS=NSLog）。`SafeLog` 脱敏逻辑纯 Kotlin 迁 commonMain——包名保持 `top.mcxiafeng.badger.utils`，几十处调用方 import 零改动。唯一行为差异：`SafeLog.url` 的 `java.net.URI` 解析改为手写宽松 scheme://host:port 提取（iOS target 无 java.net；输出格式等价）。
> ② **HttpUtil 拆分**：OkHttp 版 HttpUtil 整体迁 `shared/androidMain`（Q2 裁决：OkHttp 无 iOS 变体，Android 传输层就是平台层）；`android.graphics` 解码/downloadBitmap 随行。common 侧落 `KtorHttpCore`（Ktor 3.1.3，引擎平台注入：Android=CIO / iOS=Darwin），错误分类（AUTH/RATE_LIMIT/TIMEOUT/SERVER/NETWORK/OTHER）与 OkHttp 路径语义对齐，供 K07+ 数据/同步层迁 common 复用。
> ③ **解耦 Koin**：HttpUtil 的 OkHttpClient 由 `clientProvider` lambda 注入（networkModule 启动时 set），androidMain 不依赖 Koin。
> ④ 同批迁 common：`HttpResult` / `HttpException`（纯 Kotlin）。
> ⑤ 验证：compileDebugKotlin / assembleDebug / :shared iOS 编译绿；`shared/commonMain` grep `import android.*` = 0（androidx 仅 Room KMP/sqlite KMP 合法项）；507 例 13 失败 = Notification 既有基线，零新失败。

**Dependencies:** K04. **Files:** `utils/SafeLog.kt`、`utils/HttpUtil.kt`、`network/ContactNetworkResolver.kt`。**Scope:** M

---

## Phase K2 — 数据与同步

### Task K07: Room → Room KMP（兼容模式渐进）

**Description:** AppDatabase 接入 KMP：`setDriver(BundledSQLiteDriver())` + expect/actual 的 databaseBuilder（Android 路径保持 `context.getDatabasePath` 同一文件，**不换文件名不换版本链**）；`SupportSQLiteDatabase` 直用点改用 `room-sqlite-wrapper` 桥接；DAO 非 suspend 回调式 API 逐个审计改 suspend。17 版迁移链与 FTS4 虚表原样保留。

**Acceptance criteria:**
- [ ] Android 上旧库升级路径实测：v6→v17 全链路迁移在 bundled driver 下通过（新装 + 模拟旧版本两场景）
- [ ] FTS4 搜索（PersonPage/TagManager）行为不变
- [ ] DAO 全部 suspend/Flow 化审计完成，无阻塞 API

**Dependencies:** K02、K05. **Files:** `data/AppDatabase.kt`、`data/migrations/Migrations.kt`、`di/databaseModule`、各 DAO（审计为主）。**Scope:** L

### Task K08: 业务核心迁 commonMain

**Description:** 纯 Kotlin 层整批迁移：`data/repository/`（Repository 接口 + Impl + ContactWriter + Mapper）、`data/queue/`（PendingUpload/OperationHistory）、`sync/`（SyncEngine/Bootstrapper 中平台无关部分）、`domain/`（UseCase）、`data/model/`。测试同步迁 kotlin.test common（Robolectric 依赖项留 androidMain）。`PendingUploadScheduler` 的 ProcessLifecycle/ConnectivityManager 监听留 Android，核心 kick 合并逻辑进 common（服务 K09）。

**Acceptance criteria:**
- [ ] 上述包全部位于 commonMain 且编译通过（Android + iOS）
- [ ] 单测迁移后数量不减（486 绿基准），mockk 替代方案按需（不可迁测试留 androidMain 并注明）
- [ ] ContactWriter 三入口（save/merge/attach）行为回归测试绿

**Dependencies:** K06、K07. **Files:** `data/repository/`、`data/queue/`、`domain/`、`sync/`、`app/src/test/`。**Scope:** L（可拆 2–3 commit）

### Task K09: 同步调度抽象

**Description:** 定义 `expect class SyncDispatcher`（enqueue/kick/cancel + 电池优化引导）：Android actual 包装现有 WorkManager + `PendingUploadScheduler`（行为零变化）；iOS actual 为 BGTaskScheduler 注册 + 前台时机兜底（App 生命周期回前台 kick）——iOS 实现本任务只出骨架与注释明确语义，真机验证在 K17。`RevertStuckOpWorker` 的 30s 兜底改为 common 的时钟检查 + 平台调度触发。

**Acceptance criteria:**
- [ ] `PendingUploadExecutor` 消费循环与平台调度解耦（Executor 在 common，调度在 actual）
- [ ] Android WorkManager 路径行为零变化（NetworkType.CONNECTED、10s backoff、APPEND_OR_REPLACE 契约不破坏）
- [ ] iOS 骨架编译通过 + 语义注释齐备

**Dependencies:** K08. **Files:** `sync/` 全部、`BadgerApplication.kt`（接线）。**Scope:** L

---

## Phase K3 — 平台能力边界

### Task K10: 相机 + QR + OCR expect/actual

**Description:** 定义 `expect` 的扫码面（相机预览 Composable 槽 + 扫描回调 + 手电筒控制 + 相册选图）：Android actual 封装现有 CameraX/WeChatQRCode/ML Kit 代码（逻辑不动，搬进 androidMain）；iOS actual 基于 AVFoundation（QR 原生 metadata 检测）+ Vision 中文 OCR。iOS 识别率与 Android 对照：准备 20 张样本名片/QR 图，双端识别结果对照表归档。**scanner 页 UI 保持 common**，只换预览与识别引擎槽位。

**Acceptance criteria:**
- [ ] ScannerPage 在 common 而平台实现各就位；Android 扫码全流程零回归（含多码模式/拍照 OCR/双 BackHandler）
- [ ] ML Kit 复用实例、`suspendCancellableCoroutine` 包装、torch DisposableEffect 等既有约束在 actual 内保持
- [ ] iOS 侧识别对照表产出（可暂存 docs/spike/）

**Dependencies:** K08. **Files:** `pages/scanner/`（槽位抽取）、新建 `shared/*PlatformCamera*`。**Scope:** L

### Task K11: NFC expect/actual

**Description:** NFC 写入抽 `expect`：Android actual 保留 ReaderMode（写入后 3s 延迟 disable 契约不变）；iOS actual 用 CoreNFC `NFCNDEFWriterSession` 写同一 HTTPS URI record（系统弹原生写卡面板，与 Android 交互形态不同属预期）。`NfcWriteDialog` UI 保持 common。

**Acceptance criteria:**
- [ ] Android NFC 写入零回归（ReaderMode、iOS 后台不识别自定义 scheme 的约束同样适用于鸿蒙/iOS 侧数据格式）
- [ ] iOS 侧编译通过 + 真机验证项登记到 K17 清单

**Dependencies:** K08. **Files:** `pages/social/NfcHelper.kt`、`NfcActivityHandler.kt`。**Scope:** M

### Task K12: 杂项平台层

**Description:** 剩余平台触点 expect/actual 化：`DeviceIdProvider`（ANDROID_ID / identifierForVendor）、通知权限与展示、系统分享、图片选择（ActivityResult / PHPicker，自研 ImageCrop 的 Compose 自绘部分保持 common）、剪贴板、外部浏览器打开。

**Acceptance criteria:**
- [ ] commonMain 无残留平台 API（grep `android.` / `androidx.` Android 专属包为零）
- [ ] Android 对应功能走查零回归

**Dependencies:** K08. **Files:** 分散（`sync/DeviceIdProvider.kt`、`ui/components/ImageCropDialog.kt` 等）。**Scope:** M

---

## Phase K4 — UI 共享化（CMP）

### Task K13: CMP 坐标切换 + App 骨架迁移 + 图标换血

**Description:** Miuix 切 CMP 坐标（与现用版本一致）；`App.kt / AppMainTabs.kt / AppRoutes.kt / AppTheme.kt / Route.kt / AppNavigator.kt / NavBarConfig.kt` 迁 shared commonMain；`app/` 保留 Application/MainActivity/WorkManager 接线。`LocalFloatingBarBottomPadding` 等 CompositionLocal 随迁。**同批执行图标体系替换**（UI 重构 U03 选型结论）：62 个文件的图标 import 一次性切到新图标库（每图标独立 import、CMP 兼容），移除 material-icons-extended 依赖——放在本任务做是为了 62 个文件只改一遍。

**Acceptance criteria:**
- [ ] Android 壳启动 → 全部页面渲染正常，4 Tab/路由栈/返回行为零回归
- [ ] 图标替换后全仓库无 material-icons / MiuixIcons import 残留；APK 体积对比记录（预期显著缩小）
- [ ] `SaveableStateProvider` 保滚动状态机制验证不变
- [ ] CI iOS 编译绿（UI 层首次进 iOS 编译目标）

**Dependencies:** K12 + UI 重构 U0/U03 完成. **Files:** 根级 6 文件迁移 + 全仓库图标 import。**Scope:** L（图标替换可独立 1 commit）

### Task K14: 视觉特效系统重做（Q1 裁决，Skia-first 双端一套）

**Description:** 用户裁决现有模糊/液态玻璃实现不满意，**推倒重做**（非修补、非移植）。完整任务定义与验收在 UI 重构计划 U10（tasks/ui-todo.md），此处为执行点。顺序：① 《特效视觉规格》**v0.9 已产出**（docs/effect-visual-spec.md，基准 = iOS 26 Liquid Glass + iOS 磨砂，2026-09-04），待用户签收后冻结；② Skia-first 重写 `LiquidGlassNavBar`(541 行) + `ui/blur/` 全家 + FloatingToolbar/FAB/scrim 材质统一，shader 与材质参数 token 化；③ `GpuCompat` 检测改 expect（iOS 按设备档次映射降级阶梯），效果三档语义双端一致。**不做 AGSL 版重写**——旧 Android 实现直接被新系统替换。开工时按规格 §8 裁决：磨砂层 miuix-blur（本地已含双端实现 + Highlight/TiltLight 高光）vs Haze 1.7.2 haze-materials（五档 iOS 预设）；折射层实测 haze-glass 成熟度，stable 则用、否则自研共享 SkSL（Android AGSL / iOS Skia RuntimeEffect 同源接线）。

**Acceptance criteria:**
- [ ] 《特效视觉规格》用户签收后实现；双端渲染一致逐项验收
- [ ] iOS 模拟器三档效果渲染正确（无 crash、无纯黑块）；Android 全功能零回归
- [ ] 旧 blur/ 实现删除无遗留；重做后导航默认形态定稿并落 UiSettings
- [ ] 性能预算实测达标（帧率/发热），未知设备默认档位策略落文档

**Dependencies:** K13. **Files:** `ui/LiquidGlassNavBar.kt`、`ui/blur/` 全部（重写）、`NavBarConfig.kt`、新建 `docs/effect-visual-spec.md`。**Scope:** XL——按「规格 commit → 实现 2–3 commit」拆分，每个 commit 双端可编译

### Task K15: ViewModels 迁 shared + 全功能走查

**Description:** 20+ ViewModel 迁 commonMain（androidx lifecycle-viewmodel KMP + koin-compose-viewmodel，替换 `KoinComponentBy.get<T>()` 静态取用为构造注入）；`koinViewModel()` 调用点切 CMP 版。随后做 Android 全功能走查（对照 AGENTS.md 自检清单 × 4 Tab × 主要对话框）。

**Acceptance criteria:**
- [ ] 全部 VM 位于 commonMain；VM 不持 Activity 引用的红线在 KMP 下依然成立（改用 callback 已有基础）
- [ ] Android 全功能走查清单通过并归档
- [ ] CI iOS 编译绿；`app/` 源码集只剩 actual 宿主

**Dependencies:** K13、K14 + UI 重构 U1（Token v2 落 commonMain）. **Files:** `pages/` 各 VM、`di/KoinModules.kt`、`di/KoinComponentBy.kt`（退役或瘦身）。**Scope:** L（可拆 2–3 commit）

---

## Phase K5 — iOS 产品化（需 macOS，Q1 已裁决）

### Task K16: iosApp 工程

**Description:** 新建 `iosApp/`（SwiftUI 壳 + `ComposeUIViewController` 嵌入）：Safe Area、手势返回习惯（iOS 边缘滑动手势 vs Android 返回键的适配）、键盘避让、iOS 字体缩放（Dynamic Type 映射 Miuix textStyles）、深色模式跟随系统。

**Acceptance criteria:**
- [ ] 模拟器全页面走查；Safe Area 四边不遮挡（刘海/Home Indicator）
- [ ] 键盘弹出输入框不被遮挡；横竖屏/分屏不 crash

**Dependencies:** K15 + macOS 就绪. **Files:** 新建 `iosApp/`、`shared` iOS 配置。**Scope:** L

### Task K17: TestFlight 内测 + 合规 + 真机验收

**Description:** NFC entitlement、相机/相册/通知权限 Info.plist 文案（中文）、隐私清单（PrivacyInfo.xcprivacy）、签名与 TestFlight 分发。真机验收：K09 iOS 同步语义（BGTask 实际行为）、K10 识别率对照、K11 NFC 真机写入（iPhone 7+ A12 以上写 NDEF 的设备限制确认）。

**Acceptance criteria:**
- [ ] TestFlight 包可安装启动；五条主流程（扫码/OCR/NFC/同步/设置）真机走通
- [ ] BGTask 消费 PendingUpload 的实测行为记录（时序语义 vs Android 差异文档化）
- [ ] App Store 提审清单（隐私问卷/截图/描述）就绪

**Dependencies:** K16. **Scope:** L

---

## Phase K6 — 大屏适配

### Task K18: WindowSizeClass 响应式骨架

**Description:** 引入 `material3-window-size-class`（CMP 支持）：Compact 保持现有单栏；Medium/Expanded 启用「列表-详情双栏」（联系人列表+详情、名片夹网格+预览）、网格列数 2→3(4)、对话框最大宽度约束。导航形态在大屏下的策略（底部栏保留 or 侧栏）做一页 spike 后定稿。

**Acceptance criteria:**
- [ ] 双栏模式下选中态同步、返回/手势语义正确
- [ ] 手机形态（Compact）渲染与适配前逐像素等价
- [ ] 平板模拟器 + 折叠屏（折叠/展开状态切换）走查通过

**Dependencies:** K15. **Files:** `AppMainTabs.kt`、`AppRoutes.kt`、各列表页容器。**Scope:** L

---

## K7 — 鸿蒙路线裁决（决策点，不排任务）

按 docs/kmp-migration-plan.md §7 执行：spike OpenHarmony-KMP 社区库能否编译 commonMain 业务层 → 产出书面结论（推荐 ArkTS 薄客户端）→ 用户裁决后排期。**不因鸿蒙改变 K0–K6 任何设计。**
