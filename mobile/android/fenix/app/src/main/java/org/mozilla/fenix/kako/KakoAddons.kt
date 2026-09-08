/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.feature.addons.Addon
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.webextensions.WebExtensionSupport
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.ext.components
import kotlin.coroutines.resume

/**
 * The installed-extension half of [KakoExim]: every installed add-on travels, not
 * just the pinned ones.
 *
 * Export reads the browser store, so it needs no network and cannot fail. Restore
 * re-installs each missing add-on from its AMO listing (looked up by id, so it works
 * whatever collection is configured), then re-applies the recorded enabled and
 * private-browsing state.
 */
object KakoAddons {

    private const val INSTALL_TIMEOUT_MS = 120_000L

    /** How long to wait for the engine to say what is already installed. */
    private const val EXTENSION_LIST_TIMEOUT_MS = 30_000L

    /** One AMO lookup, wall clock — the read timeout below bounds only the read itself. */
    private const val LOOKUP_TIMEOUT_MS = 30_000L
    private const val LOOKUP_READ_TIMEOUT_S = 10L

    /**
     * The whole extension restore, start to finish.
     *
     * Kept well under 応用管理's ten-minute silence watchdog, because this is the one
     * category of an import that can legitimately take minutes and it reports nothing
     * while it runs. Overrunning it drops the remaining add-ons rather than the restore.
     */
    private const val TOTAL_BUDGET_MS = 240_000L

    /** The installed, non-built-in extensions as JSON — id, name, and their state. */
    fun installedJson(context: Context): JSONArray {
        val array = JSONArray()
        context.components.core.store.state.extensions.values
            .filterNot { it.isBuiltIn }
            .forEach { extension ->
                array.put(
                    JSONObject().apply {
                        put("id", extension.id)
                        put("name", extension.name ?: JSONObject.NULL)
                        put("enabled", extension.enabled)
                        put("allowedInPrivateBrowsing", extension.allowedInPrivateBrowsing)
                    },
                )
            }
        return array
    }

    /**
     * Re-installs every extension in [array] that is not installed already and
     * restores its state; returns how many were installed. Add-ons that are gone
     * from AMO, or whose install fails or stalls, are skipped without aborting.
     *
     * Every step is bounded, because this runs inside a data-door import where the
     * caller is waiting on a reply and cannot see what we are doing: an unbounded
     * step here is silence on 応用管理's side, and silence is how it decides an app
     * has died. See [TOTAL_BUDGET_MS].
     */
    suspend fun restore(context: Context, array: JSONArray?): Int {
        if (array == null) return 0
        var installed = 0
        val present = installedIds(context)
        val deadline = SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.optString("id").takeIf { it.isNotEmpty() } ?: continue
            if (id in present) continue
            // Checked between add-ons rather than inside one: an install already under way
            // is left to its own timeout, so we never abandon a half-written extension.
            if (SystemClock.elapsedRealtime() >= deadline) break
            val enabled = entry.optBoolean("enabled", true)
            val allowedInPrivateBrowsing = entry.optBoolean("allowedInPrivateBrowsing", false)

            val addon = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                runCatching {
                    // AMO search by GUID — independent of the configured collection.
                    context.components.addonsProvider.getAddonByID(
                        id = id,
                        readTimeoutInSeconds = LOOKUP_READ_TIMEOUT_S,
                    )
                }.getOrNull()
            } ?: continue
            val url = addon.downloadUrl.takeIf { it.isNotEmpty() } ?: continue

            val result = runCatching { install(context, url, allowedInPrivateBrowsing) }.getOrNull()
            if (result != null) {
                installed++
                runCatching { applyState(context, id, enabled, allowedInPrivateBrowsing) }
            }
        }
        return installed
    }

    /**
     * The extensions this app already has — **waited for**, not sampled.
     *
     * [mozilla.components.browser.state.state.BrowserState.extensions] is filled
     * asynchronously: the engine enumerates what is installed and pushes it into the store
     * a few hundred milliseconds after the process starts. A headless import gets here
     * first — measured on 白い熊's Mate XT at 16:27 on 2026-09-08, the engine published the
     * installed set 280–760 ms after the service started and the import had already decided
     * — so a plain read of the store answers "nothing is installed" and every extension in
     * the archive is fetched from AMO and re-installed **over the copy already there**.
     *
     * With seven extensions in a 14 kB archive that is seven AMO lookups plus seven
     * downloads before the import replies anything at all: seconds on a fast network,
     * a quarter of an hour on a slow one, and 応用管理 gives up at ten minutes
     * (応用管理, 2026-09-08 — the whole reason this function exists).
     */
    private suspend fun installedIds(context: Context): Set<String> {
        // Bounded: if the engine never finishes enumerating we fall through to whatever the
        // store does hold, which is no worse than the sample this replaces.
        withTimeoutOrNull(EXTENSION_LIST_TIMEOUT_MS) {
            runCatching { WebExtensionSupport.awaitInitialization() }
        }
        return context.components.core.store.state.extensions.keys
    }

    /**
     * Installs one add-on, answering the permission prompt it raises on the way.
     *
     * Gecko parks that prompt in the browser store and stalls the install until it is
     * confirmed; Fenix's own prompt UI is bound to the browser and home screens, which
     * are stopped while the UI page is up — so during a restore nothing else would ever
     * answer it. The grant is the one the backup already recorded; data-collection
     * consent is never granted on the user's behalf.
     */
    private suspend fun install(
        context: Context,
        url: String,
        allowedInPrivateBrowsing: Boolean,
    ): Addon? = withContext(Dispatchers.Main) {
        // AddonManager lands in GeckoView's WebExtensionController, which asserts it is
        // called from a thread with a Handler — hence Main for the whole exchange.
        coroutineScope {
            val store = context.components.core.store
            val prompts = launch {
                store.flow()
                    .map { it.webExtensionPromptRequest }
                    .filterNotNull()
                    .collect { request ->
                        when (request) {
                            is WebExtensionPromptRequest.AfterInstallation.Permissions.Required -> {
                                request.onConfirm(
                                    PermissionPromptResponse(
                                        isPermissionsGranted = true,
                                        isPrivateModeGranted = allowedInPrivateBrowsing,
                                        isTechnicalAndInteractionDataGranted = false,
                                    ),
                                )
                                store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
                            }
                            is WebExtensionPromptRequest.AfterInstallation.PostInstallation ->
                                store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
                            else -> Unit
                        }
                    }
            }

            val addon = withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val operation = context.components.addonManager.installAddon(
                        url = url,
                        installationMethod = InstallationMethod.MANAGER,
                        onSuccess = { if (continuation.isActive) continuation.resume(it) },
                        onError = { if (continuation.isActive) continuation.resume(null) },
                    )
                    continuation.invokeOnCancellation { operation.cancel() }
                }
            }

            prompts.cancel()
            addon
        }
    }

    /** Applies the recorded state; the add-on must be re-read so it carries an installed state. */
    private suspend fun applyState(
        context: Context,
        id: String,
        enabled: Boolean,
        allowedInPrivateBrowsing: Boolean,
    ) {
        val addon = context.components.addonManager.getAddonByID(id) ?: return
        if (allowedInPrivateBrowsing && !addon.isAllowedInPrivateBrowsing()) {
            withContext(Dispatchers.Main) {
                context.components.addonManager.setAddonAllowedInPrivateBrowsing(addon, true)
            }
        }
        if (!enabled && addon.isEnabled()) {
            withContext(Dispatchers.Main) {
                context.components.addonManager.disableAddon(addon)
            }
        }
    }
}
