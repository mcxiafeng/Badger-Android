# -*- coding: utf-8 -*-
"""[KMP K13c] app 测试源集适配：internal 可见性 / LaunchAction 改型 / suspend 化"""
from pathlib import Path
import re

S = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger")
T = Path(r"F:\Java\Android Project\Badger\app\src\test\kotlin\top\mcxiafeng\badger")

def edit(path, *transforms):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    orig = s
    for t in transforms:
        s = t(s)
    if s != orig:
        p.write_text(s, encoding="utf-8", newline="")
        print("fixed:", p.name)

def add_imports(s, imports):
    for imp in imports:
        if imp not in s:
            lines = s.split("\n")
            idxs = [i for i, l in enumerate(lines) if l.startswith("import ")]
            pos = (idxs[-1] + 1) if idxs else 0
            lines.insert(pos, imp)
            s = "\n".join(lines)
    return s

# 1) shared internal 函数公开（测试/注入面）
def publicize(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    if old in s:
        s = s.replace(old, new)
        p.write_text(s, encoding="utf-8", newline="")
        print("publicized:", Path(path).name)

publicize(S / "network/ApiCore.kt", "internal fun <T> Response.unwrapApiResult", "fun <T> Response.unwrapApiResult")
publicize(S / "pages/scanner/ScannerMergeLogic.kt", "internal fun parseLocalContent", "fun parseLocalContent")

# 2) OutboxStoreTest：suspend recordFailure 包 runBlocking
p = T / "data/queue/OutboxStoreTest.kt"
s = p.read_text(encoding="utf-8")
s = s.replace('store.recordFailure(enqueued.outboxId, IllegalStateException("offline"), now = NOW)',
              'runBlocking { store.recordFailure(enqueued.outboxId, IllegalStateException("offline"), now = NOW) }')
p.write_text(s, encoding="utf-8", newline="")
print("OutboxStoreTest recordFailure bridged")

# 3) PlatformFieldsTest：LaunchAction.Intents → OpenUrls / CopyAndOpen 字段
p2 = T / "ocr/PlatformFieldsTest.kt"
s2 = p2.read_text(encoding="utf-8")
s2 = s2.replace("LaunchAction.Intents", "LaunchAction.OpenUrls")
s2 = s2.replace("action.intents", "action.targets")
s2 = s2.replace("copyAndOpen.intent", "copyAndOpen.uri")
p2.write_text(s2, encoding="utf-8", newline="")
print("PlatformFieldsTest LaunchAction updated")

# 4) ContactDetailViewModelBatchTest：ContactNetworkResolver.identifyBatch 静态 → Koin 实例
p3 = T / "pages/person/contact/ContactDetailViewModelBatchTest.kt"
s3 = p3.read_text(encoding="utf-8")
s3 = s3.replace("ContactNetworkResolver.identifyBatch(", "resolverInstance.identifyBatch(")
# resolverInstance 需要定义：看测试是否已有 resolver mock 字段
if "resolverInstance" not in s3.split("fun ")[0]:
    s3 = add_imports(s3, ["import top.mcxiafeng.badger.di.KoinComponentBy"])
    # 在类内 lazily 取 Koin 实例
    m = re.search(r"(@Before\s*\n\s*fun setUp\([^)]*\)\s*\{)", s3)
    if m:
        s3 = s3.replace(m.group(1), m.group(1) + '\n        resolverInstance = KoinComponentBy.get<ContactNetworkResolver>()')
    else:
        s3 = add_imports(s3, ["import org.koin.core.context.GlobalContext"])
    if "private lateinit var resolverInstance" not in s3:
        s3 = re.sub(r"(\nclass \w+[^{]*\{)", r"\1\n    private lateinit var resolverInstance: ContactNetworkResolver", s3, count=1)
p3.write_text(s3, encoding="utf-8", newline="")
print("BatchTest resolver instance wired")
print("done")
