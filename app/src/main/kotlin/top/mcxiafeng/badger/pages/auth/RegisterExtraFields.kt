package top.mcxiafeng.badger.pages.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.network.RegisterPolicy
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "RegisterExtraFields"

/**
 * [Phase 2] 注册表单的扩展区 —— 供 [AuthScreen] 与 `SetupStepAccount` 复用。
 *
 * 渲染顺序（全部由注册策略 [RegisterPolicy] 驱动）：
 * 1. `passwordAgain`（二次输入密码，必填且与首次一致）；
 * 2. 策略状态提示：加载中 / 加载失败 / 「注册已关闭」；
 * 3. `requireCaptcha` → 图形验证码：明文 code 样式化展示卡（点击换一张）+ 输入框；
 * 4. `requireEmailCode` → 邮箱验证码：输入框 + 「发送验证码」按钮 + 状态提示。
 *
 * 图形验证码的明文 code 是服务端 getCaptcha 的 dev 契约（`前端自行渲染图片`），
 * 这里用卡片 + 字距排版做"伪图形"占位，点击卡片即 refreshCaptcha()。
 */
@Composable
internal fun RegisterExtraFields(
    viewModel: AuthViewModel,
    enabled: Boolean,
) {
    val policy = viewModel.registerPolicy.value
    val policyLoading = viewModel.policyLoading.value
    val policyError = viewModel.policyError.value

    // ---------- 二次密码 ----------
    Text(
        text = "确认密码",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    TextField(
        value = viewModel.passwordAgain.value,
        onValueChange = viewModel.onPasswordAgain,
        label = "确认密码（需与密码一致）",
        useLabelAsPlaceholder = true,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )

    // ---------- 策略状态提示 ----------
    when {
        policyLoading -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "正在获取注册策略…",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        policyError != null && policy == null -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "注册策略加载失败：$policyError",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
        policy != null && !policy.allowRegister -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "注册功能已关闭，请联系管理员",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }

    // ---------- 图形验证码 ----------
    if (policy?.requireCaptcha == true) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "图形验证码",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // 展示卡：明文 code + 刷新入口（点击卡片或右侧图标换一张）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled && !viewModel.captchaLoading.value,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        Log.d(TAG, "captcha card tapped, refreshing")
                        viewModel.refreshCaptcha()
                    },
                ),
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (viewModel.captchaLoading.value) {
                    CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = viewModel.captchaCode.value ?: "------",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                TextButton(
                    text = "换一张",
                    enabled = enabled && !viewModel.captchaLoading.value,
                    onClick = viewModel::refreshCaptcha,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = viewModel.captchaInput.value,
            onValueChange = { viewModel.captchaInput.value = it.filterNot { c -> c.isISOControl() } },
            label = "输入上方验证码",
            useLabelAsPlaceholder = true,
            enabled = enabled && !viewModel.captchaLoading.value,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ---------- 邮箱验证码 ----------
    if (policy?.requireEmailCode == true) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "邮箱验证码",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = viewModel.emailCodeInput.value,
                onValueChange = { viewModel.emailCodeInput.value = it.filterNot { c -> c.isISOControl() } },
                label = "6 位邮箱验证码",
                useLabelAsPlaceholder = true,
                enabled = enabled && !viewModel.sendingEmailCode.value,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = viewModel::sendEmailCode,
                enabled = enabled && !viewModel.sendingEmailCode.value,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (viewModel.sendingEmailCode.value) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "发送中…")
                    }
                } else {
                    Text(text = "发送验证码")
                }
            }
        }
        viewModel.emailCodeHint.value?.let { hint ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
