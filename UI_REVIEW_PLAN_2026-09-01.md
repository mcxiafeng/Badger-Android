# Badger-Android UI Review Plan — 2026-09-01

工作分支：`refactor/dev-cleanup-2026-08-31`（不创建新分支）  
本轮 UI 审查基线：`d39429f`，当前已推进到 `c29041b`

## 目标

围绕 Compose/Miuix UI 做一次系统性回归：检查状态同步、Dialog/BottomSheet 生命周期、列表交互、Insets、无障碍、动画与性能边界，并在确认行为稳定后继续清理死代码与过大的 UI 文件职责。

## 执行顺序

- [x] 确认现有分支与 follow-up 文档
- [x] 确认已有 Debug APK 构建基线
- [x] 盘点 `ui/` 通用组件与 `pages/*` UI 目录
- [x] 检查 ContactDetail 写入后的刷新职责，修复“保存一次资料强制跳联系人 Tab”问题
- [x] 检查 TagManager 刷新/筛选/选择状态机；修复观察流终止导致 Refresh 永久失效，并补回归测试
- [x] 检查 Social 平台切换与编辑/QR 行为；修复快速切换丢持久化与非微信平台错误 QR 文案，并补回归测试
- [x] 检查通用 BottomSheet Insets 与无障碍语义；修复无按钮 SelectionSheet 被系统手势区遮挡的问题
- [x] 检查 LiquidGlassNavBar / 导航动画边界：既有边界与 a11y 回归已在前一轮收口，本轮未发现新的确定性 Bug
- [ ] 继续迁移剩余 `KoinComponentBy` UI 消费者（行为稳定后执行）
- [ ] UI dead-code / 重复组件 / 大文件职责 sweep
- [x] 更新 `UI_REVIEW_PLAN_2026-09-01.md`
- [ ] 更新 `CODE_REVIEW_FOLLOWUP_2026-09-01.md` 并完成最终 CI 验证

## 本轮已落地的确定性修复

### TagManager
- 把 Repository observation failure 从“终止整个 uiState Flow”改为 `Result` 值状态，后续 `Refresh` 可以重新建立观察流。
- 批量删除消息改为按成功/失败数量准确反馈，避免部分失败时提示“全部删除”。
- 新增 `TagManagerSettingsViewModelTest` 覆盖“连续观察失败 → Refresh → 成功恢复”。

### Social
- 平台选择不再因为 2 秒时间防抖而丢掉用户的第二次选择；改为 Mutex 串行，所有真实选择均持久化。
- `SocialViewModel` 取消旧的平台选择任务，防止过期请求回写 SUCCESS/ERROR 状态。
- 无 jumpLink 时，QR fallback 改用实际平台 displayName，而不是固定写成“微信号”。
- 已选平台编辑对话框的确认/取消状态路径保持单向关闭。
- 新增 `SelectPlatformUseCaseTest` 覆盖快速连续选择的持久化。

### 通用 UI
- `BadgerBottomSheet` 将 `navigationBarsPadding()` 从仅按钮行上移到整个内容列，避免无按钮 Sheet 的末尾内容被系统手势区域遮挡。
- `BadgerSelectionSheet` 增加 `selected` semantics，并保留 `Role.RadioButton`。

### ContactDetail / Navigation
- `ContactDetailPage` 的 refresh 回调当前仅通知 `UserProfileTicker`，不再为了刷新 PersonRoute 强制执行 `pagerState.animateScrollToPage(1)`。
- 从“名片夹”或其它入口打开联系人详情并保存信息时，底层 Tab 不再被无故切到“联系人”。

## 仍需继续检查

1. `KoinComponentBy` 剩余 UI 消费者迁移及兼容层删除。
2. `SocialPage` 的“更换背景图”入口目前仍是占位能力，需要结合产品/数据层是否仍计划支持来决定删除还是补齐能力。
3. UI 大文件职责边界；优先在行为稳定后拆分，避免纯机械拆文件。
4. 最终 CI 必须同时通过 Debug APK 构建及新增 JVM/VM 回归测试。
