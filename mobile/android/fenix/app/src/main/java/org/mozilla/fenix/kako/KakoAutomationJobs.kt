/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The jobs the data door ([KakoAutomationProvider]) has started, and the flag each of them
 * watches to stop.
 *
 * One at a time is not enforced here — a caller asking for two exports at once is asking for two
 * files, and refusing that is [KakoAutomationDataService]'s business. What this owns is the
 * mapping from the id a caller was handed to a cancellation it can act on, which must outlive the
 * binder call that created it and be reachable from a service that never saw the caller.
 */
object KakoAutomationJobs {

    private val cancelled = ConcurrentHashMap<String, Boolean>()

    fun begin(): String = UUID.randomUUID().toString().also { cancelled[it] = false }

    /**
     * Ask a job to stop. A no-op for an id that is finished or was never real.
     *
     * Deliberately silent: a cancel arriving after the work completed is the normal race, not an
     * error, and answering it as one would make every well-behaved caller look broken.
     */
    fun cancel(jobId: String?) {
        jobId?.let { cancelled.computeIfPresent(it) { _, _ -> true } }
    }

    /** Polled at write boundaries — never mid-write, so a cancelled archive is never half a file. */
    fun isCancelled(jobId: String): Boolean = cancelled[jobId] == true

    fun finish(jobId: String) {
        cancelled.remove(jobId)
    }
}
