# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交

> 本文件独立于 `CODE_REVIEW_REPORT_2026-09-01.md`，用于记录后续阶段的实际修改、发现的问题与验证状态。

## 1. 本轮目标

本轮继续处理三个方向：

1. 清理历史 Service Locator / `KoinComponentBy`；
2. 收口大型 UI Feature 对依赖、状态和交互边界的访问；
3. 修复真实 UI / 生命周期 / 状态问题，并补充针对性回归测试。

所有修改继续落在：

```text
refactor/dev-cleanup-2026-08-31
```

不创建新的工作分支。

## 2. 架构：ViewModel constructor injection

以下核心 ViewModel 已继续从历史 `KoinComponentBy` 全局取依赖迁移为 constructor injection：

- `AuthViewModel`
- `CardViewModel`
- `PersonViewModel`
- `ContactDetailViewModel`
- `TagManagerSettingsViewModel`

目标结构保持：

```text
ViewModel
  ← constructor dependencies
  ← Koin module
```

## 3. DI 迁移状态

当前仍保留明确标记为 `@Deprecated` 的历史 DI 兼容层及旧版 Compose Koin import bridge。

原因不是架构设计改变，而是在真实编译链恢复过程中发现仓库仍存在历史消费者。后续应继续迁移剩余调用点，引用归零后再删除 bridge，避免把“过渡层”误当成最终架构。

## 4. 已修复：Auth Loading 异常恢复

认证流程异常、取消、成功路径的 loading 状态恢复已经收口，避免认证异常后 UI 长时间停留在不可操作的 Loading 状态。

## 5. 已修复：完整平台 URL 二次模板化

统一平台 URL 构造语义现在为：

```text
完整 http/https URL → 直接使用
用户名 / UID / 平台标识 → 按 linkTemplate 构造
```

并有回归测试固定该契约。

## 6. 已修复：DialogButtonRow 调用契约

历史调用方允许只提供部分按钮参数，因此按钮文本与 callback 保持可选：未提供文本时不渲染，不因为兼容编译而偷偷改变旧 Dialog 的交互语义。

## 7. 已修复：LiquidGlassNavBar 边界与无障碍

已处理：

- 空 Tab 列表；
- selected index 负数 / 越界；
- drag stop 再次 clamp；
- tabs / icons 数量不一致时显式失败；
- Tab 的 `Role.Tab`、selected 和 content description 语义；
- 避免图标与外层 Tab 重复朗读。

新增 `LiquidGlassNavBarTest` 覆盖空列表、负 index、越界 index、单 Tab。

## 8. 已修复：TagManager Refresh

原错误态的“重试”按钮之前发送 `Refresh` 后实际上不会重新建立 Repository Flow。

现在为：

```text
Refresh
  → refreshTrigger + 1
  → flatMapLatest 重新建立 observeAllTags()
  → 瞬时观察异常有限退避重试
```

## 9. 已修复：TagManager 搜索全选契约

发现并修复真实交互 Bug：搜索状态下页面展示的是 `state.visibleTags + query`，但旧 `SelectAll` 只知道 `state.visibleTags`，会把搜索结果之外的标签一起选中。

现在统一收口为：

```text
TagManagerUiState.Success.searchVisibleTags(query)
                     ↓
              visibleTagIds
                     ↓
        SelectAll(visibleTagIds)
```

页面列表渲染和批量全选共用同一套可见集合计算逻辑，避免两处实现再次漂移。

## 10. 已修复：TagManager 筛选切换时的 selection 污染

发现多选状态下切换“全部 / 手动 / AI”筛选后，旧 selection 可能包含新筛选不可见的标签，导致：

- “已选数量”大于当前可见数量；
- 批量颜色 / 删除可能作用到当前界面不可见的标签。

现在筛选模式变化时，如果已经处于多选状态，会清空 selection，重新建立当前筛选下的选择集合。

## 11. TagManager 状态语义收口

将“筛选 + 排序 + 搜索”最终可见集合封装进 `TagManagerUiState.Success.searchVisibleTags(query)`，减少页面中重复实现。

当前页面职责保持：

```text
UI query / Dialog 状态
        ↓
visible 集合计算
        ↓
ViewModel intent
        ↓
Repository
```

而不是把搜索字符串写进数据层。

## 12. Scanner：相机清理生命周期修复

发现 `CameraPreview` 的 `DisposableEffect(Unit)` 只捕获初始 `camera` 值，初始值通常为 `null`，导致 onDispose 时的闪光灯关闭逻辑可能拿不到当前 Camera。

现在使用 `rememberUpdatedState(camera)` 保证 cleanup 读取最新 Camera：

```text
camera state 更新
      ↓
currentCamera
      ↓
onDispose 使用最新实例
```

