/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.CreditCardNumber
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.NewCreditCardFields
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.UpdatableAddressFields
import mozilla.components.concept.storage.VisitType
import mozilla.components.concept.storage.bookmarks.InsertableBookmarkTreeNode
import mozilla.components.concept.storage.bookmarks.InsertableBookmarkTreeRoot
import mozilla.components.service.fxa.FXA_STATE_KEY
import mozilla.components.service.fxa.FXA_STATE_PREFS_KEY
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.utils.Settings
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export / import of everything settable in 白い熊 火狐 — the Kōjiki-style engine:
 * a ZIP of plain pretty-printed JSON files (one per category) plus the imported
 * font files. No binary blobs, no serialized objects, no databases.
 *
 * Prefs serialize as a typed key→{t,v} map (t: b/i/l/f/s/ss) and import back
 * MERGED — never `clear()` — so unrelated or device-local keys survive a restore.
 */
object KakoExim {

    const val FORMAT = "kako-export"

    /**
     * 2 since 2026-09-09, when open tabs, history, the search choice and the account joined
     * the archive. **3 later the same day**, when a restore onto a fresh phone showed that
     * "every setting" still was not: Fenix keeps settings in a *second* preferences file, a
     * custom search engine is a file rather than a preference, `about:config` is neither, and
     * the pinned shortcuts, per-site permissions, never-save list and collections each live in
     * a database of their own. All of them travel now.
     *
     * An older archive still imports — it simply carries less — so `MIN_FORMAT_READABLE` on the
     * automation provider stays at 1.
     */
    const val VERSION = 3

    /**
     * Newest visits kept by the history category.
     *
     * History is the largest thing in a Fenix profile by a wide margin, and this is the only
     * category whose size is unbounded by anything the user chose. Restoring it is also the
     * slowest part of an import — one `recordVisit` per row — so the cap is what keeps a
     * restore inside the caller's ten-minute patience as much as it keeps the ZIP small.
     */
    private const val HISTORY_MAX_VISITS = 5_000L

    /**
     * How much extension data to move between liveness reports, in either direction.
     *
     * 32 MB is about a second of I/O on this phone, so a 2.7 GB category reports roughly ninety
     * times over several minutes — often enough that nobody watching can mistake it for dead,
     * rarely enough that the broadcasts are not themselves the load.
     */
    private const val EXT_DATA_PROGRESS_BYTES = 32L shl 20

    /**
     * android-components' own search-metadata preferences file, which is where the selected
     * search engine is persisted. `private const` in `SearchMetadataStorage`, hence repeated.
     */
    private const val SEARCH_METADATA_PREFS = "mozac_feature_search_metadata"

    /**
     * Where a **custom** search engine is kept: one XML file per engine under `filesDir`, named
     * for the URL-safe Base64 of its id (`CustomSearchEngineStorage`, whose `SEARCH_DIR_NAME`
     * this repeats for the same reason as above).
     *
     * The other half of the reverted-search-engine bug. Restoring the metadata preference alone
     * writes the *id* of the chosen engine; if that engine was one 白い熊 added himself, nothing
     * on the new phone answers to the id and the middleware silently falls back to the bundled
     * default. The engines have to travel with the choice.
     */
    private const val SEARCH_ENGINES_DIR = "search-engines"

    /**
     * The family file-name convention (白い熊, 2026-07-25): every backup any sister app
     * writes is `<english-dash-separated-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip` — no version,
     * no infix, no suffix — so one directory holding all apps' backups sorts and reads
     * uniformly. Both the panel and the automation receiver use it.
     */
    const val EXPORT_PREFIX = "shiroikuma-kako_"

    /** The pre-convention name (`shiroikuma-kako-<version>-export_<stamp>.zip`) stays recognised. */
    const val LEGACY_EXPORT_PREFIX = "shiroikuma-kako-"

    /** Warning red used by the exim status lines (Kōjiki convention). */
    const val WARN_COLOR = 0xFFFF5252.toInt()

