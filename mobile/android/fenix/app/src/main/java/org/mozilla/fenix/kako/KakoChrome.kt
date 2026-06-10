/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.surfaceDimVariant

/**
 * Compose helpers wiring the 白い熊 火狐 UI traced-border look into Fenix surfaces.
 */

/**
 * The traced border of a tab strip item; null when the kako theme is off or the
 * configured width is 0.
 */
@Composable
fun kakoTabStripBorder(isSelected: Boolean): BorderStroke? {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    return if (isSelected) {
        KakoTheme.borderStroke(context, KakoSlot.TAB_ACTIVE_BORDER, KakoDimen.TAB_ACTIVE_BORDER_WIDTH)
    } else {
        KakoTheme.borderStroke(context, KakoSlot.TAB_BORDER, KakoDimen.TAB_BORDER_WIDTH)
    }
}

/**
 * The traced outline of menu cards drawn by `Surface`-based composables
 * (library tiles, banners); null when off or 0-width.
 */
@Composable
fun kakoMenuCardBorder(): BorderStroke? {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    return KakoTheme.borderStroke(context, KakoSlot.MENU_BORDER, KakoDimen.MENU_BORDER_WIDTH)
}

/**
 * The clipped, traced, filled card background of menu items and similar tiles —
 * replaces the stock `clip(shape) + background(surfaceDimVariant)` chain so the
 * kako theme can draw its outline between the two.
 */
fun Modifier.kakoMenuCard(shape: Shape): Modifier = composed {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    val border = KakoTheme.borderStroke(context, KakoSlot.MENU_BORDER, KakoDimen.MENU_BORDER_WIDTH)
    this
        .clip(shape)
        .then(border?.let { Modifier.border(it, shape) } ?: Modifier)
        .background(color = MaterialTheme.colorScheme.surfaceDimVariant)
}

/**
 * The horizontal rule along the top edge of the browser toolbar (running under the
 * tab strip, like Nightly's divider). Renders nothing when disabled or 0-width.
 */
@Composable
fun KakoToolbarEdge() {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    if (!KakoTheme.isEnabled(context)) return
    val width = KakoTheme.dimenDp(context, KakoDimen.TOOLBAR_TOP_BORDER_WIDTH)
    if (width <= 0) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(width.dp)
            .background(Color(KakoTheme.color(context, KakoSlot.TOOLBAR_TOP_BORDER))),
    )
}
