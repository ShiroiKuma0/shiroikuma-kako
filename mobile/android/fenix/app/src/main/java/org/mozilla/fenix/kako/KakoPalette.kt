/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.KakoColorLoaderBridge
import mozilla.components.support.ktx.android.view.ForkViewStyle
import mozilla.components.support.ktx.android.view.createWindowInsetsController
import mozilla.components.support.ktx.android.view.setNavigationBarColorCompat
import mozilla.components.support.ktx.android.view.setStatusBarColorCompat
import org.mozilla.fenix.R
import java.util.WeakHashMap

/**
 * 白い熊 火狐 UI, the MIXED way: lays the colour roles over the app's colour resources, so that
 * a layout, a drawable, a style or a theme attribute that names `@color/kako_role_…` — or one of
 * upstream's own colour names listed in [upstreamNames] — resolves to the role's current value.
 * Upstream's layouts are therefore not edited for colour at all.
 *
 * The values travel in a [ResourcesLoader] built through [KakoColorLoaderBridge]. It is applied
 * to the application's resources at start and to each activity's before the activity is created.
 * An activity built from older colours recreates itself when it is next resumed — never at once,
 * and not while the fork's own UI page is showing the change live; leaving that page recreates
 * it then.
 *
 * Needs Android 11. Below it nothing is laid over the resources and each role shows its default
 * from `values/kako_roles.xml`; what code paints follows the stored roles on every version.
 *
 * A colour name with a qualified copy in the APK (`values-night/`) cannot be fed: the loader's
 * table carries the default configuration only, and the more specific copy wins. That is why
 * the role resources live in `values/` alone, and why the theme overlay — not the name table —
 * is what carries upstream's `fx_mobile_*` scheme, all of which have a night copy.
 */
object KakoPalette {
    private const val TAG = "KakoPalette"

    private class Applied(val loader: ResourcesLoader, val revision: Int)

    private val applied = WeakHashMap<Resources, Applied>()
    private val builtAt = WeakHashMap<Activity, Int>()
    private val showingUiPage = WeakHashMap<Activity, Boolean>()

    /** Called once, from the application, before any activity exists. */
    fun install(app: Application) {
        ForkViewStyle.hook = KakoMenuRows
        apply(app)
        app.registerActivityLifecycleCallbacks(activityWatcher)
    }

