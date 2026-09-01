# Badger Project Skills

> 从全局 `~/.claude/skills/` 引入的本项目相关 skills,落地于本仓库 `.claude/skills/`。
> 跟随符号链接已解引用为真实文件,可独立使用,不依赖全局安装。

## 总览

| # | Skill | 体积 | 类型 | 适用场景 |
|---|-------|------|------|---------|
| 1 | [claude-android-skill-main](./claude-android-skill-main) | 117K | Android 主栈 | NowInAndroid 架构参考:模块化、分层、Compose 模式、Gradle、测试 |
| 2 | [android-skills](./android-skills) | 1.5M | Android 子技能集 | AGP/Compose/CameraX/Navigation/Performance/Play/Edge-to-Edge 等子专题(8 个子 skill) |
| 3 | [awesome-android-agent-skills-main](./awesome-android-agent-skills-main) | 224K | Android 子技能集 | 架构、数据层、ViewModel、Coroutines、Compose UI/Nav、迁移、性能、测试(15 个子 skill) |
| 4 | [compose-expert](./compose-expert) | 2.8M | Compose | Compose API/Multiplatform/TV/导航/Material3 动效等 |
| 5 | [diegosouzapw-awesome-omni-skill-android-architecture-1.0.1](./diegosouzapw-awesome-omni-skill-android-architecture-1.0.1) | 16K | Android 架构 | MVVM / Clean Architecture / Hilt 注入(本项目用 Koin,需做适配) |
| 6 | [api-and-interface-design](./api-and-interface-design) | 16K | 接口设计 | REST/GraphQL 端点、模块边界、类型契约、前后端边界 |
| 7 | [bug-hunter](./bug-hunter) | 38M | 调试 | 多阶段(Hunter/Skeptic/Referee/Fixer)对抗式 bug 狩猎 |
| 8 | [code-review-and-quality](./code-review-and-quality) | 24K | 评审 | 多维度代码评审(合并前必做) |
| 9 | [code-simplification](./code-simplification) | 16K | 简化 | 不改行为的可读性/可维护性重构 |
| 10 | [context-engineering](./context-engineering) | 12K | 工程化 | Agent 上下文/CLAUDE.md/规则文件调优 |
| 11 | [debugging-and-error-recovery](./debugging-and-error-recovery) | 12K | 调试 | 系统化根因调试(测试挂/构建挂/行为不符) |
| 12 | [deprecation-and-migration](./deprecation-and-migration) | 16K | 迁移 | 老 API 退役、用户迁移决策 |
| 13 | [planning-and-task-breakdown](./planning-and-task-breakdown) | 12K | 规划 | 把需求拆成可执行任务,识别并行点 |
| 14 | [security-and-hardening](./security-and-hardening) | 24K | 安全 | 输入校验、认证、数据存储、第三方集成硬化 |
| 15 | [using-agent-skills](./using-agent-skills) | 12K | 元技能 | 何时/如何调用 skill 的总入口 |

合计 **50 个 SKILL.md**,**43 MB**。

## 不引入的(刻意剔除)

| Skill | 剔除理由 |
|-------|---------|
| `browser-testing-with-devtools` | 需要 Chrome DevTools MCP,本项目用 `android-adb` + `mobile-mcp` 即可 |
| `design-taste-frontend` / `high-end-visual-design` / `minimalist-ui` / `stitch-design-taste` / `redesign-existing-projects` | 面向网页/编辑型视觉系统,与本项目 Miuix 设计语言无关 |
| `imagegen-frontend-mobile` / `imagegen-frontend-web` / `mmx-cli` | 仅图片/媒体生成,不产出代码 |
| `mobile-app-design-mastery` | 聚焦设计而非代码,本项目 Miuix 已自带设计语言 |

## 在本项目中的典型用法

### 🟢 日常
- 新增 ViewModel/Screen → `compose-expert` + `claude-android-skill-main`(`compose-patterns.md`、`architecture.md`)
- 写 Room/Retrofit/Repository → `awesome-android-agent-skills-main`(`android-data-layer`、`android-coroutines`)
- 写测试 → `claude-android-skill-main/testing.md` + `bug-hunter/skills/recon`

### 🟡 阶段性
- 修复线上 bug → `bug-hunter`(Hunter 找→Skeptic 质疑→Referee 裁决→Fixer 修)+ `debugging-and-error-recovery`
- 合并前 → `code-review-and-quality` + `code-simplification`
- 做 API/接口设计 → `api-and-interface-design`
- 引入新依赖/换方案 → `security-and-hardening` + `planning-and-task-breakdown`

### 🔴 跨模块/重大变更
- 重构(模块拆分、迁移)→ `planning-and-task-breakdown` → `deprecation-and-migration` → `code-review-and-quality`
- 启用新 skill 体系 → `using-agent-skills`(元入口)
- 调优 agent 规则/CLAUDE.md → `context-engineering`

## 注意事项

1. **项目用 Koin,不用 Hilt**:`diegosouzapw-awesome-omni-skill-android-architecture-1.0.1` 主要讲 Hilt,本项目实际走 Koin。借鉴架构思想时记得把 DI 章节替换成 Koin 实现(参见 `app/src/main/java/top/mcxiafeng/badger/di/`)。
2. **Miuix vs 原生 Material3**:`compose-expert` 默认针对 Material3,本项目 UI 主要走 Miuix(`top.mcxiafeng.miuix.*`)。设计/导航模式可借鉴,组件 API 需适配。
3. **引入时间**:2026-09-01,基于全局 `~/.claude/skills/` 快照。
4. **更新策略**:全局 skills 升级时,运行 `cp -rL ~/.claude/skills/<skill> .claude/skills/<skill>` 单点覆盖即可(不要全量 `rm -rf` 后重拷,以免误删未在全局的本地 skill)。

## 验证

```bash
# 统计 SKILL.md 数量
find .claude/skills -name "SKILL.md" | wc -l
# 期望:50
```
