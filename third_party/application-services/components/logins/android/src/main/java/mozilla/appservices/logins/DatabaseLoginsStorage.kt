/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.appservices.logins

/*
 * Import some private Glean types, so that we can use them in type declarations.
 *
 * I do not like importing these private classes, but I do like the nice generic
 * code they allow me to write! By agreement with the Glean team, we must not
 * instantiate anything from these classes, and it's on us to fix any bustage
 * on version updates.
 */


/**
 * An artifact of the uniffi conversion - a thin-ish wrapper around a
 * LoginStore.
 */

class DatabaseLoginsStorage(dbPath: String, keyManager: KeyManager) : AutoCloseable {
    private var store: LoginStore

    init {
        val encdec = createManagedEncdec(keyManager)
        this.store = LoginStore(dbPath, encdec)
    }

    @Throws(LoginsApiException::class)
    fun reset() {
        this.store.reset()
    }

    @Throws(LoginsApiException::class)
    fun wipeLocal() {
        this.store.wipeLocal()
    }

    @Throws(LoginsApiException::class)
    fun delete(id: String): Boolean {
        return store.delete(id)
    }

    @Throws(LoginsApiException::class)
    fun get(id: String): Login? {
        return store.get(id)
    }

    @Throws(LoginsApiException::class)
    fun touch(id: String) {
        store.touch(id)
    }

    @Throws(LoginsApiException::class)
    fun isEmpty(): Boolean {
        return store.isEmpty()
    }

    @Throws(LoginsApiException::class)
    fun list(): List<Login> {
        return store.list()
    }

    /**
     * Counts the amount of logins.
     *
     * @return The number of logins.
     */
    @Throws(LoginsApiException::class)
    fun count(): Long {
        return store.count()
    }

    @Throws(LoginsApiException::class)
    fun hasLoginsByBaseDomain(baseDomain: String): Boolean {
        return store.hasLoginsByBaseDomain(baseDomain)
    }

    @Throws(LoginsApiException::class)
    fun getByBaseDomain(baseDomain: String): List<Login> {
        return store.getByBaseDomain(baseDomain)
    }

    @Throws(LoginsApiException::class)
    fun findLoginToUpdate(look: LoginEntry): Login? {
        return store.findLoginToUpdate(look)
    }

    @Throws(LoginsApiException::class)
    fun add(entry: LoginEntry): Login {
        return store.add(entry)
    }

    /**
     * Adds multiple logins.
     *
     * @return a list of inserted logins.
     */
    @Throws(LoginsApiException::class)
    fun addMany(entries: List<LoginEntry>): List<BulkResultEntry> {
        return store.addMany(entries)
    }

    @Throws(LoginsApiException::class)
    fun update(id: String, entry: LoginEntry): Login {
        return store.update(id, entry)
    }

    @Throws(LoginsApiException::class)
    fun addOrUpdate(entry: LoginEntry): Login {
        return store.addOrUpdate(entry)
    }

    fun registerWithSyncManager() {
        return store.registerWithSyncManager()
    }

    /**
     * Performs maintenance actions on the store
     */
    fun runMaintenance() {
        return store.runMaintenance()
    }

    @Synchronized
    @Throws(LoginsApiException::class)
    override fun close() {
        store.close()
    }

    @Throws(LoginsApiException::class)
    fun deleteUndecryptableLoginsAndRecordMetrics() {
        val result = store.deleteUndecryptableRecordsForRemoteReplacement()
        if (result.localDeleted > 0u) {
        }
        if (result.mirrorDeleted > 0u) {
        }
    }
}

enum class KeyRegenerationEventReason {
    Lost, Corrupt, Other,
}

fun recordKeyRegenerationEvent(reason: KeyRegenerationEventReason) {
    // Avoid the deprecation warning when calling  `record()` without the optional EventExtras param
    @Suppress("DEPRECATION")
    when (reason) {
        KeyRegenerationEventReason.Lost -> Unit
        KeyRegenerationEventReason.Corrupt -> Unit
        KeyRegenerationEventReason.Other -> Unit
    }
}
