/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mozilla.fenix.R
import org.mozilla.fenix.utils.Settings
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data-door export or import actually runs — the payload half of
 * [KakoAutomationProvider].
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [KakoAutomationProvider] before it got here, because the original belongs
 * to the binder transaction and is closed the moment `call()` returns. This service owns the copy
 * and closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and
 * the caller cannot checksum or encrypt a file that is still open.
 */
class KakoAutomationDataService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true
        val replyAction = intent?.getStringExtra(KakoAutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(KakoAutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent?.getStringExtra(KakoAutomationProvider.KEY_PROGRESS_ACTION)
        val items = intent?.getStringExtra(KakoAutomationProvider.KEY_ITEMS)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            if (jobId != null) KakoAutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    // Without this a caller that has been backgrounded never hears the answer, and
                    // on a clean phone the caller may not have been launched at all.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(KakoAutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(KakoAutomationProvider.KEY_RESULT, result)
                },
            )
        }

        // FIRST, and before ANY return out of this method.
        //
        // [ContextCompat.startForegroundService] has already promised the platform a foreground
        // service. Return without calling `startForeground` — even on a job id we do not
        // recognise — and the platform kills the process with
        // `ForegroundServiceDidNotStartInTimeException`. So a caller that retries with a stale or
        // finished job id would take 白い熊's whole browser down with it, which is why the two
        // "nothing to do" exits below are AFTER this rather than before it (shiroikuma-nekokan,
        // 2026-09-04).
        //
        // The type is named in the manifest; naming it here too only matters from API 34, where
        // `specialUse` exists as a type and carries a permission. Below that, pass 0 and let the
        // manifest speak — the platform then skips the "requested ⊆ declared" check entirely,
        // and 白い熊's Mate XT is an EMUI 14.2 phone that reports SDK_INT = 31 whatever else it
        // is. A type constant the running platform has never heard of is not worth arguing with.
        //
        // **This can throw**: a binder-call start is a background start, which API 31+ may refuse
        // outright. If it does, the caller's file must not be left open by a service that never
        // started — nothing else will ever come to close it, and a caller cannot checksum or
        // encrypt an open file.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        val wentForeground = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(importing), type)
        }

        // Claimed only now, so that a stale id costs us a no-op instead of the process.
        val fd = jobId?.let { HANDOVER.remove(it) }

        wentForeground.exceptionOrNull()?.let { t ->
            runCatching { fd?.close() }
            reply("ERROR:cannot go foreground: ${t.message ?: t.javaClass.simpleName}")
            return halt(startId)
        }
        if (jobId == null || fd == null) {
            // An unknown, stale or already-collected job id. A silent no-op — there is no
            // descriptor to move and, with no job, nobody expecting a terminal reply.
            return halt(startId)
        }

        scope.launch {
            try {
                fd.use { open ->
                    if (importing) {
                        runImport(jobId, open, ::reply)
                    } else {
                        runExport(
                            jobId = jobId,
                            fd = open,
                            items = items,
                            progressAction = progressAction,
                            replyPackage = replyPackage,
                            reply = ::reply,
                        )
                    }
                }
            } catch (t: Throwable) {
                reply("ERROR:${t.message ?: t.javaClass.simpleName}")
            } finally {
                ServiceCompat.stopForeground(this@KakoAutomationDataService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String?,
        replyPackage: String?,
        reply: (String) -> Unit,
    ) {
        val cats = resolve(items) ?: run { reply("ERROR:unknown category in items: $items"); return }
        // Enum order, exactly as KakoExim.export walks it — so the position it reports can be
        // turned back into the category id §3 wants in `item`.
        val ordered = KakoExim.Cat.entries.filter { it in cats }
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            // Counted as it goes rather than stat'ed afterwards: the caller owns the file and we
            // may not be able to see it at all — it can be an anonymous pipe or a descriptor into
            // a directory this app cannot list.
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b)
                    written++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                    written += len
                }

                override fun flush() = out.flush()
            }
            var lastProgressAt = 0L
            KakoExim.export(
                context = this,
                cats = cats,
                out = counting,
                onProgress = { done, total, label ->
                    val now = SystemClock.elapsedRealtime()
                    // At most one every 500 ms — but the closing one always goes out.
                    if (done >= total || now - lastProgressAt >= PROGRESS_MIN_INTERVAL_MS) {
                        lastProgressAt = now
                        sendProgress(
                            progressAction = progressAction,
                            replyPackage = replyPackage,
                            jobId = jobId,
                            item = ordered.getOrNull(done - 1)?.id.orEmpty(),
                            done = done,
                            total = total,
                            label = label,
                            bytes = written,
                        )
                    }
                },
                isCancelled = { KakoAutomationJobs.isCancelled(jobId) },
            )
        }
        if (KakoAutomationJobs.isCancelled(jobId)) reply("ERROR:cancelled")
        else reply("OK:$written|${cats.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [KakoExim.import] wants the bytes, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would otherwise import half an archive, and
     * a half-restored app is worse than one that refused.
     */
    private suspend fun runImport(jobId: String, fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        // Spooled to disk rather than read straight off the descriptor into a ByteArray.
        // [KakoExim.import] wants the whole archive as bytes, but growing an array from a stream
        // of unknown length reallocates as it goes and peaks at several times the final size —
        // and this app's archive carries imported font files and a whole bookmark tree, not just
        // a settings blob. A spool file has an exact length, so the array is allocated once; the
        // cap turns a hostile or corrupt descriptor into an ERROR: line instead of an OOM kill.
        val spool = File(cacheDir, "kako-automation-import-$jobId.zip")
        val bytes = try {
            var copied = 0L
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                spool.outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_IMPORT_BYTES) {
                            reply("ERROR:archive too large (over ${MAX_IMPORT_BYTES shr 20} MB)")
                            return
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (copied == 0L) {
                reply("ERROR:empty archive")
                return
            }
            spool.readBytes()
        } finally {
            // Never leave the caller's data lying in our cache, on any path out of here.
            spool.delete()
        }
        // Every category the archive actually carries, not every category we know about: asking
        // for one the archive lacks is how a restore ends up reporting success over nothing.
        val present = runCatching { KakoExim.categoriesIn(bytes) }.getOrDefault(emptySet())
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        val summary = KakoExim.import(this, bytes, present)
        flushImportedPrefs()
        // The caller force-stops us straight after this. That is deliberate and belongs on its
        // side: a running process writes its cached SharedPreferences back out at orderly shutdown
        // and silently undoes the import that just happened (応用管理 paid for this one already).
        //
        // `result` is ONE line: KakoExim.import answers a per-category block, so it is folded onto
        // one before it goes out — a reply the caller has to reassemble is a reply it will get wrong.
        reply("OK:${present.size} restored|${summary.lines().filter { it.isNotBlank() }.joinToString(" · ")}")
    }

    /**
     * Force every imported preference onto disk **before** the import is reported successful.
     *
     * 応用管理 force-stops us the instant we reply `OK` to an import, and that force-stop is a
     * `SIGKILL` — no shutdown hook, no `QueuedWork` drain. [KakoExim.import] restores preferences
     * through `SharedPreferences.edit {}`, which is `apply()`: an immediate in-memory write plus a
     * *queued* disk write. Killed in between, the restore reports success over preferences that
     * never reached disk, and the failure is invisible until 白い熊 opens the app and finds his
     * settings missing.
     *
     * An empty synchronous `commit()` is what closes it: it writes the whole current map and
     * blocks until it is on disk, so it subsumes any `apply()` still queued behind it. Both files
     * the import touches are flushed — the fork's own theme prefs and Fenix's.
     */
    private fun flushImportedPrefs() {
        listOf(
            runCatching { KakoTheme.prefs(this) },
            runCatching { getSharedPreferences(Settings.FENIX_PREFERENCES, Context.MODE_PRIVATE) },
        ).forEach { prefs ->
            prefs.getOrNull()?.let { runCatching { it.edit().commit() } }
        }
    }

    /**
     * §3's progress broadcast. `item` carries the **category id** being written so the caller's
     * panel can light the right row — it cannot work that out from `current`, which is only ever
     * a display number.
     */
    @Suppress("LongParameterList")
    private fun sendProgress(
        progressAction: String?,
        replyPackage: String?,
        jobId: String,
        item: String,
        done: Int,
        total: Int,
        label: String,
        bytes: Long,
    ) {
        if (progressAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
        val appLabel = runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)
        sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                // Both ids: the data door correlates on job_id, and §3's panel reads reply_id.
                putExtra(KakoAutomationProvider.KEY_JOB_ID, jobId)
                putExtra(EXTRA_REPLY_ID, jobId)
                putExtra(EXTRA_PROGRESS_APP, appLabel)
                putExtra(EXTRA_PROGRESS_ITEM, item)
                putExtra(EXTRA_PROGRESS_TEXT, "区分 $done/$total — $label")
                putExtra(EXTRA_PROGRESS_CURRENT, done.toLong())
                putExtra(EXTRA_PROGRESS_TOTAL, total.toLong())
                putExtra(EXTRA_PROGRESS_UNIT, PROGRESS_UNIT)
                putExtra(EXTRA_PROGRESS_BYTES, bytes)
            },
        )
    }

    /** Absent/empty `items` means this app's DEFAULT set — what it reports as `on`, not everything. */
    private fun resolve(items: String?): Set<KakoExim.Cat>? {
        if (items.isNullOrBlank()) return KakoExim.Cat.entries.filter { it.defaultOn }.toSet()
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { KakoExim.Cat.byId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    private fun notification(importing: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, CHANNEL_LABEL, NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(if (importing) "データを戻しています" else "データを書き出しています")
            .setSmallIcon(R.drawable.ic_status_logo)
            .setOngoing(true)
            .build()
    }

    /**
     * Leave cleanly from a path that does no work — foreground state dropped and the ongoing
     * notification with it, so a no-op start never leaves a notification 白い熊 cannot dismiss.
     */
    private fun halt(startId: Int): Int {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "kako_automation_data"
        private const val CHANNEL_LABEL = "自動化データ"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_PROGRESS_APP = "app"
        private const val EXTRA_PROGRESS_ITEM = "item"
        private const val EXTRA_PROGRESS_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "current"
        private const val EXTRA_PROGRESS_TOTAL = "total"
        private const val EXTRA_PROGRESS_UNIT = "unit"
        private const val EXTRA_PROGRESS_BYTES = "bytes"

        private const val PROGRESS_UNIT = "区分"
        private const val PROGRESS_MIN_INTERVAL_MS = 500L

        /** A ceiling on an incoming archive, so a hostile or corrupt descriptor cannot OOM us. */
        private const val MAX_IMPORT_BYTES = 512L shl 20

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ) {
            HANDOVER[jobId] = fd
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KakoAutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(
                            KakoAutomationProvider.KEY_ITEMS,
                            extras?.getString(KakoAutomationProvider.KEY_ITEMS),
                        )
                        putExtra(
                            KakoAutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(KakoAutomationProvider.KEY_REPLY_ACTION),
                        )
                        putExtra(
                            KakoAutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(KakoAutomationProvider.KEY_REPLY_PACKAGE),
                        )
                        putExtra(
                            KakoAutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(KakoAutomationProvider.KEY_PROGRESS_ACTION),
                        )
                    },
                )
            } catch (t: Throwable) {
                // The service will never come to collect it — do not strand the caller's file
                // open in this map. The provider turns this into an ERROR: line.
                HANDOVER.remove(jobId)
                throw t
            }
        }
    }
}
