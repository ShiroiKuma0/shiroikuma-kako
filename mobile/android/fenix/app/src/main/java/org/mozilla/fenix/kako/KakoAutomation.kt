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
 * The gate on the external-automation surface — the [KakoStateExportReceiver] broadcasts and
 * the [KakoAutomationProvider] data door. Same model as 自由作業盤's AutomationAuth, so all
 * sister apps are configured identically.
 *
 * ## v2: the switch ships ON and the token is opt-in
 *
 * v1 shipped every app closed — `automation_enabled` defaulted to false and a caller also had
 * to present a 48-character secret 白い熊 had pasted from here into the caller's settings.
 * **A pasted secret cannot survive a wipe**, and the case this family now exists to serve is
 * 応用管理 restoring apps *and their data* onto a clean phone, where nothing is configured yet
 * and nobody has pasted anything. A gate that only works once the phone is already set up is
 * no gate for setting the phone up.
 *
 * So the master switch defaults **on** (it stays a switch because it is the only way to close
 * this app off, and a feature that can be turned on but never off is one 白い熊 cannot retreat
 * from), and `automation_require_token` defaults **off**. What replaces the secret is not
 * nothing: the door that moves data ([KakoAutomationProvider]) identifies its caller through
 * the framework — see [KakoAutomationCallers].
 *
 * Device-local by design: these prefs live in their own file, which is not one of the files
 * [KakoExim] exports — the token never travels inside a backup ZIP.
 */
object KakoAutomation {

    private const val PREFS_FILE = "kako_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"

    private const val TOKEN_BYTES = 24

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Default **true** (v2): every app answers automation out of the box. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
    }

    /** Default **false** (v2): the token is an extra a caller may be asked for, not the gate. */
    fun requireToken(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_REQUIRE_TOKEN, value) }
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
     * The whole gate, in one function: `null` means proceed, anything else is the exact
     * `ERROR:` line to answer with.
     *
     * One place on purpose. Two checks written out at each entry point is how "disabled" and
     * "bad token" drift apart across forty-two apps — and they must stay distinct, because
     * they debug differently.
     *
     * **A token handed to an app that does not require one is IGNORED, never refused.** Tokens
     * live in task arguments and workspace variables that outlive the setting they were pasted
     * for; a caller still sending one — because it was configured last year, or because another
     * app on the batch does want one — must be served. Refusing it would turn "白い熊 turned a
     * switch off" into "half the batch mysteriously fails", which is precisely the friction the
     * switch exists to remove.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
        else -> null
    }

    /**
     * True when the caller's token matches the stored secret (constant-time). Only consulted
     * when [requireToken] is on — but the constant-time compare stays for that case.
     */
    fun isTokenValid(context: Context, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    private const val ABBREVIATED_EDGE = 8
}
