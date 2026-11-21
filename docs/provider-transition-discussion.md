# Badger 过渡与 JS provider 讨论稿

日期：2026-11-22。状态：供讨论的建议，尚未作为项目目标规范生效。本轮没有修改业务代码、提交或部署；本轮没有重跑构建、测试或设备联调。

## 建议方向

以后续 Android `:app` 与 Go 服务端为实现基线，先收口当前账号隔离和完整私有同步，再通过独立的 JS provider 能力实现平台资料补全。历史中的 Web 编辑、公开名片、NFC、OCR/AI、格式导入等继续保留为能力目标，分别接入已有保存、同步或分享边界。

按用户这次希望讨论的方向，历史功能可以重新排期；旧文档中的排除条目和架构选型需要显式更新，不能以目录中的旧推荐替代当前讨论结果。此次仅提出建议，不改写既有规范的状态。

## 当前事实与证据

- 客户端实际分支为 `NewMain`。待提交修改集中在账号空间、V1 认证、同步、导入、冲突和页面接线；其中包含大量未跟踪源码与测试。目录名不能代替分支名，普通 diff 不包含未跟踪文件。
- Go README 和协议文档明确以当前 Android 工作树及待提交代码为契约，路径为 `/api/v1/auth`、`/api/v1/sync`、`/api/v1/assets`，另有 `/api/info` 连通性入口。没有必要预设另做一套 Go 协议适配层。
- Go 是独立新实例，不接管 Java 数据库、旧实例身份、密钥和会话。需要重新登录并明确导入资料。客户端登录和导入是分别发生的动作。
- Go 的 `docs/android-e2e.md` 记录了 2026-11-21 当前 APK 的真实单模拟器登录、双向资料修改、私有图片摘要和退出撤销会话。属于已有记录，本轮没有复跑；不外推为真机、双设备并发、完整导入或 NFC 验证。
- Go 已有一致性备份；恢复到新世代并撤销旧会话的完整流程尚未实现。`docs/operations.md:28` 明确区分备份与恢复。
- 当前 Android `PlatformAdapterRegistry.kt:15` 按 ContactType 注册 13 个适配器。`PlatformFields.kt:43` 同时包含平台键、显示、识别、Android 打开方式等信息，平台获取还存在多个调用入口。
- 当前 `Models.kt:399` 对 `(contactId, platformKey)` 有唯一约束。当前同平台多资料源与未来同平台多账号，需要分别设计。
- 当前 `FullSyncContract.kt:85` 严格限制完整平台条目字段。新增 provider 的来源、版本等不能直接塞入同步对象；可以先作为补全结果元数据返回，永久保存/同步需要明确演进契约。

直接来源：

- [Go README](F:/GolandProjects/BadgerServer/README.md)
- [Go 协议](F:/GolandProjects/BadgerServer/docs/protocol.md)
- [Go Android 联调记录](F:/GolandProjects/BadgerServer/docs/android-e2e.md)
- [Go 验证记录](F:/GolandProjects/BadgerServer/docs/verification.md)
- [Go 运维边界](F:/GolandProjects/BadgerServer/docs/operations.md)
- [客户端账号同步说明](account-sync.md)
- [平台注册中心](../app/src/main/kotlin/top/mcxiafeng/badger/network/adapter/PlatformAdapterRegistry.kt)
- [平台字段定义](../app/src/main/kotlin/top/mcxiafeng/badger/ocr/PlatformFields.kt)
- [平台条目模型](../app/src/main/kotlin/top/mcxiafeng/badger/data/Models.kt)
- [同步字段契约](../app/src/main/kotlin/top/mcxiafeng/badger/network/sync/FullSyncContract.kt)

## 历史应提供什么

旧 Java 服务端 `2f88d42` 曾实现规则描述符体系，其中 `PlatformDescriptor` 已区分逻辑平台与具体 implementation；同时包含 detect、fetch、display、credential、params。`ResolverSelector` 提供加权选择和 failover。`1799a39` 后该体系及订阅相关实现被整体移除。

当前旧 Java `UapisQQ.resolve()` 的实际资料请求处于注释状态；其 `ExplicitRoutes` 没有注册 resolver。历史中某项能力存在，不代表当前分支仍可运行。恢复功能价值，需要重新核对契约和运行结果。

建议延续：平台与资料源分离、可替换获取实现、明确配置、统一候选结果。

建议逐步引入：订阅更新、凭据池、端点池、动态权重、复杂回退。先用可解释的 provider 优先顺序和明确失败类型验证需求。

## provider 的职责边界

| 概念 | 例子 | 稳定性与职责 |
| --- | --- | --- |
| 平台定义 | `qq`、`github` | 稳定平台键、显示与链接等受限元数据 |
| Provider 包 | QQ 某个接口的 JS 实现 | 获取和解析方法；独立包 ID、SDK 版本和发布版本 |
| Provider 实例配置 | 某个接口地址、凭据引用、启停与优先级 | 服主或账号配置；与脚本分开保存 |
| 已保存平台账号 | 用户录入的 QQ 号、原链接、手工显示名 | 属于用户业务资料；切换 provider 不改变它的身份 |

建议链路：用户输入 → 本地识别与草稿 → 明确触发补全 → Go provider 服务 → JS 与宿主受控 HTTP → 校验后的资料候选 → 用户确认 → 当前 Repository 保存 → 当前私有同步。

