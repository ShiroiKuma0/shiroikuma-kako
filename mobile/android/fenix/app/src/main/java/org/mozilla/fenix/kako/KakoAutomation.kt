/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate on the external-automation intent surface ([KakoStateExportReceiver]): a master
 * switch that is OFF until 白い熊 turns it on, plus a shared secret every automation
 * broadcast must carry. Same model as the renrakusaki fork's Config and 自由作業盤's
 * AutomationAuth, so all sister apps are configured identically.
 *
 * Device-local by design: these prefs live in their own file, which is not one of the
 * files [KakoExim] exports — the token never travels inside a backup ZIP.
 */
object KakoAutomation {

    private const val PREFS_FILE = "kako_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"

    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
    }

    /** The shared secret; generated on first read so the settings row always shows a value. */
    fun token(context: Context): String =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            ?: regenerateToken(context)

    fun regenerateToken(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit { putString(KEY_TOKEN, token) }
        return token
    }

    /** `80922d8c…4c49a87c` — what the settings row shows instead of the whole secret. */
    fun abbreviated(token: String): String =
        if (token.length <= ABBREVIATED_EDGE * 2) token
        else "${token.take(ABBREVIATED_EDGE)}…${token.takeLast(ABBREVIATED_EDGE)}"

    /**
     * True when the caller's token matches the stored secret (constant-time). The enabled
     * check is deliberately separate so callers can report "automation disabled" and
     * "bad token" as distinct failures — they debug differently.
     */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    private const val ABBREVIATED_EDGE = 8
}
