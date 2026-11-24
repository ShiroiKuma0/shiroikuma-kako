/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import mozilla.components.compose.base.theme.ForkIconRole
import org.mozilla.fenix.R
import kotlin.math.roundToInt
import mozilla.components.ui.icons.R as iconsR

/**
 * 白い熊 火狐 UI: the icon a row of a classic (View) screen carries — which drawable, told from
 * a name (a preference key, a menu item id), and how it sits in the row's label.
 *
 * The icon stands in the label as its start compound drawable, 1.25 × the label's text size
 * and never under 18 dp, and follows the label when a later pass changes its font size.
 */
object KakoRowIcons {
    private const val LABEL_SCALE = 1.25f
    private const val MIN_ICON_DP = 18f

    /** What a label carries; kept in a tag so that a second pass with nothing new does nothing. */
    private data class LabelIcon(@param:DrawableRes val res: Int, val color: Int, val size: Int)

    /** [res] tinted [color] at [sizePx], ready to be a compound drawable. */
    fun tinted(context: Context, @DrawableRes res: Int, color: Int, sizePx: Int): Drawable? {
        val drawable = AppCompatResources.getDrawable(context, res)?.mutate() ?: return null
        drawable.setTint(color)
        drawable.setBounds(0, 0, sizePx, sizePx)
        return drawable
    }

    /** The size of the icon beside [label]: as tall as its line of text, at least 18 dp. */
    fun sizeFor(label: TextView): Int =
        maxOf((label.textSize * LABEL_SCALE).roundToInt(), KakoPills.px(label.context, MIN_ICON_DP))

