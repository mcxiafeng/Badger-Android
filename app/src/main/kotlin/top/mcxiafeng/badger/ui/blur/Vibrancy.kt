package top.mcxiafeng.badger.ui.blur

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

// Adapted from miuix example Vibrancy.kt — https://github.com/YuKongA/miuix (Apache 2.0).

/**
 * 鲜艳度增强：提高饱和度 1.5x，让模糊后的背景颜色更鲜艳。
 */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}