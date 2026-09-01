# Badger-Android 代码审查后续报告

日期：2026-09-01  
审查分支：`refactor/dev-cleanup-2026-08-31`  
对应基线：`dev` 及本轮持续重构提交  

> 本文件独立于 `CODE_REVIEW_REPORT_2026-09-01.md`，用于记录后续阶段的实际修改、发现的问题与验证状态，避免与持续更新的主报告产生覆盖冲突。

## 1. 本轮目标

本轮继续处理三个方向：

1. 清理历史 Service Locator / `KoinComponentBy`；
2. 收口大型 UI Feature 对依赖和状态的访问边界；
3. 修复审查过程中发现的真实 UI / 状态 / URL 构造问题，并补充回归测试。

不创建新的工作分支，所有修改继续落在：

```text
refactor/dev-cleanup-2026-08-31
```

## 2. 已完成：核心 ViewModel constructor injection

以下大型/历史迁移 ViewModel 已继续从 `KoinComponentBy` 全局获取依赖迁移为 constructor injection：

- `AuthViewModel`
- `CardViewModel`
- `PersonViewModel`
- `ContactDetailViewModel`
- `TagManagerSettingsViewModel`

迁移原则：

```text
ViewModel
  ← constructor dependencies
  ← Koin module
```

而不是：

```text
ViewModel
  → KoinComponentBy.get()
  → global service lookup
```

## 3. DI 迁移状态：兼容层暂存，而非宣称已删除

上一阶段曾计划在全部消费者迁移后删除 `KoinComponentBy`。真实编译链恢复过程中发现仓库仍有历史 UI / ViewModel 调用点依赖该符号。

因此当前保留明确标记为 `@Deprecated` 的兼容层与旧版 Compose Koin import bridge，避免在没有完成全部消费者迁移时继续制造级联回归。

当前状态：

```text
新代码 → constructor injection / org.koin.compose.koinInject
旧代码 → Deprecated compatibility bridge
```

后续 dead-code sweep 应继续迁移剩余消费者，引用归零后再删除兼容层。

## 4. 已修复：Auth Loading 异常恢复

认证异常路径此前存在 UI 长时间保持 Loading 的风险。本轮将 loading 状态恢复收口，并保证成功、异常、取消路径都能结束当前 loading 状态。

## 5. 已修复：完整平台 URL 二次套模板

统一平台链接构造逻辑现在遵循：

```text
完整 http/https URL → 直接使用
用户名 / UID / 平台标识 → 通过 linkTemplate 构造
```

并已增加回归测试，避免识别结果被错误二次模板化。

## 6. UI：DialogButtonRow 调用契约

历史调用点有不少只传部分按钮参数。为了避免“修编译”的同时悄悄改变界面行为，按钮文本与 callback 保持可选：

- 未提供文本时不渲染该按钮；
- 默认 callback 不产生行为；
- 老调用方无需被强制添加新的按钮。

## 7. UI：LiquidGlassNavBar 边界与无障碍

已处理：

- 空 Tab 列表安全返回；
- selected index 统一约束到合法区间；
- drag stop 再次 clamp；
- tabs/icons 数量不一致时显式失败；
- Tab 增加 `Role.Tab`、selected 和 content description 语义；
- 避免图标与外层 Tab 重复暴露 TalkBack 描述。

## 8. UI 回归测试

新增 `LiquidGlassNavBarTest`，覆盖：

- 空列表；
- 负 index；
- 超过最大 index；
- 单 Tab。

测试采用纯函数边界检查，不依赖 Android UI 环境。

## 9. TagManager：刷新与状态恢复

发现页面错误态的“重试”按钮之前发送的 `Refresh` 实际是空操作，因此用户点重试不会重新创建 Repository 观察流。

现在改为：

```text
Refresh
  → refreshTrigger + 1
  → flatMapLatest 重新建立 observeAllTags()
```

观察异常同时增加有限次数的短退避重试，降低瞬时错误直接落入不可恢复错误态的概率。

## 10. TagManager：已知的搜索全选边界

