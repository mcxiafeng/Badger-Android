package top.mcxiafeng.badger.page.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import top.mcxiafeng.badger.ui.designsystem.BadgerMotion
import top.mcxiafeng.badger.ui.designsystem.BadgerRadius
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// —— 页面域视觉常量（token 无对应项，按 ui-spec 以命名常量收敛在本文件） ——

private val HERO_DISC_SIZE = 64.dp
private val HERO_ICON_SIZE = 32.dp
private const val HERO_BG_ALPHA = 0.12f
private const val HERO_TITLE_SLIDE_DIVISOR = 8

private val MODE_SWITCH_HEIGHT = 44.dp
private val MODE_SWITCH_ITEM_HEIGHT = 36.dp
private const val MODE_TRACK_ALPHA = 0.55f
private const val MODE_GLOW_ALPHA = 0.18f

private val SPINNER_SIZE = 16.dp
private val SPINNER_STROKE = 2.dp

private val POLICY_BAR_WIDTH = 4.dp
private val POLICY_BAR_HEIGHT = 16.dp
private const val POLICY_ERROR_BG_ALPHA = 0.5f
private const val POLICY_INFO_BG_ALPHA = 0.35f

private val MIN_TOUCH_SIZE = 48.dp

// —— Hero 区（图标圆盘 + 标题/副标题切换动画） ——

@Composable
internal fun AuthHero(title: String, subtitle: String, icon: ImageVector) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BadgerSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(HERO_DISC_SIZE)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = HERO_BG_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(HERO_ICON_SIZE),
            )
        }
        Spacer(modifier = Modifier.height(BadgerSpacing.md))

        AuthHeroText(
            text = title,
            style = textStyles.headline1,
            color = colorScheme.onBackground,
            label = "authHeroTitle",
            slideDivisor = HERO_TITLE_SLIDE_DIVISOR,
        )
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))

        AuthHeroText(
            text = subtitle,
            style = textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
            label = "authHeroSubtitle",
        )
    }
}

@Composable
private fun AuthHeroText(
    text: String,
    style: TextStyle,
    color: Color,
    label: String,
    slideDivisor: Int = 0,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (slideDivisor == 0) {
                fadeIn(tween(BadgerMotion.DURATION_BASE)) togetherWith
                    fadeOut(tween(BadgerMotion.DURATION_FAST))
            } else {
                (fadeIn(tween(BadgerMotion.DURATION_BASE)) +
                    slideInVertically(tween(BadgerMotion.DURATION_BASE)) { it / slideDivisor })
                    .togetherWith(
                        fadeOut(tween(BadgerMotion.DURATION_FAST)) +
                            slideOutVertically(tween(BadgerMotion.DURATION_FAST)) { -it / slideDivisor }
                    )
            }
        },
        label = label,
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}

// —— 登录/注册分段切换（胶囊指示器随选页滑动） ——

@Composable
internal fun AuthModeSwitch(
    tabs: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(MODE_SWITCH_HEIGHT)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = MODE_TRACK_ALPHA))
            .padding(BadgerSpacing.xs),
    ) {
        val tabWidth = maxWidth / tabs.size
        val animatedOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = tween(BadgerMotion.DURATION_BASE, easing = FastOutSlowInEasing),
            label = "authModeSwitchOffset",
        )
        val primaryTint = MiuixTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(tabWidth)
                .height(MODE_SWITCH_ITEM_HEIGHT)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface)
                .drawBehind {
                    drawRoundRect(
                        color = primaryTint.copy(alpha = MODE_GLOW_ALPHA),
                        cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f),
                        topLeft = Offset(0f, 6f),
                        size = Size(size.width, size.height - 6f),
                    )
                },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(MODE_SWITCH_ITEM_HEIGHT)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled && !isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.subtitle,
                        color = if (isSelected) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}

// —— 表单字段组件 ——

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
internal fun FieldError(hint: String) {
    Text(
        text = hint,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.error,
    )
}

