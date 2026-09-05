# -*- coding: utf-8 -*-
"""
[KMP K13b] 业务层尾巴迁移脚本：app → shared（commonMain / androidMain）。

内容变换规则（逐条、显式）：
T1 android.util.Log → BadgerLog（shared commonMain 日志底座，签名兼容 d/i/w/e）
T2 System.currentTimeMillis() → nowMs()（shared.util expect，Android actual 同语义）
T3 e.javaClass.simpleName → e::class.simpleName（KMP：javaClass 是 JVM 专属）
T4 java.util.concurrent.atomic.AtomicBoolean → atomicfu atomic（shared 已依赖 atomicfu）
T5 Dispatchers.IO → BadgerDispatchers.io（kotlinx IO 调度器 JVM 专属）
T6 ContactNetworkResolver：删 KoinJavaComponent import + 3 个 @Deprecated 静态门面（JVM 专属）
T7 ShortLinkService：删未使用的 android.content.Context import
T8 PlatformManifestRepository：删未使用的 top.mcxiafeng.badger.R import
T9 WorldRegionRepository：HttpUtil.getResult → KtorHttpCore.get（Q2 裁决：common 网络底座）
T10 SyncEngine：Methods.deleteAvatarFile → deleteFileQuietly（shared.util expect 等语义）
"""
import re
from pathlib import Path

ROOT = Path(r"F:\Java\Android Project\Badger")
S = ROOT / "app" / "src" / "main" / "kotlin" / "top" / "mcxiafeng" / "badger"
CM = ROOT / "shared" / "src" / "commonMain" / "kotlin" / "top" / "mcxiafeng" / "badger"
AM = ROOT / "shared" / "src" / "androidMain" / "kotlin" / "top" / "mcxiafeng" / "badger"

BADGERLOG = "import top.mcxiafeng.badger.utils.BadgerLog"
NOWMS = "import top.mcxiafeng.badger.shared.util.nowMs"
DISPATCHERS = "import top.mcxiafeng.badger.shared.util.BadgerDispatchers"

def t_log(src):
    if "import android.util.Log" not in src:
        return src
    src = re.sub(r"^import android\.util\.Log\r?\n", "", src, flags=re.M)
    src = re.sub(r"\bLog\.(d|i|w|e)\(", r"BadgerLog.\1(", src)
    return ensure_import(src, BADGERLOG)

def t_nowms(src):
    if "System.currentTimeMillis()" not in src:
        return src
    src = src.replace("System.currentTimeMillis()", "nowMs()")
    return ensure_import(src, NOWMS)

def t_javaclass(src):
    return re.sub(r"(\w+)\.javaClass\.simpleName", r"\1::class.simpleName", src)

def t_atomicfu(src):
    if "import java.util.concurrent.atomic.AtomicBoolean" not in src:
        return src
    src = re.sub(r"^import java\.util\.concurrent\.atomic\.AtomicBoolean\r?\n", "", src, flags=re.M)
    src = src.replace("AtomicBoolean(false)", "atomic(false)")
    src = src.replace("private val started = atomic(false)", "private val started = atomic(false)")
    # AtomicBoolean 实例 API（compareAndSet/get/set）与 atomicfu 同名兼容
    return ensure_import(src, "import kotlinx.atomicfu.atomic")

def t_dispatchers(src):
    if "Dispatchers.IO" not in src:
        return src
    src = src.replace("Dispatchers.IO", "BadgerDispatchers.io")
    return ensure_import(src, DISPATCHERS)

def ensure_import(src, imp):
    if imp in src:
        return src
    lines = src.split("\n")
    idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
    pos = (idxs[-1] + 1) if idxs else 0
    lines.insert(pos, imp)
    return "\n".join(lines)

def drop_lines(src, patterns):
    lines = src.split("\n")
    out = [l for l in lines if not any(re.search(p, l) for p in patterns)]
    return "\n".join(out)

def t_resolver(src):
    # T6: 删 KoinJavaComponent import + 3 个 @Deprecated 静态门面（保留 TAG/MAX_BATCH_SIZE 常量）
    src = drop_lines(src, [r"^import org\.koin\.java\.KoinJavaComponent"])
    pat = re.compile(
        r"\n        @Deprecated\([^)]*?\)\n(?:        .*\n)+?        \)\n",
        re.M,
    )
    # 逐个 @Deprecated 函数块删除（从 @Deprecated 行到函数右括号行）
    lines = src.split("\n")
    out, i = [], 0
    while i < len(lines):
        if lines[i].strip().startswith("@Deprecated"):
            depth = 0
            j = i
            while j < len(lines):
                depth += lines[j].count("(") - lines[j].count(")")
                j += 1
                if depth == 0 and (")" in lines[j - 1]):
                    break
            # 跳过函数体：从 @Deprecated 后第一个非注解行到函数结束（右花括号回缩到 4 空格缩进）
            body_end = j
            k = j
            seen_open = False
            while k < len(lines):
                if "{" in lines[k]:
                    seen_open = True
                if seen_open and lines[k].strip() == ")":
                    body_end = k + 1
                    break
                if seen_open and lines[k].rstrip().endswith(")"):
                    # identifyWith(...) 单表达式结束
                    if not lines[k].lstrip().startswith("."):
                        body_end = k + 1
                        break
                k += 1
            i = max(body_end, j)
        else:
            out.append(lines[i])
            i += 1
    return "\n".join(out)

