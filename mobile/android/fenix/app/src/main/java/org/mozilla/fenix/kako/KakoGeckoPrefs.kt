/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.preferences.Branch
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.ext.components
import java.io.File

/**
 * The `about:config` half of the backup — every preference 白い熊 has changed in Gecko itself.
 *
 * This fork opens `about:config` on the release channel, so those edits are settings like any
 * other and belong in an archive that claims to carry "every setting". They are in no
 * SharedPreferences file: Gecko keeps its user branch in `prefs.js` inside the engine profile,
 * which is why every archive written before 2026-09-09 missed them entirely.
 *
 * ## Export reads the file, import does not write it
 *
 * `prefs.js` is Gecko's to own. It is rewritten from memory whenever the pref service flushes,
 * so a file we edit under a running engine is simply overwritten, and on a phone where the app
 * has been installed but never opened the profile directory does not exist yet at all — which
 * is exactly the restore-onto-a-new-device case this has to survive.
 *
 * So the import **stages** the prefs into [PENDING_FILE] and [applyPending] hands them to the
 * engine on the next start, through the same queued `setBrowserPref` path [KakoRestrictedDomains]
 * uses: the GeckoView event dispatcher holds the write until the runtime is up, and Gecko
 * persists it to `prefs.js` itself. The staged file is deleted only once its prefs have been
 * handed over, so an import followed by a crash before the next start still applies.
 */
object KakoGeckoPrefs {

    /** Where the import parks prefs for the next engine start. Private storage, ours alone. */
    private const val PENDING_FILE = "kako_pending_gecko_prefs.json"

    /** Gecko's own profile root inside `filesDir`; the profile itself is a salted subdirectory. */
    private const val PROFILE_PARENT = "mozilla"

    private const val PREFS_JS = "prefs.js"

    /**
     * `user_pref("name", value);` — one line, one preference. Gecko writes exactly this form,
     * one per line, and nothing else that matters to us.
     */
    private val USER_PREF_LINE = Regex("""^\s*user_pref\("((?:[^"\\]|\\.)*)"\s*,\s*(.*?)\);\s*$""")

    /**
     * Preference name prefixes that describe *this install* rather than 白い熊's choices.
     *
     * A deny-list, not an allow-list, because the whole point of `about:config` is prefs nobody
     * enumerated in advance — but `prefs.js` is mostly not that. Of the 52 user-branch prefs on
     * 白い熊's phone on 2026-09-09, five were settings (`intl.accept_languages`,
     * `intl.locale.requested`, `browser.translations.neverTranslateLanguages` and two tracking
     * flags) and the rest were bookkeeping: GPU capability caches, blocklist and safe-browsing
     * update stamps, per-extension storage-migration flags, schema versions.
     *
     * The ones that must not travel are those that would tell the new phone that work it has
     * never done is already done — migration flags, schema versions, "last updated" stamps. The
     * device caches below are merely noise, but a backup should not carry another phone's GPU
     * strings either.
     */
    private val EXCLUDE_PREFIXES = listOf(
        "app.normandy.",
        "app.update.",
        "browser.contentblocking.cfr-milestone",
        "browser.migration.",
        "browser.region.",
        "browser.safebrowsing.provider.",
        "browser.search.region",
        "browser.startup.homepage_override.",
        "captchadetection.",
        "datareporting.",
        "distribution.",
        "dom.push.userAgentID",
        "extensions.blocklist.",
        "extensions.dnr.lastStoreUpdateTag",
        "extensions.getAddons.",
        "extensions.lastApp",
        "extensions.lastPlatformVersion",
        "extensions.pendingOperations",
        "extensions.signatureCheckpoint",
        "extensions.systemAddonSet",
        "extensions.webextensions.ExtensionStorageIDB.",
        "extensions.webextensions.uuids",
        "gecko.handlerService.",
        "gfx-shader-check.",
        "gfx.blacklist.",
        "gfxinfo.",
        "idle.",
        "media.gmp",
        "network.cookie.CHIPS.",
        "network.cookie.validation.",
        "network.predictor.cleaned-up",
        "pdfjs.enabledCache",
        "pdfjs.migrationVersion",
        "places.database.",
        "privacy.bounceTrackingProtection.hasMigrated",
        "privacy.purge_trackers.",
        "privacy.trackingprotection.allow_list.hasMigrated",
        "security.sandbox.",
        "services.settings.",
        "storage.vacuum.last.",
        "toolkit.startup.last_success",
        "toolkit.telemetry.",
    )

    /**
     * The same judgement by shape rather than by name, so that next cycle's bookkeeping pref is
     * dropped without anyone having to notice it appeared.
     *
     * Deliberately narrow: a fragment like "cache" would take `browser.cache.disk.enable` with
     * it, which is a setting somebody might genuinely have changed. These name a *time* or a
     * *schema*, and a setting never does.
     */
    private val EXCLUDE_FRAGMENTS = listOf(
        ".apiRevision",
        ".migrated.",
        "databaseSchema",
        "lastupdatetime",
        "nextupdatetime",
    )