已确认一个真实交互问题：

```text
页面 query 由 Composable 本地持有
↓
ViewModel 的 SelectAll 只能看到 filter 后的 visibleTags
↓
搜索状态下可能把搜索结果之外的标签一并选中
```

曾尝试将 `SelectAll` 修改为携带可见 ID，但当前仓库编辑接口要求整文件替换，无法在不同步整个大型页面的情况下安全完成局部修改；为避免留下不可编译状态，已回退该半成品改动。

因此本项仍列为 P1，后续应采用完整同步页面的方式一次性完成：

```text
搜索结果计算
      ↓
visibleTagIds
      ↓
SelectAll(visibleTagIds)
```

不要通过隐藏搜索条件或清空 query 掩盖问题。

## 11. UI dead-code / 质量观察

当前继续发现：

- 部分大型 Compose 文件仍有历史 import/注释噪声；
- `TagManagerSettingsPage` orchestration 与 UI event wiring 仍偏重；
- `App.kt` 仍承担大量顶层导航 orchestration，但目前有明确真实消费者，不做机械拆分；
- LiquidGlassNavBar 的动画与视觉层耦合较重，后续可按“交互 / 视觉”职责拆，但应先保证现有行为测试稳定。

清理原则：

```text
真实消费者确认
  → 明确行为契约
  → 再删除 / 拆分
```

## 12. 当前分支状态

当前分支 HEAD：

```text
3c07f93f0714fb732738964d8f216a8f3314b0b6
```

最新功能提交：

```text
fix(tag): make refresh actually restart tag observation
```

所有修改仍然落在原有：

```text
refactor/dev-cleanup-2026-08-31
```

没有创建新的工作分支。

## 13. CI / 构建验证状态

近期多个 `Build Debug APK` workflow 因后续提交进入 PR workflow 而发生并发取消，其中一次明确在 Android SDK 安装阶段被取消，还没有到 Gradle 构建步骤。

因此当前不能宣称：

```text
assembleDebug 通过
```

也不能把这些取消的 run 解释成代码编译失败。

后续必须以一个稳定完成到：

```text
./gradlew assembleDebug --stacktrace
```

的 run 为最终构建依据。

## 14. 当前剩余工作

### P0：真实构建结果

取得一次没有被新提交取消的稳定 CI，并确认 `assembleDebug` 完整通过。

### P1：关键 UI 回归测试

继续：

- ContactDetail 写入完成后的刷新顺序；
- Scanner 保存期间重复提交保护；
- TagManager 搜索退出与 Dialog 状态机；
- TagManager 搜索结果与批量全选契约。

### P1：最终 UI dead-code sweep

继续检查：

- 未使用 Compose state；
- 无效 remember key；
- 不可达分支；
- 仅用于历史过渡且已无消费者的 wrapper；
- 无效 import 和重复 helper。

### P2：大型 UI 文件职责边界

剩余大型页面继续按：

```text
Screen orchestration
Presentation components
Action components
ViewModel state/mutations
```

进行必要拆分，不为“文件数量更多”而拆。

## 15. 当前结论

本阶段已经从单纯架构清理推进到真实 UI 行为收口：

1. 核心 ViewModel 继续向 constructor injection 收口；
2. 历史 DI bridge 明确降级为过渡层；
3. Auth Loading、平台 URL、Dialog 参数契约得到修复；
4. LiquidGlassNavBar 的边界与无障碍语义得到强化并有测试；
5. TagManager 的 Refresh 已从空操作变为真实重新订阅；
6. TagManager 搜索全选的已知边界问题保持为明确 P1，没有通过危险的半成品改动掩盖；
7. CI 仍需要一次稳定 `assembleDebug` 结果后才能进入最终验收。

下一步严格遵循：

```text
稳定 CI
  → 修复真实编译/测试错误
  → 补关键 UI regression tests
  → 完成 TagManager 搜索全选契约
  → 迁移剩余兼容层消费者
  → 删除兼容层
  → 最终 dead-code sweep
```
