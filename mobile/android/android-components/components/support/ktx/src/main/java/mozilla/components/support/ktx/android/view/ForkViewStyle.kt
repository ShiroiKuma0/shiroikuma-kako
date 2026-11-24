/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.support.ktx.android.view

import android.view.View
import android.widget.TextView

/**
 * Fork (白い熊 火狐): the hook through which the view-based menus and action lists of
 * android-components take the fork's look. These modules cannot depend on Fenix, so they hand
 * each row to [hook] and Fenix — which owns the colour roles, the icons and the shapes — does
 * the rest. A null [hook] is the stock look; so is a hook that leaves the view alone.
 */
object ForkViewStyle {
    /** Set once by Fenix at application start. */
    @Volatile
    var hook: Hook? = null

    /** What Fenix implements. */
    interface Hook {
        /**
         * An action row of a menu. [label] is the row's text view, already bound; [id] says what
         * the row does (`mozac.feature.contextmenu.copy_link`), or is empty when the row has no
         * identifier, in which case the label itself is all there is to go by.
         */
        fun menuRow(label: TextView, id: String)

        /**
         * The list holding such rows (a RecyclerView), with the identifiers of its rows in
         * order — for the dividers between groups.
         */
        fun menuList(list: View, ids: List<String>)

        /** The divider line of a menu. */
        fun menuDivider(divider: View)
    }
}
