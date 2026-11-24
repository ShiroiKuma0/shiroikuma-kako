/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.Locale
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.support.locale.LocaleManager
import mozilla.components.support.locale.LocaleUseCases
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.nimbus.FxNimbus
import org.mozilla.fenix.settings.advanced.getSelectedLocale
import org.mozilla.fenix.settings.advanced.getSupportedLocales
import org.mozilla.fenix.settings.advanced.isDefaultLocaleSelected
import org.mozilla.fenix.utils.LocaleUtils

/**
 * The app's language, as the first section of the 白い熊 火狐 UI page sets it.
 *
 * There is one stored value and this is not a second one: the choice is read and written
 * through the same [LocaleManager] that upstream's own picker (Settings → Language) uses, so
 * the two always agree. That manager keeps the tag in the app's own preferences, wraps the
 * application context as well as every activity, and re-asserts the choice at each start — so
 * it survives a sideload update, and a notification or a reply to a sister app is in the chosen
 * language too.
 *
 * The list is the set of languages this build ships, which is the build's own declaration
 * (`BuildConfig.SUPPORTED_LOCALE_ARRAY`, the same list the manifest's locale config is generated
 * from); picker and declaration cannot drift apart.
 */
object KakoLanguage {

    /** The current choice as the row shows it: the language's own name, or "System default". */
    fun currentLabel(context: Context): String =
        if (LocaleManager.isDefaultLocaleSelected(context)) {
            context.getString(R.string.kako_language_system)
        } else {
            LocaleUtils.getDisplayName(LocaleManager.getSelectedLocale(context))
        }

    /**
     * The single-choice list. Rows are plain; only the current language sits in a pill. A row
     * is, left to right: the selection mark, the language's name in its own language with its
     * region beneath in the note colour, and the tag, right-aligned in a column of its own.
     * "System default" comes first, with neither region nor tag. Choosing a row applies it at
     * once and closes the list.
     */
    fun showPicker(activity: Activity) {
        val isDefault = LocaleManager.isDefaultLocaleSelected(activity)
        val selected = LocaleManager.getSelectedLocale(activity)
        // The first entry of the manager's list is the system's own locale: "System default".
        val shipped = LocaleManager.getSupportedLocales().drop(1)
            .sortedBy { LocaleUtils.getDisplayName(it).lowercase(it) }

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.kakoDp(LIST_PAD_DP), activity.kakoDp(ROW_PAD_DP), activity.kakoDp(LIST_PAD_DP), 0)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.kako_section_language)
            .setView(ScrollView(activity).apply { addView(list) })
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .applyKakoBorder()

        val entries = listOf<Locale?>(null) + shipped
        entries.forEachIndexed { index, locale ->
            val current = if (locale == null) isDefault else !isDefault && locale == selected
            val next = entries.getOrNull(index + 1)
            val nextCurrent = index + 1 < entries.size &&
                (if (next == null) isDefault else !isDefault && next == selected)
            list.addView(
                row(activity, locale, current) {
                    dialog.dismiss()
                    if (!current) choose(activity, locale)
                },
            )
            // A hairline between rows, inset to the text; none beside the current row's pill.
            if (index < entries.size - 1 && !current && !nextCurrent) {
                list.addView(
                    View(activity).apply {
                        setBackgroundColor(KakoTheme.color(activity, KakoSlot.PAGE_DIVIDER))
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = activity.kakoDp(ROW_INNER_PAD_DP + MARK_DP + MARK_GAP_DP)
                        marginEnd = activity.kakoDp(ROW_INNER_PAD_DP)
                    },
                )
            }
        }
        dialog.show()
    }

    private fun row(context: Context, locale: Locale?, current: Boolean, onClick: () -> Unit): View {
        val text = KakoTheme.color(context, KakoSlot.DIALOG_TEXT)
        val note = KakoTheme.color(context, KakoSlot.NOTE)
        val name = locale?.let { LocaleUtils.getDisplayName(Locale.forLanguageTag(it.language)) }
            ?.takeIf { it.isNotBlank() && !it.equals(locale.language, ignoreCase = true) }
            ?: locale?.let { LocaleUtils.getDisplayName(it) }
            ?: context.getString(R.string.kako_language_system)
        val region = locale?.getDisplayCountry(locale).orEmpty()

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The tag column stays at the right on every row, whatever the language's direction.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            val pad = context.kakoDp(ROW_INNER_PAD_DP)
            setPadding(pad, context.kakoDp(ROW_PAD_DP), pad, context.kakoDp(ROW_PAD_DP))
            if (current) background = context.kakoPillBackground(main = true)
            addView(
                View(context).apply { background = mark(context, current) },
                LinearLayout.LayoutParams(context.kakoDp(MARK_DP), context.kakoDp(MARK_DP)).apply {
                    marginEnd = context.kakoDp(MARK_GAP_DP)
                },
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(context).apply {
                            this.text = name
                            setTextColor(text)
                            textSize = NAME_TEXT_SIZE_SP
                            if (current) setTypeface(typeface, Typeface.BOLD)
                        },
                    )
                    if (region.isNotEmpty()) {
                        addView(
                            TextView(context).apply {
                                this.text = region
                                setTextColor(note)
                                textSize = NOTE_TEXT_SIZE_SP
                            },
                        )
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (locale != null) {
                addView(
                    TextView(context).apply {
                        this.text = locale.toLanguageTag()
                        typeface = Typeface.MONOSPACE
                        setTextColor(note)
                        textSize = NOTE_TEXT_SIZE_SP
                        gravity = Gravity.END
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = context.kakoDp(MARK_GAP_DP) },
                )
            }
            setOnClickListener { onClick() }
        }
    }

    /** The selection mark: a ring, in the chosen colour with a dot on the current row. */
    private fun mark(context: Context, current: Boolean): Drawable {
        val color = KakoTheme.color(context, if (current) KakoSlot.RADIO_ON else KakoSlot.RADIO_OFF)
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(context.kakoDp(MARK_RING_DP), color)
        }
        if (!current) return ring
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        val inset = context.kakoDp(MARK_DOT_INSET_DP)
        return LayerDrawable(arrayOf(ring, dot)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    /**
     * Applies the choice at once, for this app only — the same steps upstream's own picker
     * takes: store it, refresh what depends on the language, and rebuild the screen.
     */
    private fun choose(activity: Activity, locale: Locale?) {
        val store = activity.components.core.store
        val useCases = LocaleUseCases(store)
        val effective = locale ?: LocaleManager.getSystemDefault()
        if (locale == null) {
            LocaleManager.resetToSystemDefault(activity, useCases)
        } else {
            LocaleManager.setNewLocale(activity, useCases, locale)
        }
        store.dispatch(SearchAction.RefreshSearchEnginesAction)
        updateBaseConfiguration(activity, effective)
        // Cached experiment values hold strings of the old language.
        FxNimbus.features.nimbusValidation.withCachedValue(null)
        activity.recreate()
    }

    @Suppress("DEPRECATION")
    private fun updateBaseConfiguration(context: Context, locale: Locale) {
        val resources = context.applicationContext.resources
        val config = resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private const val LIST_PAD_DP = 12f
    private const val ROW_PAD_DP = 6f
    private const val ROW_INNER_PAD_DP = 12f
    private const val MARK_DP = 18f
    private const val MARK_GAP_DP = 12f
    private const val MARK_RING_DP = 2f
    private const val MARK_DOT_INSET_DP = 5f
    private const val NAME_TEXT_SIZE_SP = 16f
    private const val NOTE_TEXT_SIZE_SP = 13f
}
