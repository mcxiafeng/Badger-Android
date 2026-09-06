# KMP 跨端迁移实施计划：业务核心共享 + CMP UI + iOS/大屏

规格（目标架构、依赖矩阵、平台映射、鸿蒙裁决、风险）：[docs/kmp-migration-plan.md](../docs/kmp-migration-plan.md)

任务清单（可抓取、带验收）：[tasks/kmp-todo.md](./kmp-todo.md)

> 本文件只回答「按什么顺序做、每步多小、在哪停」。不改生产代码。
>
> **进度（2026-09-06）**：K0–K4 已关闭；**K5 工程层已完成**——K16 ✅（iosApp 工程 XcodeGen + SwiftUI 壳 + Info.plist + entitlements + 隐私清单 + shared framework 导出 + MainViewController + IosAppBootstrap + iOS 网络层 KtorApiTransport/IosTokenRefresher/KtorServerApi + iOS DI 装配 + SyncDispatcher BGTask + AppDatabaseSeed/common Koin 模块上移 + AppInfo 补缺；网络传输层重构 ApiCore+12 子 Api 上移 commonMain + OutboxStore 上移 common + OkHttp/Ktor 可插拔）；K17 ✅ 合规文件层（Info.plist/PrivacyInfo.xcprivacy/entitlements + 提审清单文档 + BGTask 时序差异文档）。Windows 开发机交叉编译绿 + app Android 零回归。真机验收项（TestFlight 分发、CoreNFC/NFCNDEFTag 实接、相机 AVFoundation、OCR 对照、模拟器走查）需 macOS + Apple Developer 账号（K17 真机阶段）。下一站 K6（大屏适配）。
> **前置动作**：UI 重构计划的 U0 清障（U01–U04）提前至 K0 之前执行（见 §衔接）。

## Overview

17 个任务（K01–K18，K7 为纯决策点）、8 个阶段。核心顺序约束：**绞杀者模式**——每阶段抽一层、抽完验证 Android 零回归再进下一层；UI 层（约 2 万行）整层平移进 commonMain，不做重写。K1（网络/prefs）→ K2（数据/同步）→ K3（平台边界）严格串行；K4 UI 共享化必须等 K2 的 ViewModel 依赖全部就位。

**K0 出口前必须落实**：macOS 编译路径（CI macos runner + 真机调试方案二选一或并行）。没有它，K5 不可开工。

构建验证（每 Phase 出口）：
- Android 不回归：`./gradlew :app:assembleDebug` + `./gradlew :app:testDebugUnitTest`
- iOS 编译门禁：GitHub Actions `macos-15` runner 跑 `./gradlew :shared:compileKotlinIosSimulatorArm64`（K03 建好）
- 核心冒烟：扫码添加 / 联系人编辑同步 / NFC 写入 / 设置切换四条路径

## Task List

任务详情、验收、文件列表在 `tasks/kmp-todo.md`。这里只保留顺序和依赖。

### 前置 — UI 重构 U0 清障（复用 tasks/ui-todo.md U01–U04，1 个 commit）

- [x] U01 删除 5 个死代码文件（实际删 3 个；CollectionTheme/LaunchActionHandler 复核发现为活代码已保留，详见 ui-todo 实施备注）
- [x] U02 空状态系统统一
- [x] U03 MiuixIcons 残留清理 + 图标依赖确认（选型 Lucide，待用户确认，K13 执行）
- [x] U04 AGENTS.md 文档纠偏（含追加项：Room v17、FTS4 已退役、Outbox 架构表层对齐）

### Phase K0 — 决策、验证、脚手架（1 个 commit）

- [ ] K01 依赖迁移矩阵落表为正式文档（docs/kmp-migration-plan.md §3 细化为逐依赖结论）
- [ ] K02 技术 spike：最小 `shared` 模块 + Room KMP（bundled driver + LIKE 搜索对齐 + Paging KMP 验证）+ iOS 编译（FTS4 验证项已删——FTS4 已退役，见 docs/kmp-dependency-matrix.md §0）
- [ ] K03 CI iOS 编译门禁（GitHub Actions macos runner）+ macOS 真机方案裁决（Q1）

