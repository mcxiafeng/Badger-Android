package top.mcxiafeng.badger.pages.setupguide

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.mcxiafeng.badger.ui.LocalFloatingBarBottomPadding
import top.mcxiafeng.badger.ui.designsystem.BadgerSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

@Composable
internal fun StepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isActive = i == currentStep

            val size by animateDpAsState(
                targetValue = if (isActive) 10.dp else 8.dp,
                animationSpec = tween(300)
            )
            val color by animateColorAsState(
                targetValue = when {
                    isCompleted || isActive -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)
                },
                animationSpec = tween(300)
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SetupStepNavButtons(
    onBack: (() -> Unit)? = null,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    nextText: String = "下一步",
    backText: String = "上一步"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BadgerSpacing.md)
    ) {
        if (onBack != null) {
            TextButton(
                text = backText,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
        }
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
            enabled = nextEnabled
        ) {
            Text(text = nextText)
        }
    }
}

@Composable
internal fun SetupStepScaffold(
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    nextText: String = "下一步",
    backText: String = "上一步",
    skipText: String = "暂不填写",
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp + LocalFloatingBarBottomPadding.current)
        ) {
            content()
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = BadgerSpacing.xxl, vertical = BadgerSpacing.lg)
        ) {
            SetupStepNavButtons(
                onBack = onBack,
                onNext = onNext,
                nextEnabled = nextEnabled,
                nextText = nextText,
                backText = backText
            )
            if (onSkip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = BadgerSpacing.sm),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = skipText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.clickable { onSkip() }
                    )
                }
            }
        }
    }
}