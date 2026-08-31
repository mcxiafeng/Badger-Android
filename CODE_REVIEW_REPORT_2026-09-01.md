# Badger-Android 代码质量 / API 契约审查报告

日期：2026-09-01  
审查基线：`dev` 分支 + 现有 `refactor/dev-cleanup-2026-08-31` 分支  
当前分支 HEAD：`a0c84d8a504f6be61dae55c8005fe205ad5e1738`  
当前分支相对 `dev`：65 commits ahead / 0 behind

## 1. 结论

当前项目已经完成一轮非常大规模的 V1 → V2/API 收口工作，整体质量明显高于最初的代码状态；现阶段不属于“不能维护”的屎山，更准确地说是“V1/V2 双时代代码正在收口，但部分 Service Locator、兼容 facade、UI 超大 Composable 和同步/缓存边界仍偏复杂”。

本轮继续处理了 4 个方向：

1. 对齐服务端 `/api/resolve/` 单条请求契约：单条请求现在真正发送 `{input}`，不再伪装成 `{items:[input]}` 批量请求。
2. 修复 API URL 拼接边界：无论 base URL 是否自带 `/`，都不会生成 `//api/...`。
3. 删除 `ContactNetworkResolver` 中已经不再承载业务职责的 `getResultInfo*` 投影 API 与对应测试，缩小兼容面。
4. CI 增加当前清理分支的 push 触发，并新增 resolver / URL join 回归测试。

## 2. 服务端契约核对

依据服务端交接文档：除 AI / short.io 两个代理成功响应外，其余 `/api` 接口统一使用 `ApiResult`；`Authorization: Bearer <token>` 是 Android 的标准携带方式；安装未完成时 `/api/setup/*` 之外统一受 503 安装守卫；`/api/resolve/`、`/api/settings/`、`/api/shortlinks/`、`/api/admin/shortlinks/` 明确要求尾斜杠。

本客户端当前已经按这些规则组织网络层：`ApiCore.unwrapApiResult()` 统一解析 ApiResult，AI / short.io 走裸 JSON，resolver 使用 `/api/resolve/` 尾斜杠。服务端还规定 resolver 批量上限 50、ResolveResult 为 camelCase，并已移除旧 `signature/avatar_url/jump_link` 字段。

### 已核实的契约一致项

- 登录：`POST /api/auth/login`，携带可选 `deviceId/deviceName`。
- 刷新：`POST /api/auth/refresh`，读取 `data.token`。
- 用户：Person / Collection / Tag / Device / Notification 基本 CRUD 路径均已迁移到 `/api/user/*`。
- 图片：`POST /api/user/upload` 使用 multipart 字段 `file`，并限制 5 MiB。
- 增量同步：`GET /api/user/sync?since=&limit=`。
- 用户设置：`shortioApiKey` 只写不回，读取侧使用 `shortioApiKeySet`。
- resolver：`POST /api/resolve/`，单条与批量两种 body 均按文档收口。
- AI：成功裸 JSON，失败通过 ApiResult / HTTP 错误路径处理。
- short.io：成功裸 JSON，且更新链接使用 POST `/api/proxy/shortio/links/{id}`。

## 3. 本轮发现并修复的问题

### P0/P1：resolver 单条请求形态错误

旧实现 `ResolverApi.resolveIdentify()` 通过 `resolveIdentifyBatch(listOf(input))` 间接发送批量 body。服务端文档将单条 body 明确定义为 `{input,...}`、响应 `data` 直接就是 ResolveResult；批量才是 `{items:[...]}` + `data.results`。

修复后：

- 单条请求发送 `{ "input": ... }`
- 单条响应直接读取 `data` 对象
- 保留批量请求的 `items/results` 逻辑
- 新增单条契约回归测试

### P1：base URL 双斜杠风险

旧 `urlOf()` 仅根据字符串两端条件拼接，在 base URL 已经包含尾斜杠时可能形成 `https://host//api/...`。

