package top.mcxiafeng.badger.pages.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff

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
internal fun PrimaryButtonContent(isLoading: Boolean, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                size = 16.dp,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(BadgerSpacing.sm))
        }
        Text(text = if (isLoading) "处理中…" else label)
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
            minHeight = 48.dp,
        ) {
            if (sending) {
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
