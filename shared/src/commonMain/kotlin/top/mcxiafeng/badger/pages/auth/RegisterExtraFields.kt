package top.mcxiafeng.badger.pages.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "RegisterExtraFields"

/** 图形验证码占位串（等宽数量的空位提示）。 */
private const val CAPTCHA_PLACEHOLDER = "------"

/**
 * 注册表单扩展区 —— 供 [AuthRegisterCard] 与 SetupStepAccount 复用。
 *
 * 渲染内容全部由注册策略驱动：
 * 1. 确认密码（必填，与首次一致）；
 * 2. 策略状态条：加载中 / 加载失败 / 注册关闭；
 * 3. `requireCaptcha` → 图形验证码卡 + 输入框（整卡可点刷新）；
 * 4. `requireEmailCode` → 邮箱验证码行（[CodeSendRow]）。
 */
@Composable
internal fun RegisterExtraFields(
    state: RegisterUiState,
    enabled: Boolean,
    onPasswordAgain: (String) -> Unit,
    onCaptchaInput: (String) -> Unit,
    onEmailCodeInput: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSendEmailCode: () -> Unit,
) {
    // ---------- 确认密码 ----------
    FieldLabel("确认密码")
    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
    TextField(
        value = state.passwordAgain,
        onValueChange = onPasswordAgain,
        label = "需与密码一致",
        useLabelAsPlaceholder = true,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )

    // ---------- 策略状态条 ----------
    when {
        state.policyLoading -> {
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            PolicyStatusBar(message = "正在获取注册策略…", isError = false)
        }
        state.policyError != null && state.policy == null -> {
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            PolicyStatusBar(message = "注册策略加载失败:${state.policyError}", isError = true)
        }
        state.policy != null && !state.policy.allowRegister -> {
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            PolicyStatusBar(message = "注册功能已关闭,请联系管理员", isError = true)
        }
    }

    // ---------- 图形验证码 ----------
    if (state.policy?.requireCaptcha == true) {
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("图形验证码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        CaptchaCard(
            code = state.captchaCode,
            imageBase64 = state.captchaImageBase64,
            loading = state.captchaLoading,
            enabled = enabled,
            onRefresh = {
                BadgerLog.d(TAG, "captcha card tapped, refreshing")
                onRefreshCaptcha()
            },
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
        TextField(
            value = state.captchaInput,
            onValueChange = onCaptchaInput,
            label = "输入上方验证码",
            useLabelAsPlaceholder = true,
            enabled = enabled && !state.captchaLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ---------- 邮箱验证码 ----------
    if (state.policy?.requireEmailCode == true) {
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("邮箱验证码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        CodeSendRow(
            code = state.emailCodeInput,
            onCodeChange = onEmailCodeInput,
            codeHint = "6 位邮箱验证码",
            enabled = enabled,
            sending = state.sendingEmailCode,
            onSend = onSendEmailCode,
        )
        CodeHintText(state.emailCodeHint)
    }
}

/**
 * 策略加载 / 注册关闭状态条 —— 左侧色条 + 圆角背景填充区分严重级别。
 */
@Composable
private fun PolicyStatusBar(message: String, isError: Boolean) {
    val (barColor, bgColor, fgColor) = if (isError) {
        Triple(
            MiuixTheme.colorScheme.error,
            MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MiuixTheme.colorScheme.onErrorContainer,
        )
    } else {
        Triple(
            MiuixTheme.colorScheme.primary,
            MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            MiuixTheme.colorScheme.onPrimaryContainer,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BadgerRadius.inner))
            .background(bgColor)
            .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor),
        )
        Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = fgColor,
        )
    }
}

/**
 * 图形验证码卡 —— 品牌主色渐变锚点。
 *
 * - Linear Gradient（primaryContainer → primary）让卡片成为表单视觉锚点；
 * - Mono 加粗大字号 + 字距构成"这是一串验证码"的信号；
 * - 整卡可点刷新（MiuixIndication），与右侧「换一张」双通道入口；
 * - code 变化时 fade 切换，不闪跳。
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun CaptchaCard(
    code: String?,
    imageBase64: String?,
    loading: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
) {
    val captchaBitmap = remember(imageBase64) {
        imageBase64?.let {
            runCatching { Base64.decode(it).decodeToImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(BadgerRadius.inner))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.55f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, 56f * 3f),
                ),
            )
            .clickable(
                enabled = enabled && !loading,
                interactionSource = remember { MutableInteractionSource() },
                indication = MiuixIndication(),
                onClickLabel = "刷新验证码",
                onClick = onRefresh,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BadgerSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = loading,
                transitionSpec = {
                    fadeIn(tween(BadgerMotion.DURATION_BASE)) togetherWith
                        fadeOut(tween(BadgerMotion.DURATION_FAST))
                },
                label = "captchaLoading",
            ) { isLoading ->
                if (isLoading) {
                    CircularProgressIndicator(
                        size = 22.dp,
                        strokeWidth = 2.dp,
                    )
                } else if (captchaBitmap != null) {
                    // [C3 fix] 服务端返回 PNG 图片，不再暴露明文 code
                    Image(
                        bitmap = captchaBitmap,
                        contentDescription = "图形验证码",
                        modifier = Modifier.height(40.dp),
                    )
                } else {
                    CaptchaCodeText(code = code ?: CAPTCHA_PLACEHOLDER)
                }
            }

            TextButton(
                text = "换一张",
                enabled = enabled && !loading,
                onClick = onRefresh,
            )
        }
    }
}

/** 验证码逐字符渲染 —— Mono 加粗 + 大字号 + 字距。 */
@Composable
private fun CaptchaCodeText(code: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        code.take(6).forEach { char ->
            Text(
                text = char.toString().uppercase(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp,
                    color = MiuixTheme.colorScheme.onPrimary,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
