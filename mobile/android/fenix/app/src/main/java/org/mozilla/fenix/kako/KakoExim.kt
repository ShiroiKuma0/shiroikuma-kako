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
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.CreditCardNumber
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.NewCreditCardFields
import mozilla.components.concept.storage.UpdatableAddressFields
import mozilla.components.concept.storage.bookmarks.InsertableBookmarkTreeNode
import mozilla.components.concept.storage.bookmarks.InsertableBookmarkTreeRoot
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.utils.Settings
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    const val VERSION = 1

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
     */
    enum class Cat(
        val id: String,
        @param:StringRes val labelRes: Int,
        val sensitive: Boolean = false,
    ) {
        KAKO_UI("kako_ui", R.string.kako_eim_cat_ui),
        FONTS("fonts", R.string.kako_eim_cat_fonts),
        EXTENSIONS("extensions", R.string.kako_eim_cat_extensions),
        APP_SETTINGS("app_settings", R.string.kako_eim_cat_app_settings),
        BOOKMARKS("bookmarks", R.string.kako_eim_cat_bookmarks),
        LOGINS("logins", R.string.kako_eim_cat_logins, sensitive = true),
        CARDS("credit_cards", R.string.kako_eim_cat_cards, sensitive = true),
        ADDRESSES("addresses", R.string.kako_eim_cat_addresses, sensitive = true),
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
     * Writes the selected categories to [out]; returns a short summary ("N categories").
     *
     * The single export core, callable headlessly: the Export/Import panel and the
     * automation receiver ([KakoStateExportReceiver]) are both thin callers of this.
     * [onProgress] (done, total, category label) fires after each written category —
     * the receiver forwards it as contract progress broadcasts; UI callers omit it.
     */
    suspend fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
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
                    Cat.APP_SETTINGS -> put(cat.fileName, exportPrefs(fenixPrefs(context)) { key ->
                        key !in extensionKeys(context) &&
                            APP_SETTINGS_EXCLUDE_FRAGMENTS.none { key.contains(it, ignoreCase = true) }
                    })
                    Cat.BOOKMARKS -> put(cat.fileName, exportBookmarks(context))
                    Cat.LOGINS -> put(cat.fileName, exportLogins(context))
                    Cat.CARDS -> put(cat.fileName, exportCards(context))
                    Cat.ADDRESSES -> put(cat.fileName, exportAddresses(context))
                }
                onProgress(index + 1, ordered.size, context.getString(cat.labelRes))
            }
        }
        return "${cats.size} categories"
    }

    // Import

    /** The categories present in [zip] — used to reject files that are not our exports. */
    fun categoriesIn(zip: ByteArray): Set<Cat> {
        val names = readZip(zip).keys
        return Cat.entries.filter { it.fileName in names }.toSet()
    }

    /**
     * Restores the selected categories from [zip]; returns a per-category summary
     * ("Label: N" lines). A category missing from the file is skipped silently; a
     * failing category is skipped without aborting the rest.
     */
    suspend fun import(context: Context, zip: ByteArray, cats: Set<Cat>): String {
        val entries = readZip(zip)
        val lines = mutableListOf<String>()

        cats.forEach { cat ->
            val bytes = entries[cat.fileName] ?: return@forEach
            val applied = runCatching {
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
                    Cat.APP_SETTINGS -> importPrefs(fenixPrefs(context), bytes) { key ->
                        key !in extensionKeys(context) &&
                            APP_SETTINGS_EXCLUDE_FRAGMENTS.none { key.contains(it, ignoreCase = true) }
                    }
                    Cat.BOOKMARKS -> importBookmarks(context, bytes)
                    Cat.LOGINS -> importLogins(context, bytes)
                    Cat.CARDS -> importCards(context, bytes)
                    Cat.ADDRESSES -> importAddresses(context, bytes)
                }
            }.getOrDefault(-1)
            if (applied >= 0) lines.add("${context.getString(cat.labelRes)}: $applied")
        }

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
    // [Cat.sensitive] and start unchecked.

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

    private fun readZip(zip: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(zip.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = stream.readBytes()
                entry = stream.nextEntry
            }
        }
        return entries
    }
}
