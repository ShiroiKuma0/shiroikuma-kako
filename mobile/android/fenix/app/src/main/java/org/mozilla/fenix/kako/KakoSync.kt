/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.scale
import org.mozilla.fenix.ext.bitmapForUrl
import org.mozilla.fenix.ext.components

/**
 * 白い熊 火狐: the Mozilla-account avatar worn by the toolbar's Sync-now button.
 *
 * The trailing toolbar actions are rebuilt on every extension, tab and sync change,
 * so a network image cannot be part of that build: the avatar is fetched once per
 * URL and display size and kept here, and every rebuild is then served from memory
 * without waiting on the network. The bitmap is what is cached — each build gets its
 * own drawable, since a drawable carries per-composition state.
 */
object KakoSyncAvatar {
    private val cache = mutableMapOf<String, Bitmap>()

    /**
     * The avatar for [url] at [sizePx], circle-cropped and ready for the toolbar, or
     * null when it has not been fetched yet (see [prefetch]).
     */
    fun drawable(context: Context, url: String, sizePx: Int): Drawable? {
        val bitmap = synchronized(cache) { cache[key(url, sizePx)] } ?: return null
        return RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
            isCircular = true
            setAntiAlias(true)
        }
    }

    /**
     * Fetches [url] — from Gecko's HTTP cache when it holds it — and keeps it scaled to
     * [sizePx]. Returns true once the avatar is cached, false when the fetch failed and
     * the button has to keep the generic avatar glyph.
     */
    suspend fun prefetch(context: Context, url: String, sizePx: Int): Boolean {
        if (synchronized(cache) { cache.containsKey(key(url, sizePx)) }) return true

        val fetched = context.components.core.client.bitmapForUrl(url) ?: return false
        // The toolbar takes an action's size from its drawable's intrinsic size, so the
        // bitmap is scaled to the icon size and stamped with the device's density —
        // the same honest-intrinsic-size trick the pinned extension icons use.
        val scaled = fetched.scale(sizePx, sizePx).apply {
            density = context.resources.displayMetrics.densityDpi
        }
        synchronized(cache) { cache[key(url, sizePx)] = scaled }
        return true
    }

    private fun key(url: String, sizePx: Int) = "$url@$sizePx"
}
