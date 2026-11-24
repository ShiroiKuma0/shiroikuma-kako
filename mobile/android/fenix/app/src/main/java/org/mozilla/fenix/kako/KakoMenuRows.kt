/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.compose.base.theme.forkIconRoleForName
import mozilla.components.support.ktx.android.view.ForkViewStyle
import org.mozilla.fenix.R
import androidx.appcompat.R as appcompatR
import mozilla.components.ui.icons.R as iconsR

/**
 * 白い熊 火狐 UI: the view-based menus and action lists that live in android-components — the
 * long-press link and image menu, and the legacy browser menu rows Fenix still shows (the
 * Edit / Delete menu of a search engine). Those modules cannot see Fenix, so they hand each row
 * to [ForkViewStyle.hook], which [KakoPalette] sets to this object.
 *
 * Every action row is a full-width outlined pill, 1.25 dp, with its icon inside at the left in
 * the colour of what the row does; a › at the right where the row opens something; a deleting
 * row lends the delete colour to frame, icon and label; groups are parted by a 1 dp line.
 * With the theme off every method leaves the view as upstream made it.
 */
object KakoMenuRows : ForkViewStyle.Hook {
    private const val SIDE_DP = 8f
    private const val GAP_DP = 3f
    private const val INSIDE_DP = 14f
    private const val ICON_DP = 20f
    private const val ICON_GAP_DP = 12f
    private const val DIVIDER_DP = 1f
    private const val GROUP_GAP_DP = 9f
    private const val RIPPLE_ALPHA = 0.3f
    private const val CONTEXT_MENU_PREFIX = "mozac.feature.contextmenu."

    /** What one row is: its icon, what it does, and whether it opens something. */
    private class Row(@param:DrawableRes val icon: Int, val role: ForkIconRole, val opens: Boolean)

    override fun menuRow(label: TextView, id: String) {
        if (!KakoTheme.isEnabled(label.context)) return
        paint(label, if (id.isEmpty()) rowForLabel(label) else rowForId(id))
    }

    /**
     * Makes [label] an action row for a list the fork builds itself: the same full-width pill,
     * with [icon] in the colour of [role] and a › when it [opens] something.
     */
    fun actionRow(label: TextView, @DrawableRes icon: Int, role: ForkIconRole, opens: Boolean) =
        paint(label, Row(icon, role, opens))

    private fun paint(label: TextView, row: Row) {
        val context = label.context
        val delete = row.role == ForkIconRole.DELETE
        val ink = KakoTheme.color(context, if (delete) KakoSlot.MENU_DELETE else KakoSlot.MENU_LABEL)
        val frame = KakoTheme.color(context, if (delete) KakoSlot.MENU_DELETE else KakoSlot.MENU_ROW_FRAME)

        val side = KakoPills.px(context, SIDE_DP)
        val gap = KakoPills.px(context, GAP_DP)
        val inside = side + KakoPills.px(context, INSIDE_DP)
        // The whole pill is the touch target and carries the ripple; the list's own
        // rectangular highlight goes with the background it replaces.
        val pill = KakoPills.pill(
            context,
            main = false,
            frame = frame,
            ground = null,
            ripple = KakoTheme.alpha(frame, RIPPLE_ALPHA),
        )
        label.background = InsetDrawable(pill, side, gap, side, gap)
        label.setPaddingRelative(inside, 0, inside, 0)
        label.setTextColor(ink)

        val size = KakoPills.px(context, ICON_DP)
        val icon = KakoRowIcons.tinted(context, row.icon, KakoTheme.iconColor(context, row.role), size)
        // The › stays in the row's own colour, not the icon's.
        val chevron =
            if (row.opens) KakoRowIcons.tinted(context, iconsR.drawable.mozac_ic_chevron_right_24, ink, size) else null
        label.setCompoundDrawablesRelative(icon, null, chevron, null)
        label.compoundDrawablePadding = KakoPills.px(context, ICON_GAP_DP)
    }

    override fun menuList(list: View, ids: List<String>) {
        val context = list.context
        if (list !is RecyclerView || !KakoTheme.isEnabled(context)) return
        list.addItemDecoration(GroupDividers(context, ids.map { groupOf(it) }))
    }

    override fun menuDivider(divider: View) {
        val context = divider.context
        if (!KakoTheme.isEnabled(context)) return
        divider.setBackgroundColor(KakoTheme.color(context, KakoSlot.MENU_DIVIDER))
    }

    private fun rowForId(id: String): Row {
        val name = id.removePrefix(CONTEXT_MENU_PREFIX)
        CONTEXT_MENU[name]?.let { return it }
        val role = forkIconRoleForName(name)
        return Row(KakoRowIcons.iconForName(name) ?: KakoRowIcons.iconForRole(role), role, opens = false)
    }

