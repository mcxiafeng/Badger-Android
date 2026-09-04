# KMP 跨端迁移实施计划：业务核心共享 + CMP UI + iOS/大屏

规格（目标架构、依赖矩阵、平台映射、鸿蒙裁决、风险）：[docs/kmp-migration-plan.md](../docs/kmp-migration-plan.md)

任务清单（可抓取、带验收）：[tasks/kmp-todo.md](./kmp-todo.md)

> 本文件只回答「按什么顺序做、每步多小、在哪停」。不改生产代码。
>
> **进度（2026-09-04）**：K0–K1 全部完成（Checkpoint K0/K1 关闭）；K2 进行中——K07 完成，K08 完成约 70%（分批 A 全部 + 分批 B 清障全部，剩 ServerApi Ktor suspend 化 + 整批搬移），K09 未开工。详见 tasks/kmp-todo.md 各任务实施备注。
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
- [ ] K08 Repository/ContactWriter/Outbox/SyncEngine/UseCase 迁 commonMain（测试迁 kotlin.test）——**分批 A 完成**（model/cache/queue 已进 commonMain）；**分批 B 清障完成**（BadgerDispatchers/Context 参数链全清/BadgerLog+randomUuid 收敛，repository/domain/SyncEngine 零 android.*）；**搬移硬前置 = ServerApi Ktor suspend 化**（56 API 方法 + 64 调用点，见 kmp-todo 备注③④）
- [ ] K09 同步调度抽象：SyncDispatcher expect/actual（Android=WorkManager 现状不变）

### Checkpoint K2
- [ ] 数据/领域/同步层 100% 位于 commonMain（K08-B 未完，暂不关闭）
- [x] Room 17 版迁移链测试全绿（MigrationChainTest 6→17 / 13→17）；iOS 模拟器空库 bootstrap 留 K16 后真机验证
- [x] Android 全量单测绿（509 例 13 失败 = Notification 旧基线）

### Phase K3 — 平台能力边界（每任务 1 commit）

- [ ] K10 相机 + QR + OCR expect/actual（Android 封装现状 / iOS AVFoundation+Vision spike）
- [ ] K11 NFC expect/actual（Android ReaderMode / iOS CoreNFC）
- [ ] K12 杂项平台层：DeviceId / 通知 / 分享 / 图片选择裁剪 / 剪贴板

### Checkpoint K3
- [ ] 七类平台边界全部 expect/actual 化，业务代码零平台 import
- [ ] Android 四条核心冒烟路径不变

### Phase K4 — UI 共享化（CMP，每任务 1 commit）

- [ ] K13 依赖切 CMP 坐标 + App 骨架（App/MainTabs/AppRoutes/Route/AppNavigator）迁 shared + **图标体系替换**（按 UI 重构 U03 选型结论，material-icons-extended 移除，62 文件 import 一次换到位）
- [ ] K14 **视觉特效系统重做**（Q1 裁决：Skia-first 双端一套，规格先行——UI 重构 U10 持有规格与验收；含 Haze iOS 路径 + AGSL→Skia，参考 miuix-blur skikoMain）
- [ ] K15 ViewModels 迁 shared（lifecycle-viewmodel KMP + koin-compose）+ 全页面功能走查
- [ ] （并入）UI 重构 U1 设计系统直接落 commonMain（Token v2/BadgerMotion/FloatingBarScaffold）

### Checkpoint K4
- [ ] pages/ + ui/ 全部位于 `shared` commonMain，`app/` 只剩 Application/MainActivity/actual 实现
- [ ] 特效系统重做后三档效果模式双端渲染一致（Q1 裁决落地）
- [ ] Android 全功能走查零回归
- [ ] CI iOS 编译持续绿

### Phase K5 — iOS 产品化（K4 出口后开工，需 macOS）

- [ ] K16 iosApp 工程（SwiftUI 壳 + ComposeViewController + Safe Area + 手势习惯）
- [ ] K17 TestFlight 内测 + 合规（NFC entitlement、权限文案、隐私清单、QR/OCR 识别率验收）

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
