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

这样带来的收益：

- 依赖关系可以直接从构造函数读取；
- 单元测试不需要为了创建 ViewModel 初始化全局 Koin 容器；
- 依赖遗漏会在编译/DI 装配阶段暴露；
- ViewModel 的实际职责边界更容易审查。

## 3. DI 迁移状态：兼容层暂存，而非宣称已删除

上一阶段曾计划在全部消费者迁移后删除 `KoinComponentBy`。本阶段在真实 CI 编译链恢复过程中发现，仓库仍有历史 UI / ViewModel 调用点依赖该符号。

因此没有直接删除后继续制造大量级联回归，而是临时恢复一个明确标记为 `@Deprecated` 的兼容层，同时为旧版 `org.koin.androidx.compose.koinInject` 增加过渡 bridge。

当前状态：

```text
新代码 → constructor injection / org.koin.compose.koinInject
旧代码 → Deprecated compatibility bridge
```

这不是最终架构终点。后续 dead-code sweep 应继续迁移剩余消费者，并在引用归零后删除两个兼容层。

## 4. 修复：Auth UI 可能永久停留在 Loading

审查认证状态处理时发现异常路径存在状态恢复风险：如果登录/注册流程抛出异常而 loading 状态没有在所有路径完成恢复，UI 可能长期保持 Loading。

本轮将异常处理与 loading 状态恢复收口，保证：

```text
开始认证
  → Loading = true
  → 成功 / 普通异常 / 取消
  → 正确结束或恢复状态
```

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

并已加入回归测试。

## 6. UI 修复：DialogButtonRow 保持调用方语义

历史 UI 调用点中存在大量只传部分参数的 `DialogButtonRow`。将参数直接改成“默认显示取消/确定”会在编译通过后引入新的界面语义变化。

因此改为：

- `negativeText` 默认 `null`；
- `positiveText` 默认 `null`；
- callback 默认 no-op；
- 调用方明确提供文本时才渲染对应按钮。

这样既消除旧调用点的编译阻塞，又避免无意给历史 Dialog 增加按钮。

## 7. UI 修复：LiquidGlassNavBar 边界与可访问性

本阶段继续处理底部 Liquid Glass 导航栏：

- 增加统一 `normalizeNavBarIndex()`，集中处理空 Tab、负数和越界 index；
- drag stop、外部 selectedIndex 同步均通过同一边界规则；
- 修复 drag lambda 中潜在的错误 labeled-return 用法；
- Tab item 增加 `Role.Tab`、selected 与 contentDescription 语义；
- 图标不再与外层 Tab 语义重复暴露描述，减少 TalkBack 重复朗读。

## 8. UI 回归测试

新增 `LiquidGlassNavBarTest`，覆盖：

- 空 Tab 时没有合法 index；
- selected index 小于 0 时归一到 0；
- selected index 大于最大 Tab 时归一到最后一个；
- 单 Tab 场景始终归一到 0。

测试采用纯函数，不依赖 Android UI 环境，因此可以稳定参与单元测试阶段。

## 9. TagManager 状态边界

`TagManagerSettingsViewModel` 已迁移为 constructor injection，并对：

- 全选时 UI state 尚未准备完成；
- 批量改色/删除后的 selection 清理；
- 空标签名；
- 重命名重复检测；
- 合并源/目标相同

增加了显式保护。

仍需继续处理的 UI 行为问题：

```text
搜索条件 query 目前仍由页面本地持有
↓
批量“全选”语义尚未完全绑定到当前搜索结果集
```

该项暂不通过“清空搜索”掩盖，因为正确方案应该把“当前可见集合”作为明确状态契约后再迁移。

## 10. 当前 CI / 构建验证状态

之前的 CI 编译失败主要来自历史 DI 符号、旧版 Compose Koin import 和 DialogButtonRow 参数契约不一致。针对这些编译阻塞，本阶段已完成集中修复。

随后产生的几个 workflow run 存在并发取消：其中一个 run 在 Android SDK 安装阶段即被后续提交取消，因此不能把它当作“构建失败”，也不能当作“构建通过”。

当前最新 push 已重新触发 `Build Debug APK` workflow，正在执行中；最终结论必须以该 run 真正进入 `./gradlew assembleDebug --stacktrace` 后的结果为准。

## 11. 仍在推进的工作

### P0：真实构建结论

等待最新稳定 CI 完成：

```text
./gradlew assembleDebug --stacktrace
```

若出现错误，只修复实际编译/测试错误，不进行无依据的大范围改动。

### P1：Compose / 状态回归测试

已完成：

- `LiquidGlassNavBar` 空 Tab / index 边界。

待继续：

- ContactDetail 写入完成后的刷新顺序；
- Scanner 保存期间重复提交保护；
- TagManager 搜索退出与 Dialog 状态机；
- TagManager 当前搜索结果集与批量选择契约。

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

## 12. 当前结论

本阶段已经把重点从单纯架构重构推进到真实 UI 行为收口：

1. 修复了 DialogButtonRow 的历史调用契约问题；
2. 修复了 LiquidGlassNavBar index 边界、拖动结束路径以及无障碍语义问题；
3. 为导航边界增加了稳定的纯单元回归测试；
4. TagManagerSettingsViewModel 完成 constructor injection；
5. 真实 CI 已重新触发，但截至本次记录仍不能宣称最终 assembleDebug 通过；
6. `KoinComponentBy` 目前是 Deprecated 过渡层，不再把“已删除”作为完成状态。

下一阶段应严格遵循：

```text
真实 CI 结果
  → 修复真实错误
  → 补关键 UI regression tests
  → 迁移剩余兼容层消费者
  → 删除兼容层
  → 最终 dead-code sweep
```
