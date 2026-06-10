/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import mozilla.components.compose.base.theme.AcornColors
import mozilla.components.compose.base.theme.AcornForkOverrides
import mozilla.components.compose.base.theme.ForkButtonStyle
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
    BUTTONS(R.string.kako_section_buttons),
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
    ADDRESSBAR_BORDER("kako_theme_addressbar_border", KakoSection.TOOLBAR, R.string.kako_slot_addressbar_border),
    TOOLBAR_TOP_BORDER("kako_theme_toolbar_top_border", KakoSection.TOOLBAR, R.string.kako_slot_toolbar_top_border),

    // Menus, cards and dialogs.
    MENU_BACKGROUND("kako_theme_menu_background", KakoSection.MENU, R.string.kako_slot_menu_background),
    MENU_BORDER("kako_theme_menu_border", KakoSection.MENU, R.string.kako_slot_menu_border),

    // Tab strip.
    TAB_SELECTED("kako_theme_tab_selected", KakoSection.TABS, R.string.kako_slot_tab_selected),
    TAB_UNSELECTED("kako_theme_tab_unselected", KakoSection.TABS, R.string.kako_slot_tab_unselected),
    TAB_BORDER("kako_theme_tab_border", KakoSection.TABS, R.string.kako_slot_tab_border),
    TAB_ACTIVE_BORDER("kako_theme_tab_active_border", KakoSection.TABS, R.string.kako_slot_tab_active_border),

    // Buttons.
    BUTTON_BACKGROUND("kako_theme_button_background", KakoSection.BUTTONS, R.string.kako_slot_button_background),
    BUTTON_TEXT("kako_theme_button_text", KakoSection.BUTTONS, R.string.kako_slot_button_text),
    BUTTON_BORDER("kako_theme_button_border", KakoSection.BUTTONS, R.string.kako_slot_button_border),
}

/**
 * One user-settable border thickness in dp (0 hides the border), sister-repo
 * ThemeDimen style.
 */
