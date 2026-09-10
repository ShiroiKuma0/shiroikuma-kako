/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * What the extensions themselves have stored — the largest thing this app owns by an order of
 * magnitude, and until 2026-09-09 the largest thing missing from its backup.
 *
 * ## Why this has to exist at all
 *
 * **This app is the only thing that reads this app's data — by design.** The app-supplied data
 * door exists exactly so that kako opens its own profile and hands the contents over; 応用管理 has
 * no business inside `/data/data/shiroikuma.kako` and does not want to be.
 *
 * The consequence is that a shortfall in a backup is always ours, and there is nowhere else to
 * look for it. On 2026-09-09 [KakoExim] exported 229.5 kB of a 2.78 GB profile — the backup
 * finished in 6.8 seconds and looked like a success — because nothing had ever told the exporter
 * that the extension storage was there.
 *
 * ## What is in the missing 2.7 GB
 *
 * Gecko keeps each extension's storage in the profile, under a quota-manager origin directory
 * named for the extension's `moz-extension` UUID:
 *
 * - `storage/default/moz-extension+++<uuid>^userContextId=4294967295/` — `storage.local`, an
 *   extension's own settings. Small, and the half nobody would think to call "data": it is where
 *   an extension's options page writes. Carried by [KakoExim.Cat.EXT_SETTINGS].
 * - `storage/default/moz-extension+++<uuid>/` — everything an extension put in `indexedDB`.
 *   This is the 2.7 GB: one 1.94 GiB `.sqlite` of yomitan dictionaries on 白い熊's phone. Carried
 *   by [KakoExim.Cat.EXT_DATABASES], its own category so a routine backup can leave it out.
 * - `browser-extension-data/<id>/` — the pre-IndexedDB JSON backend for `storage.local`, still
 *   present for extensions that were never migrated. Small; rides with the settings.
 *
 * **Both are under `default`, and the `^userContextId` suffix is the only thing that tells them
 * apart.** `ExtensionStorageIDB` opens `storage.local` with an ordinary (non-persistent) storage
 * principal, so `storage/permanent` is empty on this phone — it is only isolated into a reserved
 * user context, `WEBEXT_STORAGE_USER_CONTEXT_ID` = `-1 >>> 0` (`ExtensionStorageIDB.sys.mjs:25`).
 * Splitting on the persistence directory instead, as the first cut did, put every extension's
 * settings in the gigabytes category — so unticking the dictionaries would silently have dropped
 * the settings too, which is precisely the split 白い熊 asked for and would not have got.
 *
 * ## The UUID map is the load-bearing part
 *
 * Those directory names are **per install**. Gecko mints a fresh `moz-extension` UUID for every
 * extension on every profile and records the map in the `extensions.webextensions.uuids`
 * preference. Restore the directories without it and they belong to nobody: the extension comes
 * back, asks for its storage under a *new* UUID, and finds an empty origin — with the old one
 * sitting beside it, taking gigabytes, referenced by nothing. So the map travels with the files,
 * even though [KakoGeckoPrefs] deliberately refuses it as an `about:config` value.
 *
 * ## Import stages, then moves before Gecko starts
 *
 * Nothing here may be written into a profile Gecko is using — these are live SQLite databases. So
 * an import unpacks into [PENDING_DIR] and [applyPending] moves the tree into place from
 * `FenixApplication`, **before** anything touches `components.core.engine`. The move is a rename
 * within `filesDir`, so it costs nothing and needs no second copy of 2.7 GB. If the profile does
 * not exist yet — a phone where the app has been installed and never opened — the staging is left
 * alone and the next start does it, by which time Gecko has created the profile.
 */
internal object KakoExtData {

    /**
     * ZIP path prefixes: **the category's own id**, so that every entry says which category it
     * belongs to. The remainder of the name is the path relative to the profile.
     *
     * Derived from [KakoExim.Cat.id] rather than spelled out, so they cannot drift from it.
     *
     * They used to be `extdb/` and `extstore/`, and that was a real defect even though every byte
     * was present: a consumer totting up a category's size by entry name found only the `<id>.json`
     * side-car and reported "Extension databases — 40 bytes" over 1.89 GiB of dictionaries
     * (応用管理, 2026-09-09). An archive should not need outside knowledge to say what is in it.
     */
    val SETTINGS_PREFIX = KakoExim.Cat.EXT_SETTINGS.id + "/"
    val DATABASES_PREFIX = KakoExim.Cat.EXT_DATABASES.id + "/"

