/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.base.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fork hooks for the 白い熊 火狐 UI: chrome styling that cannot be expressed through
 * [AcornColors] or the Material color scheme alone. Set by Fenix's KakoTheme whenever
 * the user changes a setting; consumed by the compose-base buttons, switches, menus and
 * dialogs and by the browser toolbar. Null values mean "stock behavior".
 *
 * Every colour in these styles is a settable role of the fork's UI page; nothing here
 * holds a colour of its own.
 */
object AcornForkOverrides {
    /** Traced outline of the address bar pill in the browser toolbar. */
    @Volatile
    var addressBarBorder: BorderStroke? = null

    /** Replacement style for snackbars and similar transient flashes. */
    @Volatile
    var snackbarStyle: ForkChromeStyle? = null

    /** The outlined pill every button is drawn as. */
    @Volatile
    var pills: ForkPillStyle? = null

    /** The outlined switch. */
    @Volatile
    var switches: ForkSwitchStyle? = null

    /** Menu rows: full-width pills, role-coloured icons, the group divider. */
    @Volatile
    var menus: ForkMenuStyle? = null

    /** Dialog panel and text. */
    @Volatile
    var dialogs: ForkDialogStyle? = null
}

/**
 * A fill color, content color and optional traced outline applied to a piece of
 * chrome when the fork theme is active.
 */
data class ForkChromeStyle(
    val containerColor: Color,
    val contentColor: Color,
    val border: BorderStroke?,
)

/**
 * The pill: fully round, an accent frame, the ground showing through, never filled. The main
 * action of a block has the [thick] frame and a bold label, every other the [thin] one; a
 * deleting action lends [delete] to frame and label.
 */
data class ForkPillStyle(
    val ground: Color,
    val frame: Color,
    val label: Color,
    val mainFrame: Color,
    val mainLabel: Color,
    val delete: Color,
    val ripple: Color,
    val thin: Dp = 1.25.dp,
    val thick: Dp = 3.dp,
)

/** The outlined switch: a round outline with a solid dot, right when on and left when off. */
data class ForkSwitchStyle(
    val on: Color,
    val onDot: Color,
    val off: Color,
    val offDot: Color,
)

/** What a row does, which decides the colour of its icon. */
enum class ForkIconRole {
    /** Connects, synchronises, reloads, signs in, resets. */
    CONNECT,

    /** Writes: a message, a note, an edit, a translation. */
    MESSAGE,

    /** Is about a person or an account. */
    CONTACT,

    /** Copies, shares, saves, exports, downloads, prints. */
    SHARE,

    /** Views, opens a page, settings. */
    VIEW,

    /** Blocks, mutes, locks, keeps private. */
    BLOCK,

    /** Deletes, removes, closes for good. */
    DELETE,
}

/** Menu rows. */
data class ForkMenuStyle(
    val ground: Color,
    val rowFrame: Color,
    val label: Color,
    val header: Color,
    val divider: Color,
    val delete: Color,
    val iconConnect: Color,
    val iconMessage: Color,
    val iconContact: Color,
    val iconShare: Color,
    val iconView: Color,
    val iconBlock: Color,
) {
    /** The colour of an icon with [role]. */
    fun icon(role: ForkIconRole): Color =
        when (role) {
            ForkIconRole.CONNECT -> iconConnect
            ForkIconRole.MESSAGE -> iconMessage
            ForkIconRole.CONTACT -> iconContact
            ForkIconRole.SHARE -> iconShare
            ForkIconRole.VIEW -> iconView
            ForkIconRole.BLOCK -> iconBlock
            ForkIconRole.DELETE -> delete
        }
}

/** Dialog panel and text. */
data class ForkDialogStyle(
    val ground: Color,
    val frame: Color,
    val title: Color,
    val text: Color,
    val frameWidth: Dp = 2.dp,
)

/**
 * The role of an icon, told from the entry name of its drawable (`mozac_ic_delete_24`), or of a
 * row from the name of its view or menu item id. Unknown names are [ForkIconRole.VIEW].
 */
@Suppress("CyclomaticComplexMethod")
fun forkIconRoleForName(name: String): ForkIconRole {
    val n = name.lowercase()
    fun has(vararg words: String) = words.any { it in n }
    return when {
        has("delete", "trash", "remove", "clear", "uninstall", "erase", "quit", "discard") -> ForkIconRole.DELETE
        has(
            "block", "private", "lock", "shield", "mute", "disable", "permission", "tracking", "cookie",
            "warning", "critical", "report", "stop", "cross", "close", "dismiss", "cancel",
        ) -> ForkIconRole.BLOCK
        has("avatar", "account", "profile", "contact", "login", "password", "passkey", "sign", "user", "person") ->
            ForkIconRole.CONTACT
        has(
            "share", "copy", "export", "download", "save", "print", "send", "pdf", "import", "upload", "link",
            "collection", "bookmark", "pin", "shortcut", "homescreen", "add_to",
        ) -> ForkIconRole.SHARE
        has("edit", "pencil", "translate", "message", "feedback", "rename", "note", "write", "reader", "font", "text") ->
            ForkIconRole.MESSAGE
        has(
            "sync", "reload", "refresh", "arrow_clockwise", "update", "restore", "undo", "reset", "connect", "vpn",
            "globe", "open_in", "external", "app_", "plus", "add", "new", "back", "forward", "desktop", "device",
        ) -> ForkIconRole.CONNECT
        else -> ForkIconRole.VIEW
    }
}