JS 不直接操作 Room、Go 数据库、Outbox 或当前登录会话。JS 所需 HTTP 由宿主提供；插件声明请求权限，实际权限由宿主和配置批准。公网平台和运维明确配置的本地连接器使用不同出站规则，插件不能凭 URL 自行获得内网访问权。凭据按 provider 实例和目标注入，不向脚本开放整个进程环境或其他账号凭据。

匹配/识别、取得资料、不支持、需要配置、限流和请求失败应明确区分。未取得资料时不把账号或链接伪装成昵称、头像或简介。回退至另一个供应商可能改变接收方，需限于已允许的来源。

服务器获取结果只产生候选；接受结果时重读业务数据并处理手工差异。在途任务固定账号与草稿上下文，切账号后不能把旧候选写入新空间。

## 两种灵活性分别交付

1. **已有平台更换 provider**：客户端使用稳定输入输出契约，可以不发布 APK 就更新服务端资料获取逻辑。
2. **增加全新平台**：当前客户端仍有枚举和编译期资源，需要受限平台描述、通用展示与未知平台保存能力；单独加入 JS 引擎不能保证这一点。

同平台多账号属于业务模型迁移，独立于以上两项。providerId 不能替代 platformKey，也不能替代已保存账号的稳定身份。

## 建议工作顺序与验收

| 工作单元 | 主要内容 | 验收依据 |
| --- | --- | --- |
| 当前基线收口 | 当前 Android 账号/同步改动、Go 初始基线、兼容关系与已有验证记录 | 实际构建、聚焦回归和对应制品；既有联调记录保留范围 |
| 统一资料获取入口 | 汇聚扫码、详情、引导、资料展示和旧 ID 提取中的网络获取 | 既有调用方均使用该入口，离线手工仍可保存，失败保留输入 |
| Provider SDK 与小型原型 | 包清单、JS、宿主 HTTP、统一结果、版本启用与回滚；先由服主管理受信脚本 | 更新脚本无需重编 Go/发布 APK；同平台两个 provider 可选择；异常不写业务库 |
| 真实平台纵切 | 一个容易验证的平台和一个实际常用平台；例如 GitHub 与 QQ | 超时/限流/缺凭据可区分；确认后才保存；账号切换、手工修改和回滚可验证 |
| 后续能力 | 通用平台目录、公开名片与稳定 HTTPS 入口、NFC、Web 本人编辑、OCR/AI、导入连接器等分别推进 | 每项围绕真实任务验收，复用保存/同步或公开发布边界 |

Provider 原型可用合成资料与基线收口并行。真实资料的正式切换还要验证恢复、新世代与原空间保留。可靠恢复优先用离线 CLI 闭环实现；自动计划和 Web 恢复控制流程可以另行排期。

Go 目前的数据库迁移器只接受现有版本及对应校验和。持久化新的 provider 配置表之前，需要先实现可连续升级的迁移路径。

## 待提交内容如何组织

建议先把现有账号/同步工作与后续 provider 变动分开。客户端逻辑分组可为：账号空间与本地兼容基础 → V1 认证与会话切换 → 同步/导入/冲突引擎 → 页面入口与自动触发；需要实际检查每个中间提交可构建，不能只按目录机械拆分。

Go 是另一个仓库，其基线和 provider 实现独立提交，并记录对应客户端版本。诊断转储、工具缓存和构建产物不与功能变动混为一组；本轮没有暂存、提交或删除它们。

## 本轮尚待讨论的关键选择

建议第一版由服主安装和审核脚本，普通用户使用已启用 provider，并填写获准的个人配置。如果第一版就允许普通用户上传任意脚本，进程、权限、网络与资源隔离会成为前置工作，实施范围需要相应调整。

执行运行时的事实与参考实现见 [NewAPI / Magpie 一手资料研究](provider-reference-research.md)。运行时选型需要实际验证脚本兼容、取消传播和资源限制；本稿没有声称完成此原型。

## 目录盘点范围

| 位置 | 本次观察到的内容与角色 |
| --- | --- |
| `F:\Java\Badger` | `Badger` 客户端原检出、`Badger-Server` Java 服务端、`badger-ui-demo`、`worktrees`，以及共享 `docs/tasks/scripts/tests`、架构和领域文档、工具与设计备份目录 |
| `F:\Java\Badger\worktrees` | `_backup-main-plus-new-backend-20261103`、`_backup-v2-20261105`、`_dev-ref`、当前 `main-plus-new-backend` |
| `F:\GolandProjects` | `BadgerServer`、`KeyRouter`、`SDKROOT`、`matrix-pylon`、`参考项目` |
| `KeyRouter` | 架构文档为 AI 网关设计，另有 Go 源码与构建产物；本轮未验证运行能力 |
| `matrix-pylon` | README 描述 Matrix / OneBot 桥，列出 NapCatQQ 等连接器；本轮未验证部署 |
| `参考项目` | 旧 Kotlin `di/network/utils`，含平台 adapter、WebDAV、短链等参考实现 |
| `F:\Java` | 另列举了 `Android_Project`、`Badger`、`Environment`、`MCP918`、`Projects`、`反编译`、`抓包`、`环境安装路径`、`环境本地库`；没有扩大为这些无关项目的源码审计 |

本轮对目录做有界盘点，对与迁移相关的实现和文档做只读核对；没有读取真实 Data、`.env` 或实际密钥，也没有运行参考项目或业务服务。
