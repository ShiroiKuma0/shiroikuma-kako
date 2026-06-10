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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.surfaceDimVariant

/**
 * Compose helpers wiring the 白い熊 火狐 UI traced-border look into Fenix surfaces.
 */

/**
 * The traced border of the ACTIVE tab strip item — inactive tabs are drawn as one
 * shared rectangular block by [kakoInactiveTabLines] instead, so only the active
 * tab carries a discrete rounded outline and stands out.
 */
@Composable
fun kakoTabStripBorder(isSelected: Boolean): BorderStroke? {
    KakoTheme.revision.intValue
    if (!isSelected) return null
    val context = LocalContext.current
    return KakoTheme.borderStroke(context, KakoSlot.TAB_ACTIVE_BORDER, KakoDimen.TAB_ACTIVE_BORDER_WIDTH)
}

/**
 * Draws the inactive tabs of the tab strip as one continuous rectangular block:
 * a single line along the top and bottom, one shared vertical separator between
 * adjacent inactive tabs, and closing edges where the block starts/ends (or meets
 * the active tab, which carries its own rounded border).
 *
 * Applied to the full item slot (including the inter-tab gap) so the lines run
 * unbroken across tab boundaries.
 *
 * @param isSelected Whether this item is the active tab (then nothing is drawn).
 * @param closeStart Whether the block starts here (first tab, or previous is active).
 * @param joinNext Whether the next item is another inactive tab to join up with.
 * @param gap The spacing after the card, part of this item's slot.
 */
@Suppress("CognitiveComplexMethod")
fun Modifier.kakoInactiveTabLines(
    isSelected: Boolean,
    closeStart: Boolean,
    joinNext: Boolean,
    gap: Dp,
): Modifier = composed {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    val widthDp = KakoTheme.dimenDp(context, KakoDimen.TAB_BORDER_WIDTH)
    if (isSelected || !KakoTheme.isEnabled(context) || widthDp <= 0f) {
        return@composed this
    }
    val color = Color(KakoTheme.color(context, KakoSlot.TAB_BORDER))
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val strokePx = with(LocalDensity.current) { widthDp.dp.toPx() }

    drawWithContent {
        drawContent()
        val cardEnd = size.width - gapPx
        val lineEnd = if (joinNext) size.width else cardEnd
        // Top and bottom rails.
        drawLine(color, Offset(0f, strokePx / 2), Offset(lineEnd, strokePx / 2), strokePx)
        drawLine(
            color,
            Offset(0f, size.height - strokePx / 2),
            Offset(lineEnd, size.height - strokePx / 2),
            strokePx,
        )
        if (closeStart) {
            drawLine(color, Offset(strokePx / 2, 0f), Offset(strokePx / 2, size.height), strokePx)
        }
        if (joinNext) {
            // One shared separator, centred in the gap between the two tabs.
            val x = cardEnd + gapPx / 2
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokePx)
        } else {
            drawLine(
                color,
                Offset(cardEnd - strokePx / 2, 0f),
                Offset(cardEnd - strokePx / 2, size.height),
                strokePx,
            )
        }
    }
}

/**
 * The traced outline of standalone menu cards drawn by `Surface`-based composables
 * (library tiles, banners); null when off or 0-width.
 */
@Composable
fun kakoMenuCardBorder(): BorderStroke? {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    return KakoTheme.borderStroke(context, KakoSlot.MENU_BORDER, KakoDimen.MENU_BORDER_WIDTH)
}

/**
 * The clipped, filled card background of menu items and similar tiles. Items inside
 * a [kakoMenuGroup]-styled container stay untraced — the group draws one shared
 * outline and the separators; standalone cards pass [traced] = true to get their
 * own outline.
 */
fun Modifier.kakoMenuCard(shape: Shape, traced: Boolean = false): Modifier = composed {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    val border = if (traced) {
        KakoTheme.borderStroke(context, KakoSlot.MENU_BORDER, KakoDimen.MENU_BORDER_WIDTH)
    } else {
        null
    }
    this
        .clip(shape)
        .then(border?.let { Modifier.border(it, shape) } ?: Modifier)
        .background(color = MaterialTheme.colorScheme.surfaceDimVariant)
}

/**
 * Group container treatment: one traced outline around the whole group, with the
 * inter-item gaps revealing a border-colored backdrop — adjoining items thus share
 * a single thin separator line instead of stacking two borders.
 */
fun Modifier.kakoMenuGroup(shape: Shape): Modifier = composed {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    val border = KakoTheme.borderStroke(context, KakoSlot.MENU_BORDER, KakoDimen.MENU_BORDER_WIDTH)
    this
        .clip(shape)
        .then(
            border?.let {
                Modifier
                    .border(it, shape)
                    .background(Color(KakoTheme.color(context, KakoSlot.MENU_BORDER)))
            } ?: Modifier,
        )
}

/**
 * The spacing between items inside a menu group: the configured separator width
 * when the kako theme traces groups, else the stock 2dp gap.
 */
@Composable
fun kakoMenuGroupSpacing(): Dp {
    KakoTheme.revision.intValue
    val context = LocalContext.current
    val width = KakoTheme.dimenDp(context, KakoDimen.MENU_BORDER_WIDTH)
    return if (KakoTheme.isEnabled(context) && width > 0f) width.dp else 2.dp
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
    if (width <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(width.dp)
            .background(Color(KakoTheme.color(context, KakoSlot.TOOLBAR_TOP_BORDER))),
    )
}
