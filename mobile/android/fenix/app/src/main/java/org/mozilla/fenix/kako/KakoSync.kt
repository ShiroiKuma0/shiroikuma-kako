/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.fenix.ext.bitmapForUrl
import org.mozilla.fenix.ext.components

/**
 * 白い熊 火狐: the Mozilla-account avatar worn by the toolbar's Sync-now button.
 *
 * The trailing toolbar actions are rebuilt on every extension, tab and sync change,
 * so a network image cannot be part of that build: the avatar is fetched once per
 * URL and kept here, and every rebuild is then served from memory without waiting on
 * the network.
 *
 * What is kept is one source bitmap per URL, decoded large enough for the biggest
 * toolbar icon size the 白い熊 火狐 UI slider allows; the size actually shown is scaled
 * from it on demand. Keying the fetch by display size, as the first cut did, meant a
 * change of icon size looked up a size nobody had fetched and the button fell back to
 * the generic glyph until the next restart. The bitmap is what is cached — each build
 * gets its own drawable, since a drawable carries per-composition state.
 */
object KakoSyncAvatar {
    private val sources = mutableMapOf<String, Bitmap>()
    private val scaled = mutableMapOf<String, Bitmap>()

    private val _revision = MutableStateFlow(0)

    /** Bumped whenever a picture lands in the cache, so the toolbar knows to redraw. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Whether the picture for [url] is in memory. */
    fun has(url: String): Boolean = synchronized(sources) { sources.containsKey(url) }

    /**
     * The avatar for [url] at [sizePx], circle-cropped and ready for the toolbar, or
     * null when it has not been fetched yet (see [prefetch]).
     */
    fun drawable(context: Context, url: String, sizePx: Int): Drawable? {
        val bitmap = synchronized(sources) {
            val source = sources[url] ?: return null
            scaled.getOrPut(key(url, sizePx)) {
                // The toolbar takes an action's size from its drawable's intrinsic size, so
                // the bitmap is scaled to the icon size and stamped with the device's
                // density — the same honest-intrinsic-size trick the pinned extension
                // icons use.
                source.scale(sizePx, sizePx).apply {
                    density = context.resources.displayMetrics.densityDpi
                }
            }
        }
        return RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
            isCircular = true
            setAntiAlias(true)
        }
    }

    /**
     * Fetches [url] — from Gecko's HTTP cache when it holds it, or straight from the
     * server when [force] is set — and keeps it. Returns true once the avatar is cached,
     * false when the fetch failed and the button has to keep the generic avatar glyph.
     */
    suspend fun prefetch(context: Context, url: String, force: Boolean = false): Boolean {
        if (!force && has(url)) return true

        val sourcePx = (KakoUiSettingsFragment.EXTENSION_ICON_MAX_DP * context.resources.displayMetrics.density).toInt()
        val fetched = context.components.core.client.bitmapForUrl(
            url,
            targetWidth = sourcePx,
            targetHeight = sourcePx,
            useCaches = !force,
        ) ?: return false
        synchronized(sources) {
            sources[url] = fetched
            scaled.keys.removeAll { it.startsWith("$url@") }
        }
        _revision.update { it + 1 }
        return true
    }

    private fun key(url: String, sizePx: Int) = "$url@$sizePx"
}
