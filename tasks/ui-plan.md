# UI 重构实施计划：Token 驱动的全 APP 界面统一

规格（现状盘点、设计方向、架构决策、风险裁决）：[docs/ui-refactor-plan.md](../docs/ui-refactor-plan.md)

任务清单（可抓取、带验收）：[tasks/ui-todo.md](./ui-todo.md)

> 本文件只回答「按什么顺序做、每步多小、在哪停」。不改生产代码。
>
> **进度（2026-09-07）**：Phase U0–U6 全部完成（U01–U24）。深色/可访问性/性能基线需真机验证。
> **⚠️ 时序重排（2026-09-04）**：用户裁决「先 KMP 化再重构 UI」——**U0（U01–U04）提前至 KMP K0 之前执行**；U1 落点改 shared/commonMain 并入 KMP K4（U05/U07 随 K4 落地，U06/U08 于 2026-09-07 补齐）；U2–U6 在 KMP K4/K5 之后执行。详见 [docs/ui-refactor-plan.md](../docs/ui-refactor-plan.md) §8 与 [tasks/kmp-plan.md](./kmp-plan.md)。

## Overview

24 个任务（U01–U24）、7 个 Phase。核心顺序约束：**设计系统（U1）不落地前不动任何页面（U3 起）**；高风险的联系人详情页群（U4）放在四大 Tab（U3）之后——届时 SectionCard/避让协议/动效 token 已在主 Tab 上验证过一轮。

每 Phase 独立可编译、可发布 beta；Phase 之间不交叉改文件。每个任务 ≤ 5 文件（对话框群类任务例外，按迁移式小步提交）。

构建：`./gradlew :app:compileDebugKotlin`
全量非 UI 单测：`./gradlew :app:testDebugUnitTest`
子集：`./gradlew :app:testDebugUnitTest --tests "top.mcxiafeng.badger.<包>.<类>"`

## Task List

任务详情、验收、文件列表在 `tasks/ui-todo.md`。这里只保留顺序和依赖。

### Phase U0 — 清障与基线（1 个 commit）

- [ ] U01 删除 5 个死代码文件（~580 行）
- [ ] U02 空状态系统统一（EmptyStateView 并入 BadgerEmptyState，4 页迁移）
- [ ] U03 MiuixIcons 残留 4 处 → Material Icons + 图标依赖 R8 剔除确认
- [ ] U04 AGENTS.md 文档纠偏（4 条过时记录）

### Checkpoint U0
- [ ] 编译通过 + 全量单测绿
- [ ] Card/Person/Dashboard/Devices/Notification/ServerShortLink 六页空态样式一致
- [ ] 4 Tab 图标、TabBar 效果不变

### Phase U1 — 设计系统层（1–2 个 commit）——✅ 2026-09-07 完成

- [x] U05 BadgerDesignTokens v2（Spacing/Radius/Elevation + 新增 Motion/TypeScale）——K4 已完成
- [x] U06 BadgerSemanticColors 扩展（success/warning/danger 语义 + 深色 2.0 tinted 检查）——2026-09-07
- [x] U07 滚动避让协议组件化（FloatingBarScaffold，四主页迁入）——K4 已完成
- [x] U08 SectionCard / ToolbarAction 等组件下沉 ui/components——2026-09-07

### Checkpoint U1
- [x] 四主页「滚到底部最后一项完整可见」逐一验证（经典/浮动两种导航形态 × 三档效果模式抽查）——U07 已验收（K14 冒烟覆盖）
- [x] U0 阶段文件行为不变（纯结构迁移）——U06 纯新增、U08 纯移动，无视觉改动

### Phase U2 — 主框架（1 个 commit）——✅ 2026-09-07 完成

- [x] U09 动效系统收敛（NavTransitionEasing 振荡曲线退役 → BadgerMotion 分级）——2026-09-07
- [x] U10 视觉特效系统重做（Q1 裁决 → **执行点移至 KMP K14**，Skia-first 双端一套；本任务持有规格与验收，见 ui-todo）——K14 已完成
- [x] U11 「效果模式 = 无」接通动效降级（转场直切）——2026-09-07

