# -*- coding: utf-8 -*-
"""
[KMP K13b] 编译修复批次 2：
F1 OutboxQueue 扩约：+getReady/markSuccess/backfillAfterCreate/recordFailure（SyncEngine 依赖面）
F2 SyncEngine: OutboxStore → OutboxQueue（androidMain 类型不可被 common 引用）
F3 @Volatile → kotlin.concurrent.Volatile（KMP std 版注解）
F4 KoinComponentBy: GlobalContext(JVM 专属) → KoinPlatformTools.defaultContext()（Koin KMP 正道）
F5 PlatformManifestRepository: ApiCore.TAG → "ServerApi" 字面量（ApiCore 在 androidMain）
F6 AiOcrConfig: 丢弃未使用的 ctx: android.content.Context 形参 + 更新 3 处调用点
"""
import re
from pathlib import Path

ROOT = Path(r"F:\Java\Android Project\Badger")
SH = ROOT / "shared" / "src" / "commonMain" / "kotlin" / "top" / "mcxiafeng" / "badger"
AP = ROOT / "app" / "src" / "main" / "kotlin" / "top" / "mcxiafeng" / "badger"

def edit(path, *transforms, must=True):
    p = ROOT / path if not Path(path).is_absolute() else Path(path)
    src = p.read_text(encoding="utf-8")
    orig = src
    for t in transforms:
        src = t(src)
    if src != orig:
        p.write_text(src, encoding="utf-8", newline="")
        print("edited:", path)
    elif must:
        print("NO-OP!:", path)

# F1+F2: OutboxQueue 扩约
OQ = SH / "sync" / "OutboxQueue.kt"
def extend_queue(src):
    add = """    /** 取消同实体未发的 CREATE/PATCH（DELETE 入队前调用），返回取消行数。 */
    fun cancelEntity(entityKind: EntityKind, localId: Long): Int

    /** [K13b 扩约] 取「到期待重放」行，FIFO；includeBackoff=true 无视退避窗口（手动同步用）。 */
    fun getReady(
        limit: Int = 20,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
        includeBackoff: Boolean = false,
    ): List<OutboxOp>

    /** [K13b 扩约] 成功出队（行已换代时为 no-op，新代 payload 留队重放）。 */
    fun markSuccess(outboxId: Long)

    /** [K13b 扩约] 失败记账：attempts 自增 + 指数退避（单条 SQL，原子）。 */
    fun recordFailure(outboxId: Long, error: Throwable, now: Long = top.mcxiafeng.badger.shared.util.nowMs())

    /** [K13b 扩约] CREATE 成功后的 uuid 兑现回填（PATCH/MEMBER/DELETE 行 remoteId 换代）。 */
    fun backfillAfterCreate(
        entityKind: EntityKind,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
    )
}"""
    old = """    /** 取消同实体未发的 CREATE/PATCH（DELETE 入队前调用），返回取消行数。 */
    fun cancelEntity(entityKind: EntityKind, localId: Long): Int
}"""
    if old not in src:
        raise SystemExit("OutboxQueue anchor missing")
    return src.replace(old, add)

edit(OQ, extend_queue)

# F2: SyncEngine 类型替换
SE = SH / "sync" / "SyncEngine.kt"
def se_fix(src):
    src = src.replace("private val outboxStore: OutboxStore,", "private val outboxStore: OutboxQueue,")
    src = src.replace("[includeBackoff] 见 [OutboxStore.getReady]", "[includeBackoff] 见 [OutboxQueue.getReady]")
    return src
edit(SE, se_fix)

# F3: @Volatile → kotlin.concurrent.Volatile
def volatile_fix(src):
    if "@Volatile" not in src:
        return src
    src = src.replace("@Volatile", "@kotlin.concurrent.Volatile")
    return src

for rel in [
    "data/repository/NotificationRepository.kt",
    "data/repository/WorldRegionRepository.kt",
    "network/PlatformManifestRepository.kt",
]:
    edit(SH / rel, volatile_fix)

# F4: KoinComponentBy KMP 化
KCB = SH / "di" / "KoinComponentBy.kt"
def kcb_fix(src):
    src = src.replace(
        "import org.koin.core.context.GlobalContext\n",
        "",
    )
    src = src.replace(
        "return runCatching { GlobalContext.get() }.getOrElse { err ->",
        "return runCatching { KoinPlatformTools.defaultContext().get() }.getOrElse { err ->",
    )
    return src
edit(KCB, kcb_fix)

# F5: PlatformManifestRepository TAG
PM = SH / "network" / "PlatformManifestRepository.kt"
def pm_fix(src):
    return src.replace('private const val TAG = ApiCore.TAG', 'private const val TAG = "ServerApi"')
edit(PM, pm_fix)

# F6: AiOcrConfig 去 ctx
AC = SH / "ocr" / "AiOcrConfig.kt"
def ac_fix(src):
    src = re.sub(r"\(@Suppress\(\"UNUSED_PARAMETER\"\) ctx: android\.content\.Context\)", "()", src)
    src = re.sub(r"\(ctx: android\.content\.Context\)", "()", src)
    src = re.sub(r"\(@Suppress\(\"UNUSED_PARAMETER\"\) ctx: android\.content\.Context, ", "(", src)
    src = re.sub(r"\(ctx: android\.content\.Context, ", "(", src)
    # setEnabled(ctx, b) 形态 → setEnabled(b)
    src = src.replace("fun setEnabled(ctx: android.content.Context, b: Boolean)", "fun setEnabled(b: Boolean)")
    return src
edit(AC, ac_fix)

# F6b: 调用点去 ctx 参数
def caller_fix(src, fname):
    # AiOcrConfig.isConfigured(ctx) / isAiOcrEnabled(ctx) / hasVisionModel(ctx) — ctx 是 LocalContext.current 等
    src = re.sub(r"AiOcrConfig\.(isConfigured|isAiOcrEnabled|hasVisionModel|supportsVision|setEnabled)\(([^()]+),\s*", r"AiOcrConfig.\1(", src)
    src = re.sub(r"AiOcrConfig\.(isConfigured|isAiOcrEnabled|hasVisionModel|supportsVision|setEnabled)\(appContext\)", r"AiOcrConfig.\1()", src)
    return src

for rel in [
    "pages/scanner/ScannerPage.kt",
    "pages/scanner/ScannerComponents.kt",
]:
    edit(AP / rel, lambda s: caller_fix(s, rel), must=False)

print("done")