    /**
     * What 155.0.1+023 and +024 wrote. Still read, so the backups 白い熊 already has restore in
     * full — dropping 1.89 GiB of an existing archive to tidy up a naming decision would be the
     * same silent loss this rename exists to prevent.
     */
    private const val LEGACY_SETTINGS_PREFIX = "extstore/"
    private const val LEGACY_DATABASES_PREFIX = "extdb/"

    /** Where an import unpacks to, and [applyPending] moves from. Private storage, ours alone. */
    private const val PENDING_DIR = "kako_pending_extdata"

    /**
     * Where an import unpacks the add-ons' own XPI files.
     *
     * A separate directory because these are **not** moved into the profile: an XPI dropped into
     * `<profile>/extensions/` is invisible to Gecko, which knows only what its own extension
     * database says. They are installed properly, by [KakoAddons], and deleted afterwards.
     */
    private const val PENDING_ADDONS_DIR = "kako_pending_addons"

    /** Written by 155.0.1+030; removed on sight so that build's staging cannot be re-applied. */
    private const val STALE_UUIDS_FILE = "kako_pending_uuids.json"

    /**
     * The add-ons' XPI files, under the [KakoExim.Cat.EXTENSIONS] id like every other bulk entry.
     *
     * Carrying them is what makes an extension restore self-contained. The archive used to record
     * only *which* add-ons were installed, and the restore re-downloaded each one from AMO — so it
     * needed the network, needed AMO to answer, needed every add-on to still be listed, and could
     * only ever return whatever version AMO offers today. When any of that failed the add-ons
     * simply did not come back (白い熊, 2026-09-09: "Plugins not restored"). With the XPI in hand
     * the restore installs the exact build that was backed up, offline.
     */
    val ADDONS_PREFIX = KakoExim.Cat.EXTENSIONS.id + "/"

    /** Gecko keeps installed add-ons here, one XPI per add-on. */
    private const val PROFILE_EXTENSIONS_DIR = "extensions"

    private const val STORAGE_PERMANENT = "storage/permanent"
    private const val STORAGE_DEFAULT = "storage/default"
    private const val LEGACY_LOCAL_STORAGE = "browser-extension-data"

    /** Quota-manager origin directories for extensions all begin with this. */
    private const val EXTENSION_ORIGIN = "moz-extension+++"

    /**
     * The reserved user context `storage.local` is isolated into — `WEBEXT_STORAGE_USER_CONTEXT_ID`,
     * `-1 >>> 0`, in `ExtensionStorageIDB.sys.mjs`. An origin directory carrying this suffix holds
     * an extension's settings; one without it holds whatever the extension put in `indexedDB`.
     */
    private const val STORAGE_LOCAL_CONTEXT = "^userContextId=4294967295"

    /**
     * SQLite's shared-memory file. Not copied: it is scratch belonging to the processes that had
     * the database open, rebuilt from the `-wal` on the next open, and a stale one carried to
     * another phone is a hazard rather than a help. The `-wal` itself DOES travel — without it a
     * database copied mid-write loses its most recent transactions.
     */
    private const val SQLITE_SHM_SUFFIX = "-shm"

    /**
     * The map from extension id to `moz-extension` UUID — the whole reason a restored origin
     * directory is findable — and the flags saying which extensions have moved to the IndexedDB
     * backend for `storage.local`. Without the second, Gecko can decide to migrate again over
     * storage that is already migrated.
     */
    private const val UUID_PREF = "extensions.webextensions.uuids"
    private const val STORAGE_MIGRATED_PREFIX = "extensions.webextensions.ExtensionStorageIDB.migrated."

    /** One file to carry: where it is now, and what to call it in the archive. */
    data class Entry(val zipName: String, val file: File)

    // Export

    /**
     * The preferences that make the restored directories mean something.
     *
     * A JSON side-car in the same `{"prefs":[…]}` shape [KakoGeckoPrefs] stages, so the import can
     * hand it straight over.
     */
    fun exportSettingsJson(context: Context): ByteArray {
        val wanted = KakoGeckoPrefs.readUserPrefs(context).filter { entry ->
            val name = entry.optString("name")
            name == UUID_PREF || name.startsWith(STORAGE_MIGRATED_PREFIX)
        }
        return JSONObject().put("prefs", JSONArray(wanted)).toString(2).toByteArray()
    }

