/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import org.mozilla.fenix.R
import java.util.Locale

/**
 * Plain programmatic dialogs for the 白い熊 火狐 UI page — kept free of Fenix's
 * preference machinery so they can be styled entirely from the kako palette.
 */

private fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

private fun kakoDialog(context: Context, title: CharSequence, content: View): AlertDialog {
    val text = KakoTheme.color(context, KakoSlot.TEXT)
    val titleView = TextView(context).apply {
        this.text = title
        setTextColor(text)
        setTypeface(typeface, Typeface.BOLD)
        textSize = TITLE_TEXT_SIZE_SP
        setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(8))
    }
    return AlertDialog.Builder(context)
        .setCustomTitle(titleView)
        .setView(content)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
        .apply {
            window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    cornerRadius = context.dp(8).toFloat()
                    setColor(KakoTheme.color(context, KakoSlot.MENU_BACKGROUND))
                    setStroke(context.dp(1), KakoTheme.color(context, KakoSlot.BORDER))
                },
            )
            setOnShowListener {
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(KakoTheme.color(context, KakoSlot.ACCENT))
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(KakoTheme.color(context, KakoSlot.ACCENT))
                getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(KakoTheme.color(context, KakoSlot.ACCENT))
            }
        }
}

/**
 * Color picker: a swatch grid, a hex field, and a "解除" (inherit again) action for
 * slots carrying an explicit override.
 */
class KakoColorPickerDialog(
    private val context: Context,
    private val slot: KakoSlot,
    private val onPicked: (Int?) -> Unit,
) {
    fun show() {
        val textColor = KakoTheme.color(context, KakoSlot.TEXT)
        val current = KakoTheme.color(context, slot)

        val hexField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(String.format(Locale.ROOT, "#%08X", current))
            setTextColor(textColor)
            setHintTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                KakoTheme.color(context, KakoSlot.BORDER),
            )
        }

        val grid = GridLayout(context).apply {
            columnCount = SWATCH_COLUMNS
            setPadding(context.dp(16), context.dp(8), context.dp(16), 0)
        }

        lateinit var dialog: AlertDialog
        PRESETS.forEach { preset ->
            grid.addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(preset)
                        setStroke(context.dp(2), KakoTheme.color(context, KakoSlot.BORDER))
                    }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = context.dp(SWATCH_SIZE_DP)
                        height = context.dp(SWATCH_SIZE_DP)
                        setMargins(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        onPicked(preset)
                    }
                },
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid)
            addView(
                hexField,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(context.dp(24), context.dp(8), context.dp(24), context.dp(8)) },
            )
        }

        dialog = kakoDialog(context, context.getString(slot.labelRes), ScrollView(context).apply { addView(content) })
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { _, _ ->
            runCatching { hexField.text.toString().trim().toColorInt() }
                .getOrNull()
                ?.let(onPicked)
        }
        if (KakoTheme.isOverridden(context, slot)) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.kako_color_inherit)) { _, _ ->
                onPicked(null)
            }
        }
        dialog.show()
    }

    private companion object {
        const val SWATCH_COLUMNS = 6
        const val SWATCH_SIZE_DP = 40
        val PRESETS = listOf(
            0xFF000000, 0xFF101010, 0xFF202020, 0xFF404040, 0xFF808080, 0xFFFFFFFF,
            0xFFFFEB3B, 0xFFFFD600, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFFF44336,
            0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4,
            0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFF795548,
        ).map { it.toInt() }
    }
}

/**
 * Font picker, sister-repo style: every option is rendered with its own typeface,
 * and the last row imports a new ttf/otf from storage.
 */
class KakoFontPickerDialog(
    private val context: Context,
    private val onAddFont: () -> Unit,
    private val onPick: (fileName: String) -> Unit,
) {
    fun show() {
        val textColor = KakoTheme.color(context, KakoSlot.TEXT)
        val accentColor = KakoTheme.color(context, KakoSlot.ACCENT)
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, context.dp(8))
        }

        lateinit var dialog: AlertDialog
        fun addRow(label: String, typeface: Typeface, color: Int, onClick: () -> Unit) {
            holder.addView(
                TextView(context).apply {
                    text = label
                    this.typeface = typeface
                    setTextColor(color)
                    textSize = OPTION_TEXT_SIZE_SP
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = context.dp(48)
                    setPadding(context.dp(24), context.dp(4), context.dp(24), context.dp(4))
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        dialog.dismiss()
                        onClick()
                    }
                },
            )
        }

        KakoFonts.availableFontOptions(context).forEach { option ->
            addRow(option.displayName, KakoFonts.fontTypeface(context, option.fileName), textColor) {
                onPick(option.fileName)
            }
        }
        addRow(context.getString(R.string.kako_font_add), Typeface.DEFAULT, accentColor) {
            onAddFont()
        }

        dialog = kakoDialog(
            context,
            context.getString(R.string.kako_font_row),
            ScrollView(context).apply { addView(holder) },
        )
        dialog.show()
    }

    private companion object {
        const val OPTION_TEXT_SIZE_SP = 18f
    }
}

/**
 * Weight picker: each choice rendered in its own weight; 0 = inherit per-style weights.
 */
class KakoFontWeightPickerDialog(
    private val context: Context,
    private val onPick: (Int) -> Unit,
) {
    fun show() {
        val textColor = KakoTheme.color(context, KakoSlot.TEXT)
        val base = KakoFonts.fontTypeface(context, KakoFonts.fontFamily(context))
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(8), 0, context.dp(8))
        }

        lateinit var dialog: AlertDialog
        WEIGHTS.forEach { (weight, labelRes) ->
            holder.addView(
                TextView(context).apply {
                    text = context.getString(labelRes)
                    typeface = if (weight > 0) Typeface.create(base, weight, false) else base
                    setTextColor(textColor)
                    textSize = OPTION_TEXT_SIZE_SP
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = context.dp(48)
                    setPadding(context.dp(24), context.dp(4), context.dp(24), context.dp(4))
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        dialog.dismiss()
                        onPick(weight)
                    }
                },
            )
        }

        dialog = kakoDialog(
            context,
            context.getString(R.string.kako_font_weight_row),
            ScrollView(context).apply { addView(holder) },
        )
        dialog.show()
    }

    private companion object {
        const val OPTION_TEXT_SIZE_SP = 18f
        val WEIGHTS = listOf(
            0 to R.string.kako_font_weight_inherit,
            100 to R.string.kako_font_weight_100,
            300 to R.string.kako_font_weight_300,
            400 to R.string.kako_font_weight_400,
            500 to R.string.kako_font_weight_500,
            700 to R.string.kako_font_weight_700,
            900 to R.string.kako_font_weight_900,
        )
    }
}

private const val TITLE_TEXT_SIZE_SP = 18f
