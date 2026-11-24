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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import mozilla.components.compose.base.theme.AcornColors
import mozilla.components.compose.base.theme.AcornForkOverrides
import mozilla.components.compose.base.theme.ForkChromeStyle
import mozilla.components.compose.base.theme.ForkDialogStyle
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.compose.base.theme.ForkMenuStyle
import mozilla.components.compose.base.theme.ForkPillStyle
import mozilla.components.compose.base.theme.ForkSwitchStyle
import mozilla.components.compose.base.theme.acornDarkColorScheme
import mozilla.components.compose.base.theme.darkColorPalette
import org.mozilla.fenix.R

/**
 * 白い熊 火狐 UI — the fork's user-themable black/yellow design system.
 *
 * Every colour the app shows under this theme is a [KakoSlot]: a role with a two-tier
 * resolution — an explicit override stored in SharedPreferences, else a default that is one of
 * the two primaries, a colour that carries meaning, or a value derived from other roles. The
 * table of roles is generated (tools/kako/gen_roles.py → KakoRoleTable.kt); this object reads
 * and writes them and hands them to the three consumers:
 *
 *  - Compose, through [materialColorScheme] and [acornColors] (read in FirefoxTheme) and the
 *    widget styles pushed into [AcornForkOverrides] by [refreshChromeOverrides];
 *  - upstream's XML, through [KakoPalette], which lays the values over the colour resources
 *    the theme overlay names;
 *  - the fork's own views, which ask [color] directly.
 *
 * The two primaries are written here, once, and nowhere else.
 */

const val KAKO_THEME_UNSET = Int.MIN_VALUE
const val KAKO_PALETTE_BLACK = 0xFF000000.toInt()
const val KAKO_PALETTE_YELLOW = 0xFFFFFF00.toInt()

/** The pre-migration default yellow (material #FFEB3B); see [KakoTheme.migratePureYellow]. */
private const val KAKO_PALETTE_OLD_YELLOW = 0xFFFFEB3B.toInt()

internal const val PREFS_NAME = "kako_theme"
private const val KEY_ENABLED = "kako_theme_enabled"
private const val KEY_PURE_YELLOW_MIGRATED = "kako_pure_yellow_migrated"
const val KAKO_FONT_FAMILY_KEY = "kako_font_family"
const val KAKO_FONT_WEIGHT_KEY = "kako_font_weight"
const val KAKO_FONT_SCALE_KEY = "kako_font_scale"
const val KAKO_EXTENSION_ICON_SIZE_KEY = "kako_extension_icon_size"
const val KAKO_EXTENSION_ICON_SIZE_DEFAULT_DP = 24
const val KAKO_TOOLBAR_TWO_ROWS_KEY = "kako_toolbar_two_rows"

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
}

/**
 * What a default in the role table may use: another role, and the two ways a colour is derived
 * from others. The receiver of [KakoSlot.default].
 */
class KakoDefaults(private val resolve: (KakoSlot) -> Int) {
    /** The current colour of [slot]. */
    fun c(slot: KakoSlot): Int = resolve(slot)

    /** [f] of the way from [a] to [b], opaque. */
    fun mix(a: Int, b: Int, f: Float): Int = KakoTheme.mix(a, b, f)

    /** [c] at opacity [f]. */
    fun alpha(c: Int, f: Float): Int = KakoTheme.alpha(c, f)
}

object KakoTheme {

    /**
     * Bumped on every change made from the UI page; [org.mozilla.fenix.theme.FirefoxTheme]
     * reads it so Compose surfaces restyle immediately, and [KakoPalette] compares it with the
     * value an activity was built at.
     */
    val revision = mutableIntStateOf(0)

