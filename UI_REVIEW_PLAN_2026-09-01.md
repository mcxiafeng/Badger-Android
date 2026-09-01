# Badger-Android UI / Legacy / V1 Global Audit Plan — 2026-09-01

工作分支：`refactor/dev-cleanup-2026-08-31`（继续使用，绝不创建新分支）  
当前审查基线：`aa07c0bd5e4d42c9e95fcd37c317ddda4546180a`

## 本轮原则

本轮不再先做机械式“大文件拆分”。先对全库进行一次 UI、V1 遗留架构、V1 视觉设计和无效功能的全局盘点，再决定保留、恢复、删除或按职责重构。

所有 UI 改动遵守 `AGENTS.md`：先看 `libdocs/` 与现有代码；优先 Miuix；不修改第三方源码；避免 UI 直接访问 Repository；WindowDialog 使用外层 `if` 控制挂载；Card 点击不叠加 clickable；新增逻辑补脱敏日志；确认功能完成后再统一提交。

## 四类全局扫描

### A. V1 设计资产扫描

目标：找回 V1 中仍然更好看的视觉与交互资产，而不是把 V1 整体回滚。

- [ ] 梳理 `dev` / 历史提交与当前分支的 UI 目录差异
- [ ] 为每个主页面建立“V1 → 当前 → 目标”对照
- [ ] 标记被重构损坏的布局、层级、动画、间距、点击路径
- [ ] 保留可复用的 V1 视觉设计，避免把旧架构一起带回

优先页面：Social、Person、ContactDetail、Card、CollectionDetail、Settings、Scanner、主导航。

### B. 老架构残留扫描

目标：区分“数据兼容必须保留”和“仅仅是旧 UI/旧入口仍在引用”。

- [ ] 扫描 V1 entity/DAO 对 UI 的直接耦合
- [ ] 扫描旧 Route / Navigator / Dialog / Page 入口
- [ ] 扫描 Hilt/Koin 迁移后的兼容层与 `KoinComponentBy` 消费者
- [ ] 扫描废弃网络、图片、平台和同步入口
- [ ] 对每项标注：必须兼容 / 可迁移 / 可删除 / 需人工产品决策

### C. 无用 UI / 死入口扫描

目标：找出用户根本到不了、只有占位提示、没有数据链路或重复实现的 UI。

- [ ] 搜索 placeholder / 暂未支持 / TODO / FIXME / 旧功能入口
- [ ] 建立 Route → Screen → Action → ViewModel → UseCase 的可达性链
- [ ] 检查 manifest、导航、设置项、Dialog、Popup 是否存在孤儿入口
- [ ] 检查重复页面与重复组件
- [ ] 删除前先确认没有 DeepLink、外部 Intent、测试或反射引用

### D. UI 职责与体验扫描

目标：在确认功能真实需要后，再按职责拆分。

- [ ] 主入口：启动/认证、Tab、导航栈、页面分发、GPU/Blur 生命周期分别盘点
- [ ] Page：Route 协调、状态、纯布局、Dialog、Sheet、组件分别盘点
- [ ] ViewModel：状态机、事件、一次性 effect、业务调用分别盘点
- [ ] 通用组件：重复模式达到抽取阈值后统一收口
- [ ] 同时检查 Insets、BackHandler、semantics、动画和低端 GPU fallback

## 当前已确认的高优先级审计对象

1. `App.kt`：入口、Pager、导航栈、Route 分发、生命周期与视觉效果集中，需要先建立职责地图。
2. `PersonPage.kt`：大型联系人页面，需要先识别列表、搜索、多选、Dialog 和导航职责。
3. `CardPage.kt` / `CollectionDetailPage.kt`：已有 Components/Dialog 拆分，但 Page 本身仍承担大量流程协调。
4. `SocialPage.kt` / `SocialPageComponents.kt` / `QrCodeCard.kt`：继续检查视觉、平台编辑、背景图占位和 NFC/QR 边界。
5. `ui/`：检查 LiquidGlass、Dialog、Sheet、blur 与旧组件是否重复。

## 已完成的行为修复（保留）

- [x] TagManager 观察失败后 Refresh 无法恢复
- [x] TagManager 批量删除部分失败提示
- [x] Social 快速切换平台丢持久化
- [x] Social 旧请求覆盖新状态
- [x] 非微信平台 QR fallback 文案错误
- [x] SelectionSheet 底部 Insets
- [x] SelectionSheet RadioButton semantics
- [x] ContactDetail 保存后强制跳联系人 Tab

## 推进顺序

```text
全库文件树 + AGENTS/libdocs/skills 盘点
        ↓
V1 / dev / 当前 UI 差异审计
        ↓
旧架构与无用 UI 可达性扫描
        ↓
建立“保留 / 恢复 / 删除 / 重构”清单
        ↓
先修复被改坏且影响体验的问题
        ↓
删除确认无用的 UI / 死入口
        ↓
按职责拆 App / Page / Dialog / Components
        ↓
KoinComponentBy 与旧 UI 兼容层收口
        ↓
全量 dead-code sweep + 构建/测试验证
```

## 交付物

- 本文件持续记录全局审计结果与计划状态。
- `CODE_REVIEW_FOLLOWUP_2026-09-01.md` 记录最终已确认的 Bug、删除项和风险。
- 任何删除项必须记录原入口、引用检查结果和删除原因。
- 不为了“拆文件”改变用户可见行为；发现已被改坏的 UI 优先恢复正确体验。
