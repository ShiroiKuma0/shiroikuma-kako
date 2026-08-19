/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.preferences.Branch
import org.mozilla.fenix.ext.components

/**
 * The restricted-domains fence, opened by default.
 *
 * Stock Firefox refuses every extension a content script, a host permission and a
 * webRequest hook on Mozilla's own hosts — AMO, its CDN, the discovery pane, SUMO and
 * the Firefox Accounts and Sync servers. The list is the Gecko pref
 * `extensions.webextensions.restrictedDomains` (`modules/libpref/init/all.js`), read by
 * `ExtensionPolicyService`, which observes it and re-reads on every change, so a write
 * takes effect on the next navigation with no restart.
 *
 * On Android that default lives in the prebuilt GeckoView, out of reach of an artifact
 * build, so the fork opens the fence from this side instead: setting an empty string on
 * the USER branch leaves upstream's own list intact on the DEFAULT branch, and turning
 * the setting off clears the user value rather than writing a copy of the list back —
 * so the list stays upstream's to change on every rebase.
 */
object KakoRestrictedDomains {

    private const val PREF = "extensions.webextensions.restrictedDomains"

    /**
     * addons.mozilla.org is fenced a SECOND time, and not by that list:
     * `WebExtensionPolicy::IsRestrictedURI` also returns true wherever
     * `AddonManagerWebAPI::IsValidSite` does, and the AMO hostname is compiled into
     * the engine. This pref -- Tor Browser's, for hiding mozAddonManager -- makes
     * IsValidSite return false and so lifts the AMO restriction with it. It is the
     * only lever over that check from outside the engine, which matters here: an
     * artifact build cannot patch the C++.
     *
     * The cost is that navigator.mozAddonManager disappears on AMO, so its
     * "Add to Firefox" button falls back to a plain XPI link and the normal install
     * prompt. Turning the setting off clears this again.
     */
    private const val BLOCK_ADDON_MANAGER_PREF =
        "privacy.resistFingerprinting.block_mozAddonManager"

    /**
     * Applies the current setting to Gecko. Safe to call before Gecko has started: the
     * GeckoView event dispatcher queues the message until the runtime is ready.
     */
    @OptIn(ExperimentalAndroidComponentsApi::class)
    fun apply(context: Context) {
        val engine = context.components.core.engine
        if (context.components.settings.kakoIgnoreRestrictedDomains) {
            engine.setBrowserPref(PREF, "", Branch.USER, onSuccess = {}, onError = {})
            engine.setBrowserPref(BLOCK_ADDON_MANAGER_PREF, true, Branch.USER, onSuccess = {}, onError = {})
        } else {
            engine.clearBrowserUserPref(PREF, onSuccess = {}, onError = {})
            engine.clearBrowserUserPref(BLOCK_ADDON_MANAGER_PREF, onSuccess = {}, onError = {})
        }
    }
}
