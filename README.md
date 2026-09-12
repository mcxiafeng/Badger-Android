<div align="center">

# 🦡 Badger

### 一本不只是名片的电子名片册

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com)
[![iOS](https://img.shields.io/badge/Platform-iOS%2017%2B-silver?style=flat-square&logo=apple&logoColor=white)](https://www.apple.com/ios)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/UI-Compose%20Multiplatform%201.11.1-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Miuix](https://img.shields.io/badge/UI-Miuix%200.9.3-FF6B6B?style=flat-square)](https://github.com/compose-miuix/miuix)
[![Room](https://img.shields.io/badge/DB-Room%202.8.4-5B6FCF?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![Koin](https://img.shields.io/badge/DI-Koin%204.0.0-FF9800?style=flat-square)](https://insert-koin.io)
[![License](https://img.shields.io/badge/License-GPL--v3-blue?style=flat-square)](./LICENSE)

把联系人、社交身份、NFC 活链，统统装进你的口袋。

[✨ 功能](#-功能) · [🌐 支持平台](#-支持平台) · [📱 系统要求](#-系统要求) · [🏗️ 技术架构](#-技术架构) · [🗂️ 项目结构](#-项目结构) · [🛠️ 构建](#-本地构建) · [💬 联系方式](#-联系方式)

</div>

---

## ✨ 功能

<table>
<tr>
<td width="50%" valign="top">

### 📷 扫码添加联系人
CameraX + 微信二维码引擎（WeChatQRCode）扫描二维码，支持国内主流社交平台的全部短链跳转（如 `v.douyin.com`、`xhslink.com` 等）。
对实体名片拍照，ML Kit 中文 OCR 提取文字再交 AI 解析为结构化联系人。iOS 端走 AVFoundation + Vision。

</td>
<td width="50%" valign="top">

### 🪪 我的名片
把你所有的社交账号汇聚到一张数字名片上（13 个平台适配器），支持账号 / 链接两种录入方式。
通过 `Intent.ACTION_SEND` 一键分享好友，或导出 JSON 文件跨设备迁移；平台资料随云同步在设备间往返。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📇 名片夹（Collection）
按场景把联系人分到不同名片夹（工作 / 熟人 / 同好…），网格布局随屏幕尺寸自适应列数（手机 2 列 / 大屏 3-4 列），支持多选批量操作。
整夹可导出为 JSON 文件；导入时分析冲突，提供合并 / 改名 / 跳过三选项。

</td>
<td width="50%" valign="top">

### 📡 NFC 活链（动态 NFC）
NFC 标签只写入一个**短链 URL**。APP 内切换「我想让别人看到哪个平台」时，自动更新短链的目标地址（short.io 或自建短链服务）。
**一张卡可持续复用**，跳转目标任意切换、卡片无需重新碰写。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🤖 AI 提取 & 标签
- **AI OCR**：基于 OpenAI 兼容 `/v1/chat/completions`，支持 6 家厂商（DeepSeek / 通义千问 / 智谱清言 / 月之暗面 / 硅基流动 / 自定义）。
- **AI 标签推荐**：根据联系人 bio 自动归档到现有 Tag，本地兜底。
- **字段抽取**：从名片图片 OCR 文字 → 提取姓名 / 平台 / 账号 → 自动入库。

</td>
<td width="50%" valign="top">

### ☁️ 云同步
自建服务器双向同步：**乐观更新 + Outbox 离线写队列**——本地先行渲染，写意图落盘后后台推送，失败自动退避重试（Android WorkManager / iOS BGAppRefreshTask）。
断网可操作、联网自动续传，多设备间联系人 / 名片夹 / 用户资料保持一致。服务器地址用户自填（NAS / 自建服务器）。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📥 QAuxv 好友批量导入
从 QAuxv 导出的好友 JSON/CSV 文件批量入库，自动识别 QQ 号、正则匹配昵称。
带两阶段进度：先下载头像、再写入联系人。

</td>
<td width="50%" valign="top">

### 🔍 搜索 · 标签 · 位置
- **模糊搜索**：在 `name / note / bio` 上做 LIKE 匹配，支持拼音首字母排序。
- **多对多标签**：联系人 × 标签任意关联，自定义标签面板。
- **联系人位置**：国家 / 地区行政区划级联选择，随联系人同步往返。

</td>
</tr>
</table>

---

## 🌐 支持平台

图例：✅ 完整 · ⚠️ 仅识别（受平台政策限制，无法抓取资料）

| 平台 | 状态 |
|:---:|:---:|
| QQ · QQ 群 · B 站 · GitHub · Telegram · Telegram 群 · X (Twitter) · 个人网站 | ✅ |
| 微信 · 抖音 · 微博 · 小红书 · Facebook | ⚠️ |

> 想要新平台？提供样本链接开 Issue 即可。

---

## 📱 系统要求

| 项目 | 要求 |
|:---|:---|
| 最低 Android | 8.0（API 26） |
| 目标 / 编译 SDK | API 37 |
| 最低 iOS | 17.0 |
| JVM | 17 |
| ABI | `arm64-v8a` · `armeabi-v7a` · `x86_64`（按 ABI 拆分，**不输出 universal**） |

### 权限说明

| 权限 | 用途 | 必需 |
|:---|:---|:---:|
| 📷 `CAMERA` | 扫描二维码 / 拍摄名片 | ✅ |
| 🌐 `INTERNET` | AI 识别 / 头像抓取 / 云同步 / 短链更新 | ✅ |
| 📡 `NFC` | 写入 NFC 标签 | ❌ |

相机和 NFC 硬件均声明 `required="false"`，无相应硬件的设备仍可安装（功能降级）。

---

## 🏗️ 技术架构

Kotlin Multiplatform：业务主体（数据 / 同步 / 网络 / UI）位于 `shared/` commonMain，Android / iOS 各持平台壳与平台 actual。

```
┌──────────────────────────────────────────────────────────────┐
│             UI Layer（Compose Multiplatform 双端复用）         │
│        Miuix · Material 3 · miuix-blur · 自研 LiquidGlass      │
└──────────────────────────┬───────────────────────────────────┘
                           │ StateFlow / @Immutable UiState
┌──────────────────────────▼───────────────────────────────────┐
│               ViewModel（Koin 4 · koinViewModel）             │
└──────────────────────────┬───────────────────────────────────┘
                           │ Domain（UseCase）
┌──────────────────────────▼───────────────────────────────────┐
│    Data Layer（V2 cache 主路径 · Repository 薄协调层 +          │
│    DataSource 双层 · Room 2.8.4 KMP version 17 · 16 条迁移）    │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│   Sync：Outbox 写意图队列（乐观更新 · 退避重试） +              │
│   SyncEngine 双向同步（pull + push 重放）                      │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│   Network：ApiTransport 抽象（Android=OkHttp / iOS=Ktor） ·    │
│   ServerApi · PlatformAdapterRegistry × 13 · 短链服务          │
└──────────────────────────────────────────────────────────────┘
```

---

## 🗂️ 项目结构

```
├── shared/                        ← KMP 业务主体（commonMain 双端复用）
│   ├── src/commonMain/kotlin/top/mcxiafeng/badger/
│   │   ├── App.kt                 # 4 Tab 主框架 + 路由组合
│   │   ├── ai/                    # AiTagGenerator 标签推荐
│   │   ├── data/                  # Room 数据库 / cache entity / prefs / Outbox / Repository
│   │   ├── di/                    # Koin 公共模块（双端复用）
│   │   ├── domain/                # UseCase
│   │   ├── network/               # ServerApi / ApiTransport / 13 个 PlatformAdapter / 短链服务
│   │   ├── ocr/                   # AI OCR 服务 + 平台字段注册表
│   │   ├── pages/                 # auth / card / dashboard / person / scanner / settings / setupguide / social
│   │   ├── sync/                  # SyncEngine 双向同步 + Outbox 队列
│   │   ├── ui/                    # LiquidGlassNavBar / blur / components / windowsize / navigation
│   │   └── utils/                 # SafeLog 脱敏 / PinyinUtils / QrUtils / HttpUtil ...
│   ├── src/androidMain/           # 平台 actual：CameraX + WeChatQRCode + ML Kit / OkHttp / WorkManager / NFC ReaderMode
│   └── src/iosMain/               # 平台 actual：AVFoundation + Vision / Ktor / BGAppRefreshTask / CoreNFC
├── app/                           # Android 宿主壳（Application / MainActivity / 平台 Koin 模块 / Room schemas）
├── iosApp/                        # iOS 壳（XcodeGen + SwiftUI）
├── docs/                          # V2 客户端规约 / KMP 迁移计划 / 特效视觉规格
└── libdocs/                       # Miuix 源码参考（只读，勿改）
```

---

## 🛠️ 本地构建

### 1. 克隆与同步

```bash
git clone https://github.com/mcxiafeng/Badger-Android.git
cd Badger-Android
```

### 2. 构建变体

| 任务 | 命令 | 说明 |
|:---|:---|:---|
| Debug | `./gradlew assembleDebug` | 无混淆无压缩；`applicationId` 附加 `.debug`，`versionName` 附加 `-dev` |
| Beta | `./gradlew assembleBeta` | 混淆 + 资源压缩；`applicationId` 附加 `.beta`，`versionName` 附加 `-beta` |
| Release | `./gradlew assembleRelease` | 混淆 + 资源压缩 + release 签名 |

### 3. 测试与检查

| 任务 | 命令 |
|:---|:---|
| 全部单元测试（Robolectric） | `./gradlew test` |
| 仅 debug variant | `./gradlew testDebugUnitTest` |
| 静态检查 | `./gradlew lintDebug` |

### 4. iOS（KMP）

Windows 可交叉编译 shared 模块；链接 framework / 构建 App 需 macOS + Xcode 26：

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64   # 编译门禁（Windows 可跑）
# macOS 上：
cd iosApp && xcodegen generate                      # 生成 .xcodeproj（不入库）
```

### 5. Release 签名（可选）

`assembleRelease` 在没读环境变量时不会签名，但构建仍会成功（产出未签名 APK）。
要正式签名需设置：

```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export KEYSTORE_PASSWORD=********
export KEY_ALIAS=badger
export KEY_PASSWORD=********
```

> 只有 `beta` / `release` 变体会读取这些环境变量。

### 6. ABI 拆分

构建产物按 ABI 切分，**不**生成 universal APK：

```
app/build/outputs/apk/beta/
├── Badger-1.0.0-beta-3-arm64-v8a-20260713-1501.apk
├── Badger-1.0.0-beta-3-armeabi-v7a-20260713-1501.apk
└── Badger-1.0.0-beta-3-x86_64-20260713-1501.apk
```

命名规则：`Badger-${versionName}-${versionCode}-${abi}-${yyyyMMdd-HHmm}.apk`

### 7. 环境要求

- **JDK** 17（`gradle.properties` 已锁定路径）
- **Android SDK** 37
- **iOS 构建**需 macOS + Xcode 26
- **Gradle** Wrapper 已包含

---

## 🤝 贡献

欢迎提 Issue 和 PR！如果你：

- 🐞 **发现了 Bug** — 提交 Issue 时附复现步骤 + 设备信息 + logcat
- 🌐 **想新增平台解析** — 提供 1-2 条样本链接（最好是被加密短链 + 已解密的真实 URL 各一）
- 🌏 **想贡献翻译 / 文档** — 当前仅 `values/strings.xml`（中文），多语言资源待补
- 💡 **有新功能想法** — 先开 Issue 讨论，避免重复造轮子

> 参与开发请先阅读 [`AGENTS.md`](./AGENTS.md)（架构红线、日志规范、NFC 陷阱等约束）与 [`docs/BADGER_V2_CLIENT_PLAN.md`](./docs/BADGER_V2_CLIENT_PLAN.md)（客户端协议规约）。

---

## 💬 联系我们

一起讨论功能、反馈问题、催更 🦡

| 平台 | 链接 |
|:---|:---|
| QQ 群 | `1106424576` |
| Telegram 群 | `https://t.me/+TCvPsqPXQltjOWM1` |
| Matrix 房间 | `https://matrix.to/#/#Open-Badger-APP:matrix.org` |

---

## 📄 许可

本项目基于 [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html) 开源。

你可以自由地使用、修改和分发本项目，但所有衍生作品**必须同样以 GPL-3.0 协议开源**。完整条款见根目录 `LICENSE` 文件。

---

<div align="center">

**[⬆ 回到顶部](#-badger)**

Made with ❤️ & ☕ by [mcxiafeng](https://github.com/mcxiafeng)

</div>