    @Volatile
    private var resolved: IntArray? = null

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        changed(context)
    }

    /** Every role resolved once: the stored value, else the default, each memoised. */
    private fun all(context: Context): IntArray {
        resolved?.let { return it }
        val prefs = prefs(context)
        val slots = KakoSlot.entries
        val out = IntArray(slots.size)
        val done = BooleanArray(slots.size)
        lateinit var defaults: KakoDefaults
        fun get(slot: KakoSlot): Int {
            val i = slot.ordinal
            if (done[i]) return out[i]
            val stored = prefs.getInt(slot.key, KAKO_THEME_UNSET)
            // Marked before the default is asked for, so a table that names itself cannot loop.
            done[i] = true
            out[i] = KAKO_PALETTE_YELLOW
            out[i] = if (stored != KAKO_THEME_UNSET) stored else slot.default(defaults)
            return out[i]
        }
        defaults = KakoDefaults(::get)
        slots.forEach { get(it) }
        resolved = out
        return out
    }

    fun color(context: Context, slot: KakoSlot): Int = all(context)[slot.ordinal]

    fun isOverridden(context: Context, slot: KakoSlot): Boolean =
        prefs(context).getInt(slot.key, KAKO_THEME_UNSET) != KAKO_THEME_UNSET

    fun setColor(context: Context, slot: KakoSlot, color: Int) {
        prefs(context).edit { putInt(slot.key, color) }
        changed(context)
    }

    /** Back to the default: the role follows the primaries again. */
    fun clearColor(context: Context, slot: KakoSlot) {
        prefs(context).edit { remove(slot.key) }
        changed(context)
    }

    /** The inherited default of [slot], whatever is stored for it. */
    fun defaultColor(context: Context, slot: KakoSlot): Int =
        slot.default(KakoDefaults { color(context, it) })

    /** Every colour role back to its default; widths, fonts and the page's arrangement stay. */
    fun resetAllColors(context: Context) {
        prefs(context).edit { KakoSlot.entries.forEach { remove(it.key) } }
        changed(context)
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
            remove(KAKO_TOOLBAR_TWO_ROWS_KEY)
            putBoolean(KEY_ENABLED, true)
        }
        changed(context)
    }

    /**
     * One-time migration: the default yellow changed from material yellow (#FFEB3B)
     * to pure yellow (#FFFF00). Rewrites every persisted slot override whose RGB part
     * is the old yellow to the new one, preserving the alpha byte. Runs at startup,
     * before any slot color is read; guarded by [KEY_PURE_YELLOW_MIGRATED].
     */
    fun migratePureYellow(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_PURE_YELLOW_MIGRATED, false)) return
        prefs.edit {
            KakoSlot.entries.forEach { slot ->
                val stored = prefs.getInt(slot.key, KAKO_THEME_UNSET)
                if (stored == KAKO_THEME_UNSET) return@forEach
                if ((stored and RGB_MASK) == (KAKO_PALETTE_OLD_YELLOW and RGB_MASK)) {
                    putInt(slot.key, (stored and RGB_MASK.inv()) or (KAKO_PALETTE_YELLOW and RGB_MASK))
                }
            }
            putBoolean(KEY_PURE_YELLOW_MIGRATED, true)
        }
        resolved = null
    }

    /** Whether the browser toolbar is laid out on two rows (address bar on top,
     * new tab/extensions/tab counter/menu below). Default on. */
    fun toolbarTwoRows(context: Context): Boolean =
        prefs(context).getBoolean(KAKO_TOOLBAR_TWO_ROWS_KEY, true)

    fun setToolbarTwoRows(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KAKO_TOOLBAR_TWO_ROWS_KEY, enabled) }
        changed(context)
    }

    /** Display size in dp of pinned extension toolbar icons. */
    fun extensionIconSizeDp(context: Context): Int =
        prefs(context).getInt(KAKO_EXTENSION_ICON_SIZE_KEY, KAKO_EXTENSION_ICON_SIZE_DEFAULT_DP)

    fun setExtensionIconSizeDp(context: Context, sizeDp: Int) {
        prefs(context).edit { putInt(KAKO_EXTENSION_ICON_SIZE_KEY, sizeDp) }
        bump()
    }

    /** Call after anything of the theme changed outside this object (an import, a font). */
    fun changed(context: Context) {
        resolved = null
        refreshChromeOverrides(context)
        bump()
    }

    fun bump() {
        resolved = null
        revision.intValue++
    }

    /**
     * Border thicknesses are stored in half-dp units (0.5dp steps — Compose renders
     * fractional dp fine). 0 hides the border.
     */
    fun dimenHalfUnits(context: Context, dimen: KakoDimen): Int =
        prefs(context).getInt(dimen.key, dimen.defaultDp * 2)

    /** Border thickness of [dimen] in dp. */
    fun dimenDp(context: Context, dimen: KakoDimen): Float =
        dimenHalfUnits(context, dimen) / 2f

    fun setDimenHalfUnits(context: Context, dimen: KakoDimen, halfUnits: Int) {
        prefs(context).edit { putInt(dimen.key, halfUnits) }
        changed(context)
    }

    /**
     * A [BorderStroke] for [slot]/[dimen], or null when disabled or 0-width.
     * Fenix composables (tab strip, menu cards) call this per frame of recomposition.
     */
    fun borderStroke(context: Context, slot: KakoSlot, dimen: KakoDimen): BorderStroke? {
        if (!isEnabled(context)) return null
        val width = dimenDp(context, dimen)
        if (width <= 0f) return null
        return BorderStroke(width.dp, Color(color(context, slot)))
    }

    /** The colour of an icon whose row does [role]. */
    fun iconColor(context: Context, role: ForkIconRole): Int =
        color(
            context,
            when (role) {
                ForkIconRole.CONNECT -> KakoSlot.MENU_ICON_CONNECT
                ForkIconRole.MESSAGE -> KakoSlot.MENU_ICON_MESSAGE
                ForkIconRole.CONTACT -> KakoSlot.MENU_ICON_CONTACT
                ForkIconRole.SHARE -> KakoSlot.MENU_ICON_SHARE
                ForkIconRole.VIEW -> KakoSlot.MENU_ICON_VIEW
                ForkIconRole.BLOCK -> KakoSlot.MENU_ICON_BLOCK
                ForkIconRole.DELETE -> KakoSlot.MENU_DELETE
            },
        )

    /**
     * Pushes the current roles into the android-components fork hooks (address-bar outline,
     * pills, switches, menus, dialogs, snackbars). Call after every change and once at startup.
     */
    fun refreshChromeOverrides(context: Context) {
        if (!isEnabled(context)) {
            AcornForkOverrides.addressBarBorder = null
            AcornForkOverrides.snackbarStyle = null
            AcornForkOverrides.pills = null
            AcornForkOverrides.switches = null
            AcornForkOverrides.menus = null
            AcornForkOverrides.dialogs = null
            return
        }
        fun c(slot: KakoSlot) = Color(color(context, slot))
        AcornForkOverrides.addressBarBorder =
            borderStroke(context, KakoSlot.ADDRESSBAR_BORDER, KakoDimen.ADDRESSBAR_BORDER_WIDTH)
        AcornForkOverrides.snackbarStyle = ForkChromeStyle(
            containerColor = c(KakoSlot.TOAST_BACKGROUND),
            contentColor = c(KakoSlot.TOAST_TEXT),
            border = BorderStroke(PILL_THIN_DP.dp, c(KakoSlot.TOAST_BORDER)),
        )
        AcornForkOverrides.pills = ForkPillStyle(
            ground = c(KakoSlot.BUTTON_BACKGROUND),
            frame = c(KakoSlot.BUTTON_BORDER),
            label = c(KakoSlot.BUTTON_TEXT),
            mainFrame = c(KakoSlot.BUTTON_MAIN_BORDER),
            mainLabel = c(KakoSlot.BUTTON_MAIN_TEXT),
            delete = c(KakoSlot.BUTTON_DELETE),
            ripple = c(KakoSlot.BUTTON_RIPPLE),
            thin = PILL_THIN_DP.dp,
            thick = PILL_THICK_DP.dp,
        )
        AcornForkOverrides.switches = ForkSwitchStyle(
            on = c(KakoSlot.SWITCH_ON),
            onDot = c(KakoSlot.SWITCH_ON_DOT),
            off = c(KakoSlot.SWITCH_OFF),
            offDot = c(KakoSlot.SWITCH_OFF_DOT),
        )
        AcornForkOverrides.menus = ForkMenuStyle(
            ground = c(KakoSlot.MENU_BACKGROUND),
            rowFrame = c(KakoSlot.MENU_ROW_FRAME),
            label = c(KakoSlot.MENU_LABEL),
            header = c(KakoSlot.MENU_HEADER),
            divider = c(KakoSlot.MENU_DIVIDER),
            delete = c(KakoSlot.MENU_DELETE),
            iconConnect = c(KakoSlot.MENU_ICON_CONNECT),
            iconMessage = c(KakoSlot.MENU_ICON_MESSAGE),
            iconContact = c(KakoSlot.MENU_ICON_CONTACT),
            iconShare = c(KakoSlot.MENU_ICON_SHARE),
            iconView = c(KakoSlot.MENU_ICON_VIEW),
            iconBlock = c(KakoSlot.MENU_ICON_BLOCK),
        )
        AcornForkOverrides.dialogs = ForkDialogStyle(
            ground = c(KakoSlot.DIALOG_BACKGROUND),
            frame = c(KakoSlot.DIALOG_BORDER),
            title = c(KakoSlot.DIALOG_TITLE),
            text = c(KakoSlot.DIALOG_TEXT),
        )
    }

    /**
     * The dynamic [AcornColors] palette fed to Compose in place of [darkColorPalette].
     */
    fun acornColors(context: Context): AcornColors {
        fun c(slot: KakoSlot) = Color(color(context, slot))
        val ground = c(KakoSlot.BACKGROUND)
        return darkColorPalette.copy(
            formDefault = c(KakoSlot.TEXT_SECONDARY),
            information = c(KakoSlot.SCHEME_INFORMATION),
            onInformation = ground,
            informationContainer = ground,
            onInformationContainer = c(KakoSlot.SCHEME_INFORMATION),
            success = c(KakoSlot.SCHEME_SUCCESS),
            onSuccess = ground,
            successContainer = ground,
            onSuccessContainer = c(KakoSlot.SCHEME_SUCCESS),
            warning = c(KakoSlot.SCHEME_WARNING),
            onWarning = ground,
            warningContainer = ground,
            onWarningContainer = c(KakoSlot.SCHEME_WARNING),
            // The "slightly dimmer surface" grey behind menu items, library tiles
            // and banners — readability comes from traced borders instead.
            surfaceDimVariant = c(KakoSlot.MENU_BACKGROUND),
            surfaceContainerSelected = c(KakoSlot.LIST_SELECTED),
            autofillText = c(KakoSlot.TEXT_SECONDARY),
            selectedText = c(KakoSlot.TEXT_SELECTION),
            iconPrivate = c(KakoSlot.PRIVATE_ICON),
            sheetOutline = c(KakoSlot.SHEET_BORDER),
        )
    }

    /**
     * The dynamic Material 3 [ColorScheme]: every entry upstream's screens read is a role.
     */
    fun materialColorScheme(context: Context): ColorScheme {
        fun c(slot: KakoSlot) = Color(color(context, slot))
        val background = c(KakoSlot.BACKGROUND)
        val text = c(KakoSlot.TEXT)
        val accent = c(KakoSlot.ACCENT)
        val onAccent = c(KakoSlot.TEXT_ON_ACCENT)
        val card = c(KakoSlot.CARD_BACKGROUND)
        val container = c(KakoSlot.SCHEME_CONTAINER)
        val onContainer = c(KakoSlot.SCHEME_ON_CONTAINER)

        return acornDarkColorScheme().copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            inversePrimary = accent,
            secondary = c(KakoSlot.SCHEME_SECONDARY),
            onSecondary = onAccent,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = container,
            onTertiaryContainer = onContainer,
            background = background,
            onBackground = text,
            surface = background,
            onSurface = text,
            surfaceVariant = c(KakoSlot.SCHEME_SURFACE_VARIANT),
            onSurfaceVariant = c(KakoSlot.TEXT_SECONDARY),
            // surfaceTint drives Material tonal elevation: keeping it at the background
            // color stops sheets/dialogs from drifting into olive-grey.
            surfaceTint = background,
            inverseSurface = c(KakoSlot.SCHEME_INVERSE_SURFACE),
            inverseOnSurface = c(KakoSlot.SCHEME_INVERSE_TEXT),
            error = c(KakoSlot.SCHEME_ERROR),
            onError = c(KakoSlot.SCHEME_ON_ERROR),
            errorContainer = background,
            onErrorContainer = c(KakoSlot.SCHEME_ERROR),
            outline = c(KakoSlot.BORDER),
            outlineVariant = c(KakoSlot.SCHEME_OUTLINE_VARIANT),
            scrim = c(KakoSlot.SCRIM),
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

    /** [f] of the way from [a] to [b], opaque. */
    fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and BYTE
            val y = (b shr shift) and BYTE
            return (x + (y - x) * f + ROUND).toInt().coerceIn(0, BYTE)
        }
        return (BYTE shl ALPHA_SHIFT) or (ch(RED_SHIFT) shl RED_SHIFT) or (ch(GREEN_SHIFT) shl GREEN_SHIFT) or ch(0)
    }

    /** [c] at opacity [f]. */
    fun alpha(c: Int, f: Float): Int =
        (c and RGB_MASK) or ((f * BYTE + ROUND).toInt().coerceIn(0, BYTE) shl ALPHA_SHIFT)

    /** The frame of every pill that is not the main action of its block, in dp. */
    const val PILL_THIN_DP = 1.25f

    /** The frame of the main action of a block, in dp. */
    const val PILL_THICK_DP = 3f

    /** How much of a disabled control is left. */
    const val DISABLED_ALPHA = 0.38f

    private const val RGB_MASK = 0x00FFFFFF
    private const val BYTE = 0xFF
    private const val ROUND = 0.5f
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
}
