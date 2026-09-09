/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import kotlinx.coroutines.flow.first
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.tab.collections.TabCollectionStorage
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.ext.components

/**
 * The categories of [KakoExim] that live in a Room database of their own rather than in a
 * preferences file — pinned shortcuts, per-site permission exceptions, the never-save-a-password
 * list, and tab collections.
 *
 * Split out of [KakoExim] only for length; every function here is one category's export or
 * import and is called from nowhere else.
 *
 * **None of these touches Gecko.** A backup runs headlessly, in a service started for the job,
 * where the engine may never come up at all — so the site-permission exceptions are read from
 * and written to [OnDiskSitePermissionsStorage] directly rather than through
 * `geckoSitePermissionsStorage`, whose `all()` waits on the runtime's storage controller. The
 * Gecko-side mirror of a permission is per-install state anyway; what travels is the decision.
 */
internal object KakoEximStores {

    // Pinned shortcuts (the home screen's "Shortcuts" row).

    /** `{"topSites":[{title,url}]}` — the pinned sites, in the order the storage holds them. */
    suspend fun exportPinnedSites(context: Context): ByteArray {
        val array = JSONArray()
        context.components.core.pinnedSiteStorage.getPinnedSites().forEach { site ->
            array.put(
                JSONObject().apply {
                    put("title", site.title ?: JSONObject.NULL)
                    put("url", site.url)
                },
            )
        }
        return JSONObject().put("topSites", array).toString(2).toByteArray()
    }