def t_worldregion(src):
    # T9: HttpUtil.getResult(url, timeoutMs = x) → KtorHttpCore().get(url, timeoutMs = x.toLong())
    src = re.sub(r"^import top\.mcxiafeng\.badger\.utils\.HttpUtil\r?\n", "", src, flags=re.M)
    src = ensure_import(src, "import top.mcxiafeng.badger.utils.KtorHttpCore")
    src = src.replace(
        "private suspend fun downloadWithFallback(",
        "private val http = KtorHttpCore()\n\n    private suspend fun downloadWithFallback(",
    )
    src = src.replace(
        "val result = HttpUtil.getResult(url, timeoutMs = timeoutMs)",
        "val result = http.get(url, timeoutMs = timeoutMs.toLong())",
    )
    return src

def t_syncengine(src):
    # T10: Methods.deleteAvatarFile → deleteFileQuietly（shared.util expect 顶层函数）
    src = re.sub(r"^import top\.mcxiafeng\.badger\.utils\.Methods\r?\n", "", src, flags=re.M)
    src = ensure_import(src, "import top.mcxiafeng.badger.shared.util.deleteFileQuietly")
    src = src.replace("Methods.deleteAvatarFile(", "deleteFileQuietly(")
    return src

CM_FILES = {
    "data/repository/CollectionRepository.kt": [],
    "data/repository/CollectionRepositoryImpl.kt": [t_log, t_nowms],
    "data/repository/FieldRepositoryImpl.kt": [t_log, t_nowms],
    "data/repository/UserProfileRepositoryImpl.kt": [t_log, t_nowms],
    "data/repository/NotificationRepository.kt": [t_log, t_javaclass],
    "data/CollectionUtils.kt": [t_nowms],
    "data/LegacyTagFixup.kt": [t_log],
    "data/importer/CollectionExporter.kt": [t_log, t_nowms],
    "sync/SyncEngine.kt": [t_log, t_atomicfu, t_syncengine],
    "domain/DuplicateDetectionUseCase.kt": [t_log],
    "domain/ImportProfileFieldsUseCase.kt": [t_log],
    "domain/PrepareNfcWriteUseCase.kt": [t_log],
    "domain/SelectPlatformUseCase.kt": [t_log],
    "network/ContactNetworkResolver.kt": [t_log, t_resolver],
    "network/ShortLinkService.kt": [t_log, t_dispatchers,
        lambda s: drop_lines(s, [r"^import android\.content\.Context"])],
    "network/PlatformManifestRepository.kt": [t_log, t_nowms, t_javaclass,
        lambda s: drop_lines(s, [r"^import top\.mcxiafeng\.badger\.R"])],
    "data/repository/WorldRegionRepository.kt": [t_worldregion, t_javaclass],
    "ai/AiTagGenerator.kt": [t_log, t_dispatchers],
    "ocr/AiOcrConfig.kt": [],
    "di/KoinComponentBy.kt": [],
}

AM_FILES = {
    "network/ApiCore.kt": [t_log],
    "network/AuthApi.kt": [t_log],
    "network/DeviceApi.kt": [t_log],
    "network/NotificationApi.kt": [t_log],
    "network/PersonApi.kt": [t_log],
    "network/SecondaryApis.kt": [t_log],
    "network/ServerApi.kt": [t_log],
    "network/ServerShortLinkApi.kt": [t_log],
    "network/SettingsApi.kt": [t_log],
    "network/StatsApi.kt": [t_log],
    "network/SyncApi.kt": [t_log],
    "network/V2DomainApi.kt": [t_log],
    "ocr/AiOcrService.kt": [t_log],
}

BANNED_CM = [
    (r"^import (android|java)\.", "platform import"),
    (r"^import androidx\.room\.withTransaction$", None),  # withTransaction 是合法 KMP API（room-runtime）
    (r"System\.currentTimeMillis", "System.currentTimeMillis"),
    (r"\.javaClass", "javaClass"),
]

def check_common(rel, src):
    problems = []
    for pat, label in BANNED_CM:
        if label is None:
            continue
        for i, l in enumerate(src.split("\n")):
            if re.search(pat, l):
                problems.append(f"  {rel}:{i+1} [{label}] {l.strip()}")
    return problems

def main():
    all_problems = []
    for rel, transforms in CM_FILES.items():
        src_path = S / rel
        src = src_path.read_text(encoding="utf-8")
        for t in transforms:
            src = t(src)
        for t in (t_log, t_nowms, t_javaclass, t_dispatchers):
            src = t(src)  # 兜底再过一遍，防漏
        probs = check_common(rel, src)
        all_problems += probs
        dst = CM / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(src, encoding="utf-8", newline="")
        src_path.unlink()
        print("CM :", rel)
    for rel, transforms in AM_FILES.items():
        src_path = S / rel
        src = src_path.read_text(encoding="utf-8")
        for t in transforms:
            src = t(src)
        dst = AM / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(src, encoding="utf-8", newline="")
        src_path.unlink()
        print("AM :", rel)
    if all_problems:
        print("\n!! commonMain 违规残留（需人工处理）:")
        for p in all_problems:
            print(p)
    else:
        print("\ncommonMain 自检通过：无平台 import / JVM API 残留")

if __name__ == "__main__":
    main()