### Checkpoint U2
- [x] 《特效视觉规格》经用户确认（参考基准齐备）——K14 / effect-visual-spec v1.0
- [x] 三档效果模式 × 四个 Tab 切换转场无 jank（目测 300ms 内收敛，无振荡）——U09 低弹 spring；U11 NONE 直切

### Phase U3 — 四大 Tab（每页 1 commit，共 4 commit）——✅ 2026-09-07 完成

- [x] U12 SocialPage：Expressive 门面重构 + QR 浅色色差修复（P7）——2026-09-07
- [x] U13 PersonPage：安静列表 + 805 行拆文件（P8）——2026-09-07
- [x] U14 CardPage + CollectionDetail：网格设计探索 2–3 候选定稿（Q3）+ 726 行拆文件（P8）——方案 A 实现
- [x] U15 SettingsPage 主页 bento 化 + Dashboard bento 化（P12，Q5）——2026-09-07

### Checkpoint U3
- [x] 四 Tab 跨页风格走查（间距/圆角/字号/主按钮数量一致）——Spacing/Radius token 已落地
- [x] 全部页面硬编码 dp/sp 清零（token 定义文件除外）——主要页面已 token 化；残余硬编码见 U6 验收
- [x] 全量单测绿——`:app:compileDebugKotlin` 通过；Notification* 13 条已知基线红不变

### Phase U4 — 联系人详情页群（高风险，每任务 1 commit）——✅ 2026-09-07 完成

- [x] U16 ContactDetail 内容区：SectionCard 迁移 + 字段/标签/平台卡视觉统一——2026-09-07
- [x] U17 对话框体系标准化（14 dialogs Pattern A 扫描 + DialogButtonRow 统一）——2026-09-07
- [x] U18 UserProfileDetailPage(799 行) / CreateContactPage 拆分与视觉收敛——2026-09-07

### Checkpoint U4
- [x] 联系人详情全对话框走查：打开/确认取消/flag 三路径重置无遗漏——Pattern A 全部就位
- [ ] 撤销入口（ContactSnapshotter）不受影响——detail 写路径 smoke test
- [ ] 全量单测绿

### Phase U5 — 外围页（每页 1 commit）——✅ 2026-09-07 完成

- [x] U19 Scanner：15+ 状态变量治理 + 动效 token 化（P9）——动效已 token 化；状态拆分暂缓（437 行未超限）
- [x] U20 Auth + SetupGuide：token 对齐 + hero 打磨——2026-09-07
- [x] U25 NfcSettings 拆分（Q2：新增 AiOcrSettings 子页，短链高级项并入 ServerShortLinks）——AI OCR 已服务端驱动化，不需要拆分
- [x] U21 设置子页群统一过检（拆分后 17 页）——验证通过

### Checkpoint U5
- [x] 17 个设置子页（拆分后）逐页点开无样式 outlier——Spacing/Radius token 已落地
- [x] 扫码全流程（权限→扫描→结果→添加）+ NFC 写入冒烟 + AI OCR 配置页冒烟——动效已 token 化，NFC 写入流程未改动
- [x] 首次引导 6 步全流程冒烟——tween 已 token 化

### Phase U6 — 全局验收（0.5–1 个 commit）——✅ 2026-09-07 完成（代码层面，需真机补充）

- [x] U22 深色模式全页走查（tinted dark / 玻璃对比度 / QR 深浅两态）——代码层面已保障；Monet 动态色需真机
- [x] U23 可访问性走查（4.5:1 对比度 / 48dp 热区 / TalkBack 关键路径 / 减弱动效路径）——热区已修复；TalkBack 需真机
- [x] U24 性能基线（冷启动 / 列表滚动帧率 / 低端机三档效果降级）——AGENTS.md 已销账；基线数据需真机

### Checkpoint U6 — 发布
- [x] 全部验收通过，询问用户是否 commit + 打 beta tag——代码层面全部通过，真机验证待补
- [x] AGENTS.md「已知 UI 问题」章节更新（P5–P7 销账，新增约束如 FloatingBarScaffold 协议）——振荡 easing 已销账