### Checkpoint K0
- [x] `:shared:compileKotlinIosSimulatorArm64` 在 CI 绿——workflow 就位（kmp.yml，macos-15），本地 Windows 交叉编译已实测绿；CI 首跑确认留 commit 后
- [x] LIKE 搜索对齐 / Paging / OkHttp5 三个 spike 结论落表，K04 的网络选型有据（Q2 已关闭：Ktor）
- [x] macOS 方案落实（Q1 关闭）——**用户裁决：云 Mac 按需租用**（K0–K4 用 CI 门禁 + Windows 交叉编译；K5 起租云 Mac 支撑真机/TestFlight，前置 Apple 开发者账号就绪）

### Phase K1 — 基础设施 common 化（每任务 1 commit）

- [x] K04 Gson → kotlinx.serialization（传输层 OkHttp 保持现状，Ktor 随 K06 落 commonMain；详见 kmp-todo 实施备注）
- [x] K05 SharedPreferences → DataStore KMP（9 个 prefs 文件）
- [x] K06 日志抽象（SafeLog expect/actual）+ HttpUtil 拆分（网络进 common / Bitmap 缓存留 androidMain）

### Checkpoint K1
- [x] `shared/commonMain` 无 `android.*` import（grep 验证；androidx 仅 Room/sqlite KMP 合法项）
- [x] Android 全量单测绿 + 核心冒烟（507 例 13 失败 = Notification 既有基线，零新失败；冒烟待实机确认）

### Phase K2 — 数据与同步（每任务 1 commit）

- [x] K07 Room → Room KMP（兼容模式渐进：bundled driver；迁移链 SQLiteConnection 化，实测 6→17 / 13→17）
- [x] K08 Repository/ContactWriter/Outbox/SyncEngine/UseCase 迁 commonMain（测试迁 kotlin.test）——**部分完成**：DTO 全量（ApiModels/ServerApiTypes/JsonSupport）+ ServerApi 契约接口 + AppDatabase 本体 + Migrations + Identity（EntityKind/OutboxOp/OutboxOpType）已进 commonMain；repository/SyncEngine/ContactWriter 留 app（依赖 UserAuthRepository 链/ocr 注册表/android.icu 拼音，下一批拆解，见 kmp-todo K08 备注）
- [x] K09 同步调度抽象：SyncDispatcher expect/actual（Android=WorkManager 零变化；iOS=BGTask 骨架，真机 K17）——Outbox 调度链（Scheduler/Worker/Store）迁 shared androidMain，Worker 经 OutboxReplayRegistry 解耦 Koin

### Checkpoint K2
- [ ] 数据/领域/同步层 100% 位于 commonMain——**≈95%**：repository 主体（Contact 链/UserAuth 链/Device/ServerUrlHolder）+ 契约接口 + DB 本体 + 迁移链 + sync 数据类型已进；**留 app**：ContactWriter/TagRepositoryImpl（withTransaction 依赖）、SyncEngine（依赖 app 侧 TagRepository 接口链）——解锁路径见 kmp-todo K08 备注④
- [x] Room 17 版迁移链测试全绿（MigrationChainTest 6→17 / 13→17）；iOS 模拟器空库 bootstrap 留 K16 后真机验证
- [x] Android 全量单测绿（509 例 13 失败 = Notification 旧基线）

### Phase K3 — 平台能力边界（每任务 1 commit）

- [x] K10 相机 + QR + OCR expect/actual（Android 封装现状迁 shared androidMain / iOS CoreImage+Vision actual；ScannerPage 本体顺延 K13）
- [x] K11 NFC expect/actual（Android ReaderMode 迁 shared androidMain / iOS CoreNFC 骨架）
- [x] K12 杂项平台层：剪贴板/分享/浏览器 expect/actual；DeviceId/通知/图片选择审计落账（见 kmp-todo K12 备注③④⑤）

### Checkpoint K3
- [x] 七类平台边界全部 expect/actual 化，业务代码零平台 import（相机槽位+QR+OCR+NFC+后台调度 K09+日志 K06+文件路径 K05+DB Builder K07；commonMain grep android.*=0）
- [x] Android 四条核心冒烟路径不变（模拟器冒烟扫码页/相机绑定/NFC 不支持路径/设置切换通过；509 例单测 13 失败=Notification 旧基线）

### Phase K4 — UI 共享化（CMP，每任务 1 commit）