    // Device-local prefs carrying the chosen export directory — deliberately its
    // own file so it is itself never part of an export.
    private const val EXIMPORT_PREFS = "kako_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    /**
     * One exportable category. [id] is the stable identifier — it names the category in
     * the ZIP manifest, in [fileName] (the `<id>.json` entry), and in the `items` extra of
     * the automation contract ([KakoStateExportReceiver]).
     *
     * [sensitive] marks the categories that can only travel as plaintext inside the
     * ZIP (passwords, card numbers, postal addresses); the panel prints a warning
     * under each. Every category is selected by default — the export is meant to be
     * a complete backup — so the resulting ZIP deserves the care its contents do.
     *
     * [defaultOn] is that default, stated rather than assumed: the Export/Import panel
     * seeds its checkboxes from it and the automation contract's `LIST_CATEGORIES`
     * reply sends it as the `on`/`off` column, so a caller's picker starts ticked the
     * way this app says. It would be `false` only for something large, derived and
     * re-creatable (a regenerable cache); nothing here is.
     */
    enum class Cat(
        val id: String,
        @param:StringRes val labelRes: Int,
        val sensitive: Boolean = false,
        val defaultOn: Boolean = true,
    ) {
        KAKO_UI("kako_ui", R.string.kako_eim_cat_ui),
        FONTS("fonts", R.string.kako_eim_cat_fonts),
        EXTENSIONS("extensions", R.string.kako_eim_cat_extensions),
        APP_SETTINGS("app_settings", R.string.kako_eim_cat_app_settings),
        BOOKMARKS("bookmarks", R.string.kako_eim_cat_bookmarks),
        LOGINS("logins", R.string.kako_eim_cat_logins, sensitive = true),
        CARDS("credit_cards", R.string.kako_eim_cat_cards, sensitive = true),
        ADDRESSES("addresses", R.string.kako_eim_cat_addresses, sensitive = true),

        // Added 2026-09-09. Everything above this line lives in one of two SharedPreferences
        // files or in places/autofill storage; these four do not, which is exactly why they
        // used to go missing from a restore while the archive still carried the *flags* that
        // describe them — `pref_key_open_tabs_count` said 6 and `pref_key_fxa_signed_in` said
        // true over a profile that had neither (白い熊, 2026-09-09).
        TABS("tabs", R.string.kako_eim_cat_tabs),
        HISTORY("history", R.string.kako_eim_cat_history, sensitive = true),
        SEARCH("search", R.string.kako_eim_cat_search),
        ACCOUNT("account", R.string.kako_eim_cat_account, sensitive = true),

        // Added 2026-09-09, after a restore onto a fresh phone came up with default settings and
        // no extensions. Each of these is somewhere no preferences file reaches: `about:config`
        // is Gecko's own profile, and the last four are a Room database apiece.
        GECKO_PREFS("gecko_prefs", R.string.kako_eim_cat_gecko_prefs),
        TOP_SITES("top_sites", R.string.kako_eim_cat_top_sites),
        SITE_PERMISSIONS("site_permissions", R.string.kako_eim_cat_site_permissions),
        LOGIN_EXCEPTIONS("login_exceptions", R.string.kako_eim_cat_login_exceptions),
        COLLECTIONS("collections", R.string.kako_eim_cat_collections),

        // What the extensions themselves have stored — see [KakoExtData]. Last, and in this
        // order, because they are the bulk of the archive by a factor of ten thousand: everything
        // above is written and safe before the gigabytes start.
        EXT_SETTINGS("extension_settings", R.string.kako_eim_cat_ext_settings),
        EXT_DATABASES("extension_databases", R.string.kako_eim_cat_ext_databases),
        ;

        /** This category's JSON entry inside the ZIP. */
        val fileName: String get() = "$id.json"

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    /** The Places roots whose subtrees travel with [Cat.BOOKMARKS]. */
    private val BOOKMARK_ROOTS = listOf(
        BookmarkRoot.Mobile.id,
        BookmarkRoot.Menu.id,
        BookmarkRoot.Toolbar.id,
        BookmarkRoot.Unfiled.id,
    )

    // The font keys live in the kako_theme prefs but ride with the FONTS category.
    private val FONT_KEYS = setOf(KAKO_FONT_FAMILY_KEY, KAKO_FONT_WEIGHT_KEY, KAKO_FONT_SCALE_KEY)

    // Device-local kako_theme keys that never travel (one-time migration stamps).
    private val KAKO_UI_EXCLUDE = setOf("kako_pure_yellow_migrated")

    // Fenix settings whose keys contain any of these fragments are device-local or
    // ephemeral (telemetry ids, experiment state, install/migration stamps, CFR
    // counters) — excluded on both export and import.
    private val APP_SETTINGS_EXCLUDE_FRAGMENTS = listOf(
        "telemetry", "glean", "adjust", "experiment", "nimbus", "migrat",
        "install", "crash", "first_run", "onboarding", "review_prompt",
        "growth", "usage_reporting", "distribution", "last_", "_time",
    )

    /**
     * The keyed-store files [Cat.APP_SETTINGS] leaves alone, by exact name.
     *
     * Everything else in `shared_prefs/` travels — a deny-list rather than a list of the files
     * we happen to know about, because [Cat.APP_SETTINGS] claims to be *the app's settings* and
     * an allow-list quietly stops being true the first time upstream adds a file. That is how
     * `mozac_feature_search_metadata` went missing for a year.
     *
     * What is denied is either owned by another category, or describes this phone rather than
     * 白い熊: the export directory he picked here, the region this device is in, the push
     * registration that identifies it, and the icon and thumbnail caches, which are large,
     * derived, and rebuilt on demand.
     */
    private val PREFS_FILE_EXCLUDE = setOf(
        EXIMPORT_PREFS,
        PREFS_NAME, // kako_theme — Cat.KAKO_UI and Cat.FONTS
        SEARCH_METADATA_PREFS, // Cat.SEARCH
        FXA_STATE_PREFS_KEY, // Cat.ACCOUNT

        // This app's own automation token, and whether the data door is open. A secret, and one
        // that pairs a *caller* with *this install* — restoring another phone's would break the
        // pairing 応用管理 already has, and exporting it puts the token in the archive.
        "kako_automation",

        // Account-scoped caches. The session itself travels in [Cat.ACCOUNT]; these are derived
        // from it and are rebuilt on the first sync, so a stale copy can only mislead.
        "SyncAuthInfoCache",
        "FxaDeviceSettingsCache",
        "syncPrefs",

        // Migration stamps: restoring one says "already done" about work the new phone has not
        // done. `custom-search-engines` holds nothing else — one boolean, `pref_search_migrated`;
        // `sync.logins.prefs` is the "undecryptable logins have been cleaned" flag, and the whole
        // point of the two key stores above is that a restored profile may need that clean-up.
        "custom-search-engines",
        "sync.logins.prefs",

        // Device-local, derived, or telemetry residue.
        "client_uuid",
        "app_exit_info",
        "mozac_feature_search_region",
        "mozac_feature_push",
        "mozac_feature_accounts_push",
        "mozac_browser_icons",
        "mozac_browser_thumbnails",
        "mozac_share_cache",
        "mozac_support_base_shared_ids_helper",
        "mozac_error_lock",
    )

    /**
     * …and by suffix.
     *
     * `_kp_pre_m` / `_kp_post_m` are `SecureAbove22Preferences`, which stores the **key to the
     * logins and autofill databases** — in plaintext on the release channel. `…Crypto` is the
     * matching pair: application-services keeps a `canaryPhrase` there, encrypted under that key,
     * and checks it before opening the store.
     *
     * Restoring either would be far worse than dropping it. The logins and cards travel as
     * entries in [Cat.LOGINS] and [Cat.CARDS] and are re-encrypted under the new phone's own key
     * on the way in; a canary from the old phone then fails to decrypt, and application-services
     * treats a store whose canary does not match as unreadable.
     */
    private val PREFS_FILE_EXCLUDE_SUFFIXES = listOf("_kp_pre_m", "_kp_post_m", "Crypto")

    /**
     * …and by prefix: the framework's and the (now-absent) telemetry SDK's own scratch files,
     * which no upstream cycle will stop adding to.
     */
    private val PREFS_FILE_EXCLUDE_PREFIXES = listOf(
        "androidx.",
        "com.google.android.gms",
        "mozilla.telemetry.glean",
        "org.mozilla.fenix.components.metrics.",
    )

    /** Android's own suffix for a `SharedPreferences` backing file. */
    private const val PREFS_FILE_SUFFIX = ".xml"

    // Directory preference

    private fun eximPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(EXIMPORT_PREFS, Context.MODE_PRIVATE)

    fun dirUri(context: Context): Uri? =
        eximPrefs(context).getString(KEY_DIR_URI, null)?.let(Uri::parse)

    fun setDirUri(context: Context, uri: Uri) {
        eximPrefs(context).edit { putString(KEY_DIR_URI, uri.toString()) }
    }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)?.let { DocumentFile.fromTreeUri(context, it) }?.takeIf { it.isDirectory }

