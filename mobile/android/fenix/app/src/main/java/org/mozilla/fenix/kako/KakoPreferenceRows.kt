/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.compose.base.theme.forkIconRoleForName

/**
 * 白い熊 火狐 UI: upstream's settings screens. They are Preference-based and not ours to edit,
 * so one hook — registered for every activity by [KakoPalette] — dresses each row as it comes
 * on screen:
 *
 *  - every row gets an icon coloured by what the row does, told from the preference's key, and
 *    put in the title as a compound drawable (not in the library's icon frame, which stands in
 *    a column of its own);
 *  - a row upstream already gave an icon keeps it, tinted by the same role;
 *  - a category title takes its own role;
 *  - rows of a pure choice list (radio rows) stay iconless.
 *
 * Nothing about a row's behaviour is touched: a row that opens another screen still does.
 * Upstream's rows are not turned into pills; only the fork's own UI page does that.
 */
object KakoPreferenceRows : FragmentManager.FragmentLifecycleCallbacks() {

    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
        if (f !is PreferenceFragmentCompat || !KakoTheme.isEnabled(v.context)) return
        val list = f.listView ?: return
        list.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = dress(f, list, view)
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            },
        )
    }

    private fun dress(fragment: PreferenceFragmentCompat, list: RecyclerView, row: View) {
        val position = list.getChildAdapterPosition(row)
        if (position == RecyclerView.NO_POSITION) return
        val preference = preferenceAt(fragment, list, position) ?: return
        val title = row.findViewById<TextView>(android.R.id.title) ?: return
        val context = row.context

        if (preference is PreferenceCategory) {
            title.setTextColor(KakoTheme.color(context, KakoSlot.PREF_CATEGORY))
            return
        }
        val key = preference.key.orEmpty()
        val role = if (key.isEmpty()) ForkIconRole.VIEW else forkIconRoleForName(key)
        val color = KakoTheme.iconColor(context, role)

        val own = preference.icon
        when {
            // Upstream's own icon stays where it is; a picture (a favicon, an avatar) is not
            // a mask and keeps its colours.
            own != null -> {
                KakoRowIcons.clearLabelIcon(title)
                // The row is recycled: a picture must not inherit the tint of the mask before it.
                row.findViewById<ImageView>(android.R.id.icon)?.let { view ->
                    if (own is BitmapDrawable) view.clearColorFilter() else view.setColorFilter(color)
                }
            }
            // A choice among several is read by its mark, not by an icon.
            preference.javaClass.simpleName.contains(CHOICE_ROW) -> KakoRowIcons.clearLabelIcon(title)
            else -> KakoRowIcons.setLabelIcon(
                title,
                KakoRowIcons.iconForName(key) ?: KakoRowIcons.iconForRole(role),
                color,
            )
        }
    }

    /**
     * The preference shown at [position]. The adapter is the library's own and keeps its list
     * to itself, but it answers the opposite question — where a preference is — in public.
     */
    private fun preferenceAt(fragment: PreferenceFragmentCompat, list: RecyclerView, position: Int): Preference? {
        val where = list.adapter as? PreferenceGroup.PreferencePositionCallback ?: return null
        fun find(group: PreferenceGroup): Preference? {
            for (i in 0 until group.preferenceCount) {
                val preference = group.getPreference(i)
                if (where.getPreferenceAdapterPosition(preference) == position) return preference
                if (preference is PreferenceGroup) find(preference)?.let { return it }
            }
            return null
        }
        return fragment.preferenceScreen?.let { find(it) }
    }

    private const val CHOICE_ROW = "Radio"
}