    /**
     * Puts [res] in [color] at the start of [label], keeping the label's other compound
     * drawables. Safe to call on every bind.
     */
    fun setLabelIcon(label: TextView, @DrawableRes res: Int, color: Int) {
        val want = LabelIcon(res, color, sizeFor(label))
        val had = label.getTag(R.id.kako_label_icon)
        if (had == want) return
        val icon = tinted(label.context, res, color, want.size) ?: return
        val others = label.compoundDrawablesRelative
        label.setCompoundDrawablesRelative(icon, others[1], others[2], others[3])
        label.compoundDrawablePadding = want.size / 2
        label.setTag(R.id.kako_label_icon, want)
        if (had == null) {
            // The font pass may run after the row is built: read the size again at each layout.
            label.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val now = label.getTag(R.id.kako_label_icon) as? LabelIcon
                if (now != null && now.size != sizeFor(label)) {
                    label.post { setLabelIcon(label, now.res, now.color) }
                }
            }
        }
    }

    /** Takes the icon [setLabelIcon] put on [label] away again (a recycled row). */
    fun clearLabelIcon(label: TextView) {
        if (label.getTag(R.id.kako_label_icon) !is LabelIcon) return
        val others = label.compoundDrawablesRelative
        label.setCompoundDrawablesRelative(null, others[1], others[2], others[3])
        label.setTag(R.id.kako_label_icon, CLEARED)
    }

    /** The drawable for a row named [name] — the first keyword the name contains decides. */
    @DrawableRes
    fun iconForName(name: String): Int? {
        val n = name.lowercase()
        return BY_KEYWORD.firstOrNull { (word, _) -> word in n }?.second
    }

    /** The drawable that stands for [role] where no keyword says more. */
    @DrawableRes
    fun iconForRole(role: ForkIconRole): Int = when (role) {
        ForkIconRole.CONNECT -> iconsR.drawable.mozac_ic_sync_24
        ForkIconRole.MESSAGE -> iconsR.drawable.mozac_ic_edit_24
        ForkIconRole.CONTACT -> iconsR.drawable.mozac_ic_avatar_circle_24
        ForkIconRole.SHARE -> iconsR.drawable.mozac_ic_share_android_24
        ForkIconRole.VIEW -> iconsR.drawable.mozac_ic_settings_24
        ForkIconRole.BLOCK -> iconsR.drawable.mozac_ic_lock_24
        ForkIconRole.DELETE -> iconsR.drawable.mozac_ic_delete_24
    }

    private const val CLEARED = "cleared"

    /**
     * Keyword → drawable, most specific first: a key such as `pref_key_delete_browsing_data`
     * must meet "delete" before it meets "data" or "browsing".
     */
    private val BY_KEYWORD: List<Pair<String, Int>> = listOf(
        "delete_browsing_data" to iconsR.drawable.mozac_ic_data_clearance_24,
        "delete" to iconsR.drawable.mozac_ic_delete_24,
        "clear" to iconsR.drawable.mozac_ic_delete_24,
        "remove" to iconsR.drawable.mozac_ic_delete_24,
        "sign_out" to iconsR.drawable.mozac_ic_cross_circle_24,
        "download" to iconsR.drawable.mozac_ic_download_24,
        "passkey" to iconsR.drawable.mozac_ic_passkey_24,
        "password" to iconsR.drawable.mozac_ic_login_24,
        "login" to iconsR.drawable.mozac_ic_login_24,
        "credit_card" to iconsR.drawable.mozac_ic_credit_card_24,
        "address" to iconsR.drawable.mozac_ic_location_24,
        "autofill" to iconsR.drawable.mozac_ic_login_24,
        "email_mask" to iconsR.drawable.mozac_ic_email_mask_24,
        "camera" to iconsR.drawable.mozac_ic_camera_24,
        "microphone" to iconsR.drawable.mozac_ic_microphone_24,
        "location" to iconsR.drawable.mozac_ic_location_24,
        "notification" to iconsR.drawable.mozac_ic_notification_24,
        "autoplay" to iconsR.drawable.mozac_ic_autoplay_24,
        "storage" to iconsR.drawable.mozac_ic_storage_24,
        "local_network" to iconsR.drawable.mozac_ic_local_network_24,
        "local_device" to iconsR.drawable.mozac_ic_local_host_24,
        "permission" to iconsR.drawable.mozac_ic_permission_24,
        "cookie" to iconsR.drawable.mozac_ic_cookies_24,
        "https_only" to iconsR.drawable.mozac_ic_lock_24,
        "doh" to iconsR.drawable.mozac_ic_lock_24,
        "certs" to iconsR.drawable.mozac_ic_lock_24,
        "tracking" to iconsR.drawable.mozac_ic_shield_24,
        "etp" to iconsR.drawable.mozac_ic_shield_24,
        "privacy" to iconsR.drawable.mozac_ic_shield_24,
        "protection" to iconsR.drawable.mozac_ic_shield_24,
        "restricted" to iconsR.drawable.mozac_ic_shield_24,
        "screenshot" to iconsR.drawable.mozac_ic_screenshot_24,
        "private" to iconsR.drawable.mozac_ic_private_mode_24,
        "pbm" to iconsR.drawable.mozac_ic_private_mode_24,
        "sync" to iconsR.drawable.mozac_ic_sync_24,
        "account" to iconsR.drawable.mozac_ic_avatar_circle_24,
        "avatar" to iconsR.drawable.mozac_ic_avatar_circle_24,
        "search" to iconsR.drawable.mozac_ic_search_24,
        "suggest" to iconsR.drawable.mozac_ic_search_24,
        "google_lens" to iconsR.drawable.mozac_ic_camera_24,
        "translat" to iconsR.drawable.mozac_ic_translate_24,
        "language" to iconsR.drawable.mozac_ic_translate_24,
        "summar" to iconsR.drawable.mozac_ic_sparkle_24,
        "ai_controls" to iconsR.drawable.mozac_ic_sparkle_24,
        "listen" to iconsR.drawable.mozac_ic_listen_to_page_24,
        "pdf" to iconsR.drawable.mozac_ic_page_portrait_24,
        "accessibility" to iconsR.drawable.mozac_ic_accessibility_24,
        "font" to iconsR.drawable.mozac_ic_text_size_24,
        "zoom" to iconsR.drawable.mozac_ic_text_size_24,
        "wallpaper" to iconsR.drawable.mozac_ic_wallpaper_24,
        "theme" to iconsR.drawable.mozac_ic_themes_24,
        "app_icon" to iconsR.drawable.mozac_ic_themes_24,
        "customiz" to iconsR.drawable.mozac_ic_themes_24,
        "kako_ui" to iconsR.drawable.mozac_ic_themes_24,
        "toolbar" to iconsR.drawable.mozac_ic_tool_24,
        "addon" to iconsR.drawable.mozac_ic_extension_24,
        "amo_collection" to iconsR.drawable.mozac_ic_extension_24,
        "extension" to iconsR.drawable.mozac_ic_extension_24,
        "bookmark" to iconsR.drawable.mozac_ic_bookmark_24,
        "history" to iconsR.drawable.mozac_ic_history_24,
        "collection" to iconsR.drawable.mozac_ic_collection_24,
        "inactive_tabs" to iconsR.drawable.mozac_ic_tab_tray_24,
        "tab" to iconsR.drawable.mozac_ic_tab_24,
        "home" to iconsR.drawable.mozac_ic_home_24,
        "stories" to iconsR.drawable.mozac_ic_newsfeed_24,
        "pocket" to iconsR.drawable.mozac_ic_newsfeed_24,
        "link_sharing" to iconsR.drawable.mozac_ic_share_android_24,
        "share" to iconsR.drawable.mozac_ic_share_android_24,
        "open_links_in_apps" to iconsR.drawable.mozac_ic_external_link_24,
        "external" to iconsR.drawable.mozac_ic_external_link_24,
        "default_browser" to iconsR.drawable.mozac_ic_globe_24,
        "desktop" to iconsR.drawable.mozac_ic_device_desktop_24,
        "ip_protection" to iconsR.drawable.mozac_ic_globe_24,
        "import" to iconsR.drawable.mozac_ic_import_data_24,
        "power_saving" to iconsR.drawable.mozac_ic_lightning_24,
        "battery" to iconsR.drawable.mozac_ic_lightning_24,
        "labs" to iconsR.drawable.mozac_ic_labs_24,
        "nimbus" to iconsR.drawable.mozac_ic_experiments_24,
        "experiment" to iconsR.drawable.mozac_ic_experiments_24,
        "studies" to iconsR.drawable.mozac_ic_experiments_24,
        "rollout" to iconsR.drawable.mozac_ic_experiments_24,
        "debug" to iconsR.drawable.mozac_ic_debug_drawer_24,
        "secret" to iconsR.drawable.mozac_ic_debug_drawer_24,
        "leakcanary" to iconsR.drawable.mozac_ic_debug_drawer_24,
        "gecko_logs" to iconsR.drawable.mozac_ic_debug_drawer_24,
        "server" to iconsR.drawable.mozac_ic_cloud_24,
        "data_choices" to iconsR.drawable.mozac_ic_information_24,
        "learn" to iconsR.drawable.mozac_ic_help_circle_24,
        "help" to iconsR.drawable.mozac_ic_help_circle_24,
        "rate" to iconsR.drawable.mozac_ic_thumbs_up_24,
        "about" to iconsR.drawable.mozac_ic_information_24,
        "whats_new" to iconsR.drawable.mozac_ic_whats_new_24,
    )
}