    /** The chosen directory's display name, for status lines and automation replies. */
    fun dirLabel(context: Context): String? =
        exportDir(context)?.name ?: dirUri(context)?.lastPathSegment

    /** The newest export in the chosen directory — the current name or the legacy one — or null. */
    fun latestExport(context: Context): DocumentFile? {
        val dir = exportDir(context) ?: return null
        return runCatching {
            dir.listFiles().filter { file ->
                val name = file.name.orEmpty()
                file.isFile && name.endsWith(".zip") &&
                    (name.startsWith(EXPORT_PREFIX) || name.startsWith(LEGACY_EXPORT_PREFIX))
            }.maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    fun fmtTs(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(ts))

    fun exportFileName(): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // Export

    /**
     * Thrown out of [export] when [isCancelled] goes true — deliberately *not* a
     * [kotlinx.coroutines.CancellationException], which a coroutine would swallow as
     * ordinary cancellation instead of letting the caller unwind and clean up.
     */
    class ExportCancelled : Exception("cancelled")

    /**
     * Writes the selected categories to [out]; returns a short summary ("N categories").
     *
     * The single export core, callable headlessly: the Export/Import panel and the
     * automation receiver ([KakoStateExportReceiver]) are both thin callers of this.
     * [onProgress] (done, total, category label) fires after each written category —
     * the receiver forwards it as contract progress broadcasts; UI callers omit it.
     * [isCancelled] is polled at each category boundary so a `CANCEL_EXPORT` unwinds
     * promptly without ever interrupting a write in flight.
     */
    suspend fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String {
        // Enum order, not selection order — the ZIP and its progress line read the same
        // however the caller assembled the set.
        val ordered = Cat.entries.filter { it in cats }
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }

            /**
             * Streams one file straight through. Never read whole: these are SQLite databases of
             * a gigabyte and more.
             *
             * **Never call `setLevel` here.** The first cut of this stored the bulk entries
             * uncompressed, on the theory that IndexedDB compresses its own records — and both
             * halves of that were wrong. The data deflates six to one (1.94 GiB of yomitan
             * dictionaries became a 337 MB archive), and switching the level between entries
             * corrupts the stream: `ZipOutputStream` shares one `Deflater` across the whole
             * archive, and changing its level mid-flight makes `deflateParams` flush into the
             * bitstream. `ZipFile` never noticed — it only reads the central directory, whose
             * sizes were right — but `ZipInputStream` inflates, and answered
             * `ZipException: invalid block type`. That is [import]'s own reader, so the archive
             * this wrote could not have been restored (2026-09-09, found by the 辞書 chat's
             * insistence on running both readers).
             */
            fun putFile(name: String, file: File) {
                runCatching {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip, DEFAULT_BUFFER_SIZE) }
                    zip.closeEntry()
                }
            }

            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("app", context.packageName)
                put(
                    "appVersion",
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "unknown",
                )
                put("createdTs", System.currentTimeMillis())
                put("categories", JSONArray(ordered.map { it.id }))
            }
            put("manifest.json", manifest.toString(2).toByteArray())

            ordered.forEachIndexed { index, cat ->
                if (isCancelled()) throw ExportCancelled()
                when (cat) {
                    Cat.KAKO_UI -> put(cat.fileName, exportPrefs(KakoTheme.prefs(context)) { key ->
                        key !in FONT_KEYS && key !in KAKO_UI_EXCLUDE
                    })
                    Cat.FONTS -> {
                        put(cat.fileName, exportPrefs(KakoTheme.prefs(context)) { it in FONT_KEYS })
                        KakoFonts.fontsDir(context).listFiles()?.filter { it.isFile }?.forEach { font ->
                            put("fonts/${font.name}", font.readBytes())
                        }
                    }
                    Cat.EXTENSIONS -> put(
                        cat.fileName,
                        JSONObject().apply {
                            // The pinned set/order and the custom AMO collection…
                            put("prefs", prefsJson(fenixPrefs(context)) { it in extensionKeys(context) })
                            // …plus every installed add-on, not only the pinned ones.
                            put("installed", KakoAddons.installedJson(context))
                        }.toString(2).toByteArray(),
                    )
                    Cat.APP_SETTINGS -> put(cat.fileName, exportAppSettings(context))
                    Cat.BOOKMARKS -> put(cat.fileName, exportBookmarks(context))
                    Cat.LOGINS -> put(cat.fileName, exportLogins(context))
                    Cat.CARDS -> put(cat.fileName, exportCards(context))
                    Cat.ADDRESSES -> put(cat.fileName, exportAddresses(context))
                    Cat.TABS -> put(cat.fileName, exportTabs(context))
                    Cat.HISTORY -> put(cat.fileName, exportHistory(context))
                    Cat.SEARCH -> {
                        put(cat.fileName, exportPrefs(searchPrefs(context)) { true })
                        // The engines 白い熊 added himself ride with the choice that points at one.
                        customSearchEnginesDir(context).listFiles()?.filter { it.isFile }?.forEach { engine ->
                            put("$SEARCH_ENGINES_DIR/${engine.name}", engine.readBytes())
                        }
                    }
                    Cat.ACCOUNT -> put(cat.fileName, exportAccount(context))
                    Cat.GECKO_PREFS -> put(cat.fileName, KakoGeckoPrefs.export(context))
                    Cat.TOP_SITES -> put(cat.fileName, KakoEximStores.exportPinnedSites(context))
                    Cat.SITE_PERMISSIONS -> put(cat.fileName, KakoEximStores.exportSitePermissions(context))
                    Cat.LOGIN_EXCEPTIONS -> put(cat.fileName, KakoEximStores.exportLoginExceptions(context))
                    Cat.COLLECTIONS -> put(cat.fileName, KakoEximStores.exportCollections(context))
                    Cat.EXT_SETTINGS -> {
                        // The UUID map first: without it the directories that follow name nobody.
                        put(cat.fileName, KakoExtData.exportSettingsJson(context))
                        writeExtData(
                            KakoExtData.settingsFiles(context),
                            ::putFile,
                            isCancelled,
                        ) { onProgress(index + 1, ordered.size, context.getString(cat.labelRes)) }
                    }
                    Cat.EXT_DATABASES -> {
                        val files = KakoExtData.databaseFiles(context)
                        put(
                            cat.fileName,
                            JSONObject()
                                .put("files", files.size)
                                .put("bytes", files.sumOf { it.file.length() })
                                .toString(2).toByteArray(),
                        )
                        writeExtData(files, ::putFile, isCancelled) {
                            onProgress(index + 1, ordered.size, context.getString(cat.labelRes))
                        }
                    }
                }
                onProgress(index + 1, ordered.size, context.getString(cat.labelRes))
            }
        }
        return "${cats.size} categories"
    }

    /**
     * Writes one category's bulk files, reporting liveness as it goes.
     *
     * A category that takes minutes and says nothing is indistinguishable from a hung one, and
     * 応用管理 abandons an app that has been silent for ten minutes. The per-category progress the
     * rest of the export leans on cannot help here — this *is* one category — so [alive] fires
     * every [EXT_DATA_PROGRESS_BYTES] and repeats the current position rather than advancing it.
     */
    private inline fun writeExtData(
        files: List<KakoExtData.Entry>,
        putFile: (String, File) -> Unit,
        isCancelled: () -> Boolean,
        alive: () -> Unit,
    ) {
        var sinceReport = 0L
        files.forEach { entry ->
            if (isCancelled()) throw ExportCancelled()
            val length = entry.file.length()
            putFile(entry.zipName, entry.file)
            sinceReport += length
            if (sinceReport >= EXT_DATA_PROGRESS_BYTES) {
                sinceReport = 0
                alive()
            }
        }
    }

    // Import

    /**
     * The categories present in [archive] — used to reject files that are not our exports.
     *
     * Reads names only, never content: the file may be gigabytes, and this runs before the caller
     * has decided to import anything at all.
     */
    fun categoriesIn(archive: InputStream): Set<Cat> {
        val names = mutableSetOf<String>()
        ZipInputStream(archive).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                names.add(entry.name)
                entry = stream.nextEntry
            }
        }
        return Cat.entries.filter { it.fileName in names }.toSet()
    }

    /**
     * Restores the selected categories from [zip]; returns a per-category summary
     * ("Label: N" lines). A category missing from the file is skipped silently; a
     * failing category is skipped without aborting the rest.
     *
     * [onProgress] (done, total, category label) fires after each category, exactly as
     * [export]'s does — and for the same reason. A caller on the other side of an IPC
     * boundary cannot tell a slow import from a dead one, and 応用管理 gives up on an app
     * that has said nothing for ten minutes (応用管理, 2026-09-08). The import used to say
     * nothing at all, which made a slow restore indistinguishable from a hung one.
     */
    suspend fun import(
        context: Context,
        archive: InputStream,
        cats: Set<Cat>,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): String {
        // ONE pass over the stream, and the archive is never held whole.
        //
        // It used to arrive as a `ByteArray` and be exploded into a map of every entry's bytes,
        // which was fine while the largest thing in it was a bookmark tree. The extension
        // databases are 2.7 GB on 白い熊's phone; that map is now an instant OOM, and the caller
        // cannot rewind a pipe to have a second go. So the small entries — every category's JSON,
        // the fonts, the search engines — are collected as before, and the bulk entries are
        // streamed straight to [KakoExtData]'s staging as they go past.
        val entries = mutableMapOf<String, ByteArray>()
        val stagedFiles = mutableMapOf<Cat, Int>()
        var sinceReport = 0L
        val orderedForProgress = Cat.entries.filter { it in cats }
        ZipInputStream(archive).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    val extCat = KakoExtData.categoryOf(name)
                    if (extCat == null) {
                        entries[name] = stream.readBytes()
                    } else if (extCat in cats) {
                        // Unticked, and it is simply not read: `nextEntry` skips the rest.
                        sinceReport += KakoExtData.stageFile(context, name, stream)
                        stagedFiles[extCat] = (stagedFiles[extCat] ?: 0) + 1
                        if (sinceReport >= EXT_DATA_PROGRESS_BYTES) {
                            sinceReport = 0
                            val position = orderedForProgress.indexOf(extCat) + 1
                            if (position > 0) {
                                onProgress(
                                    position,
                                    orderedForProgress.size,
                                    context.getString(extCat.labelRes),
                                )
                            }
                        }
                    }
                }
                entry = stream.nextEntry
            }
        }

        val lines = mutableListOf<String>()
        // Enum order, exactly as [export] walks it, so the position handed to [onProgress]
        // can be turned back into the category id the contract wants in `item`.
        val ordered = Cat.entries.filter { it in cats }

        ordered.forEachIndexed { index, cat ->
            val bytes = entries[cat.fileName]
            val applied = if (bytes == null) -1 else runCatching {
                when (cat) {
                    Cat.KAKO_UI -> importPrefs(KakoTheme.prefs(context), bytes) { key ->
                        key !in FONT_KEYS && key !in KAKO_UI_EXCLUDE
                    }
                    Cat.FONTS -> {
                        var count = importPrefs(KakoTheme.prefs(context), bytes) { it in FONT_KEYS }
                        entries.filterKeys { it.startsWith("fonts/") }.forEach { (name, data) ->
                            // Basename only — no path traversal; a bad font is skipped.
                            runCatching {
                                File(KakoFonts.fontsDir(context), File(name).name).writeBytes(data)
                                count++
                            }
                        }
                        count
                    }
                    Cat.EXTENSIONS -> {
                        val root = JSONObject(String(bytes))
                        val prefs = root.optJSONObject("prefs") ?: JSONObject()
                        importPrefsJson(fenixPrefs(context), prefs) { it in extensionKeys(context) } +
                            KakoAddons.restore(context, root.optJSONArray("installed"))
                    }
                    Cat.APP_SETTINGS -> importAppSettings(context, bytes)
                    Cat.BOOKMARKS -> importBookmarks(context, bytes)
                    Cat.LOGINS -> importLogins(context, bytes)
                    Cat.CARDS -> importCards(context, bytes)
                    Cat.ADDRESSES -> importAddresses(context, bytes)
                    Cat.TABS -> importTabs(context, bytes)
                    Cat.HISTORY -> importHistory(context, bytes)
                    Cat.SEARCH -> {
                        var count = importPrefs(searchPrefs(context), bytes) { true }
                        val dir = customSearchEnginesDir(context)
                        entries.filterKeys { it.startsWith("$SEARCH_ENGINES_DIR/") }.forEach { (name, data) ->
                            // Basename only — no path traversal; a bad engine is skipped.
                            runCatching {
                                File(dir, File(name).name).writeBytes(data)
                                count++
                            }
                        }
                        count
                    }
                    Cat.ACCOUNT -> importAccount(context, bytes)
                    Cat.GECKO_PREFS -> KakoGeckoPrefs.stagePending(context, bytes)
                    Cat.TOP_SITES -> KakoEximStores.importPinnedSites(context, bytes)
                    Cat.SITE_PERMISSIONS -> KakoEximStores.importSitePermissions(context, bytes)
                    Cat.LOGIN_EXCEPTIONS -> KakoEximStores.importLoginExceptions(context, bytes)
                    Cat.COLLECTIONS -> KakoEximStores.importCollections(context, bytes)
                    // The files themselves were staged during the pass above; what is left is the
                    // UUID map, without which they name nobody.
                    Cat.EXT_SETTINGS ->
                        KakoExtData.importSettingsJson(context, bytes) +
                            (stagedFiles[Cat.EXT_SETTINGS] ?: 0)
                    Cat.EXT_DATABASES -> stagedFiles[Cat.EXT_DATABASES] ?: 0
                }
            }.getOrDefault(-1)
            if (applied >= 0) lines.add("${context.getString(cat.labelRes)}: $applied")
            // Fires for a category the archive lacks as well: a caller watching these to
            // decide whether we are still alive must see the count reach the total, and a
            // category it never hears about is one it would wait for forever.
            onProgress(index + 1, ordered.size, context.getString(cat.labelRes))
        }

        // Everything written above went in through `edit {}` — that is `apply()`, an in-memory
        // write plus a *queued* disk write. Whoever asked for this import may kill us the moment
        // we answer, so the queue is drained here rather than left to luck.
        flushPrefs(context)

        // Caches backing the fork prefs/fonts were swapped underneath; refresh so
        // the running app shows as much of the import as it can before a restart.
        KakoTheme.refreshChromeOverrides(context)
        KakoTheme.revision.intValue++
        KakoFonts.refresh(context)

        return lines.joinToString("\n")
    }

    // Personal data — saved logins, autofill, bookmarks.
    //
    // These leave the encrypted on-device stores and land as plaintext in the ZIP:
    // that is the only portable form, and it is why their categories are marked
    // [Cat.sensitive] and carry a warning wherever they are offered.

    private suspend fun exportLogins(context: Context): ByteArray {
        val logins = context.components.core.passwordsStorage.list()
        val array = JSONArray()
        logins.forEach { login ->
            val entry = login.toEntry()
            array.put(
                JSONObject().apply {
                    put("origin", entry.origin)
                    put("formActionOrigin", entry.formActionOrigin ?: JSONObject.NULL)
                    put("httpRealm", entry.httpRealm ?: JSONObject.NULL)
                    put("usernameField", entry.usernameField)
                    put("passwordField", entry.passwordField)
                    put("username", entry.username)
                    put("password", entry.password)
                },
            )
        }
        return JSONObject().put("logins", array).toString(2).toByteArray()
    }

    /** Restores logins through [addMany] so one malformed row cannot abort the rest. */
    private suspend fun importLogins(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("logins") ?: return 0
        val entries = (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            LoginEntry(
                origin = obj.optString("origin"),
                formActionOrigin = obj.optStringOrNull("formActionOrigin"),
                httpRealm = obj.optStringOrNull("httpRealm"),
                usernameField = obj.optString("usernameField"),
                passwordField = obj.optString("passwordField"),
                username = obj.optString("username"),
                password = obj.optString("password"),
            )
        }
        if (entries.isEmpty()) return 0
        return context.components.core.passwordsStorage.addMany(entries).count { it.isSuccess }
    }

    private suspend fun exportCards(context: Context): ByteArray {
        val storage = context.components.core.autofillStorage
        val crypto = storage.getCreditCardCrypto()
        // The key is mutex-serialized — resolve it once for the whole batch.
        val key = crypto.getOrGenerateKey()
        val array = JSONArray()
        storage.getAllCreditCards().forEach { card ->
            val number = crypto.decrypt(key, card.encryptedCardNumber)?.number ?: return@forEach
            array.put(
                JSONObject().apply {
                    put("billingName", card.billingName)
                    put("cardNumber", number)
                    put("cardNumberLast4", card.cardNumberLast4)
                    put("expiryMonth", card.expiryMonth)
                    put("expiryYear", card.expiryYear)
                    put("cardType", card.cardType)
                },
            )
        }
        return JSONObject().put("creditCards", array).toString(2).toByteArray()
    }

    private suspend fun importCards(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("creditCards") ?: return 0
        val storage = context.components.core.autofillStorage
        var applied = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            // The storage encrypts the plaintext number itself on insert.
            runCatching {
                storage.addCreditCard(
                    NewCreditCardFields(
                        billingName = obj.optString("billingName"),
                        plaintextCardNumber = CreditCardNumber.Plaintext(obj.optString("cardNumber")),
                        cardNumberLast4 = obj.optString("cardNumberLast4"),
                        expiryMonth = obj.optLong("expiryMonth"),
                        expiryYear = obj.optLong("expiryYear"),
                        cardType = obj.optString("cardType"),
                    ),
                )
                applied++
            }
        }
        return applied
    }

    private suspend fun exportAddresses(context: Context): ByteArray {
        val array = JSONArray()
        context.components.core.autofillStorage.getAllAddresses().forEach { address ->
            array.put(
                JSONObject().apply {
                    put("name", address.name)
                    put("organization", address.organization)
                    put("streetAddress", address.streetAddress)
                    put("addressLevel3", address.addressLevel3)
                    put("addressLevel2", address.addressLevel2)
                    put("addressLevel1", address.addressLevel1)
                    put("postalCode", address.postalCode)
                    put("country", address.country)
                    put("tel", address.tel)
                    put("email", address.email)
                },
            )
        }
        return JSONObject().put("addresses", array).toString(2).toByteArray()
    }

    private suspend fun importAddresses(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("addresses") ?: return 0
        val storage = context.components.core.autofillStorage
        var applied = 0
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            runCatching {
                storage.addAddress(
                    UpdatableAddressFields(
                        name = obj.optString("name"),
                        organization = obj.optString("organization"),
                        streetAddress = obj.optString("streetAddress"),
                        addressLevel3 = obj.optString("addressLevel3"),
                        addressLevel2 = obj.optString("addressLevel2"),
                        addressLevel1 = obj.optString("addressLevel1"),
                        postalCode = obj.optString("postalCode"),
                        country = obj.optString("country"),
                        tel = obj.optString("tel"),
                        email = obj.optString("email"),
                    ),
                )
                applied++
            }
        }
        return applied
    }

    // Open tabs, history, the search choice and the account. None of these are a preference
    // in one of the two files the categories above share, which is why they were absent from
    // every archive this fork wrote before 2026-09-09.

    /**
     * The open tabs, in order, as the browser store holds them.
     *
     * URL, title and the selection — not the engine session. A [RecoverableTab]'s
     * `engineSessionState` is Gecko's own opaque per-tab blob (scroll offsets, form state,
     * session history); it does not serialise to JSON, it is meaningless in another profile,
     * and it is the bulk of the size. Restored tabs therefore come back unloaded and at the
     * top of their page, which is what a restore onto a clean phone can honestly offer.
     */
    private fun exportTabs(context: Context): ByteArray {
        val state = context.components.core.store.state
        val array = JSONArray()
        state.tabs.forEach { tab ->
            array.put(
                JSONObject().apply {
                    put("url", tab.content.url)
                    put("title", tab.content.title)
                    put("private", tab.content.private)
                    put("selected", tab.id == state.selectedTabId)
                },
            )
        }
        return JSONObject().put("tabs", array).toString(2).toByteArray()
    }

    private fun importTabs(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("tabs") ?: return 0
        val recovered = mutableListOf<RecoverableTab>()
        var selectId: String? = null
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val url = obj.optStringOrNull("url") ?: continue
            // Our own id: the exporting profile's ids mean nothing here, and reusing one
            // would collide with a tab this profile already has.
            val id = UUID.randomUUID().toString()
            if (obj.optBoolean("selected", false)) selectId = id
            recovered.add(
                RecoverableTab(
                    engineSessionState = null,
                    state = TabState(
                        id = id,
                        url = url,
                        title = obj.optString("title"),
                        private = obj.optBoolean("private", false),
                        index = index,
                    ),
                ),
            )
        }
        if (recovered.isEmpty()) return 0
        // Additive, deliberately: restoring is not a reason to close whatever is already open.
        context.components.useCases.tabsUseCases.restore(recovered, selectId)
        return recovered.size
    }

    /**
     * Browsing history, newest first, capped at [HISTORY_MAX_VISITS].
     *
     * Uncapped this is by far the largest thing in a Fenix profile and it would dominate both
     * the archive and the import's running time — the one category that could push a restore
     * past the caller's patience.
     */
    private suspend fun exportHistory(context: Context): ByteArray {
        val visits = context.components.core.historyStorage
            .getVisitsPaginated(offset = 0, count = HISTORY_MAX_VISITS)
        val array = JSONArray()
        visits.forEach { visit ->
            array.put(
                JSONObject().apply {
                    put("url", visit.url)
                    put("title", visit.title ?: JSONObject.NULL)
                    put("visitTime", visit.visitTime)
                    put("visitType", visit.visitType.name)
                },
            )
        }
        return JSONObject().put("visits", array).toString(2).toByteArray()
    }

    /**
     * **Visit times do not survive.** [HistoryStorage] has no timestamped write — `recordVisit`
     * stamps now, and places offers nothing else through the public API — so a restored history
     * comes back with every visit dated to the restore. The URLs, titles and the fact of the
     * visit are what travel; frecency rebuilds itself from there. Exported times are kept in
     * the archive anyway, against a future API that can use them.
     */
    private suspend fun importHistory(context: Context, bytes: ByteArray): Int {
        val array = JSONObject(String(bytes)).optJSONArray("visits") ?: return 0
        val storage = context.components.core.historyStorage
        var applied = 0
        // Oldest first, so that what little ordering survives is the original one.
        for (index in array.length() - 1 downTo 0) {
            val obj = array.optJSONObject(index) ?: continue
            val url = obj.optStringOrNull("url") ?: continue
            val type = runCatching { VisitType.valueOf(obj.optString("visitType")) }
                .getOrDefault(VisitType.LINK)
            runCatching {
                storage.recordVisit(url, PageVisit(visitType = type))
                obj.optStringOrNull("title")?.let {
                    storage.recordObservation(url, PageObservation(title = it))
                }
                applied++
            }
        }
        return applied
    }

    /**
     * The Firefox Account session, verbatim.
     *
     * 白い熊 asked for the sign-in to travel with the backup (2026-09-09) after being told what
     * that means, and this is what it means: the string below is the account state
     * [mozilla.components.service.fxa.SharedPrefAccountStorage] persists — refresh token
     * included — and the archive it lands in is a plain ZIP on shared storage unless 応用管理's
     * own encryption is turned on. Anything that can read the backup can sign in as him.
     *
     * Reading the preference directly rather than going through `AccountStorage` because that
     * interface is `internal` to the component; [FXA_STATE_PREFS_KEY] and [FXA_STATE_KEY] are
     * its public constants and name the same file and key. This is the plaintext storage, which
     * is what the release channel selects (`secureStateAtRest = Config.channel.isNightlyOrDebug`
     * in BackgroundServices) — on a build where that flips, the state lives in the Keystore-backed
     * store instead and this category will simply find nothing.
     */
    private fun exportAccount(context: Context): ByteArray {
        val state = context
            .getSharedPreferences(FXA_STATE_PREFS_KEY, Context.MODE_PRIVATE)
            .getString(FXA_STATE_KEY, null)
        return JSONObject().apply {
            put("fxaState", state ?: JSONObject.NULL)
        }.toString(2).toByteArray()
    }

    /**
     * Writes the account state back, synchronously.
     *
     * `commit()` rather than `apply()` for the reason the whole import flushes at the end: the
     * caller force-stops this app the moment it answers, and a queued write does not survive
     * SIGKILL. The account manager reads this file when the app next starts.
     */
    private fun importAccount(context: Context, bytes: ByteArray): Int {
        val state = JSONObject(String(bytes)).optStringOrNull("fxaState") ?: return 0
        val committed = context
            .getSharedPreferences(FXA_STATE_PREFS_KEY, Context.MODE_PRIVATE)
            .edit()
            .putString(FXA_STATE_KEY, state)
            .commit()
        return if (committed) 1 else 0
    }

    /**
     * Where the selected search engine actually lives — `mozac_feature_search_metadata`, a
     * SharedPreferences file of android-components' own, not Fenix's.
     *
     * This is the whole reason the search engine reverted to Google on every restore: the
     * exporter only ever read [KakoTheme.prefs] and Fenix's own preferences, and the choice is
     * in neither. Fenix's `pref_key_search_engine` looks like it should hold it and does not.
     *
     * The file name is `private const` in `SearchMetadataStorage`, so it is repeated here; it
     * is part of that component's on-disk layout and changing it upstream would be a migration.
     */
    private fun searchPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SEARCH_METADATA_PREFS, Context.MODE_PRIVATE)

    /** Each Places root is exported as its own subtree so a restore lands back in place. */
    private suspend fun exportBookmarks(context: Context): ByteArray {
        val storage = context.components.core.bookmarksStorage
        val roots = JSONObject()
        BOOKMARK_ROOTS.forEach { root ->
            val tree = storage.getTree(root, recursive = true).getOrNull() ?: return@forEach
            roots.put(root, JSONArray(tree.children.orEmpty().map { bookmarkToJson(it) }))
        }
        return JSONObject().put("roots", roots).toString(2).toByteArray()
    }

    private fun bookmarkToJson(node: BookmarkNode): JSONObject = JSONObject().apply {
        put("type", node.type.name)
        put("title", node.title ?: JSONObject.NULL)
        put("url", node.url ?: JSONObject.NULL)
        put("dateAdded", node.dateAdded)
        put("lastModified", node.lastModified)
        if (node.type == BookmarkNodeType.FOLDER) {
            put("children", JSONArray(node.children.orEmpty().map { bookmarkToJson(it) }))
        }
    }

    /**
     * Merges each exported root's children back under the matching live root: folders
     * go in whole through the bulk [insertTree], loose items through [addItem]. The
     * subtrees are appended, so importing twice duplicates rather than overwrites.
     */
    private suspend fun importBookmarks(context: Context, bytes: ByteArray): Int {
        val roots = JSONObject(String(bytes)).optJSONObject("roots") ?: return 0
        val storage = context.components.core.bookmarksStorage
        var applied = 0
        BOOKMARK_ROOTS.forEach { root ->
            val children = roots.optJSONArray(root) ?: return@forEach
            for (index in 0 until children.length()) {
                val obj = children.optJSONObject(index) ?: continue
                val inserted = runCatching {
                    when (obj.optString("type")) {
                        BookmarkNodeType.FOLDER.name -> {
                            val folder = jsonToInsertable(obj) as? InsertableBookmarkTreeNode.Folder
                                ?: return@runCatching false
                            storage.insertTree(InsertableBookmarkTreeRoot(root, folder)).isSuccess
                        }
                        BookmarkNodeType.ITEM.name -> {
                            val url = obj.optStringOrNull("url") ?: return@runCatching false
                            storage.addItem(root, url, obj.optString("title"), null).isSuccess
                        }
                        else -> false
                    }
                }.getOrDefault(false)
                if (inserted) applied++
            }
        }
        return applied
    }

    private fun jsonToInsertable(obj: JSONObject): InsertableBookmarkTreeNode? {
        val dateAdded = obj.optLong("dateAdded")
        val lastModified = obj.optLong("lastModified")
        return when (obj.optString("type")) {
            BookmarkNodeType.FOLDER.name -> {
                val children = obj.optJSONArray("children")
                InsertableBookmarkTreeNode.Folder(
                    title = obj.optStringOrNull("title"),
                    dateAddedTimestamp = dateAdded,
                    lastModifiedTimestamp = lastModified,
                    position = null,
                    children = (0 until (children?.length() ?: 0)).mapNotNull { index ->
                        children?.optJSONObject(index)?.let { jsonToInsertable(it) }
                    },
                )
            }
            BookmarkNodeType.ITEM.name -> InsertableBookmarkTreeNode.Item(
                title = obj.optStringOrNull("title"),
                url = obj.optStringOrNull("url") ?: return null,
                dateAddedTimestamp = dateAdded,
                lastModifiedTimestamp = lastModified,
                position = null,
            )
            BookmarkNodeType.SEPARATOR.name -> InsertableBookmarkTreeNode.Separator(
                dateAddedTimestamp = dateAdded,
                lastModifiedTimestamp = lastModified,
                position = null,
            )
            else -> null
        }
    }

    /** JSON null and the literal string "null" both mean "absent" here. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    // Prefs <-> JSON (typed t/v map)

    private fun exportPrefs(sp: SharedPreferences, include: (String) -> Boolean): ByteArray =
        prefsJson(sp, include).toString(2).toByteArray()

    private fun prefsJson(sp: SharedPreferences, include: (String) -> Boolean): JSONObject {
        val root = JSONObject()
        for ((key, value) in sp.all) {
            if (!include(key)) continue
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> null
            } ?: continue
            root.put(key, entry)
        }
        return root
    }

    /** Merged restore: one putX per included key; returns the number applied. */
    private fun importPrefs(sp: SharedPreferences, bytes: ByteArray, include: (String) -> Boolean): Int =
        importPrefsJson(sp, JSONObject(String(bytes)), include)

    private fun importPrefsJson(
        sp: SharedPreferences,
        root: JSONObject,
        include: (String) -> Boolean,
    ): Int {
        var applied = 0
        sp.edit {
            for (key in root.keys()) {
                if (!include(key)) continue
                val entry = root.optJSONObject(key) ?: continue
                when (entry.optString("t")) {
                    "b" -> putBoolean(key, entry.getBoolean("v"))
                    "i" -> putInt(key, entry.getInt("v"))
                    "l" -> putLong(key, entry.getLong("v"))
                    "f" -> putFloat(key, entry.getDouble("v").toFloat())
                    "s" -> putString(key, entry.getString("v"))
                    "ss" -> {
                        val arr = entry.getJSONArray("v")
                        putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                    else -> continue
                }
                applied++
            }
        }
        return applied
    }

    private fun fenixPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Settings.FENIX_PREFERENCES, Context.MODE_PRIVATE)

    /**
     * **Fenix keeps its settings in two preferences files, not one.**
     *
     * `Settings` reads `fenix_preferences`, and that is the file this fork exported for a year.
     * But only one settings screen in Fenix (`AccessibilityFragment`) points `preferenceManager`
     * at it: every other screen leaves the AndroidX default in place, so each switch also
     * persists itself to `<package>_preferences` — and a handful of settings live *only* there,
     * read straight back out of it (`pref_key_external_download_manager`,
     * `pref_key_downloads_clean_up_files_automatically`, the what's-new state).
     *
     * Restoring one file and not the other is why a restored phone came up looking untouched
     * (白い熊, 2026-09-09).
     */
    private fun defaultPrefsName(context: Context): String = "${context.packageName}_preferences"

    /**
     * Every `SharedPreferences` file this app has, minus [PREFS_FILE_EXCLUDE] and the key
     * stores in [PREFS_FILE_EXCLUDE_SUFFIXES], read off disk rather than listed by name.
     *
     * A file that has never been written does not exist yet, so this is also the answer to
     * "which of them does this profile actually use".
     */
    private fun settingsPrefsFiles(context: Context): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val names = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(PREFS_FILE_SUFFIX) }
            ?.map { it.name.removeSuffix(PREFS_FILE_SUFFIX) }
            .orEmpty()
        return names.filter { prefsFileTravels(it) }
    }

    private fun prefsFileTravels(name: String): Boolean =
        name !in PREFS_FILE_EXCLUDE &&
            PREFS_FILE_EXCLUDE_SUFFIXES.none { name.endsWith(it) } &&
            PREFS_FILE_EXCLUDE_PREFIXES.none { name.startsWith(it) }

    /**
     * Whether [key] in the preferences file [name] belongs to [Cat.APP_SETTINGS].
     *
     * The noisy-key filter applies to Fenix's own two files, which is where the telemetry ids,
     * experiment state and CFR counters are; the component files hold nothing of the sort, and
     * filtering them on the same fragments would drop real settings for the sake of a substring.
     * The extension keys are skipped everywhere — [Cat.EXTENSIONS] owns them.
     */
    private fun settingsKeyTravels(context: Context, name: String, key: String): Boolean {
        if (key in extensionKeys(context)) return false
        val fenixOwned = name == Settings.FENIX_PREFERENCES || name == defaultPrefsName(context)
        if (!fenixOwned) return true
        return APP_SETTINGS_EXCLUDE_FRAGMENTS.none { key.contains(it, ignoreCase = true) }
    }

    /**
     * `{"files": {"<prefs file>": {<typed key map>}}}`.
     *
     * A v2 archive wrote the typed key map alone, being the one file it knew about; [importAppSettings]
     * still reads that shape.
     */
    private fun exportAppSettings(context: Context): ByteArray {
        val files = JSONObject()
        settingsPrefsFiles(context).forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            files.put(name, prefsJson(prefs) { settingsKeyTravels(context, name, it) })
        }
        return JSONObject().put("files", files).toString(2).toByteArray()
    }

    private fun importAppSettings(context: Context, bytes: ByteArray): Int {
        val root = JSONObject(String(bytes))
        val files = root.optJSONObject("files")
            // A pre-v3 archive: one flat typed key map, which was Fenix's own file.
            ?: return importPrefsJson(fenixPrefs(context), root) {
                settingsKeyTravels(context, Settings.FENIX_PREFERENCES, it)
            }
        var applied = 0
        for (name in files.keys()) {
            // The archive names the file, so re-check it here too: an archive is an input, and
            // the one thing that must never come back is a key store from another phone.
            if (!prefsFileTravels(name)) continue
            val values = files.optJSONObject(name) ?: continue
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            applied += importPrefsJson(prefs, values) { settingsKeyTravels(context, name, it) }
        }
        return applied
    }

    /**
     * Forces every preferences file this app has onto disk, synchronously.
     *
     * An empty `commit()` writes the whole current map and blocks until it is there, so it
     * subsumes any `apply()` still queued behind it. Called at the end of [import] because the
     * caller of a headless import force-stops this app the instant it is told the import
     * succeeded, and a `SIGKILL` runs no shutdown hook and drains no queued write: the restore
     * reports success over preferences that never reached disk.
     *
     * Every file, not the two the import used to name. The search-engine choice went missing
     * exactly this way — restored into `mozac_feature_search_metadata`, never flushed, killed.
     */
    fun flushPrefs(context: Context) {
        val names = buildSet {
            add(PREFS_NAME)
            add(Settings.FENIX_PREFERENCES)
            add(defaultPrefsName(context))
            add(SEARCH_METADATA_PREFS)
            add(FXA_STATE_PREFS_KEY)
            addAll(settingsPrefsFiles(context))
        }
        names.forEach { name ->
            runCatching {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit()
            }
        }
    }

    /** Where android-components keeps one XML file per search engine 白い熊 added himself. */
    private fun customSearchEnginesDir(context: Context): File =
        File(context.filesDir, SEARCH_ENGINES_DIR).also { if (!it.exists()) it.mkdirs() }

    /**
     * The Fenix settings that belong to [Cat.EXTENSIONS] rather than to the general
     * settings: the pinned toolbar set/order and the custom AMO collection the fork
     * installs extensions from. Restoring the collection needs a restart to bite,
     * which the import dialog offers anyway.
     */
    private fun extensionKeys(context: Context): Set<String> = setOf(
        context.getString(R.string.pref_key_kako_toolbar_extensions),
        context.getString(R.string.pref_key_override_amo_user),
        context.getString(R.string.pref_key_override_amo_collection),
    )

}