@Composable
internal fun CodeHintText(hint: String?) {
    if (hint == null) return
    Spacer(modifier = Modifier.height(BadgerSpacing.xs))
    Text(
        text = hint,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
internal fun SubmitButton(label: String, enabled: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(size = SPINNER_SIZE, strokeWidth = SPINNER_STROKE)
                Spacer(modifier = Modifier.width(BadgerSpacing.sm))
            }
            Text(text = if (isLoading) "处理中…" else label)
        }
    }
}

@Composable
internal fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = hint,
        useLabelAsPlaceholder = true,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = if (onImeAction != null) {
            KeyboardActions(onDone = { onImeAction() })
        } else {
            KeyboardActions.Default
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Lucide.EyeOff else Lucide.Eye,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun CodeSendRow(
    code: String,
    onCodeChange: (String) -> Unit,
    codeHint: String,
    enabled: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = code,
            onValueChange = onCodeChange,
            label = codeHint,
            useLabelAsPlaceholder = true,
            enabled = enabled && !sending,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        Button(
            onClick = onSend,
            enabled = enabled && !sending,
            minHeight = MIN_TOUCH_SIZE,
        ) {
            if (sending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(size = SPINNER_SIZE, strokeWidth = SPINNER_STROKE)
                    Spacer(modifier = Modifier.width(BadgerSpacing.xs))
                    Text(text = "发送中…")
                }
            } else {
                Text(text = "发送验证码")
            }
        }
    }
}

// —— 三张表单卡 ——