现在统一采用 `baseUrl.trimEnd('/') + '/' + path.trimStart('/')`。

### P2：无用 resolver 投影 API

`ContactNetworkResolver.getResultInfo()` / `getResultInfoInternal()` 已经没有生产职责，前面的 scanner 代码自己完成了 `IdentifyResponse → NetworkResolveResult` 映射；保留两个方法只会继续扩大 legacy facade API 表面积。

本轮删除这两个入口及其专属测试，保留真正被 scanner 使用的 identify / batch identify。

## 4. 现有代码质量评价

### 优点

**网络层已经明显“收口”。** `ServerApi` 现在是 facade，具体功能下沉到 Auth / AI / Resolver / short.io / user-domain API 类；`ApiCore` 统一处理 URL、认证头、HTTP 执行与 ApiResult。相比单个超大 `ServerApi`，可测试性和边界清晰度明显更好。

**防御式 JSON 解析已经比较成熟。** `takeIfString()` 已经阻止 object/array 被错误地强转为 string；时间、数值、通知、设备、统计等 DTO 解析均做了明显的空值保护。

**Room / V2 cache 路径比 V1 清晰。** Repository 多为薄协调层，映射集中到 `ContactMapper`，本地状态与远端同步开始分离。

**上一轮修复覆盖了不少真实 correctness bug。** 现有历史审查记录中已经处理了双 Cache 实例、token refresh TOCTOU、forgot-password 空操作、异步函数错误返回 -1、Bitmap double recycle、Compose launcher 重组时序、StateFlow stale derived state 等问题。

**测试意识已经到位。** 当前已经存在 resolver、HTTP utility、SafeLog 等针对边界问题的测试；本轮又增加了 URL join 与 canonical single-resolve 的回归测试。

### 仍然偏复杂 / 偏脆弱的地方

**1. `ContactNetworkResolver` 仍然是兼容 facade。** 它目前仍承担全局 Koin locator + legacy `IdentifyResponse` model，数据字段里还保留名为 `signature` 的内部属性。服务端契约本身已经只有 `description`，所以这个命名属于“客户端内部兼容债”，不是服务端必需。

**2. `GlobalContext.get()` / `KoinComponentBy.get()` 偏 Service Locator。** 多个 ViewModel 直接通过静态 locator 取 Repository/UseCase，隐藏了依赖关系，降低了纯单测能力。现阶段可以工作，但随着模块继续增加会慢慢成为主要架构债。

**3. UI 层仍存在大 Composable。** `ContactDetailPage`、`ContactDetailDialogs`、`ScannerDialogs` 参数很多，多个状态变量平铺在 Composable 作用域里；短期没有 correctness 问题，但维护成本高。

**4. V1 与 V2 数据模型仍共存。** 这在迁移期是合理的，但应该把 V1 读取/写入进一步限制到 migration / import / compatibility 边界，否则以后容易继续出现“新旧两套来源哪个是真实状态”的问题。

**5. Repository 的远端失败策略还不完全一致。** 部分操作是“本地成功、远端失败保留本地”，部分操作则先远端后本地；随着 PendingUpload / sync 队列成熟，最终应统一成“本地事务 + pending operation”，而不是每个 Repository 自己决定网络补偿。

## 5. 建议的下一阶段结构

建议最终形成以下边界：

```text
network/
  api/
    ApiCore
    AuthApi
    UserApi
    ResolveApi
    AiApi
    ShortLinkApi
  model/
    Auth
    User
    Resolve

repository/
  ContactRepository      # 只负责 domain orchestration
  CollectionRepository
  TagRepository
  UserProfileRepository

sync/
  PendingUploadScheduler
  PendingUploadWorker
  PendingUploadExecutor

ui/
  feature/contact/
  feature/scanner/
  feature/settings/

legacy/
  仅允许 migration / one-shot importer / V1 数据读取
```