    /** `storage.local`, both backends: the reserved-context origins and the legacy JSON directory. */
    fun settingsFiles(context: Context): List<Entry> {
        val profile = KakoGeckoPrefs.profileDir(context) ?: return emptyList()
        return storageLocalOrigins(profile).flatMap { origin ->
            filesUnder(profile, origin, SETTINGS_PREFIX)
        } + filesUnder(profile, File(profile, LEGACY_LOCAL_STORAGE), SETTINGS_PREFIX)
    }

    /** Everything the extensions put in `indexedDB` — the gigabytes. */
    fun databaseFiles(context: Context): List<Entry> {
        val profile = KakoGeckoPrefs.profileDir(context) ?: return emptyList()
        return extensionOrigins(profile)
            .filterNot { it.name.contains(STORAGE_LOCAL_CONTEXT) }
            .flatMap { filesUnder(profile, it, DATABASES_PREFIX) }
    }

    private fun storageLocalOrigins(profile: File): List<File> =
        extensionOrigins(profile).filter { it.name.contains(STORAGE_LOCAL_CONTEXT) }

    /**
     * Every extension origin directory in the profile.
     *
     * Both persistence types are swept even though today everything is under `default` — the
     * cost is one `listFiles` on an empty directory, and the alternative is silently exporting
     * nothing the day upstream moves `storage.local` back to the persistent branch.
     */
    private fun extensionOrigins(profile: File): List<File> =
        listOf(STORAGE_DEFAULT, STORAGE_PERMANENT).flatMap { branch ->
            File(profile, branch).listFiles()
                ?.filter { it.isDirectory && it.name.startsWith(EXTENSION_ORIGIN) }
                .orEmpty()
        }