    /**
     * Brings the resources of [context] (and of the application) up to the current colours.
     * Safe to call at any time; views that already exist keep what they were inflated with.
     */
    @Synchronized
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        applyTo(context)
        val app = context.applicationContext
        if (app != null && app !== context) applyTo(app)
    }

    /**
     * Recreates [activity] when it was built from older colours. The UI page calls this for
     * itself through the fragment watcher below when it closes.
     */
    fun recreateIfStale(activity: Activity) {
        val at = builtAt[activity] ?: return
        val now = KakoTheme.revision.intValue
        if (at == now || activity.isFinishing || activity.isDestroyed) return
        builtAt[activity] = now
        activity.recreate()
    }

    /**
     * The system bars and the window ground of [window], from their roles. Dark or light icons
     * follow from how bright each bar's colour is.
     */
    fun paintWindow(window: Window, context: Context) {
        val status = KakoTheme.color(context, KakoSlot.STATUS_BAR)
        val navigation = KakoTheme.color(context, KakoSlot.NAVIGATION_BAR)
        window.setStatusBarColorCompat(status)
        window.setNavigationBarColorCompat(navigation)
        window.createWindowInsetsController().apply {
            isAppearanceLightStatusBars = isLight(status)
            isAppearanceLightNavigationBars = isLight(navigation)
        }
    }

    /** The window ground of [activity], which the overlay can only give a default. */
    fun paintWindowGround(activity: Activity) {
        activity.window?.setBackgroundDrawable(ColorDrawable(KakoTheme.color(activity, KakoSlot.BACKGROUND)))
    }

    private fun isLight(color: Int): Boolean = ColorUtils.calculateLuminance(color) > LIGHT_LUMINANCE

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyTo(context: Context) {
        val resources = context.resources ?: return
        val current = applied[resources]
        // Under upstream's own look nothing is laid over the resources.
        if (!KakoTheme.isEnabled(context)) {
            if (current != null) {
                resources.removeLoaders(current.loader)
                applied.remove(resources)
            }
            return
        }
        val revision = KakoTheme.revision.intValue
        if (current != null && current.revision == revision) return
        try {
            val loader = KakoColorLoaderBridge.create(context, resourceColors(context)) ?: return
            if (current != null) resources.removeLoaders(current.loader)
            resources.addLoaders(loader)
            applied[resources] = Applied(loader, revision)
        } catch (e: RuntimeException) {
            // A table the system will not take, or a Resources without loaders (unit tests):
            // the role resources' defaults show.
            Log.e(TAG, "could not lay the colour roles over the resources", e)
        }
    }

    /** Colour resource id → ARGB: every role's own resource, then upstream's names. */
    private fun resourceColors(context: Context): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        KakoSlot.entries.forEach { out[it.colorRes] = KakoTheme.color(context, it) }
        upstreamNames(context).forEach { (id, slot) -> out[id] = KakoTheme.color(context, slot) }
        return out
    }

    @Volatile
    private var names: Map<Int, KakoSlot>? = null

    /**
     * Upstream's colour names that layouts, drawables and styles use directly, each with the
     * role of what it paints. Resolved once; a name this build does not have is skipped.
     */
    @SuppressLint("DiscouragedApi")
    private fun upstreamNames(context: Context): Map<Int, KakoSlot> {
        names?.let { return it }
        val out = HashMap<Int, KakoSlot>()
        OWN_NAMES.forEach { (id, slot) -> out[id] = slot }
        val resources = context.resources
        val pkg = context.packageName
        fun byName(name: String, slot: KakoSlot) {
            val id = resources.getIdentifier(name, "color", pkg)
            // Only plain colour values: a selector file of the same type cannot be overridden.
            if (id != 0 && isColorValue(resources, id)) out[id] = slot
        }
        OTHER_MODULE_NAMES.forEach { (name, slot) -> byName(name, slot) }
        // Upstream's scheme, in its three spellings. The plain ones have a night copy and are
        // fed only where the device is in the day configuration; the private and OLED ones
        // have none and always follow.
        SCHEME_PREFIXES.forEach { prefix ->
            SCHEME_SUFFIXES.forEach { (suffix, slot) -> byName(prefix + suffix, slot) }
        }
        names = out
        return out
    }

    private fun isColorValue(resources: Resources, id: Int): Boolean =
        try {
            val value = TypedValue()
            // Resolved: an alias of a plain colour counts, a selector file (a string) does not.
            resources.getValue(id, value, true)
            value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        } catch (_: Resources.NotFoundException) {
            false
        }

    private val activityWatcher = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            apply(activity)
            builtAt[activity] = KakoTheme.revision.intValue
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            // Below Android 10 there is no pre-created call.
            if (builtAt[activity] == null) builtAt[activity] = KakoTheme.revision.intValue
            (activity as? FragmentActivity)?.supportFragmentManager?.apply {
                registerFragmentLifecycleCallbacks(uiPageWatcher, true)
                registerFragmentLifecycleCallbacks(KakoPreferenceRows, true)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (builtAt[activity] == KakoTheme.revision.intValue) return
            if (showingUiPage[activity] == true) {
                // The page repaints itself; what it opens next (a dialog, a picker row)
                // should still resolve today's colours.
                apply(activity)
            } else {
                recreateIfStale(activity)
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /** Knows when the fork's UI page is on screen, and recreates the activity when it leaves. */
    private val uiPageWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            if (f is KakoUiSettingsFragment) f.activity?.let { showingUiPage[it] = true }
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
            if (f !is KakoUiSettingsFragment) return
            val activity = f.activity ?: return
            showingUiPage[activity] = false
            // After the navigation that closed the page has settled.
            activity.window?.decorView?.post {
                if (!activity.isChangingConfigurations) recreateIfStale(activity)
            }
        }
    }

    private const val LIGHT_LUMINANCE = 0.5

    /** Names defined in this module's own `values/colors.xml`, none with a qualified copy. */
    private val OWN_NAMES: List<Pair<Int, KakoSlot>> = listOf(
        // The fill every icon vector is drawn with where nothing tints it.
        R.color.mozac_ui_icons_fill to KakoSlot.ICON_BUTTON,
        R.color.accent_private_theme to KakoSlot.ACCENT,
        R.color.toggle_off_knob_dark_theme to KakoSlot.SWITCH_OFF_DOT,
        R.color.toggle_off_track_dark_theme to KakoSlot.SWITCH_OFF,
        R.color.prompt_login_edit_text_cursor_color_private_theme to KakoSlot.TEXT_CURSOR,
        R.color.search_view_hint_color to KakoSlot.FIELD_HINT,
        R.color.spinner_selected_item to KakoSlot.LIST_SELECTED,
        R.color.sync_error_background_color to KakoSlot.CARD_BACKGROUND,
        R.color.sync_error_text_color to KakoSlot.TEXT_ERROR,
        R.color.default_share_background to KakoSlot.CARD_BACKGROUND,
        R.color.device_foreground to KakoSlot.LIST_ICON,
        R.color.mozac_feature_readerview_selected to KakoSlot.ACCENT,
        R.color.mozac_ic_private_mode_mask_fill to KakoSlot.PRIVATE_ICON,
        R.color.protections_dashboard_chip_background_private to KakoSlot.CARD_BACKGROUND,
        R.color.protections_dashboard_chip_text_private to KakoSlot.TEXT,
        R.color.protections_dashboard_progress_bar_private to KakoSlot.PROGRESS,
        R.color.protections_dashboard_gradient_start_private to KakoSlot.BACKGROUND,
        R.color.protections_dashboard_gradient_end_private to KakoSlot.BACKGROUND,
    )

    /** Names that live in android-components, read by name. */
    private val OTHER_MODULE_NAMES: List<Pair<String, KakoSlot>> = listOf(
        "photonWhite" to KakoSlot.TEXT,
        "photonLightGrey05" to KakoSlot.TEXT,
        "photonLightGrey30" to KakoSlot.TEXT_SECONDARY,
        "photonGrey30" to KakoSlot.LIST_DIVIDER,
        "novaWhite" to KakoSlot.PRIVATE_ICON,
        "novaViolet20" to KakoSlot.PRIVATE_ICON,
    )

    private val SCHEME_PREFIXES = listOf("fx_mobile_", "fx_mobile_private_", "fx_mobile_oled_")

    /** Upstream's scheme entry → role, as the theme overlay and the Compose scheme have it. */
    private val SCHEME_SUFFIXES: List<Pair<String, KakoSlot>> = listOf(
        "primary" to KakoSlot.ACCENT,
        "on_primary" to KakoSlot.TEXT_ON_ACCENT,
        "primary_container" to KakoSlot.SCHEME_CONTAINER,
        "on_primary_container" to KakoSlot.SCHEME_ON_CONTAINER,
        "primary_inverse" to KakoSlot.ACCENT,
        "secondary" to KakoSlot.SCHEME_SECONDARY,
        "on_secondary" to KakoSlot.TEXT_ON_ACCENT,
        "secondary_container" to KakoSlot.SCHEME_CONTAINER,
        "on_secondary_container" to KakoSlot.SCHEME_ON_CONTAINER,
        "tertiary" to KakoSlot.ACCENT,
        "on_tertiary" to KakoSlot.TEXT_ON_ACCENT,
        "tertiary_container" to KakoSlot.SCHEME_CONTAINER,
        "on_tertiary_container" to KakoSlot.SCHEME_ON_CONTAINER,
        "background" to KakoSlot.BACKGROUND,
        "on_background" to KakoSlot.TEXT,
        "surface" to KakoSlot.BACKGROUND,
        "on_surface" to KakoSlot.TEXT,
        "surface_variant" to KakoSlot.SCHEME_SURFACE_VARIANT,
        "on_surface_variant" to KakoSlot.TEXT_SECONDARY,
        "surface_inverse" to KakoSlot.SCHEME_INVERSE_SURFACE,
        "on_surface_inverse" to KakoSlot.SCHEME_INVERSE_TEXT,
        "error" to KakoSlot.SCHEME_ERROR,
        "on_error" to KakoSlot.SCHEME_ON_ERROR,
        "error_container" to KakoSlot.BACKGROUND,
        "on_error_container" to KakoSlot.SCHEME_ERROR,
        "information" to KakoSlot.SCHEME_INFORMATION,
        "on_information" to KakoSlot.BACKGROUND,
        "information_container" to KakoSlot.BACKGROUND,
        "on_information_container" to KakoSlot.SCHEME_INFORMATION,
        "success" to KakoSlot.SCHEME_SUCCESS,
        "on_success" to KakoSlot.BACKGROUND,
        "success_container" to KakoSlot.BACKGROUND,
        "on_success_container" to KakoSlot.SCHEME_SUCCESS,
        "warning" to KakoSlot.SCHEME_WARNING,
        "on_warning" to KakoSlot.BACKGROUND,
        "warning_container" to KakoSlot.BACKGROUND,
        "on_warning_container" to KakoSlot.SCHEME_WARNING,
        "outline" to KakoSlot.BORDER,
        "outline_variant" to KakoSlot.SCHEME_OUTLINE_VARIANT,
        "sheet_outline" to KakoSlot.SHEET_BORDER,
        "surface_bright" to KakoSlot.CARD_BACKGROUND,
        "surface_dim" to KakoSlot.TOOLBAR_FILL,
        "surface_container" to KakoSlot.CARD_BACKGROUND,
        "surface_container_high" to KakoSlot.CARD_BACKGROUND,
        "surface_container_highest" to KakoSlot.CARD_BACKGROUND,
        "surface_container_low" to KakoSlot.BACKGROUND,
        "surface_container_lowest" to KakoSlot.BACKGROUND,
        "surface_container_selected" to KakoSlot.LIST_SELECTED,
        "autofill_text" to KakoSlot.TEXT_SECONDARY,
        "selected_text" to KakoSlot.TEXT_SELECTION,
        "icon_private" to KakoSlot.PRIVATE_ICON,
        "scrim" to KakoSlot.SCRIM,
        "layer_color_2" to KakoSlot.CARD_BACKGROUND,
        "action_color_primary_disabled" to KakoSlot.SCHEME_CONTAINER,
        "text_color_action_primary_disabled" to KakoSlot.TEXT_DISABLED,
        "text_color_oncolor_primary" to KakoSlot.TEXT_ON_ACCENT,
        "text_color_oncolor_secondary" to KakoSlot.TEXT_ON_ACCENT,
        "icon_color_oncolor" to KakoSlot.TEXT_ON_ACCENT,
    )
}