其中 resolver 最值得继续做的一步，是把 `ContactNetworkResolver` 彻底替换为直接注入的 resolver repository/service，并删除 `IdentifyResponse.signature` 这一内部旧命名。

## 6. API 文档对客户端功能覆盖情况

### 已覆盖

认证、注册策略、验证码、忘记密码、refresh、登出、个人资料、人物、名片夹、标签、设备、通知、sync、settings、stats、图片上传、resolver、AI tag_generate、AI contact_ocr、short.io links/domains、本地短链及其用户端 CRUD。

### 目前明确没有看到对应客户端模型 / API 入口的功能

服务端文档定义了 `/api/user/backups` 全套备份接口，而且服务端特别注明兼容旧 `/v1/backups` 字段格式（`id/name/size/created_at`）。当前 Android 网络层没有看到对应 `BackupApi` / `ServerApi` facade，也没有相应的客户端 DTO。

这不是“应该删掉的死代码”，而是一个**服务端已存在、客户端目前缺失的功能面**。因此本轮没有凭空实现它，避免给没有 UI / use case 的功能增加另一套代码。

## 7. 死代码 / 兼容代码收敛情况

上一轮报告记录约 193 项发现、约 45 项修复；本轮继续复核后发现，其中若干“待修”项实际上已经在后续提交中完成，例如：

- `OnboardingPrefs` 已抽出共享 Preferences helper。
- `TagRepository` 中旧 `searchTagsFts` 重复入口已经消失。
- `FilterContactsUseCase` 已不再进入当前 Koin useCaseModule。
- `SettingsHomeViewModel` 的无效 Experimental API opt-in 已移除。
- `QrCoordinateMapper` / `CollectionExporter` / Scanner 相关多处死 import 与旧函数已在前轮处理。
- `NetworkModule` 的双 Cache、refresh TOCTOU、refresh 异常保护已处理。

因此不能再把旧报告里全部“待修项”原封不动视为当前状态；真正剩余的重点已经转向架构债，而不是简单删 import。

## 8. 测试与 CI 状态

此前分支最新完整 CI（commit `10a68b15...`）的 `Build Debug APK` 已成功。

本轮将 CI push 触发器加入当前清理分支 `refactor/dev-cleanup-2026-08-31`，因此后续继续往该分支提交会自动触发构建。当前 HEAD `a0c84d8...` 对应的 run `33418646171` 已进入队列，job 状态为 `queued`；本响应时刻尚未完成，因此不能把“本轮最终编译通过”当成已验证事实。

## 9. 推荐优先级

**P0：** 完成 `ContactNetworkResolver` → `ResolveRepository/Service` 的迁移，彻底去掉 `IdentifyResponse.signature` 旧命名。  
**P1：** 把 V1 entities 的生产读取路径冻结，只允许 migration/import 使用。  
**P1：** 统一 Repository 本地写入与 PendingUpload 的失败补偿模型。  
**P2：** 拆分 `ContactDetailDialogs` / `ScannerDialogs` / `ContactDetailPage` 的状态与参数。  
**P2：** 清理 Service Locator，改成构造注入到 ViewModel / UseCase。  
**P3：** 再做 Kotlin idiom / 命名 / 文件粒度的美化，不建议在 correctness 收口前继续大规模格式化。

## 10. 本轮修改清单

- `ApiCore.kt`：规范 API URL 拼接。
- `SecondaryApis.kt`：单条 resolver 使用 canonical `{input}` 请求及直接 ResolveResult 响应。
- `ContactNetworkResolver.kt`：删除未使用的 `getResultInfo*` 投影 API。
- `ContactNetworkResolverTest.kt`：删除对应旧 projection 测试，并改为 canonical single-item contract 测试。
- `ApiCoreTest.kt`：新增 URL join 回归测试。
- `.github/workflows/ci.yml`：加入当前清理分支 push 构建。