    /**
     * Every file under [root], named relative to [profile].
     *
     * `walkTopDown` rather than a hand-rolled recursion because an IndexedDB origin is a shallow
     * tree of a few directories and a `.files` store, and the quota manager's own `.metadata-v2`
     * — a dotfile, and required: it is what lets the quota manager adopt the origin on a profile
     * whose `storage.sqlite` has never heard of it.
     */
    private fun filesUnder(profile: File, root: File, prefix: String): List<Entry> {
        if (!root.exists()) return emptyList()
        val base = profile.path + File.separator
        return root.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(SQLITE_SHM_SUFFIX) }
            .mapNotNull { file ->
                val path = file.path
                if (!path.startsWith(base)) return@mapNotNull null
                Entry(prefix + path.removePrefix(base).replace(File.separatorChar, '/'), file)
            }
            .toList()
    }

    // Import

    /**
     * Which category [name] belongs to, so an unticked one can be skipped as it streams past —
     * and null for everything else, which [KakoExim] then reads into memory as before.
     *
     * `<id>/` cannot be confused with the `<id>.json` side-car: the separator differs.
     */
    fun categoryOf(name: String): KakoExim.Cat? = when {
        name.startsWith(ADDONS_PREFIX) -> KakoExim.Cat.EXTENSIONS
        name.startsWith(SETTINGS_PREFIX) || name.startsWith(LEGACY_SETTINGS_PREFIX) ->
            KakoExim.Cat.EXT_SETTINGS
        name.startsWith(DATABASES_PREFIX) || name.startsWith(LEGACY_DATABASES_PREFIX) ->
            KakoExim.Cat.EXT_DATABASES
        else -> null
    }

    /** The add-on XPIs an import unpacked, for [KakoAddons] to install from. */
    fun stagedAddonsDir(context: Context): File = File(context.filesDir, PENDING_ADDONS_DIR)

    /** Dropped once the add-ons are installed — they are megabytes, and single-use. */
    fun clearStagedAddons(context: Context) {
        runCatching { stagedAddonsDir(context).deleteRecursively() }
    }

    /** The installed add-ons as Gecko stores them, one XPI apiece. */
    fun addonFiles(context: Context): List<Entry> {
        val profile = KakoGeckoPrefs.profileDir(context) ?: return emptyList()
        return filesUnder(profile, File(profile, PROFILE_EXTENSIONS_DIR), ADDONS_PREFIX)
    }

    /**
     * Unpacks one archive entry into the staging tree; returns the bytes written.
     *
     * The path is rebuilt segment by segment and checked to stay inside the staging root, because
     * an archive is an input: `..` in an entry name would otherwise write anywhere this app can.
     */
    fun stageFile(context: Context, zipName: String, input: InputStream): Long {
        val addon = zipName.startsWith(ADDONS_PREFIX)
        val relative = zipName
            .removePrefix(ADDONS_PREFIX)
            .removePrefix(SETTINGS_PREFIX).removePrefix(DATABASES_PREFIX)
            .removePrefix(LEGACY_SETTINGS_PREFIX).removePrefix(LEGACY_DATABASES_PREFIX)
        val root = if (addon) {
            stagedAddonsDir(context).also { if (!it.exists()) it.mkdirs() }
        } else {
            pendingDir(context)
        }
        val target = File(root, relative)
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) return 0L
        target.parentFile?.mkdirs()
        var written = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                written += read
            }
        }
        return written
    }

    /**
     * Applies the migration flags. **The UUID map is deliberately NOT applied.**
     *
     * It rides in the archive, because a future restore that gets this right will need it, and it
     * is read back by the diagnostics. But nothing here writes it, in either direction.
     *
     * 155.0.1+029 applied it through `setBrowserPref` after the add-ons were installed: the write
     * is queued, so Gecko had already registered them under UUIDs of its own, and it rewrote the
     * map from memory at shutdown. Result: the dictionaries stayed under the archive's UUID and
     * nothing referred to them. 155.0.1+030 tried to win that race by writing `prefs.js` before
     * the engine started, and it won it — and that was worse. On 白い熊's phone the five add-ons
     * whose UUID was forced to the archive's value went on showing as installed and enabled while
     * serving none of their own resources: `loadIcon` returned null for every one and the toolbar
     * drew placeholder puzzle pieces. The two add-ons that kept Gecko's own UUID were untouched.
     *
     * The lesson is not which moment to write it at. It is that an add-on's UUID cannot be
     * changed out from under Gecko once it has installed and registered the add-on, and no amount
     * of timing fixes that. Making the storage travel needs the map to be in place *before* the
     * add-ons are installed, or another route entirely — and neither is something to guess at a
     * third time on 白い熊's phone (2026-09-10).
     */
    fun importSettingsJson(context: Context, bytes: ByteArray): Int {
        val prefs = JSONObject(String(bytes)).optJSONArray("prefs") ?: return 0
        val rest = (0 until prefs.length())
            .mapNotNull { prefs.optJSONObject(it) }
            .filterNot { it.optString("name") == UUID_PREF }
        return KakoGeckoPrefs.stage(context, rest)
    }

    /**
     * Moves a staged tree into the live profile. Called from `FenixApplication` **before** the
     * engine is touched — see the class comment.
     *
     * Leaves the staging in place if there is no profile yet, so the next start finishes the job
     * rather than the data being lost to a phone that had not been opened.
     */
    fun applyPending(context: Context) {
        runCatching { File(context.filesDir, STALE_UUIDS_FILE).delete() }
        val root = File(context.filesDir, PENDING_DIR)
        // The UUID map is tried on every start until it lands: the add-ons are installed during
        // the import, but Gecko writes their UUIDs to `prefs.js` on its own schedule and the
        // caller's force-stop is a SIGKILL, so the map we have to merge into may not exist yet.
        if (!root.isDirectory) return
        val profile = KakoGeckoPrefs.profileDir(context) ?: return
        val base = root.path + File.separator
        var failed = 0
        root.walkTopDown().filter { it.isFile }.forEach { staged ->
            val relative = staged.path.removePrefix(base)
            val target = File(profile, relative)
            val moved = runCatching {
                target.parentFile?.mkdirs()
                target.delete()
                // A rename inside filesDir: same filesystem, so no second copy of the gigabytes.
                staged.renameTo(target) || run {
                    staged.copyTo(target, overwrite = true)
                    staged.delete()
                }
            }.getOrDefault(false)
            if (!moved) failed++
        }
        // **Only when every file moved.** The first cut deleted the staging tree unconditionally,
        // with each move wrapped in its own `runCatching` — so a destination that could not be
        // written, for any reason, threw away 2.7 GB that had just been unpacked and reported
        // nothing. A staging tree left standing costs disk and is retried on the next start;
        // a staging tree deleted after a failed move is gone for good.
        if (failed == 0) runCatching { root.deleteRecursively() }
    }

    private fun pendingDir(context: Context): File =
        File(context.filesDir, PENDING_DIR).also { if (!it.exists()) it.mkdirs() }
}
