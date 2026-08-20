/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import org.mozilla.gecko.EventDispatcher
import org.mozilla.gecko.util.BundleEventListener
import org.mozilla.gecko.util.GeckoBundle
import java.util.concurrent.ConcurrentHashMap

/**
 * The embedder half of the `menus` WebExtension API the fork adds to GeckoView
 * (`mobile/shared/components/extensions/ext-menus.js`).
 *
 * On the PC an extension hangs its own options off a right-click on its toolbar button;
 * upstream ships that API for desktop only, so on Android those options had nowhere to
 * live. The engine side now publishes each extension's browser-action items here, and
 * the long-press menu of a pinned toolbar button renders them below the fork's own
 * move/remove entries.
 *
 * Talking to Gecko over [EventDispatcher] directly keeps the whole feature inside the
 * fork: GeckoView's own `WebExtension.Menu` scaffolding is a package-private stub
 * (`WebExtensionController.getMenu()` still returns null for bug 1595822), and going
 * through it would mean carrying a patch on GeckoView, android-components *and* Fenix
 * through every rebase instead of one file here.
 */
object KakoExtensionMenus {

    /** Menu item types, mirroring `menus.ItemType`. */
    const val TYPE_SEPARATOR = "separator"
    const val TYPE_CHECKBOX = "checkbox"
    const val TYPE_RADIO = "radio"

    /**
     * One entry of an extension's browser-action menu.
     *
     * @property key Opaque handle identifying the item to the engine.
     * @property title Label to display, already stripped of access-key markers.
     * @property type One of `normal`, `checkbox`, `radio` or `separator`.
     * @property checked Whether a checkbox/radio item is currently ticked.
     * @property enabled Whether the item accepts a tap.
     * @property depth Nesting level; the engine flattens submenus and reports the depth
     * so the popup, which has no submenus of its own, can indent instead.
     */
    data class Item(
        val key: String,
        val title: String,
        val type: String,
        val checked: Boolean,
        val enabled: Boolean,
        val depth: Int,
    )

    private const val EVENT_UPDATE = "Kako:ExtensionMenu:Update"
    private const val EVENT_CLICK = "Kako:ExtensionMenu:Click"
    private const val EVENT_SHOWN = "Kako:ExtensionMenu:Shown"
    private const val EVENT_HIDDEN = "Kako:ExtensionMenu:Hidden"

    private val menus = ConcurrentHashMap<String, List<Item>>()

    @Volatile
    private var registered = false

    private val listener = BundleEventListener { _, message, callback ->
        val extensionId = message.getString("extensionId")
        if (extensionId.isNullOrEmpty()) {
            callback?.sendError("Missing extensionId")
            return@BundleEventListener
        }

        val items = (message.getBundleArray("items") ?: emptyArray()).mapNotNull { it.toItem() }
        if (items.isEmpty()) {
            menus.remove(extensionId)
        } else {
            menus[extensionId] = items
        }
        callback?.sendSuccess(null)
    }

    private fun GeckoBundle.toItem(): Item? {
        val key = getString("key") ?: return null
        return Item(
            key = key,
            title = getString("title").orEmpty(),
            type = getString("type") ?: "normal",
            checked = getBoolean("checked", false),
            enabled = getBoolean("enabled", true),
            depth = getInt("depth", 0),
        )
    }

    /**
     * Starts listening for menu updates. Safe to call before Gecko has started — the
     * dispatcher holds the registration until the runtime is up — and safe to call
     * twice, which matters because a duplicate registration throws on a debug build.
     */
    fun register() {
        if (registered) return
        registered = true
        EventDispatcher.getInstance().registerUiThreadListener(listener, EVENT_UPDATE)
    }

    /**
     * The items an extension currently offers on its browser action, or an empty list
     * when it offers none — which is the common case, since only extensions that call
     * `browser.menus.create` with a `browser_action`/`action` context appear here.
     */
    fun itemsFor(extensionId: String): List<Item> = menus[extensionId].orEmpty()

    /**
     * The extension whose menu is currently on screen, so the `menus.onShown` an
     * extension gets is always closed by an `onHidden`. The popup has no dismissal
     * callback that reaches this far, so the pair is closed on the next open or on a
     * tap; a menu abandoned by tapping elsewhere stays "shown" until then, which
     * matters to nobody -- both events are advisory refresh hints.
     */
    private var shownFor: String? = null

    /** Tells the extension its menu is on screen, so `menus.onShown` fires. */
    fun notifyShown(extensionId: String) {
        notifyHidden()
        shownFor = extensionId
        dispatch(EVENT_SHOWN, extensionId)
    }

    /** Closes the open menu, if any, so `menus.onHidden` fires. */
    fun notifyHidden() {
        val open = shownFor ?: return
        shownFor = null
        dispatch(EVENT_HIDDEN, open)
    }

    /**
     * Reports a tap, which fires the item's `menus.onClicked` (or its deprecated
     * `onclick` property) in the extension.
     */
    fun click(extensionId: String, key: String) {
        notifyHidden()
        val bundle = GeckoBundle(2).apply {
            putString("extensionId", extensionId)
            putString("key", key)
        }
        EventDispatcher.getInstance().dispatch(EVENT_CLICK, bundle)
    }

    private fun dispatch(event: String, extensionId: String) {
        val bundle = GeckoBundle(1).apply { putString("extensionId", extensionId) }
        EventDispatcher.getInstance().dispatch(event, bundle)
    }
}