@Composable
internal fun AuthLoginCard(
    viewModel: AuthViewModel,
    enabled: Boolean,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
) {
    val credentials by viewModel.credentials.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    AuthFormCard {
        FieldLabel("用户名")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        TextField(
            value = credentials.username,
            onValueChange = viewModel::onUsername,
            label = "用户名",
            useLabelAsPlaceholder = true,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("密码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        AuthPasswordField(
            value = credentials.password,
            onValueChange = viewModel::onPassword,
            hint = "密码",
            enabled = enabled,
            imeAction = ImeAction.Done,
            onImeAction = { if (enabled && viewModel.canSubmitLogin()) viewModel.signIn() },
            modifier = Modifier.fillMaxWidth(),
        )

        val error = (submitState as? AuthSubmitState.Error)?.message
        if (error != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            FieldError(error)
        }

        Spacer(modifier = Modifier.height(BadgerSpacing.lg))
        SubmitButton(
            label = "登录",
            enabled = enabled && viewModel.canSubmitLogin(),
            isLoading = submitState is AuthSubmitState.Loading,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.sm))
        TextButton(
            text = "忘记密码？",
            enabled = enabled,
            onClick = onForgotPassword,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AuthRegisterCard(viewModel: AuthViewModel, enabled: Boolean, onSubmit: () -> Unit) {
    val credentials by viewModel.credentials.collectAsState()
    val register by viewModel.register.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    AuthFormCard {
        FieldLabel("用户名")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        TextField(
            value = credentials.username,
            onValueChange = viewModel::onUsername,
            label = "${AuthValidator.USERNAME_MIN} - ${AuthValidator.USERNAME_MAX} 位字母数字下划线",
            useLabelAsPlaceholder = true,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("邮箱")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        TextField(
            value = register.email,
            onValueChange = viewModel::onEmail,
            label = "example@domain.com",
            useLabelAsPlaceholder = true,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("密码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        AuthPasswordField(
            value = credentials.password,
            onValueChange = viewModel::onPassword,
            hint = "至少 ${AuthValidator.PASSWORD_MIN} 位",
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("确认密码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        AuthPasswordField(
            value = register.passwordAgain,
            onValueChange = viewModel::onPasswordAgain,
            hint = "需与密码一致",
            enabled = enabled,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )

        RegisterPolicyBar(register, onRetry = viewModel::loadRegisterPolicy)

        val hint = AuthValidator.registerHint(
            username = credentials.username,
            email = register.email,
            password = credentials.password,
            passwordAgain = register.passwordAgain,
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.xs))
            FieldError(hint)
        }

        val error = (submitState as? AuthSubmitState.Error)?.message
        if (error != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            FieldError(error)
        }

        Spacer(modifier = Modifier.height(BadgerSpacing.lg))
        SubmitButton(
            label = "注册",
            enabled = enabled && viewModel.canSubmitRegister(),
            isLoading = submitState is AuthSubmitState.Loading,
            onClick = onSubmit,
        )
    }
}

@Composable
internal fun AuthForgotCard(viewModel: AuthViewModel, enabled: Boolean, onSubmit: () -> Unit) {
    val forgot by viewModel.forgot.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    AuthFormCard {
        FieldLabel("邮箱")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        TextField(
            value = forgot.email,
            onValueChange = viewModel::onForgotEmail,
            label = "注册时使用的邮箱",
            useLabelAsPlaceholder = true,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("邮箱验证码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        CodeSendRow(
            code = forgot.code,
            onCodeChange = viewModel::onForgotCode,
            codeHint = "6 位数字验证码",
            enabled = enabled,
            sending = forgot.delivery.sending,
            onSend = viewModel::sendForgotCode,
        )
        CodeHintText(forgot.delivery.hint)

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("新密码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        AuthPasswordField(
            value = forgot.newPassword,
            onValueChange = viewModel::onForgotNewPassword,
            hint = "至少 ${AuthValidator.PASSWORD_MIN} 位",
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(BadgerSpacing.md))
        FieldLabel("确认新密码")
        Spacer(modifier = Modifier.height(BadgerSpacing.xs))
        AuthPasswordField(
            value = forgot.newPasswordAgain,
            onValueChange = viewModel::onForgotNewPasswordAgain,
            hint = "再输入一次",
            enabled = enabled,
            imeAction = ImeAction.Done,
            onImeAction = { if (enabled && viewModel.canSubmitForgotPassword()) viewModel.resetPassword() },
            modifier = Modifier.fillMaxWidth(),
        )

        val error = (submitState as? AuthSubmitState.Error)?.message
        if (error != null) {
            Spacer(modifier = Modifier.height(BadgerSpacing.sm))
            FieldError(error)
        }

        Spacer(modifier = Modifier.height(BadgerSpacing.lg))
        SubmitButton(
            label = "重置密码",
            enabled = enabled && viewModel.canSubmitForgotPassword(),
            isLoading = submitState is AuthSubmitState.Loading,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun AuthFormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(BadgerSpacing.lg),
        cornerRadius = BadgerRadius.card,
        content = content,
    )
}

/** 注册策略状态条：加载中 / 加载失败（可点击重试）/ 服务端限制说明。 */
@Composable
private fun RegisterPolicyBar(state: RegisterFormState, onRetry: () -> Unit) {
    when {
        state.policyLoading -> PolicyBar(message = "正在获取注册策略…", isError = false)
        state.policyFailed -> PolicyBar(
            message = "注册策略加载失败，点击重试",
            isError = true,
            onClick = onRetry,
        )
        state.policy != null && !state.policy.allowRegister ->
            PolicyBar(message = "注册功能已关闭，请联系管理员", isError = true)
        state.policy != null && state.policy.requireCaptcha ->
            PolicyBar(message = "服务端要求图形验证码，当前版本暂不支持", isError = true)
        state.policy != null && state.policy.requireEmailCode ->
            PolicyBar(message = "服务端要求邮箱验证码，当前版本暂不支持", isError = true)
    }
}

@Composable
private fun PolicyBar(message: String, isError: Boolean, onClick: (() -> Unit)? = null) {
    val colorScheme = MiuixTheme.colorScheme
    val bgColor = if (isError) {
        colorScheme.errorContainer.copy(alpha = POLICY_ERROR_BG_ALPHA)
    } else {
        colorScheme.primaryContainer.copy(alpha = POLICY_INFO_BG_ALPHA)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BadgerSpacing.sm)
            .clip(RoundedCornerShape(BadgerRadius.inner))
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = BadgerSpacing.md, vertical = BadgerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = POLICY_BAR_WIDTH, height = POLICY_BAR_HEIGHT)
                .clip(CircleShape)
                .background(if (isError) colorScheme.error else colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = if (isError) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer,
        )
    }
}