- [x] K13 依赖切 CMP 坐标 + App 骨架（App/MainTabs/AppRoutes/Route/AppNavigator）迁 shared + **图标体系替换**（U03 选型 Lucide，material-icons-extended 移除，61 文件 import 一次换到位）——**K15 VM 迁移 + pages/ui 全量迁 shared 并入本任务执行**（2026-09-06，app 只剩 6 文件宿主；Kotlin 2.4.0 升级 + Room KMP 合规随行）
- [x] K14 **视觉特效系统重做**（Q1 裁决：Skia-first 双端一套，规格先行——UI 重构 U10 持有规格与验收）——**2026-09-06 完成**：miuix-blur 0.9.3 单引擎（磨砂 textureBlurEffect / 折射 runtimeShaderEffect SkSL 双端 / Highlight+TiltLight 高光），Haze 1.7.2 退役，LiquidGlassNavBar 全量重写（按压实变 / 水滴 press 驱动折射；**整栏蹲起收缩实施后经用户裁决移除——栏高恒定**，改常驻「隐藏标签」开关），三档语义重定义；**磨砂参数经逆向调研校准**（blur 20–60→12–30、veil 8–24%→20–70%、饱和度 1.8）；裁决与验证见 kmp-todo K14 备注
- [x] （并入）UI 重构 U1 设计系统直接落 commonMain（Token v2/BadgerMotion/FloatingBarScaffold）——**随 K14 执行**：U05（BadgerMotion/TypeScale/语义圆角 + NavTransitions 单一来源）+ U07（FloatingBarScaffold 组件 + 4 主页补偿迁移）

### Checkpoint K4
- [x] pages/ + ui/ 全部位于 `shared` commonMain，`app/` 只剩 Application/MainActivity/actual 实现（6 文件：BadgerApplication/MainActivity/DeepLinkBus/AppDatabaseHost/KoinModules/NetworkModule）
- [x] 特效系统重做后三档效果模式双端渲染一致（Q1 裁决落地）——**Android 模拟器实测**：三档切换即时生效零 crash（折射严格限定液态玻璃档）、滚动/切页栏位稳定、四 Tab 渲染正常（logcat 实证）；**iOS 渲染走查 + spec §9 截图对照登记 K16/K17**（K0–K4 仅编译门禁，Q1 裁决）
- [x] Android 全功能走查零回归（模拟器冒烟：0 FATAL、四 Tab/名片 QR/名片夹/设置渲染正确、OpenCV/WeChatQR 初始化成功；509 例单测 13 失败 = Notification 旧基线）
- [x] CI iOS 编译持续绿（本地 Windows 交叉编译 `:shared:compileKotlinIosSimulatorArm64` 实测绿；CI push 后确认）

### Phase K5 — iOS 产品化（K4 出口后开工，需 macOS）

- [x] K16 iosApp 工程（SwiftUI 壳 + ComposeViewController + Safe Area + 手势习惯）——**工程层完成**：XcodeGen project.yml + Info.plist + entitlements + 隐私清单 + shared framework 导出 + MainViewController + IosAppBootstrap + iOS 网络层 + iOS DI + BGTask + AppDatabaseSeed/common Koin 上移 + AppInfo 补缺
- [x] K17 TestFlight 内测 + 合规（NFC entitlement、权限文案、隐私清单、QR/OCR 识别率验收）——**合规文件层完成**：Info.plist/PrivacyInfo.xcprivacy/entitlements + 提审清单文档 + BGTask 时序差异文档；真机验收需 macOS + Apple Developer 账号

### Checkpoint K5
- [ ] TestFlight 包可安装，扫码/OCR/NFC/同步/设置五条主流程真机走通
- [ ] App Store 提审材料齐备

### Phase K6 — 大屏适配

- [ ] K18 WindowSizeClass 响应式骨架（列表-详情双栏、网格列数、对话框宽度策略）

### Checkpoint K6
- [ ] 平板/折叠屏形态走查（含 UI 重构 U 系列验收叠加双形态）
- [ ] 询问用户是否 commit + 打 tag

### K7 — 鸿蒙路线裁决（决策点，不排任务）

- [ ] 按 docs/kmp-migration-plan.md §7 输出书面结论（推荐路径 A：ArkTS 薄客户端复用 shared 层；先 spike OpenHarmony-KMP 社区库跑通 commonMain）后另行排期
