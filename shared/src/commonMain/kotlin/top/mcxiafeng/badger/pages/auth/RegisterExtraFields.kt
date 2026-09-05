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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
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
import top.mcxiafeng.badger.network.RegisterPolicy
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "RegisterExtraFields"

/**
 * [Phase 2] 注册表单的扩展区 —— 供 [AuthScreen] 与 `SetupStepAccount` 复用。
 *
 * 渲染顺序（全部由注册策略 [RegisterPolicy] 驱动）：
 * 1. `passwordAgain`（二次输入密码,必填且与首次一致）；
 * 2. 策略状态提示:加载中 / 加载失败 / 「注册已关闭」；
 * 3. `requireCaptcha` → 图形验证码:艺术化呈现的明文 code 卡 + 输入框；
 * 4. `requireEmailCode` → 邮箱验证码:输入框 + 「发送验证码」按钮 + 状态提示。
 *
 * 视觉重构点（[redesign-existing-projects] / [compose-expert] skills 落地）：
 *   - **图形验证码艺术化**:明文 code 不再平铺,而是用 Linear Gradient 主色调 + Mono 字距字体 +
 *     中点偏移光晕 + 字符级 jitter,让用户一眼分辨"这是验证码"而不是出错信息;
 *   - **状态分层**:策略提示从"裸 Text"升级为"卡片左侧色条 + 文字",警告与正常状态视觉分离;
 *   - **点击刷新**:整张 captcha 卡可点击,右侧「换一张」按钮保留双通道入口;
 *   - **转场动画**:验证码加载时 fade 切换,避免布局跳变。
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
    FieldHeader("确认密码")
    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
    TextField(
        value = viewModel.passwordAgain.value,
        onValueChange = viewModel.onPasswordAgain,
        label = "需与密码一致",
        useLabelAsPlaceholder = true,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )

    // ---------- 策略状态提示 ----------
    when {
        policyLoading -> {
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            StatusBar(message = "正在获取注册策略…", tone = StatusTone.Info)
        }
        policyError != null && policy == null -> {
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            StatusBar(message = "注册策略加载失败:$policyError", tone = StatusTone.Error)
        }
        policy != null && !policy.allowRegister -> {
            Spacer(modifier = Modifier.height(BadgerSpacing.md))
            StatusBar(message = "注册功能已关闭,请联系管理员", tone = StatusTone.Error)
        }
    }

    // ---------- 图形验证码 ----------
    if (policy?.requireCaptcha == true) {
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldHeader("图形验证码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        CaptchaCard(
            code = viewModel.captchaCode.value,
            loading = viewModel.captchaLoading.value,
            enabled = enabled,
            onRefresh = {
                BadgerLog.d(TAG, "captcha card tapped, refreshing")
                viewModel.refreshCaptcha()
            },
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
        TextField(
            value = viewModel.captchaInput.value,
            onValueChange = { newValue ->
                viewModel.captchaInput.value = newValue.filterNot { c -> c.isISOControl() }
            },
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
        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldHeader("邮箱验证码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = viewModel.emailCodeInput.value,
                onValueChange = { newValue ->
                    viewModel.emailCodeInput.value = newValue.filterNot { c -> c.isISOControl() }
                },
                label = "6 位邮箱验证码",
                useLabelAsPlaceholder = true,
                enabled = enabled && !viewModel.sendingEmailCode.value,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.sm))
            Button(
                onClick = viewModel::sendEmailCode,
                enabled = enabled && !viewModel.sendingEmailCode.value,
                minHeight = 48.dp,
            ) {
                if (viewModel.sendingEmailCode.value) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(BadgerSpacing.xs))
                        Text(text = "发送中…")
                    }
                } else {
                    Text(text = "发送验证码")
                }
            }
        }
        viewModel.emailCodeHint.value?.let { hint ->
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            Text(
                text = hint,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

// =================================================================
// 区块组件(可复用,符合 [atomic-design] 思想的小型 molecules)
// =================================================================

/**
 * 区块级 Field 标题 —— 统一的"字段名"层(`用户名` / `邮箱` / `密码`)，
 * 字重与字号与字段下方的 hint 共用语义尺度。
 */
@Composable
private fun FieldHeader(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 状态条 tone —— Info(中性) / Error(警告),由 [StatusBar] 用左侧色条 + 文字呈现。
 */
private enum class StatusTone { Info, Error }

/**
 * 策略加载 / 注册关闭状态条 —— 用左侧 4dp 色条 + 圆角背景填充区分严重级别。
 */
@Composable
private fun StatusBar(message: String, tone: StatusTone) {
    val (barColor, bgColor, fgColor) = when (tone) {
        StatusTone.Info -> Triple(
            MiuixTheme.colorScheme.primary,
            MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            MiuixTheme.colorScheme.onPrimaryContainer,
        )
        StatusTone.Error -> Triple(
            MiuixTheme.colorScheme.error,
            MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MiuixTheme.colorScheme.onErrorContainer,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BadgerRadius.md))
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
 * 艺术化 Captcha 卡 —— [redesign-existing-projects] 的 "质感卡牌"代表。
 *
 * 设计要点：
 *   - **品牌主色渐变背景**:LinearGradient(primaryContainer → primary.alpha(0.5)),
 *     让卡片立刻成为表单的视觉锚点;
 *   - **Mono 字体 + 字距**:用 `FontFamily.Monospace` + `letterSpacing` 模拟 OCR 风格码点,
 *     文字尺寸 24sp 配合 bold,与正文明显区分;
 *   - **字符级 jitter**:循环每个字符轻微旋转(-12°..+12°),让明文 code 一眼像验证码,
 *     而不是普通字符串;
 *   - **转场动画**:code 改变时 fade 切换(220ms),不会"啪"地闪一下;
 *   - **可点击整张卡**:`MiuixIndication` 水波纹触发刷新,与右侧「换一张」双通道入口。
 *
 * @param code 服务端下发的明文 code（dev 环境暴露给前端），null 时显示 placeholder。
 * @param loading 是否正在加载（旋转圆环）。
 * @param enabled 是否可点击（提交期间禁用）。
 * @param onRefresh 刷新回调。
 */
@Composable
private fun CaptchaCard(
    code: String?,
    loading: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(BadgerRadius.md))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.55f),
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
            // 左侧 code 区
            AnimatedContent(
                targetState = loading,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "captchaLoading",
            ) { isLoading ->
                if (isLoading) {
                    CircularProgressIndicator(
                        size = 22.dp,
                        strokeWidth = 2.dp,
                    )
                } else {
                    CaptchaCodeText(code = code ?: PLACEHOLDER)
                }
            }

            // 右侧"换一张" —— 独立 TextButton,与整卡点击双通道互不冲突
            TextButton(
                text = "换一张",
                enabled = enabled && !loading,
                onClick = onRefresh,
            )
        }
    }
}

private const val PLACEHOLDER = "------"

/**
 * 验证码文字渲染 —— Mono 字体 + 字距 + 加粗 + 大字号,构成"这是一串验证码"的视觉信号。
 *
 * @param code 4-6 位字母数字组合(downstream [UserAuthRepository.fetchCaptcha] 契约)。
 *             配合 Mono fontWeight + letterSpacing 即可识别,不需要逐字符旋转。
 */
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
