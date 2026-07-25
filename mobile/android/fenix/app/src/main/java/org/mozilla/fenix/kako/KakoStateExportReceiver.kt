/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The sister-app **state-export automation contract**, implemented for 白い熊 火狐 — the
 * same wire shape every 白い熊 app exposes so 自由作業盤's 保存復元 project can back them
 * all up headlessly in one run (reference implementations: renrakusaki's
 * BackupContactsReceiver, the EMUI-proven round-trip, and 自由作業盤's StateExportReceiver).
 *
 * - `<pkg>.action.EXPORT_STATE`: run the full category-ZIP export ([KakoExim]) without UI.
 *   Extras (all String): `token` (required — [KakoAutomation]), `path` (optional absolute
 *   directory, wins over the configured SAF directory), `items` (optional comma list of
 *   [KakoExim.Cat] ids; absent/empty = all), `progress_action` (optional — see below), plus
 *   the reply trio `reply_action` / `reply_package` / `reply_id`.
 * - `<pkg>.action.LIST_CATEGORIES`: token-gated category enumeration for the caller's picker.
 *
 * Reply: a FRESH broadcast to `reply_package` with action `reply_action`, extras `reply_id`
 * (echoed verbatim) + `result` = `OK:<path>|<bytes>|<human size>|<n> categories`
 * (EXPORT_STATE), `OK:` + `id<TAB>label` lines (LIST_CATEGORIES), or `ERROR:<reason>`.
 * Exactly one terminal reply, single-fire guarded. NO binders and NO ordered-result
 * reliance — EMUI severs both between third-party apps (verified on the Mate XT,
 * 2026-07-23); the plain reply broadcast is the only working channel.
 * [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so a backgrounded caller still hears us.
 *
 * Progress: while exporting, plain broadcasts to `reply_package` with action
 * `progress_action`, extras `reply_id`, `app` (display label), `text` (real counts, never a
 * percentage — `区分 3/8 — Fonts`), and structured `current`/`total` (long) + `unit`.
 */
class KakoStateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()
        val pathOverride = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
        val items = intent.getStringExtra(EXTRA_ITEMS)?.trim().orEmpty()

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            if (replyAction.isEmpty() || replyPackage.isEmpty()) return
            if (!replied.compareAndSet(false, true)) return
            app.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra(EXTRA_RESULT, result)
                },
            )
        }

        // Gate first, and report "disabled" and "bad token" distinctly (family convention).
        if (!KakoAutomation.enabled(app)) {
            reply("ERROR:automation disabled")
            return
        }
        if (!KakoAutomation.isTokenValid(app, token)) {
            reply("ERROR:bad token")
            return
        }

        when (action) {
            "${app.packageName}$SUFFIX_LIST_CATEGORIES" -> {
                reply(
                    "OK:" + KakoExim.Cat.entries.joinToString("\n") {
                        "${it.id}\t${app.getString(it.labelRes)}"
                    },
                )
            }

            "${app.packageName}$SUFFIX_EXPORT_STATE" -> {
                val cats: Set<KakoExim.Cat> = if (items.isEmpty()) {
                    KakoExim.Cat.entries.toSet()
                } else {
                    val ids = items.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val resolved = ids.mapNotNull { KakoExim.Cat.byId(it) }
                    if (resolved.size != ids.size) {
                        reply("ERROR:unknown category in items: $items")
                        return
                    }
                    resolved.toSet()
                }
                val appLabel = runCatching {
                    app.packageManager.getApplicationLabel(app.applicationInfo).toString()
                }.getOrDefault(app.packageName)
                val fileName = KakoExim.exportFileName()

                var lastProgressAt = 0L
                fun progress(done: Int, total: Int, catLabel: String) {
                    if (progressAction.isEmpty() || replyPackage.isEmpty()) return
                    // At most one every 500 ms — but the closing one always goes out.
                    val now = SystemClock.elapsedRealtime()
                    if (done < total && now - lastProgressAt < PROGRESS_MIN_INTERVAL_MS) return
                    lastProgressAt = now
                    app.sendBroadcast(
                        Intent(progressAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(EXTRA_REPLY_ID, replyId)
                            putExtra(EXTRA_PROGRESS_APP, appLabel)
                            putExtra(EXTRA_PROGRESS_TEXT, "区分 $done/$total — $catLabel")
                            putExtra(EXTRA_PROGRESS_CURRENT, done.toLong())
                            putExtra(EXTRA_PROGRESS_TOTAL, total.toLong())
                            putExtra(EXTRA_PROGRESS_UNIT, PROGRESS_UNIT)
                        },
                    )
                }

                // The export walks Places, the logins store and the autofill store, then
                // writes a ZIP — go async and finish from IO.
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        reply(exportTo(app, pathOverride, fileName, cats, ::progress))
                    } catch (e: Exception) {
                        reply("ERROR:${e.message ?: e.javaClass.simpleName}")
                    } finally {
                        pending.finish()
                    }
                }
            }

            else -> reply("ERROR:unknown action: $action")
        }
    }

    /**
     * Writes the one ZIP and returns the `OK:` line. Directory precedence is the contract's:
     * the `path` extra, then the app's configured export directory, then `ERROR:no-directory`.
     *
     * `path` is honoured with plain [File] I/O, which needs All-Files-Access. Without that
     * permission the contract says to ignore `path` **only** when a SAF directory is
     * configured — and otherwise to say `no-storage-access` rather than write somewhere else.
     */
    private suspend fun exportTo(
        app: Context,
        pathOverride: String,
        fileName: String,
        cats: Set<KakoExim.Cat>,
        onProgress: (Int, Int, String) -> Unit,
    ): String {
        if (pathOverride.isNotEmpty() && hasAllFilesAccess()) {
            val dir = File(pathOverride)
            dir.mkdirs()
            if (!dir.isDirectory) throw IllegalArgumentException("not a directory: $pathOverride")
            val file = File(dir, fileName)
            file.outputStream().use { out -> KakoExim.export(app, cats, out, onProgress) }
            return okLine(file.absolutePath, file.length(), cats.size)
        }

        val dir = KakoExim.exportDir(app)
            ?: throw IllegalStateException(
                if (pathOverride.isEmpty()) "no-directory" else "no-storage-access",
            )
        val doc = dir.createFile("application/zip", fileName)
            ?: throw IllegalStateException("cannot create $fileName in the export directory")
        app.contentResolver.openOutputStream(doc.uri)?.use { out ->
            KakoExim.export(app, cats, out, onProgress)
        } ?: throw IllegalStateException("cannot open $fileName for writing")
        return okLine(absolutePathOf(app, doc, fileName), doc.length(), cats.size)
    }

    private fun okLine(path: String, bytes: Long, categories: Int): String =
        "OK:$path|$bytes|${humanSize(bytes)}|$categories categories"

    /**
     * The contract's reply carries an absolute path, but a SAF write only yields a document
     * URI — so rebuild the path from its `<volume>:<relative>` document id
     * (`primary` is the shared internal storage). Falls back to the directory's display
     * name if the id is not in that shape.
     */
    private fun absolutePathOf(app: Context, doc: DocumentFile, fileName: String): String =
        runCatching {
            val docId = DocumentsContract.getDocumentId(doc.uri)
            val volume = docId.substringBefore(':')
            val relative = docId.substringAfter(':', "")
            require(relative.isNotEmpty())
            val root = if (volume == "primary") {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                "/storage/$volume"
            }
            "$root/$relative"
        }.getOrElse { "${KakoExim.dirLabel(app).orEmpty()}/${doc.name ?: fileName}" }

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    companion object {
        const val SUFFIX_EXPORT_STATE = ".action.EXPORT_STATE"
        const val SUFFIX_LIST_CATEGORIES = ".action.LIST_CATEGORIES"

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_PROGRESS_ACTION = "progress_action"
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_PROGRESS_APP = "app"
        private const val EXTRA_PROGRESS_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "current"
        private const val EXTRA_PROGRESS_TOTAL = "total"
        private const val EXTRA_PROGRESS_UNIT = "unit"

        private const val PROGRESS_UNIT = "区分"
        private const val PROGRESS_MIN_INTERVAL_MS = 500L

        /** The caller cannot stat the file, so we hand it a display size as well. */
        fun humanSize(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
            else -> "$bytes B"
        }
    }
}
