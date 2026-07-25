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
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.R
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
    const val EXPORT_PREFIX = "shiroikuma-kako-"

    /** Warning red used by the exim status lines (Kōjiki convention). */
    const val WARN_COLOR = 0xFFFF5252.toInt()

    // Device-local prefs carrying the chosen export directory — deliberately its
    // own file so it is itself never part of an export.
    private const val EXIMPORT_PREFS = "kako_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    /** One exportable category; [id] doubles as the JSON file name inside the ZIP. */
    enum class Cat(val id: String, @param:StringRes val labelRes: Int) {
        KAKO_UI("kako_ui.json", R.string.kako_eim_cat_ui),
        FONTS("fonts.json", R.string.kako_eim_cat_fonts),
        EXTENSIONS("extensions.json", R.string.kako_eim_cat_extensions),
        APP_SETTINGS("app_settings.json", R.string.kako_eim_cat_app_settings),
    }

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

    /** The newest `shiroikuma-kako-*.zip` in the chosen directory, or null. */
    fun latestExport(context: Context): DocumentFile? {
        val dir = exportDir(context) ?: return null
        return runCatching {
            dir.listFiles().filter {
                it.isFile && it.name?.startsWith(EXPORT_PREFIX) == true && it.name?.endsWith(".zip") == true
            }.maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    fun fmtTs(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(ts))

    fun exportFileName(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        return "$EXPORT_PREFIX$version-export_$stamp.zip"
    }

    // Export

    /** Writes the selected categories to [out]; returns a short summary ("N categories"). */
    fun export(context: Context, cats: Set<Cat>, out: OutputStream): String {
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
                put("createdTs", System.currentTimeMillis())
                put("categories", JSONArray(cats.map { it.id.removeSuffix(".json") }))
            }
            put("manifest.json", manifest.toString(2).toByteArray())

            cats.forEach { cat ->
                when (cat) {
                    Cat.KAKO_UI -> put(cat.id, exportPrefs(KakoTheme.prefs(context)) { key ->
                        key !in FONT_KEYS && key !in KAKO_UI_EXCLUDE
                    })
                    Cat.FONTS -> {
                        put(cat.id, exportPrefs(KakoTheme.prefs(context)) { it in FONT_KEYS })
                        KakoFonts.fontsDir(context).listFiles()?.filter { it.isFile }?.forEach { font ->
                            put("fonts/${font.name}", font.readBytes())
                        }
                    }
                    Cat.EXTENSIONS -> put(cat.id, exportPrefs(fenixPrefs(context)) {
                        it == pinnedExtensionsKey(context)
                    })
                    Cat.APP_SETTINGS -> put(cat.id, exportPrefs(fenixPrefs(context)) { key ->
                        key != pinnedExtensionsKey(context) &&
                            APP_SETTINGS_EXCLUDE_FRAGMENTS.none { key.contains(it, ignoreCase = true) }
                    })
                }
            }
        }
        return "${cats.size} categories"
    }

    // Import

    /** The categories present in [zip] — used to reject files that are not our exports. */
    fun categoriesIn(zip: ByteArray): Set<Cat> {
        val names = readZip(zip).keys
        return Cat.entries.filter { it.id in names }.toSet()
    }

    /**
     * Restores the selected categories from [zip]; returns a per-category summary
     * ("Label: N" lines). A category missing from the file is skipped silently; a
     * failing category is skipped without aborting the rest.
     */
    fun import(context: Context, zip: ByteArray, cats: Set<Cat>): String {
        val entries = readZip(zip)
        val lines = mutableListOf<String>()

        cats.forEach { cat ->
            val bytes = entries[cat.id] ?: return@forEach
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
                    Cat.EXTENSIONS -> importPrefs(fenixPrefs(context), bytes) {
                        it == pinnedExtensionsKey(context)
                    }
                    Cat.APP_SETTINGS -> importPrefs(fenixPrefs(context), bytes) { key ->
                        key != pinnedExtensionsKey(context) &&
                            APP_SETTINGS_EXCLUDE_FRAGMENTS.none { key.contains(it, ignoreCase = true) }
                    }
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

    // Prefs <-> JSON (typed t/v map)

    private fun exportPrefs(sp: SharedPreferences, include: (String) -> Boolean): ByteArray {
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
        return root.toString(2).toByteArray()
    }

    /** Merged restore: one putX per included key; returns the number applied. */
    private fun importPrefs(sp: SharedPreferences, bytes: ByteArray, include: (String) -> Boolean): Int {
        val root = JSONObject(String(bytes))
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

    private fun pinnedExtensionsKey(context: Context): String =
        context.getString(R.string.pref_key_kako_toolbar_extensions)

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
