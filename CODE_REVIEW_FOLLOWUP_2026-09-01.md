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

这样带来的收益：

- 依赖关系可以直接从构造函数读取；
- 单元测试不需要为了创建 ViewModel 初始化全局 Koin 容器；
- 依赖遗漏会在编译/DI 装配阶段暴露；
- ViewModel 的实际职责边界更容易审查。

## 3. 已完成：删除 `KoinComponentBy` 过渡层

四个主要历史消费者完成迁移后，对仓库生产代码进行了剩余引用检查。

`di/KoinComponentBy.kt` 已不再承担生产依赖获取职责，因此删除该兼容层。

这是本轮 dead-code cleanup 中的明确删除项：删除前先迁移消费者，而不是直接删除后依赖编译错误逐个补救。

## 4. 修复：Auth UI 可能永久停留在 Loading

审查认证状态处理时发现异常路径存在状态恢复风险：如果登录/注册流程抛出异常而 loading 状态没有在所有路径完成恢复，UI 可能长期保持 Loading。

本轮将异常处理与 loading 状态恢复收口，保证：

```text
开始认证
  → Loading = true
  → 成功 / 普通异常 / 取消
  → 正确结束或恢复状态
```

同时补充测试，避免后续改动重新引入“异常后界面无法继续操作”的问题。

## 5. 修复：完整平台 URL 被二次套模板

发现一个真实的平台链接构造 Bug。

原有统一平台链接构造逻辑在某些自动识别平台场景中，即使输入本身已经是完整的：

```text
https://...
http://...
```

仍可能继续把整个 URL 当成用户名/ID 填入平台 `linkTemplate`，导致生成错误地址。

现在统一规则为：

```text
完整 http/https URL
  → 直接使用

用户名 / UID / 平台标识
  → 通过 linkTemplate 构造
```

该修复放在统一的 `buildPlatformLink()` 语义层，而不是仅在某一个 UI 页面添加特殊判断，因此 ContactDetail 和其他未来调用方都能获得一致行为。

## 6. 已新增回归测试

针对平台 URL 构造问题新增回归测试，覆盖至少以下两类输入：

- 已经是完整 URL；
- 需要通过平台模板构造的标识符。

目的不是单纯提高测试数量，而是固定本轮发现的行为契约：完整 URL 不得被二次模板化。

当前分支快照中的最新提交为：

```text
38411d956e34fb46c109b13d37c79b2cecf66d41
```

提交信息：

```text
test(platform): cover URL construction regression
```

## 7. UI 架构状态

经过此前阶段与本轮继续收口，目前大型 UI Feature 的方向保持为：

```text
Repository / Data
       ↓
   ViewModel
       ↓ StateFlow / UI State
     Compose
```

Compose 页面继续避免直接承担：

- Repository Flow 生命周期；
- 网络访问；
- 数据库写入；
- 全局 Service Locator 查询。

`ContactDetail` 已完成此前记录的 collection state 与 mutation completion 收口，本轮重点是进一步让 ViewModel 依赖显式化，而不是重新机械拆分文件。

## 8. 当前剩余工作

### P0：等待真实构建结论

当前 GitHub Actions 之前存在并发取消情况，部分 run 在真正执行 `assembleDebug` 前被取消。

因此目前不能仅凭 workflow 已启动就宣称构建通过。

后续应以最新稳定 run 的实际结果为准：

```text
./gradlew assembleDebug --stacktrace
```

若失败，只修复实际编译/测试错误，不进行无依据的大范围改动。

### P1：Compose 回归测试

优先补充针对真实风险点的测试：

- `LiquidGlassNavBar` 空 Tab / index 边界；
- ContactDetail 写入完成后刷新顺序；
- Scanner 保存期间重复提交保护；
- TagManager 搜索退出与 Dialog 状态机。

### P1：最终 UI dead-code sweep

继续检查：

- 未使用的 Compose state；
- 无效 remember key；
- 不可达分支；
- 仅用于历史过渡且已无消费者的 wrapper；
- 无效 import 和重复 helper。

删除必须基于真实消费者检查，不按命名猜测。

### P2：大型 UI 文件职责边界

剩余大型页面继续按职责拆分：

```text
Screen orchestration
Presentation components
Action components
ViewModel state/mutations
```

避免把“拆成更多文件”本身当作优化目标。

## 9. 当前结论

本轮最重要的架构收口已经完成：核心历史 ViewModel 的 Service Locator 依赖已迁移到 constructor injection，`KoinComponentBy` 兼容层可以删除。

同时修复了两个具有实际用户影响的问题：

1. 认证异常后 UI Loading 状态恢复；
2. 完整平台 URL 被错误二次模板化。

并为平台 URL 行为加入了回归测试。

下一阶段重点应从“继续大规模重构”转向：

```text
真实 CI 结果
  → 修复真实错误
  → 补关键 UI regression tests
  → 最终 dead-code sweep
```

这样可以避免项目在已经完成大量结构调整后，因为继续无目标重构而重新引入回归。