    /**
     * A row with no identifier: the label is all there is. Upstream's own strings say which
     * row it is, in whatever language the app is shown in; failing that, a label in the error
     * colour is a deleting row.
     */
    private fun rowForLabel(label: TextView): Row {
        val context = label.context
        val text = label.text?.toString().orEmpty()
        BY_LABEL.firstOrNull { (res, _) -> context.getString(res) == text }?.let { return it.second }
        val error = MaterialColors.getColor(context, appcompatR.attr.colorError, 0)
        return if (error != 0 && label.currentTextColor == error) {
            Row(iconsR.drawable.mozac_ic_delete_24, ForkIconRole.DELETE, opens = false)
        } else {
            Row(iconsR.drawable.mozac_ic_cursor_arrow_24, ForkIconRole.VIEW, opens = false)
        }
    }

    /** Rows that open a page, rows that copy, rows that hand something on. */
    private fun groupOf(id: String): Int {
        val name = id.removePrefix(CONTEXT_MENU_PREFIX)
        return when {
            name.startsWith("open") -> 0
            name.startsWith("copy") -> 1
            else -> 2
        }
    }

    /** The 1 dp line between two groups of rows, its ends meeting the pill edges. */
    private class GroupDividers(context: Context, private val groups: List<Int>) : RecyclerView.ItemDecoration() {
        private val side = KakoPills.px(context, SIDE_DP)
        private val gap = KakoPills.px(context, GROUP_GAP_DP)
        private val paint = Paint().apply {
            color = KakoTheme.color(context, KakoSlot.MENU_DIVIDER)
            strokeWidth = KakoPills.px(context, DIVIDER_DP).toFloat()
        }

        private fun startsGroup(position: Int): Boolean =
            position > 0 && position < groups.size && groups[position] != groups[position - 1]

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (startsGroup(parent.getChildAdapterPosition(view))) outRect.top = gap
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (!startsGroup(parent.getChildAdapterPosition(child))) continue
                val y = child.top - gap / 2f
                c.drawLine(side.toFloat(), y, (parent.width - side).toFloat(), y, paint)
            }
        }
    }

    private fun opens(@DrawableRes icon: Int, role: ForkIconRole) = Row(icon, role, opens = true)

    private fun acts(@DrawableRes icon: Int, role: ForkIconRole) = Row(icon, role, opens = false)

    /** The candidates of the long-press menu, by the last part of their id. */
    private val CONTEXT_MENU: Map<String, Row> = mapOf(
        "open_in_new_tab" to opens(iconsR.drawable.mozac_ic_tab_24, ForkIconRole.CONNECT),
        "open_in_private_tab" to opens(iconsR.drawable.mozac_ic_private_mode_24, ForkIconRole.BLOCK),
        "open_in_external_app" to opens(iconsR.drawable.mozac_ic_external_link_24, ForkIconRole.CONNECT),
        "open_image_in_new_tab" to opens(iconsR.drawable.mozac_ic_image_24, ForkIconRole.CONNECT),
        "add_to_contact" to opens(iconsR.drawable.mozac_ic_avatar_circle_24, ForkIconRole.CONTACT),
        "share_email" to opens(iconsR.drawable.mozac_ic_share_android_24, ForkIconRole.SHARE),
        "share_link" to opens(iconsR.drawable.mozac_ic_share_android_24, ForkIconRole.SHARE),
        "share_image" to opens(iconsR.drawable.mozac_ic_share_android_24, ForkIconRole.SHARE),
        "copy_email_address" to acts(iconsR.drawable.mozac_ic_copy_24, ForkIconRole.SHARE),
        "copy_link" to acts(iconsR.drawable.mozac_ic_link_24, ForkIconRole.SHARE),
        "copy_link_text" to acts(iconsR.drawable.mozac_ic_copy_24, ForkIconRole.SHARE),
        "copy_image" to acts(iconsR.drawable.mozac_ic_copy_24, ForkIconRole.SHARE),
        "copy_image_location" to acts(iconsR.drawable.mozac_ic_link_24, ForkIconRole.SHARE),
        "save_image" to acts(iconsR.drawable.mozac_ic_save_file_24, ForkIconRole.SHARE),
        "save_video" to acts(iconsR.drawable.mozac_ic_save_file_24, ForkIconRole.SHARE),
        "download_link" to acts(iconsR.drawable.mozac_ic_download_24, ForkIconRole.SHARE),
    )

    /** Rows of the legacy browser menu, which carry no id: told by upstream's own label. */
    private val BY_LABEL: List<Pair<Int, Row>> = listOf(
        R.string.search_engine_edit to opens(iconsR.drawable.mozac_ic_edit_24, ForkIconRole.MESSAGE),
        R.string.search_engine_delete to acts(iconsR.drawable.mozac_ic_delete_24, ForkIconRole.DELETE),
    )
}
