/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.BundleCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * The data door: export 白い熊 火狐's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared
 * secret, which cannot survive the wipe that this feature exists to recover from. A provider gets
 * the caller's identity from the framework for free — see [KakoAutomationCallers] for what is
 * actually checked and why a package-name prefix would have been worse than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a file descriptor the caller
 * opened, and the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A
 * file this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing
 * (応用管理, 2026-09-04). A descriptor is also a capability that **expires when it is closed**.
 *
 * ## Why `import` lives ONLY here
 *
 * An import overwrites this app's data, and [KakoStateExportReceiver] is `exported="true"` with no
 * permission — an import action there would let any app on the phone wipe any sister app.
 */
class KakoAutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = KakoAutomationCallers.verify(ctx, callingPackage)) {
            is KakoAutomationCallers.Verdict.Refused -> return fail(verdict.why)
            KakoAutomationCallers.Verdict.Allowed -> Unit
        }
        // Then the app's own switches — a token is ignored unless this app asks for one.
        KakoAutomation.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        // "Never throw" is a rule about the whole method, not only the parts that obviously
        // could: anything escaping here reaches the caller as a RuntimeException carrying our
        // stack trace, which tells 白い熊 nothing and tells a misbehaving caller rather more
        // than it should. So the dispatch is caught as a whole and turned into an ERROR: line.
        return runCatching {
            when (method) {
                METHOD_DESCRIBE -> ok(describe(ctx))
                METHOD_EXPORT -> start(ctx, extras, importing = false)
                METHOD_IMPORT -> start(ctx, extras, importing = true)
                METHOD_CANCEL -> {
                    KakoAutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                    ok("OK:cancelled")
                }
                else -> fail("ERROR:unknown method: $method")
            }
        }.getOrElse { fail("ERROR:${it.message ?: it.javaClass.simpleName}") }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive (応用管理, 2026-09-04).
     */
    private fun describe(ctx: Context): String {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val cats = KakoExim.Cat.entries.filter { it.defaultOn }
        val header = JSONObject().apply {
            put("app_id", ctx.packageName)
            put("version_code", PackageInfoCompat.getLongVersionCode(info))
            put("version_name", info.versionName.orEmpty())
            put("format", FORMAT)
            put("min_format_readable", MIN_FORMAT_READABLE)
            // Every storage this app imports into is lazily created and works headlessly — the
            // v1 export has run from a manifest receiver with no Activity since 2026-07.
            put("requires_launch_first", false)
            put("contains", JSONArray(cats.map { ctx.getString(it.labelRes) }))
        }
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        val fd = extras?.let { BundleCompat.getParcelable(it, KEY_FD, ParcelFileDescriptor::class.java) }
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = KakoAutomationJobs.begin()
        // A foreground-service start can be refused (background-start rules, EMUI's own
        // policies). Answering that with an ERROR: line beats throwing it across the binder —
        // and the duplicate must not be leaked when it never reaches the service, because a
        // caller cannot checksum or encrypt a file this app is still holding open.
        val started = runCatching {
            KakoAutomationDataService.start(ctx, jobId, dup, importing, extras)
        }
        return started.fold(
            onSuccess = { ok("OK:$jobId") },
            onFailure = { t ->
                KakoAutomationJobs.finish(jobId)
                runCatching { dup.close() }
                fail("ERROR:cannot start: ${t.message ?: t.javaClass.simpleName}")
            },
        )
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * This app's archive format — [KakoExim.VERSION]. Bumped when an older build could no
         * longer read what we write.
         */
        const val FORMAT = KakoExim.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