enum class KakoDimen(
    val key: String,
    val section: KakoSection,
    @param:StringRes val labelRes: Int,
    val defaultDp: Int,
) {
    ADDRESSBAR_BORDER_WIDTH("kako_dimen_addressbar_border", KakoSection.TOOLBAR, R.string.kako_dimen_addressbar_border, 1),
    TOOLBAR_TOP_BORDER_WIDTH("kako_dimen_toolbar_top_border", KakoSection.TOOLBAR, R.string.kako_dimen_toolbar_top_border, 1),
    MENU_BORDER_WIDTH("kako_dimen_menu_border", KakoSection.MENU, R.string.kako_dimen_menu_border, 1),
    TAB_BORDER_WIDTH("kako_dimen_tab_border", KakoSection.TABS, R.string.kako_dimen_tab_border, 1),
    TAB_ACTIVE_BORDER_WIDTH("kako_dimen_tab_active_border", KakoSection.TABS, R.string.kako_dimen_tab_active_border, 2),
    BUTTON_BORDER_WIDTH("kako_dimen_button_border", KakoSection.BUTTONS, R.string.kako_dimen_button_border, 1),
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
        refreshChromeOverrides(context)
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
        refreshChromeOverrides(context)
        bump()
    }

    fun clearColor(context: Context, slot: KakoSlot) {
        prefs(context).edit { remove(slot.key) }
        refreshChromeOverrides(context)
        bump()
    }

    /** Removes every override, falling back to the seeded black/yellow look. */
    fun resetAll(context: Context) {
        prefs(context).edit {
            KakoSlot.entries.forEach { remove(it.key) }
            KakoDimen.entries.forEach { remove(it.key) }
            remove(KAKO_FONT_FAMILY_KEY)
            remove(KAKO_FONT_WEIGHT_KEY)
            remove(KAKO_FONT_SCALE_KEY)
            remove(KAKO_EXTENSION_ICON_SIZE_KEY)
            putBoolean(KEY_ENABLED, true)
        }
        refreshChromeOverrides(context)
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
        KakoSlot.ADDRESSBAR_BORDER -> color(context, KakoSlot.BORDER)
        KakoSlot.TOOLBAR_TOP_BORDER -> color(context, KakoSlot.BORDER)
        KakoSlot.MENU_BACKGROUND -> color(context, KakoSlot.BACKGROUND)
        KakoSlot.MENU_BORDER -> color(context, KakoSlot.BORDER)
        // The active tab is marked by its border, not a fill (like Nightly).
        KakoSlot.TAB_SELECTED -> color(context, KakoSlot.MENU_BACKGROUND)
        KakoSlot.TAB_UNSELECTED -> color(context, KakoSlot.MENU_BACKGROUND)
        KakoSlot.TAB_BORDER -> color(context, KakoSlot.BORDER)
        KakoSlot.TAB_ACTIVE_BORDER -> color(context, KakoSlot.ACCENT)
        KakoSlot.BUTTON_BACKGROUND -> color(context, KakoSlot.BACKGROUND)
        KakoSlot.BUTTON_TEXT -> color(context, KakoSlot.ACCENT)
        KakoSlot.BUTTON_BORDER -> color(context, KakoSlot.BORDER)
    }

    /** Border thickness in dp of [dimen]; 0 hides the border. */
    fun dimenDp(context: Context, dimen: KakoDimen): Int =
        prefs(context).getInt(dimen.key, dimen.defaultDp)

    fun setDimenDp(context: Context, dimen: KakoDimen, dp: Int) {
        prefs(context).edit { putInt(dimen.key, dp) }
        refreshChromeOverrides(context)
        bump()
    }

    /**
     * A [BorderStroke] for [slot]/[dimen], or null when disabled or 0-width.
     * Fenix composables (tab strip, menu cards) call this per frame of recomposition.
     */
    fun borderStroke(context: Context, slot: KakoSlot, dimen: KakoDimen): BorderStroke? {
        if (!isEnabled(context)) return null
        val width = dimenDp(context, dimen)
        if (width <= 0) return null
        return BorderStroke(width.dp, Color(color(context, slot)))
    }

    /**
     * Pushes the current slot values into the android-components fork hooks
     * (address-bar outline, app-wide button style). Call after every change and
     * once at startup.
     */
    fun refreshChromeOverrides(context: Context) {
        if (!isEnabled(context)) {
            AcornForkOverrides.addressBarBorder = null
            AcornForkOverrides.buttonStyle = null
            return
        }
        AcornForkOverrides.addressBarBorder =
            borderStroke(context, KakoSlot.ADDRESSBAR_BORDER, KakoDimen.ADDRESSBAR_BORDER_WIDTH)
        AcornForkOverrides.buttonStyle = ForkButtonStyle(
            containerColor = Color(color(context, KakoSlot.BUTTON_BACKGROUND)),
            contentColor = Color(color(context, KakoSlot.BUTTON_TEXT)),
            border = borderStroke(context, KakoSlot.BUTTON_BORDER, KakoDimen.BUTTON_BORDER_WIDTH),
        )
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
            // The "slightly dimmer surface" grey behind menu items, library tiles
            // and banners — readability comes from traced borders instead.
            surfaceDimVariant = c(KakoSlot.MENU_BACKGROUND),
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
            // surfaceTint drives Material tonal elevation: keeping it at the background
            // color stops sheets/dialogs from drifting into olive-grey.
            surfaceTint = background,
            inverseSurface = text,
            inverseOnSurface = background,
            outline = border,
            outlineVariant = border.copy(alpha = ALPHA_SECONDARY),
            surfaceBright = card,
            // The address-bar pill fill in the composable toolbar.
            surfaceDim = c(KakoSlot.TOOLBAR_FILL),
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceContainerHighest = card,
            surfaceContainerLow = background,
            surfaceContainerLowest = background,
        )
    }

    private const val ALPHA_SECONDARY = 0.6f
    private const val SELECTED_TAB_FRACTION = 0.25f
}

private fun Int.withAlpha(alpha: Float): Int =
    Color(this).copy(alpha = alpha).toArgb()