CameraX `unbindAll()`、executor shutdown、TextRecognizer close 仍保持在同一资源释放路径。

## 13. Scanner：仍待完成的 Bitmap ownership 问题

继续审查 Scanner 时仍发现一个更深的生命周期风险：

```text
拍照 / OCR 后台处理 Bitmap
        ↓
页面 dismiss / 离开 composition
        ↓
releaseCapturedImage() recycle Bitmap
        ↓
后台识别可能仍在访问同一 Bitmap
```

该问题目前只做了风险确认，没有用“禁止返回”或随意延迟 recycle 的方式掩盖。最终修复应明确区分：

- UI 持有的展示 Bitmap；
- 后台识别任务持有的工作 Bitmap；
- 任务取消时的释放责任。

这仍是当前最高优先级 Scanner 生命周期问题。

## 14. UI dead-code / 质量观察

当前继续观察到：

- 部分大型 Compose 文件存在历史 import / 注释噪声；
- `TagManagerSettingsPage` 已经过职责收口，但仍属于高复杂度 orchestration 页面；
- `App.kt` 仍承担大量顶层导航 orchestration，不过存在真实消费者，不做机械拆分；
- LiquidGlassNavBar 的动画、手势和视觉层仍有较强耦合，应在行为稳定后再做职责拆分。

清理原则继续保持：

```text
真实消费者确认
  → 明确行为契约
  → 回归测试
  → 再删除 / 拆分
```

## 15. 当前分支状态

当前分支：

```text
refactor/dev-cleanup-2026-08-31
```

当前 HEAD：

```text
d79923fcab4568b36ffc444bbf1dbf1fda171cd0
```

最新功能提交：

```text
refactor(tag): reuse centralized search semantics
```

所有修改仍然落在原有工作分支，没有创建新分支。

## 16. CI / 构建验证状态

近期 `Build Debug APK` workflow 会因为新提交进入 PR workflow 而取消旧 run。

已经确认至少一轮旧 run：

- checkout / Java / Android SDK / Gradle setup 均成功；
- 随后在 `Build Debug APK` 已启动后被后续提交取消。

因此这些 run 不能解释为代码失败，但也不能据此宣称 `assembleDebug` 已通过。

当前最新提交已重新触发新的 `Build Debug APK` run，需要等待一个没有被后续提交取消的稳定 run，拿到真正的 Gradle 结论。

## 17. 当前剩余工作

### P0：稳定构建结论

取得一次完整执行：

```text
./gradlew assembleDebug --stacktrace
```

的稳定 CI 结果。

### P0/P1：Scanner Bitmap ownership

明确后台 OCR / 图片识别与 UI Bitmap 的 ownership、取消和释放顺序，彻底消除潜在 `Bitmap recycled` 崩溃窗口。

### P1：关键 UI 回归测试

继续补：

- ContactDetail 写入完成后的刷新顺序；
- Scanner 保存期间重复提交保护；
- Scanner Bitmap 处理与 dismiss/back 生命周期；
- TagManager 搜索退出与 Dialog 状态机；
- TagManager 搜索全选和筛选切换 selection 语义。

### P1：最终 UI dead-code sweep

继续检查：

- 未使用 Compose state；
- 无效 remember key；
- 不可达分支；
- 仅用于历史过渡且已无消费者的 wrapper；
- 无效 import 和重复 helper。

### P2：大型 UI 文件职责边界

在行为稳定后再对剩余大型页面按：

```text
Screen orchestration
Presentation components
Action components
ViewModel state/mutations
```

做必要拆分。

## 18. 当前结论

本阶段已经从架构清理进一步推进到 UI 状态一致性：

1. 核心 ViewModel 继续向 constructor injection 收口；
2. DI 兼容层保持为明确的 Deprecated 过渡层；
3. Auth Loading、平台 URL、Dialog 参数契约得到修复；
4. LiquidGlassNavBar 边界与无障碍得到强化并有测试；
5. TagManager Refresh 已经真正可重试；
6. TagManager 搜索全选已经与页面真实可见集合绑定；
7. TagManager 筛选切换时旧 selection 污染已经消除；
8. Scanner Camera cleanup 已修复最新 Camera 捕获问题；
9. Scanner Bitmap ownership 仍是当前最重要的未完成生命周期问题；
10. CI 已经真正进入 Build Debug APK，但仍缺一个未被后续提交取消的完整 assembleDebug 结论。

下一步严格遵循：

```text
稳定 CI
  → 修复真实编译/测试错误
  → 收口 Scanner Bitmap ownership
  → 补关键 UI regression tests
  → 迁移剩余兼容层消费者
  → 删除兼容层
  → 最终 dead-code sweep
```
