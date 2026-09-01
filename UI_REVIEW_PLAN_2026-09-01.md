# Badger-Android UI Review Plan — 2026-09-01

工作分支：`refactor/dev-cleanup-2026-08-31`（不创建新分支）  
基线提交：`d39429f`

## 目标

围绕 Compose/Miuix UI 做一次系统性回归：检查状态同步、Dialog/BottomSheet 生命周期、列表交互、Insets、无障碍、动画与性能边界，并在确认行为稳定后继续清理死代码与过大的 UI 文件职责。

## 执行顺序

- [x] 确认现有分支与 follow-up 文档
- [x] 确认最新 `Build Debug APK` CI 为成功
- [x] 盘点 `ui/` 通用组件与 `pages/*` UI 目录
- [ ] 检查 ContactDetail 写入完成后的刷新/状态顺序并补回归测试
- [ ] 检查 TagManager 搜索退出、全选、筛选与 Dialog 状态机并补回归测试
- [ ] 检查 Social 编辑平台字段 Dialog 的确认/取消语义并补回归测试
- [ ] 检查通用 Dialog / BottomSheet 的长内容、Insets 与无障碍语义
- [ ] 检查 LiquidGlassNavBar / blur 动画边界、重组与低版本降级
- [ ] 继续迁移剩余 `KoinComponentBy` UI 消费者（行为稳定后执行）
- [ ] UI dead-code / 重复组件 / 大文件职责 sweep
- [ ] 更新 `CODE_REVIEW_FOLLOWUP_2026-09-01.md`
- [ ] 最终 CI 验证

## 规则

1. 所有修改继续落在本分支，不创建新的工作分支。
2. 明确 Bug 直接修复；可验证的行为变化尽量配套单测或 Compose 回归测试。
3. 不为拆文件而拆文件；先稳定行为，再优化职责边界。
4. 每完成一个明显 slice，更新计划与 follow-up 状态。
