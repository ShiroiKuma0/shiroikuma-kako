/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import mozilla.components.compose.base.theme.AcornColors
import mozilla.components.compose.base.theme.acornDarkColorScheme
import mozilla.components.compose.base.theme.darkColorPalette
import org.mozilla.fenix.R

/**
 * 白い熊 火狐 UI — the fork's user-themable black/yellow design system.
 *
 * Mirrors the sister repos (shiroikuma-denwa, shiroikuma-messeji): every themable
 * attribute is a [KakoSlot] with a two-tier resolution — an explicit user override
 * stored in SharedPreferences, else a default inherited from the foundation slots.
 * Unset everything and the UI is the seeded black background / yellow text /
 * yellow border look.
 */

const val KAKO_THEME_UNSET = Int.MIN_VALUE
const val KAKO_PALETTE_BLACK = 0xFF000000.toInt()
const val KAKO_PALETTE_YELLOW = 0xFFFFEB3B.toInt()

private const val PREFS_NAME = "kako_theme"
private const val KEY_ENABLED = "kako_theme_enabled"
const val KAKO_FONT_FAMILY_KEY = "kako_font_family"
const val KAKO_FONT_WEIGHT_KEY = "kako_font_weight"
const val KAKO_FONT_SCALE_KEY = "kako_font_scale"
const val KAKO_EXTENSION_ICON_SIZE_KEY = "kako_extension_icon_size"
const val KAKO_EXTENSION_ICON_SIZE_DEFAULT_DP = 24

/**
 * Top-level sections of the 白い熊 火狐 UI page, in display order.
 */
enum class KakoSection(@param:StringRes val labelRes: Int) {
    FOUNDATION(R.string.kako_section_foundation),
    TOOLBAR(R.string.kako_section_toolbar),
    MENU(R.string.kako_section_menu),
    TABS(R.string.kako_section_tabs),
}

/**
 * One user-themable color attribute. [key] is the SharedPreferences key of its
 * override; resolution falls back to [KakoTheme.defaultColor] when unset.
 */
enum class KakoSlot(
    val key: String,
    val section: KakoSection,
    @param:StringRes val labelRes: Int,
) {
    // Foundation — everything else inherits from these.
    BACKGROUND("kako_theme_background", KakoSection.FOUNDATION, R.string.kako_slot_background),
    TEXT("kako_theme_text", KakoSection.FOUNDATION, R.string.kako_slot_text),
    TEXT_SECONDARY("kako_theme_text_secondary", KakoSection.FOUNDATION, R.string.kako_slot_text_secondary),
    ACCENT("kako_theme_accent", KakoSection.FOUNDATION, R.string.kako_slot_accent),
    BORDER("kako_theme_border", KakoSection.FOUNDATION, R.string.kako_slot_border),
    TEXT_ON_ACCENT("kako_theme_text_on_accent", KakoSection.FOUNDATION, R.string.kako_slot_text_on_accent),

    // Search bar / toolbar.
    TOOLBAR_FILL("kako_theme_toolbar_fill", KakoSection.TOOLBAR, R.string.kako_slot_toolbar_fill),

    // Menus, cards and dialogs.
    MENU_BACKGROUND("kako_theme_menu_background", KakoSection.MENU, R.string.kako_slot_menu_background),

    // Tab management.
    TAB_SELECTED("kako_theme_tab_selected", KakoSection.TABS, R.string.kako_slot_tab_selected),
    TAB_UNSELECTED("kako_theme_tab_unselected", KakoSection.TABS, R.string.kako_slot_tab_unselected),
}

object KakoTheme {

