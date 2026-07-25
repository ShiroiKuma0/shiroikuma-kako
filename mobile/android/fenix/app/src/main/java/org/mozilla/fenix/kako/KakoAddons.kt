/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
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
     */
    suspend fun restore(context: Context, array: JSONArray?): Int {
        if (array == null) return 0
        var installed = 0
        val present = context.components.core.store.state.extensions.keys
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.optString("id").takeIf { it.isNotEmpty() } ?: continue
            if (id in present) continue
            val enabled = entry.optBoolean("enabled", true)
            val allowedInPrivateBrowsing = entry.optBoolean("allowedInPrivateBrowsing", false)

            val addon = runCatching {
                // AMO search by GUID — independent of the configured collection.
                context.components.addonsProvider.getAddonByID(id)
            }.getOrNull() ?: continue
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