    /**
     * Adds every exported shortcut that is not pinned already.
     *
     * Matched on URL, because the title is the part the user renames: re-adding a site under a
     * second title would show it twice on the home screen.
     */
    suspend fun importPinnedSites(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("topSites") ?: return 0
        val storage = context.components.core.pinnedSiteStorage
        val present = storage.getPinnedSites().map { it.url }.toSet()
        var applied = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val url = obj.optString("url").takeIf { it.isNotEmpty() } ?: continue
            if (url in present) continue
            runCatching {
                storage.addPinnedSite(
                    title = if (obj.isNull("title")) "" else obj.optString("title"),
                    url = url,
                )
                applied++
            }
        }
        return applied
    }

    // Per-site permission exceptions (camera, microphone, location, notifications, autoplay …).

    suspend fun exportSitePermissions(context: Context): ByteArray {
        val array = JSONArray()
        OnDiskSitePermissionsStorage(context).all().forEach { permissions ->
            array.put(
                JSONObject().apply {
                    put("origin", permissions.origin)
                    put("location", permissions.location.name)
                    put("notification", permissions.notification.name)
                    put("microphone", permissions.microphone.name)
                    put("camera", permissions.camera.name)
                    put("bluetooth", permissions.bluetooth.name)
                    put("localStorage", permissions.localStorage.name)
                    put("autoplayAudible", permissions.autoplayAudible.name)
                    put("autoplayInaudible", permissions.autoplayInaudible.name)
                    put("mediaKeySystemAccess", permissions.mediaKeySystemAccess.name)
                    put("crossOriginStorageAccess", permissions.crossOriginStorageAccess.name)
                    put("localDeviceAccess", permissions.localDeviceAccess.name)
                    put("localNetworkAccess", permissions.localNetworkAccess.name)
                    put("savedAt", permissions.savedAt)
                },
            )
        }
        return JSONObject().put("sitePermissions", array).toString(2).toByteArray()
    }

    suspend fun importSitePermissions(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("sitePermissions") ?: return 0
        val storage = OnDiskSitePermissionsStorage(context)
        val present = runCatching { storage.all().map { it.origin }.toSet() }.getOrDefault(emptySet())
        var applied = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val origin = obj.optString("origin").takeIf { it.isNotEmpty() } ?: continue
            // A row already here was decided on *this* device; the archive does not overrule it.
            if (origin in present) continue
            runCatching {
                storage.save(
                    SitePermissions(
                        origin = origin,
                        location = status(obj, "location"),
                        notification = status(obj, "notification"),
                        microphone = status(obj, "microphone"),
                        camera = status(obj, "camera"),
                        bluetooth = status(obj, "bluetooth"),
                        localStorage = status(obj, "localStorage"),
                        autoplayAudible = autoplay(obj, "autoplayAudible", SitePermissions.AutoplayStatus.BLOCKED),
                        autoplayInaudible = autoplay(obj, "autoplayInaudible", SitePermissions.AutoplayStatus.ALLOWED),
                        mediaKeySystemAccess = status(obj, "mediaKeySystemAccess"),
                        crossOriginStorageAccess = status(obj, "crossOriginStorageAccess"),
                        localDeviceAccess = status(obj, "localDeviceAccess"),
                        localNetworkAccess = status(obj, "localNetworkAccess"),
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis()),
                    ),
                    private = false,
                )
                applied++
            }
        }
        return applied
    }

    private fun status(obj: JSONObject, key: String): SitePermissions.Status =
        runCatching { SitePermissions.Status.valueOf(obj.optString(key)) }
            .getOrDefault(SitePermissions.Status.NO_DECISION)

    private fun autoplay(
        obj: JSONObject,
        key: String,
        fallback: SitePermissions.AutoplayStatus,
    ): SitePermissions.AutoplayStatus =
        runCatching { SitePermissions.AutoplayStatus.valueOf(obj.optString(key)) }.getOrDefault(fallback)

    // "Never save a password for this site".

    suspend fun exportLoginExceptions(context: Context): ByteArray {
        val array = JSONArray()
        context.components.core.loginExceptionStorage.getLoginExceptions().first().forEach { exception ->
            array.put(JSONObject().put("origin", exception.origin))
        }
        return JSONObject().put("loginExceptions", array).toString(2).toByteArray()
    }

    suspend fun importLoginExceptions(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("loginExceptions") ?: return 0
        val storage = context.components.core.loginExceptionStorage
        var applied = 0
        for (index in 0 until array.length()) {
            val origin = array.optJSONObject(index)?.optString("origin")
                ?.takeIf { it.isNotEmpty() } ?: continue
            if (runCatching { storage.isLoginExceptionByOrigin(origin) }.getOrDefault(false)) continue
            runCatching {
                storage.addLoginException(origin)
                applied++
            }
        }
        return applied
    }

    // Tab collections.

    /**
     * The collections and the tabs in them, by title and URL.
     *
     * As with [KakoExim]'s open tabs, the per-tab engine state does not travel — it is Gecko's
     * opaque blob and means nothing in another profile — so a restored collection opens its
     * pages fresh.
     */
    suspend fun exportCollections(context: Context): ByteArray {
        val array = JSONArray()
        TabCollectionStorage(context).getCollectionsList().forEach { collection ->
            array.put(
                JSONObject().apply {
                    put("title", collection.title)
                    put(
                        "tabs",
                        JSONArray(
                            collection.tabs.map { tab ->
                                JSONObject().put("title", tab.title).put("url", tab.url)
                            },
                        ),
                    )
                },
            )
        }
        return JSONObject().put("collections", array).toString(2).toByteArray()
    }

    suspend fun importCollections(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("collections") ?: return 0
        val storage = TabCollectionStorage(context)
        val present = runCatching { storage.getCollectionsList().map { it.title }.toSet() }
            .getOrDefault(emptySet())
        var applied = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val title = obj.optString("title").takeIf { it.isNotEmpty() } ?: continue
            // Collections are identified by their title in the UI; re-creating one that is
            // already here would give 白い熊 two rows with the same name and no way to tell them
            // apart.
            if (title in present) continue
            val tabs = obj.optJSONArray("tabs") ?: JSONArray()
            val sessions = (0 until tabs.length()).mapNotNull { position ->
                val tab = tabs.optJSONObject(position) ?: return@mapNotNull null
                val url = tab.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                createTab(url = url, title = tab.optString("title"))
            }
            runCatching {
                storage.createCollection(title, sessions)
                applied++
            }
        }
        return applied
    }
}