    /**
     * Bumped on every change made from the UI page; [org.mozilla.fenix.theme.FirefoxTheme]
     * reads it so Compose surfaces restyle immediately.
     */
    val revision = mutableIntStateOf(0)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        bump()
    }

    fun color(context: Context, slot: KakoSlot): Int {
        val override = prefs(context).getInt(slot.key, KAKO_THEME_UNSET)
        return if (override != KAKO_THEME_UNSET) override else defaultColor(context, slot)
    }

    fun isOverridden(context: Context, slot: KakoSlot): Boolean =
        prefs(context).getInt(slot.key, KAKO_THEME_UNSET) != KAKO_THEME_UNSET

    fun setColor(context: Context, slot: KakoSlot, color: Int) {
        prefs(context).edit { putInt(slot.key, color) }
        bump()
    }

    fun clearColor(context: Context, slot: KakoSlot) {
        prefs(context).edit { remove(slot.key) }
        bump()
    }

    /** Removes every override, falling back to the seeded black/yellow look. */
    fun resetAll(context: Context) {
        prefs(context).edit {
            KakoSlot.entries.forEach { remove(it.key) }
            remove(KAKO_FONT_FAMILY_KEY)
            remove(KAKO_FONT_WEIGHT_KEY)
            remove(KAKO_FONT_SCALE_KEY)
            remove(KAKO_EXTENSION_ICON_SIZE_KEY)
            putBoolean(KEY_ENABLED, true)
        }
        bump()
    }

    /** Display size in dp of pinned extension toolbar icons. */
    fun extensionIconSizeDp(context: Context): Int =
        prefs(context).getInt(KAKO_EXTENSION_ICON_SIZE_KEY, KAKO_EXTENSION_ICON_SIZE_DEFAULT_DP)

    fun setExtensionIconSizeDp(context: Context, sizeDp: Int) {
        prefs(context).edit { putInt(KAKO_EXTENSION_ICON_SIZE_KEY, sizeDp) }
        bump()
    }

    fun bump() {
        revision.intValue++
    }

    /**
     * The inherited default of [slot] — the sister repos' themeDefault() chain.
     */
    fun defaultColor(context: Context, slot: KakoSlot): Int = when (slot) {
        KakoSlot.BACKGROUND -> KAKO_PALETTE_BLACK
        KakoSlot.TEXT -> KAKO_PALETTE_YELLOW
        KakoSlot.ACCENT -> KAKO_PALETTE_YELLOW
        KakoSlot.TEXT_SECONDARY -> color(context, KakoSlot.TEXT).withAlpha(ALPHA_SECONDARY)
        KakoSlot.BORDER -> color(context, KakoSlot.ACCENT)
        KakoSlot.TEXT_ON_ACCENT -> color(context, KakoSlot.BACKGROUND)
        KakoSlot.TOOLBAR_FILL -> color(context, KakoSlot.BACKGROUND)
        KakoSlot.MENU_BACKGROUND -> color(context, KakoSlot.BACKGROUND)
        KakoSlot.TAB_SELECTED -> blendOnBackground(context, accent = color(context, KakoSlot.ACCENT))
        KakoSlot.TAB_UNSELECTED -> color(context, KakoSlot.MENU_BACKGROUND)
    }

    /**
     * The dynamic [AcornColors] palette fed to Compose in place of [darkColorPalette].
     */
    fun acornColors(context: Context): AcornColors {
        fun c(slot: KakoSlot) = Color(color(context, slot))
        val accent = c(KakoSlot.ACCENT)
        return darkColorPalette.copy(
            layer2 = c(KakoSlot.MENU_BACKGROUND),
            layer3 = c(KakoSlot.TOOLBAR_FILL),
            layerAccent = accent,
            layerAccentNonOpaque = accent.copy(alpha = 0.12f),
            formDefault = c(KakoSlot.TEXT_SECONDARY),
            textOnColorPrimary = c(KakoSlot.TEXT_ON_ACCENT),
            iconPrimaryInactive = c(KakoSlot.TEXT_SECONDARY),
            iconOnColor = c(KakoSlot.TEXT_ON_ACCENT),
            ripple = accent,
            tabActive = c(KakoSlot.TAB_SELECTED),
            tabInactive = c(KakoSlot.TAB_UNSELECTED),
        )
    }

    /**
     * The dynamic Material 3 [ColorScheme] carrying the main tokens
     * (surfaces, text, accent, outline) resolved from the slots.
     */
    fun materialColorScheme(context: Context): ColorScheme {
        fun c(slot: KakoSlot) = Color(color(context, slot))
        val background = c(KakoSlot.BACKGROUND)
        val text = c(KakoSlot.TEXT)
        val textSecondary = c(KakoSlot.TEXT_SECONDARY)
        val accent = c(KakoSlot.ACCENT)
        val border = c(KakoSlot.BORDER)
        val onAccent = c(KakoSlot.TEXT_ON_ACCENT)
        val card = c(KakoSlot.MENU_BACKGROUND)
        val accentContainer = accent.copy(alpha = SELECTED_TAB_FRACTION).compositeOver(background)

        return acornDarkColorScheme().copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = text,
            inversePrimary = accent,
            secondary = textSecondary,
            onSecondary = onAccent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = text,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = text,
            background = background,
            onBackground = text,
            surface = background,
            onSurface = text,
            surfaceVariant = card,
            onSurfaceVariant = textSecondary,
            surfaceTint = accent,
            inverseSurface = text,
            inverseOnSurface = background,
            outline = border,
            outlineVariant = border.copy(alpha = ALPHA_SECONDARY),
            surfaceBright = card,
            surfaceDim = background,
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceContainerHighest = card,
            surfaceContainerLow = background,
            surfaceContainerLowest = background,
        )
    }

    private fun blendOnBackground(context: Context, accent: Int): Int {
        val bg = Color(color(context, KakoSlot.BACKGROUND))
        return Color(accent).copy(alpha = SELECTED_TAB_FRACTION).compositeOver(bg).toArgb()
    }

    private const val ALPHA_SECONDARY = 0.6f
    private const val SELECTED_TAB_FRACTION = 0.25f
}

private fun Int.withAlpha(alpha: Float): Int =
    Color(this).copy(alpha = alpha).toArgb()