    /**
     * The two prefs [KakoRestrictedDomains] owns. It rewrites them from the Fenix setting on
     * every start, so carrying a copy here would only ever fight it.
     */
    private val EXCLUDE_EXACT = setOf(
        "extensions.webextensions.restrictedDomains",
        "privacy.resistFingerprinting.block_mozAddonManager",
    )

    /** The user branch as Gecko last wrote it, as `{"prefs":[{name,type,value}]}`. */
    fun export(context: Context): ByteArray {
        val array = JSONArray()
        prefsJsFile(context)?.let { file ->
            runCatching { file.readLines() }.getOrDefault(emptyList()).forEach { line ->
                val match = USER_PREF_LINE.find(line) ?: return@forEach
                val name = unescape(match.groupValues[1])
                if (!travels(name)) return@forEach
                val raw = match.groupValues[2].trim()
                val entry = JSONObject().put("name", name)
                when {
                    raw == "true" || raw == "false" -> entry.put("type", "b").put("value", raw == "true")
                    raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                        entry.put("type", "s").put("value", unescape(raw.substring(1, raw.length - 1)))
                    // Gecko's integer branch is 32-bit; anything else on the line is not a pref
                    // we know how to set back, so it is dropped rather than guessed at.
                    raw.toIntOrNull() != null -> entry.put("type", "i").put("value", raw.toInt())
                    else -> return@forEach
                }
                array.put(entry)
            }
        }
        return JSONObject().put("prefs", array).toString(2).toByteArray()
    }

    /**
     * Parks [bytes] for [applyPending] and answers how many prefs it holds.
     *
     * Written whole, so a half-written file cannot be applied: the count comes from re-reading
     * what landed, not from what we meant to write.
     */
    fun stagePending(context: Context, bytes: ByteArray): Int {
        val prefs = JSONObject(String(bytes)).optJSONArray("prefs") ?: return 0
        if (prefs.length() == 0) return 0
        val file = File(context.filesDir, PENDING_FILE)
        runCatching { file.writeBytes(bytes) }.getOrElse { return 0 }
        return prefs.length()
    }

    /**
     * Hands any staged prefs to the engine and drops the staging file.
     *
     * Called from `FenixApplication` at start, where [KakoRestrictedDomains] is applied too and
     * for the same reason: `setBrowserPref` goes through the GeckoView event dispatcher, which
     * queues the write until the runtime is ready, so this is safe long before Gecko is up.
     */
    @OptIn(ExperimentalAndroidComponentsApi::class)
    fun applyPending(context: Context) {
        val file = File(context.filesDir, PENDING_FILE)
        if (!file.isFile) return
        val prefs = runCatching { JSONObject(file.readText()).optJSONArray("prefs") }.getOrNull()
        // Unreadable or malformed: drop it rather than retry it on every start forever.
        if (prefs == null) {
            runCatching { file.delete() }
            return
        }
        val engine = context.components.core.engine
        for (index in 0 until prefs.length()) {
            val entry = prefs.optJSONObject(index) ?: continue
            val name = entry.optString("name").takeIf { it.isNotEmpty() } ?: continue
            if (!travels(name)) continue
            runCatching {
                when (entry.optString("type")) {
                    "b" -> engine.setBrowserPref(name, entry.getBoolean("value"), Branch.USER, {}, {})
                    "i" -> engine.setBrowserPref(name, entry.getInt("value"), Branch.USER, {}, {})
                    "s" -> engine.setBrowserPref(name, entry.getString("value"), Branch.USER, {}, {})
                    else -> Unit
                }
            }
        }
        runCatching { file.delete() }
    }

    private fun travels(name: String): Boolean =
        name !in EXCLUDE_EXACT &&
            EXCLUDE_PREFIXES.none { name.startsWith(it) } &&
            EXCLUDE_FRAGMENTS.none { name.contains(it) }

    /**
     * The live profile's `prefs.js`, or null before Gecko has ever run here.
     *
     * The profile directory carries a random salt, so it is found rather than named — and when
     * more than one is present the newest wins, which is the one the engine is using.
     */
    private fun prefsJsFile(context: Context): File? =
        File(context.filesDir, PROFILE_PARENT).listFiles()
            ?.filter { it.isDirectory }
            ?.map { File(it, PREFS_JS) }
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

    /** `prefs.js` is JS source: backslash escapes, and nothing else. */
    private fun unescape(value: String): String {
        if (!value.contains('\\')) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.length - 1) {
                out.append(char)
                index++
                continue
            }
            when (val next = value[index + 1]) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                else -> out.append(next)
            }
            index += 2
        }
        return out.toString()
    }
}
