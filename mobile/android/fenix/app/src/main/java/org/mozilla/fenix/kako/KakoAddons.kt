/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.net.Uri
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
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.feature.addons.Addon
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.webextensions.WebExtensionSupport
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.ext.components
import java.io.File
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

    private const val XPI_SUFFIX = ".xpi"

    /** What the archive wants installed, waiting for the start after the import. */
    private const val PENDING_INSTALL_FILE = "kako_pending_addons.json"

    /**
     * The add-ons this object is installing right now.
     *
     * Read from the main thread by `WebExtensionPromptFeature`, written from the install loop, so
     * it is synchronized rather than a plain set. Membership means two things at once: that the
     * prompt collector below will answer that add-on's prompts, and that Fenix's own prompt UI
     * must not draw a dialog for them — 白い熊 granted these permissions on the phone the backup
     * came from, and asking again is a queue of dialogs rather than a decision.
     */
    private val installing = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** True while the restore is installing [id]; see [installing]. */
    fun isRestoreInstall(id: String): Boolean = installing.contains(id)

    /**
     * The installed, non-built-in extensions as JSON — id, name, and their state.
     *
     * **Waits for the engine**, for the same reason [installedIds] does and with far worse
     * consequences if it does not: a backup runs headlessly, in a service started for the job,
     * and the store publishes the installed set a few hundred milliseconds after the process
     * starts. Sampled before that, this answered an empty array — and an empty array is not a
     * failed backup, it is a *successful* backup of no extensions, which is what a restore then
     * faithfully reproduces (白い熊, 2026-09-09: "installed plugins not backed up").
     */
    suspend fun installedJson(context: Context): JSONArray {
        val array = JSONArray()
        awaitExtensions(context).values
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
    /**
     * Records what the archive wants installed, for [installPending] to do at the next start.
     * Returns how many add-ons were recorded.
     *
     * **The install is deliberately deferred rather than done here**, and the whole extension
     * restore hangs off that. Gecko mints a `moz-extension` UUID the moment it installs an add-on
     * and registers it under that UUID; the archive's storage is named with the UUID from the old
     * phone. Two builds tried to reconcile that afterwards and both failed — 155.0.1+029 wrote
     * the map too late and Gecko overwrote it, 155.0.1+030 wrote it early enough to win and left
     * five add-ons installed, enabled, and unable to serve a single one of their own resources.
     *
     * So nothing is installed while the import runs. [KakoExtData.applyPending] puts the archive's
     * UUIDs into `prefs.js` at the next start, with the engine still down and the add-ons still
     * absent, and only then does [installPending] install them. Gecko finds an entry already in
     * the map, reuses it instead of minting, and registers each add-on under the UUID its restored
     * storage is named with. Nothing is ever changed out from under Gecko.
     */
    fun stageForInstall(context: Context, array: JSONArray?): Int {
        if (array == null || array.length() == 0) return 0
        val file = File(context.filesDir, PENDING_INSTALL_FILE)
        return runCatching {
            file.writeText(JSONObject().put("addons", array).toString())
            array.length()
        }.getOrDefault(0)
    }

    /**
     * Installs whatever [stageForInstall] recorded, from the XPIs the archive carried.
     *
     * Runs well after startup, from `FenixApplication`'s visual-completeness queue: the engine has
     * to be up, and this is minutes of downloading in the worst case. The staging survives until
     * every add-on in it is accounted for, so a start that is killed halfway resumes at the next.
     */
    suspend fun installPending(context: Context) {
        val file = File(context.filesDir, PENDING_INSTALL_FILE)
        if (!file.isFile) return
        val array = runCatching { JSONObject(file.readText()).optJSONArray("addons") }.getOrNull()
        if (array == null) {
            runCatching { file.delete() }
            return
        }
        restore(context, array)
        runCatching { file.delete() }
        KakoExtData.clearStagedAddons(context)
    }

    /**
     * Re-installs every extension in [array] that is not installed already and
     * restores its state; returns how many were installed. Add-ons that are gone
     * from AMO, or whose install fails or stalls, are skipped without aborting.
     *
     * Every step is bounded, because this used to run inside a data-door import where the caller
     * was waiting on a reply. It no longer does — see [stageForInstall] — but the bounds are worth
     * keeping: nothing here should be able to wedge a startup either.
     */
    suspend fun restore(context: Context, array: JSONArray?): Int {
        if (array == null) return 0
        // Claimed for the WHOLE restore, not per add-on. The "was added" prompt arrives after its
        // install has already returned, so a claim released when the install finishes is released
        // too early and the dialog surfaces anyway.
        val claimed = (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotEmpty() } }
        installing.addAll(claimed)
        try {
            return restoreClaimed(context, array)
        } finally {
            installing.removeAll(claimed.toSet())
        }
    }

    private suspend fun restoreClaimed(context: Context, array: JSONArray): Int {
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

            // The XPI the backup carried, if it did — the exact build that was backed up, and no
            // network in the path. AMO is the fallback, for archives written before the add-ons
            // themselves travelled and for an add-on whose file did not survive.
            val staged = stagedXpi(context, id)
            val url = if (staged != null) {
                Uri.fromFile(staged).toString()
            } else {
                val addon = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                    runCatching {
                        // AMO search by GUID — independent of the configured collection.
                        context.components.addonsProvider.getAddonByID(
                            id = id,
                            readTimeoutInSeconds = LOOKUP_READ_TIMEOUT_S,
                        )
                    }.getOrNull()
                } ?: continue
                addon.downloadUrl.takeIf { it.isNotEmpty() } ?: continue
            }

            val result = runCatching { install(context, id, url, allowedInPrivateBrowsing) }.getOrNull()
            if (result != null) {
                installed++
                runCatching { applyState(context, id, enabled, allowedInPrivateBrowsing) }
            }
        }
        return installed
    }

    /**
     * The XPI an import unpacked for [id], or null.
     *
     * `walkTopDown`, not `listFiles`: the entries keep their path relative to the profile, so an
     * XPI unpacks to `<staging>/extensions/<id>.xpi` — a directory below the staging root.
     *
     * Gecko names the file after the add-on id, but that is its convention rather than a promise,
     * so a file whose name merely contains the id counts too.
     */
    private fun stagedXpi(context: Context, id: String): File? {
        val root = KakoExtData.stagedAddonsDir(context)
        if (!root.isDirectory) return null
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith(XPI_SUFFIX) }.toList()
        return files.firstOrNull { it.name.removeSuffix(XPI_SUFFIX) == id }
            ?: files.firstOrNull { it.name.contains(id) }
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
    private suspend fun installedIds(context: Context): Set<String> = awaitExtensions(context).keys

    /**
     * The store's extension map, read only once the engine has published it.
     *
     * Bounded: if the engine never finishes enumerating we fall through to whatever the store
     * does hold, which is no worse than the sample this replaces.
     */
    private suspend fun awaitExtensions(context: Context): Map<String, WebExtensionState> {
        withTimeoutOrNull(EXTENSION_LIST_TIMEOUT_MS) {
            runCatching { WebExtensionSupport.awaitInitialization() }
        }
        return context.components.core.store.state.extensions
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
        id: String,
        url: String,
        allowedInPrivateBrowsing: Boolean,
    ): Addon? = withContext(Dispatchers.Main) {
        // AddonManager lands in GeckoView's WebExtensionController, which asserts it is
        // called from a thread with a Handler — hence Main for the whole exchange.
        coroutineScope {
            val store = context.components.core.store
            // EXACTLY ONCE per request, by identity.
            //
            // The store re-emits on every state change while a prompt is still pending, and the
            // consume below is a dispatch — asynchronous, so more emissions carrying the SAME
            // request arrive before it takes effect. Answering each of them calls `onConfirm`
            // again on a GeckoResult that is already complete, and GeckoView throws
            // `IllegalStateException: result is already complete` on the main thread: the browser
            // dies. It never showed while this ran in the headless import service, where nothing
            // else touched the store; moving the installs to startup put it in a process with
            // tabs loading and it crashed on the first add-on (白い熊, 2026-09-10).
            val answered = mutableSetOf<WebExtensionPromptRequest>()
            val prompts = launch {
                store.flow()
                    .map { it.webExtensionPromptRequest }
                    .filterNotNull()
                    .collect { request ->
                        // OURS ONLY. This flow carries every prompt in the app, not just the ones
                        // our installs raise, and answering one 白い熊 raised himself completes the
                        // GeckoResult his own dialog is about to complete — which crashed the
                        // browser on his tap (2026-09-10). An add-on we are not installing is left
                        // strictly alone: not answered, not consumed, not looked at.
                        if (request.extensionIdOrNull() != id) return@collect
                        if (!answered.add(request)) return@collect
                        when (request) {
                            is WebExtensionPromptRequest.AfterInstallation.Permissions.Required -> {
                                // Guarded as well as gated: a double-answer that ever slips
                                // through must not be able to take the browser down with it.
                                runCatching {
                                    request.onConfirm(
                                        PermissionPromptResponse(
                                            isPermissionsGranted = true,
                                            isPrivateModeGranted = allowedInPrivateBrowsing,
                                            isTechnicalAndInteractionDataGranted = false,
                                        ),
                                    )
                                }
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

    /**
     * Which add-on a prompt is about, or null when it is not one we can attribute.
     *
     * `AfterInstallation` requests carry the extension; anything else is not ours by definition.
     */
    private fun WebExtensionPromptRequest.extensionIdOrNull(): String? =
        (this as? WebExtensionPromptRequest.AfterInstallation)?.extension?.id

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
