<div align="center">

# 🦡 Badger

### 一本不只是名片的电子名片册

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(8.0)-3DDC84?style=flat-square)](https://www.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-3DDC84?style=flat-square)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.12-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Miuix](https://img.shields.io/badge/UI-Miuix%200.9.3-FF6B6B?style=flat-square)](https://github.com/compose-miuix/miuix)
[![Room](https://img.shields.io/badge/DB-Room%20v5-5B6FCF?style=flat-square)](https://developer.android.com/training/data-storage/room)
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
CameraX + 微信 OpenCV 二维码引擎 + ZXing 三重识别，支持国内主流社交平台的全部短链跳转（如 `v.douyin.com`、`xhslink.com` 等）。
对实体名片拍照，ML Kit 中文 OCR 提取文字再交 AI 解析为结构化联系人。

</td>
<td width="50%" valign="top">

### 🪪 我的名片
把你所有的社交账号汇聚到一张数字名片上，支持账号 / 链接两种录入方式。
通过 `Intent.ACTION_SEND` 一键分享好友，或导出 JSON 文件跨设备迁移。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📇 名片夹（Collection）
按场景把联系人分到不同名片夹（工作 / 熟人 / 同好…），每行 2 个卡片的网格布局，支持多选批量操作。
整夹可导出为 JSON 文件；导入时分析冲突，提供合并 / 改名 / 跳过三选项。

</td>
<td width="50%" valign="top">

### 📡 NFC 活链（动态 NFC）
NFC 标签只写入一个**短链 URL**。APP 内切换「我想让别人看到哪个平台」时，自动调用 short.io 更新短链的目标地址。
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

### ☁️ WebDAV 备份
标准 WebDAV 协议，用户自填 NAS / 坚果云 / 自建服务器地址。
一键备份 `名片夹 + 设置（不含 API Key）`；一键恢复，自带冲突分析。
凭据存储：刷新令牌短期 + 访问令牌仅内存（API Key 明文 SharedPreferences，详见 AGENTS.md 安全章节）。

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 📥 QAuxv 好友批量导入
从 QAuxv 导出的好友 JSON/CSV 文件批量入库，自动识别 QQ 号、正则匹配昵称。
带两阶段进度：先下载头像、再写入联系人。

</td>
<td width="50%" valign="top">

### 🔍 FTS 全文搜索 & 🏷️ 多维标签
- **FTS4 全文搜索**：在 `name / note / bio` 上做模糊匹配，比 `LIKE '%...%'` 快得多。
- **多对多标签**：联系人 × 标签可任意关联，支持自定义标签面板。

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
| 目标 Android | API 37 |
| 编译 SDK | API 37 |
| JVM | 17 |
| ABI | `arm64-v8a` · `armeabi-v7a` · `x86_64`（按 ABI 拆分，**不输出 universal**） |

### 权限说明

| 权限 | 用途 | 必需 |
|:---|:---|:---:|
| 📷 `CAMERA` | 扫描二维码 / 拍摄名片 | ✅ |
| 🌐 `INTERNET` | AI 识别 / 头像抓取 / 云同步 / 短链更新 | ❌ |
| 📡 `NFC` | 写入 NFC 标签 | ❌ |

相机和 NFC 硬件均声明 `required="false"`，无相应硬件的设备仍可安装（功能降级）。

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────────┐
│                  UI Layer (Compose)                      │
│   Miuix (KMP) · Material 3 · Haze Blur · 自研 LiquidGlass │
└────────────────────────┬─────────────────────────────────┘
                         │ StateFlow / Compose State
┌────────────────────────▼─────────────────────────────────┐
│             ViewModel (Hilt @HiltViewModel)              │
│      @Immutable UiState · viewModelScope.launch          │
└────────────────────────┬─────────────────────────────────┘
                         │ Domain (UseCase)
┌────────────────────────▼─────────────────────────────────┐
│                     Domain Layer                          │
│  DuplicateDetection · FilterContacts · MergeContact       │
│  ParseQrCode · PrepareNfcWrite · SaveScannedContact       │
└────────────────────────┬─────────────────────────────────┘
                         │ Repository
┌────────────────────────▼─────────────────────────────────┐
│                     Data Layer                            │
│      Room (11 Entity · FTS4 · 4 Migration)               │
│      Coil (ImageLoader) · DataStore                       │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                    Network Layer                          │
│   OkHttp · WebDAV · ShortLink · Platform Adapter × 13    │
└──────────────────────────────────────────────────────────┘
```

---

## 🗂️ 项目结构

```
app/src/main/kotlin/top/mcxiafeng/badger/
├── App.kt · MainActivity.kt · BadgerApplication.kt
├── ai/            ← AI 标签推荐 + 请求构造
├── data/          ← Room Entity/DAO/DB、Converters、Prefs
│   └── repository/  ← Contact/Collection/Field/Tag/UserProfile Repository
├── di/            ← Hilt Modules（Database · Network · Data · Image · AI）
├── domain/        ← 7 个 UseCase
├── network/       ← WebDAV 客户端 / 短链服务 / 云同步
│   └── adapter/     ← 13 个 PlatformAdapter + Registry
├── ocr/           ← AI OCR / 平台字段表
├── pages/         ← 每个主要页面一个子包
│   ├── card/         名片夹
│   ├── person/       联系人
│   ├── scanner/      扫码
│   ├── settings/     设置（含 AI 配置 / WebDAV / NFC / 标签 / 日志）
│   ├── setupguide/   5 步首次启动引导
│   └── social/       我的名片 + NFC 写入
├── ui/            ← LiquidGlassNavBar / Avatar / Dialog / ImageCrop ...
│   ├── blur/         7 类 Blur Backdrop
│   ├── components/   通用 UI 组件
│   └── navigation/   自研 AppNavigator + Route
└── utils/         ← ColorExtractor / HttpUtil / PinyinUtils / ShortLinkUtils ...
```

---

## 🛠️ 本地构建

### 1. 克隆与同步

```bash
git clone https://github.com/yourname/badger.git
cd badger
```

### 2. 构建变体

| 任务 | 命令 | 说明 |
|:---|:---|:---|
| Debug | `./gradlew :app:assembleDebug` | 无混淆无压缩；`applicationId` 附加 `.debug`，`versionName` 附加 `-dev` |
| Beta | `./gradlew :app:assembleBeta` | 混淆 + 资源压缩；`applicationId` 附加 `.beta`，`versionName` 附加 `-beta` |
| Release | `./gradlew :app:assembleRelease` | 混淆 + 资源压缩 + release 签名 |

### 3. Release 签名（可选）

`assembleRelease` 在没读环境变量时不会签名，但构建仍会成功（产出未签名 APK）。
要正式签名需设置：

```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export KEYSTORE_PASSWORD=********
export KEY_ALIAS=badger
export KEY_PASSWORD=********
```

> 只有 `beta` / `release` 变体会读取这些环境变量。

### 4. ABI 拆分

构建产物按 ABI 切分，**不**生成 universal APK：

```
app/build/outputs/apk/beta/beta/
├── Badger-1.0.0-beta-3-arm64-v8a-20260713-1501.apk
├── Badger-1.0.0-beta-3-armeabi-v7a-20260713-1501.apk
└── Badger-1.0.0-beta-3-x86_64-20260713-1501.apk
```

命名规则：`Badger-${versionName}-${versionCode}-${abi}-${yyyyMMdd-HHmm}.apk`

### 5. 环境要求

- **Android Studio** Ladybug（2024.2.1）或更高
- **JDK** 17
- **Android SDK** 37
- **Gradle** Wrapper 已包含

---

## 🤝 贡献

欢迎提 Issue 和 PR！如果你：

- 🐞 **发现了 Bug** — 提交 Issue 时附复现步骤 + 设备信息 + logcat
- 🌐 **想新增平台解析** — 提供 1-2 条样本链接（最好是被加密短链 + 已解密的真实 URL 各一）
- 🌏 **想贡献翻译 / 文档** — 当前仅 `values/strings.xml`（中文），多语言资源待补
- 💡 **有新功能想法** — 先开 Issue 讨论，避免重复造轮子

> 参与开发请先阅读 [`AGENTS.md`](./AGENTS.md)，里面整理了架构红线、日志规范、NFC 陷阱等不容违反的约束。

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
