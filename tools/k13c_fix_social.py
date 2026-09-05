# -*- coding: utf-8 -*-
"""[KMP K13c] SocialPage NFC host 去平台化 + 裁剪/选图边界化"""
from pathlib import Path
import re

p = Path(r"F:\Java\Android Project\Badger\shared\src\commonMain\kotlin\top\mcxiafeng\badger\pages\social\SocialPage.kt")
s = p.read_text(encoding="utf-8")

# 1) imports：去平台半边，补边界
for imp in ["import androidx.activity.compose.rememberLauncherForActivityResult\n",
            "import androidx.activity.result.PickVisualMediaRequest\n",
            "import androidx.activity.result.contract.ActivityResultContracts\n",
            "import androidx.compose.ui.platform.LocalContext\n",
            "import android.graphics.Bitmap\n",
            "import top.mcxiafeng.badger.platform.NfcActivityHost\n"]:
    s = s.replace(imp, "")
s = s.replace("import top.mcxiafeng.badger.platform.NfcWriter\n",
              "import top.mcxiafeng.badger.platform.NfcWriter\nimport top.mcxiafeng.badger.platform.PlatformImage\nimport top.mcxiafeng.badger.platform.loadOrientedImage\nimport top.mcxiafeng.badger.platform.rememberImagePickerLauncher\n")

# 2) NFC：NfcActivityHost attach/detach → ActivityHost（androidMain 边界注册表）
s = s.replace("""    val context = LocalContext.current
    val activity = context as? android.app.Activity
    // [修复防御]: NfcActivityHandler 持有 Activity 弱引用；remember(activity) 避免配置变更重建，""",
"""    // [KMP K13c] Activity 依赖走 ActivityHost 注册表（androidMain，MainActivity 挂钩），
    // UI 层不再下探 Context as? Activity
    // [修复防御]: handler 生命周期语义保留；remember { } 避免对话框重开时重建，""")
s = s.replace("    val nfcHandler = remember(activity) {", "    val nfcHandler = remember {")
s = s.replace("""        object : NfcActivityHandler {
            override fun startWriting(uri: String) {
                val act = activity ?: run {
                    BadgerLog.w(TAG, "NfcActivityHandler.startWriting: activity is null")
                    return
                }
                NfcActivityHost.attach(act)
                nfcWriter.startWriting(uri)
            }
            override fun stopWriting() {
                nfcWriter.stopWriting()
            }
        }""", """        object : NfcActivityHandler {
            override fun startWriting(uri: String) {
                nfcWriter.startWriting(uri)
            }
            override fun stopWriting() {
                nfcWriter.stopWriting()
            }
        }""")
s = s.replace("onDispose { NfcActivityHost.detach() }", "onDispose { /* NFC 写入状态由 NfcWriter 单例托管 */ }")

# 3) 裁剪回调
s = s.replace("""    val onCropConfirm: (Bitmap) -> Unit = { croppedBitmap ->
        showCropDialog = false
        cropSourceUri = null
        scope.launch {
            // [修复防御]: V2 cache 已不再支持 cardImagePath(V2 改用服务端 coverAvatarUrl)。
            // 此处只做用户反馈，避免误以为已生效。
            croppedBitmap.recycle()
            showToast("暂未支持自定义背景图")
        }
    }""", """    val onCropConfirm: (ByteArray) -> Unit = { _ ->
        showCropDialog = false
        cropSourceImage = null
        // [修复防御]: V2 cache 已不再支持 cardImagePath(V2 改用服务端 coverAvatarUrl)。
        // 此处只做用户反馈，避免误以为已生效。
        showToast("暂未支持自定义背景图")
    }""")

# 4) cropSourceUri 状态 + picker（沿用既有变量名就近替换）
s = s.replace("var cropSourceUri by remember { mutableStateOf<Uri?>(null) }",
              "var cropSourceImage by remember { mutableStateOf<PlatformImage?>(null) }")
m = re.search(r"val photoPickerLauncher = rememberLauncherForActivityResult\(\s*contract = ActivityResultContracts\.PickVisualMedia\(\)\s*\) \{ uri: Uri\? ->(.*?)\n    \}\n", s, re.S)
if m:
    body = m.group(1)
    new_block = """val photoPickerLauncher = rememberImagePickerLauncher { bytes ->
        if (bytes != null) {
            scope.launch(BadgerDispatchers.io) {
                val image = loadOrientedImage(bytes)
                if (image != null) {
                    cropSourceImage = image
                    showCropDialog = true
                }
            }
        }
    }
"""
    s = s.replace(m.group(0), new_block)

# 5) onPickCardImage
s = s.replace("""    val onPickCardImage: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }""", """    val onPickCardImage: () -> Unit = {
        photoPickerLauncher.launch()
    }""")

# 6) 裁剪对话框
s = s.replace("if (showCropDialog && cropSourceUri != null) {", "if (showCropDialog && cropSourceImage != null) {")
s = s.replace("onDismissRequest = { showCropDialog = false; cropSourceUri = null },", "onDismissRequest = { showCropDialog = false; cropSourceImage = null },")
s = s.replace("imageUri = cropSourceUri!!,", "image = cropSourceImage!!,")
s = s.replace("onDismiss = { showCropDialog = false; cropSourceUri = null },", "onDismiss = { showCropDialog = false; cropSourceImage = null },")

p.write_text(s, encoding="utf-8", newline="")
residual = {k: s.count(k) for k in ["LocalContext", "Bitmap", "NfcActivityHost", "PickVisualMedia", "cropSourceUri", "rememberLauncherForActivityResult"]}
print("SocialPage:", residual)
