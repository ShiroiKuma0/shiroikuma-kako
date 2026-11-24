/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.compose.base.theme.forkIconRoleForName
import org.mozilla.fenix.R
import androidx.appcompat.R as appcompatR

/**
 * 白い熊 火狐 UI: the shapes of the black-yellow look for the classic (View) screens, drawn in
 * code from the colour roles — the pill (never filled: a 3 dp frame and bold for a block's main
 * action, 1.25 dp otherwise, a deleting one lending its red to frame and label), the outlined
 * switch with its dot, and the tints of selection marks, sliders, progress bars and text fields.
 *
 * [KakoViewInflater] sends every inflated widget through here, so a screen nobody remembered is
 * covered as well; the fork's own dialogs call in directly for views they build in code.
 */
@Suppress("TooManyFunctions")
object KakoPills {

    private const val ROUND_DP = 100f
    private const val DIALOG_INSET_H_DP = 4f
    private const val DIALOG_INSET_V_DP = 5f
    private const val DIALOG_PADDING_DP = 20f
    private const val CHECK_ROW_RADIUS_DP = 24f
    private const val SWITCH_WIDTH_DP = 52f
    private const val SWITCH_HEIGHT_DP = 28f
    private const val SWITCH_OUTLINE_DP = 2f
    private const val SWITCH_DOT = 0.46f
    private const val FAB_RING_DP = 2f
    private const val OPAQUE_ENOUGH = 0x80
    private const val SNACKBAR_ACTION = "snackbar_action"

    private val ENABLED = intArrayOf(android.R.attr.state_enabled)
    private val BY_ENABLED = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
    private val BY_CHECKED = arrayOf(
        intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_checked),
        intArrayOf(),
    )
    private val BY_FOCUSED = arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_focused),
        intArrayOf(),
    )

    internal fun px(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density + ROUNDING).toInt().coerceAtLeast(1)

    private fun c(context: Context, slot: KakoSlot): Int = KakoTheme.color(context, slot)

    private fun dim(color: Int): Int = KakoTheme.alpha(color, KakoTheme.DISABLED_ALPHA)

    /** [color] when enabled, 38 % of it when not. */
    fun byEnabled(color: Int): ColorStateList = ColorStateList(BY_ENABLED, intArrayOf(dim(color), color))

    /** [on] when checked and [off] when not, each dimmed when disabled. */
    fun byChecked(on: Int, off: Int): ColorStateList =
        ColorStateList(BY_CHECKED, intArrayOf(dim(on), dim(off), on, off))

    private fun idName(v: View): String =
        if (v.id == View.NO_ID) {
            ""
        } else {
            try {
                v.resources.getResourceEntryName(v.id)
            } catch (_: android.content.res.Resources.NotFoundException) {
                ""
            }
        }

    // ---- pills ---------------------------------------------------------------------------

    fun frameColor(context: Context, main: Boolean, delete: Boolean): Int = when {
        delete -> c(context, KakoSlot.BUTTON_DELETE)
        main -> c(context, KakoSlot.BUTTON_MAIN_BORDER)
        else -> c(context, KakoSlot.BUTTON_BORDER)
    }

    fun labelColor(context: Context, main: Boolean, delete: Boolean): Int = when {
        delete -> c(context, KakoSlot.BUTTON_DELETE)
        main -> c(context, KakoSlot.BUTTON_MAIN_TEXT)
        else -> c(context, KakoSlot.BUTTON_TEXT)
    }

    /**
     * The pill: fully round, a frame of [frame] — 3 dp when [main], else 1.25 dp — over [ground]
     * (null lets whatever lies behind show through), with a ripple clipped to the shape.
     */
    fun pill(
        context: Context,
        main: Boolean,
        frame: Int,
        ground: Int?,
        ripple: Int = c(context, KakoSlot.BUTTON_RIPPLE),
        radiusDp: Float = ROUND_DP,
    ): Drawable {
        val radius = radiusDp * context.resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            cornerRadius = radius
            if (ground != null) setColor(ground)
            setStroke(px(context, if (main) KakoTheme.PILL_THICK_DP else KakoTheme.PILL_THIN_DP), byEnabled(frame))
        }
        val mask = GradientDrawable().apply {
            cornerRadius = radius
            // Any opaque colour: a mask only lends its shape.
            setColor(KakoTheme.alpha(frame, 1f))
        }
        return RippleDrawable(ColorStateList.valueOf(ripple), shape, mask)
    }

    /** The pill of an action button built in code. */
    fun pill(context: Context, main: Boolean, delete: Boolean = false): Drawable =
        pill(context, main, frameColor(context, main, delete), c(context, KakoSlot.BUTTON_BACKGROUND))

    /**
     * The background of a painted button: the pill, inset, and deaf to tints. Upstream code
     * that later gives the button a background tint — a platform tint list or AppCompat's
     * colour filter — would otherwise flood frame and ground alike and fill the pill.
     */
    private class Untinted(pill: Drawable, insetH: Int, insetTop: Int, insetBottom: Int) :
        InsetDrawable(pill, insetH, insetTop, insetH, insetBottom) {
        override fun setTintList(tint: ColorStateList?) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    }

    /** What a button was before the fork painted it; told once, when it is first seen. */
    private class Stock(
        val main: Boolean,
        val dialog: Boolean,
        val errorText: Boolean,
        val insetTop: Int,
        val insetBottom: Int,
    )

    /** How a button is painted now, so that a second pass with nothing new does nothing. */
    private data class Painted(val delete: Boolean, val glyph: Boolean, val revision: Int)

    /**
     * Makes [b] a pill. A button upstream drew filled is its block's main action; outlined and
     * text buttons are regular ones; in a dialog's button bar the positive button is the main
     * one. A deleting action — said by its text colour, its id, its label, or a mark set with
     * [markDelete] — takes the delete colour on frame and label. A button with an icon and no
     * label is a glyph and stays one.
     */
    fun button(b: Button) {
        if (!KakoTheme.isEnabled(b.context)) return
        if (b.getTag(R.id.kako_pill_stock) == null) {
            val stock = stockOf(b)
            b.setTag(R.id.kako_pill_stock, stock)
            // A label set after inflation (every dialog button) may be what says "delete".
            b.doAfterTextChanged { paint(b) }
            if (stock.dialog) b.addOnAttachStateChangeListener(dialogButtonAttached)
        }
        paint(b)
    }

    /** Marks [b] as deleting and repaints it. */
    fun markDelete(b: Button) {
        b.setTag(R.id.kako_pill_delete, true)
        button(b)
    }

    /**
     * Paints the three buttons of a shown alert dialog — for a dialog whose buttons did not come
     * through the theme's view inflater (a platform dialog on a plain context).
     */
    fun dialogButtons(dialog: Dialog) {
        val decor = dialog.window?.decorView ?: return
        listOf(android.R.id.button1, android.R.id.button2, android.R.id.button3).forEach { id ->
            decor.findViewById<Button>(id)?.let { button(it) }
        }
    }

    private fun stockOf(b: Button): Stock {
        val id = b.id
        val dialog = id == android.R.id.button1 || id == android.R.id.button2 || id == android.R.id.button3
        var filled = false
        var top = 0
        var bottom = 0
        if (b is MaterialButton) {
            val fill = b.backgroundTintList?.getColorForState(ENABLED, 0) ?: 0
            filled = Color.alpha(fill) >= OPAQUE_ENOUGH && b.strokeWidth == 0
            top = b.insetTop
            bottom = b.insetBottom
        }
        val error = MaterialColors.getColor(b.context, appcompatR.attr.colorError, 0)
        val text = b.textColors?.getColorForState(ENABLED, 0) ?: 0
        return Stock(
            main = if (dialog) id == android.R.id.button1 else filled,
            dialog = dialog,
            errorText = error != 0 && text == error,
            insetTop = top,
            insetBottom = bottom,
        )
    }

    private val dialogButtonAttached = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            // The buttons of a dialog stand side by side: a small margin keeps the pills apart.
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                val gap = px(v.context, DIALOG_INSET_H_DP)
                if (params.marginStart < gap) {
                    params.marginStart = gap
                    v.layoutParams = params
                }
            }
            if (v.id != android.R.id.button1) return
            val decor = v.rootView ?: return
            // A dialog marked as deleting (kakoDeleting) shows it on its confirming button.
            if (decor.getTag(R.id.kako_pill_delete) == true && v is Button) markDelete(v)
            // Every alert dialog passes here, whoever built it: frame and text from the roles.
            kakoStampDialog(decor)
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    }

    private var deleteLabels: Set<String>? = null

    /** Upstream's own labels of deleting actions, in the language the app is shown in. */
    private fun isDeleteLabel(context: Context, label: String): Boolean {
        if (label.isBlank()) return false
        val labels = deleteLabels ?: DELETE_LABELS.map { context.getString(it) }.toSet().also { deleteLabels = it }
        return label in labels
    }

    @Suppress("CyclomaticComplexMethod")
    private fun paint(b: Button) {
        val context = b.context
        val stock = b.getTag(R.id.kako_pill_stock) as? Stock ?: return
        val label = b.text?.toString().orEmpty()
        val name = idName(b)
        val glyph = label.isBlank() && (b as? MaterialButton)?.icon != null
        val delete = !glyph && (
            stock.errorText ||
                b.getTag(R.id.kako_pill_delete) == true ||
                (name.isNotEmpty() && forkIconRoleForName(name) == ForkIconRole.DELETE) ||
                isDeleteLabel(context, label)
            )
        val want = Painted(delete, glyph, KakoTheme.revision.intValue)
        if (b.getTag(R.id.kako_pill_painted) == want) return
        b.setTag(R.id.kako_pill_painted, want)

        if (glyph) {
            b.iconTint = byEnabled(c(context, KakoSlot.ICON_BUTTON))
            return
        }

        val main = stock.main
        // The action of a snackbar sits on the toast's ground and takes the toast's roles.
        val toast = name == SNACKBAR_ACTION
        val frame = if (toast) c(context, KakoSlot.TOAST_ACTION) else frameColor(context, main, delete)
        val ink = if (toast) c(context, KakoSlot.TOAST_ACTION) else labelColor(context, main, delete)
        val ground = c(context, if (toast) KakoSlot.TOAST_BACKGROUND else KakoSlot.BUTTON_BACKGROUND)

        var left = b.paddingLeft
        val top = b.paddingTop
        var right = b.paddingRight
        val bottom = b.paddingBottom
        val insetH = if (stock.dialog) px(context, DIALOG_INSET_H_DP) else 0
        val insetTop = if (stock.dialog) px(context, DIALOG_INSET_V_DP) else stock.insetTop
        val insetBottom = if (stock.dialog) px(context, DIALOG_INSET_V_DP) else stock.insetBottom
        if (stock.dialog) {
            // The buttons of a dialog touch each other: the inset keeps the pills apart, and
            // the label needs room inside the frame.
            left = maxOf(left, px(context, DIALOG_PADDING_DP))
            right = maxOf(right, px(context, DIALOG_PADDING_DP))
        }

        // Replacing the background takes Material's own shape, tint, stroke and elevation with
        // it; what is left is the pill. An inset drawable brings padding of its own, hence the
        // padding put back below.
        if (b is MaterialButton) {
            // A Material button keeps Material's own background and is made a pill through its
            // own properties. Replacing the background would cost more than the look: a button
            // group (the time picker's AM/PM pair) reads the shape off it and throws when it
            // is gone.
            b.backgroundTintList = ColorStateList.valueOf(ground)
            b.strokeColor = byEnabled(frame)
            b.strokeWidth = px(context, if (main) KakoTheme.PILL_THICK_DP else KakoTheme.PILL_THIN_DP)
            b.cornerRadius = px(context, ROUND_DP)
            b.rippleColor = ColorStateList.valueOf(c(context, KakoSlot.BUTTON_RIPPLE))
            if (stock.dialog) {
                b.insetTop = insetTop
                b.insetBottom = insetBottom
            }
        } else {
            b.background = Untinted(pill(context, main, frame, ground), insetH, insetTop, insetBottom)
            b.backgroundTintList = null
            if (b is AppCompatButton) b.supportBackgroundTintList = null
        }
        b.setPadding(left, top, right, bottom)
        b.stateListAnimator = null
        b.elevation = 0f
        b.setTextColor(byEnabled(ink))
        b.typeface = Typeface.create(b.typeface ?: Typeface.DEFAULT, if (main) Typeface.BOLD else Typeface.NORMAL)
        if (b is MaterialButton) b.iconTint = byEnabled(ink)
    }

    // ---- switches ------------------------------------------------------------------------

    /**
     * The outlined switch: a 2 dp fully round outline, 52 × 28 dp, with whatever lies behind
     * showing through and a solid dot inside — right when on, left when off. Never filled; on
     * and off differ in colour only. The view keeps its class.
     */
    fun switch(v: SwitchCompat) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        val height = px(context, SWITCH_HEIGHT_DP)
        val width = px(context, SWITCH_WIDTH_DP)
        // Both drawables are masks: their colour comes from the tint lists below.
        val mask = KakoTheme.alpha(c(context, KakoSlot.SWITCH_ON), 1f)
        val track = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = height / 2f
            setStroke(px(context, SWITCH_OUTLINE_DP), mask)
            setSize(width, height)
        }
        val dotSize = (height * SWITCH_DOT).toInt()
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(mask)
            setSize(dotSize, dotSize)
        }
        if (v is MaterialSwitch) {
            v.thumbIconDrawable = null
            v.trackDecorationDrawable = null
        }
        // The dot sits in a box half the track's width and all of its height, centred with
        // LAYER insets. Never an InsetDrawable: SwitchCompat takes an InsetDrawable's insets
        // for the thumb's optical insets and shrinks the TRACK by them on every side.
        val boxWidth = width / 2
        val insetH = ((boxWidth - dotSize) / 2).coerceAtLeast(0)
        val insetV = (height - dotSize) / 2
        v.thumbDrawable = LayerDrawable(arrayOf<Drawable>(dot)).apply {
            setLayerInset(0, insetH, insetV, insetH, insetV)
        }
        v.trackDrawable = track
        v.thumbTintList = byChecked(c(context, KakoSlot.SWITCH_ON_DOT), c(context, KakoSlot.SWITCH_OFF_DOT))
        v.trackTintList = byChecked(c(context, KakoSlot.SWITCH_ON), c(context, KakoSlot.SWITCH_OFF))
        v.switchMinWidth = width
        v.showText = false
        v.splitTrack = false
    }

    // ---- selection marks -----------------------------------------------------------------

    /** A check box: a selection mark, tinted from its roles — never redrawn as a switch. */
    fun check(v: CompoundButton) {
        val context = v.context
        if (v is SwitchCompat || !KakoTheme.isEnabled(context)) return
        v.buttonTintList = byChecked(c(context, KakoSlot.CHECK_ON), c(context, KakoSlot.CHECK_OFF))
        if (v is MaterialCheckBox) v.buttonIconTintList = ColorStateList.valueOf(c(context, KakoSlot.CHECK_MARK))
        if (v.id != View.NO_ID && idName(v) in FRAMED_CHECK_ROWS) checkRow(v)
    }

    /** A radio button, likewise. */
    fun radio(v: CompoundButton) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        v.buttonTintList = byChecked(c(context, KakoSlot.RADIO_ON), c(context, KakoSlot.RADIO_OFF))
    }

    /** The mark of a checkable list row (a single- or multi-choice dialog). */
    fun checkedText(v: CheckedTextView) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        v.checkMarkTintList = byChecked(c(context, KakoSlot.CHECK_ON), c(context, KakoSlot.CHECK_OFF))
    }

    /**
     * The frame around a whole check-box row — box and label together — which the add-on
     * permission dialogs use so that the eye catches "there is something to decide here".
     * A rounded rectangle rather than a full pill: these labels wrap, and a radius larger than
     * half the height would turn a two-line row into an oval.
     */
    fun checkRow(v: View) {
        val context = v.context
        val left = v.paddingLeft
        val top = v.paddingTop
        val right = v.paddingRight
        val bottom = v.paddingBottom
        v.background = pill(
            context,
            main = false,
            frame = c(context, KakoSlot.BUTTON_BORDER),
            ground = null,
            radiusDp = CHECK_ROW_RADIUS_DP,
        )
        v.setPadding(left, top, right, bottom)
    }

    // ---- sliders, progress, fields -------------------------------------------------------

    fun seekBar(v: SeekBar) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        val active = ColorStateList.valueOf(c(context, KakoSlot.SLIDER_ACTIVE))
        v.progressTintList = active
        v.thumbTintList = active
        v.progressBackgroundTintList = ColorStateList.valueOf(c(context, KakoSlot.SLIDER_INACTIVE))
    }

    fun slider(v: Slider) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        val active = byEnabled(c(context, KakoSlot.SLIDER_ACTIVE))
        val inactive = byEnabled(c(context, KakoSlot.SLIDER_INACTIVE))
        v.trackActiveTintList = active
        v.trackInactiveTintList = inactive
        v.thumbTintList = active
        v.tickActiveTintList = inactive
        v.tickInactiveTintList = active
        v.haloTintList = ColorStateList.valueOf(c(context, KakoSlot.BUTTON_RIPPLE))
    }

    fun progress(v: ProgressBar) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        val tint = ColorStateList.valueOf(c(context, KakoSlot.PROGRESS))
        v.progressTintList = tint
        v.indeterminateTintList = tint
        if (v is BaseProgressIndicator<*>) v.setIndicatorColor(c(context, KakoSlot.PROGRESS))
    }

    /**
     * A text field: text, hint, selection and cursor from the field roles. [ownLine] says the
     * field draws its own underline (it is not inside a text input layout and its layout names
     * no background), which is then tinted too.
     */
    fun editText(v: EditText, ownLine: Boolean) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        v.setTextColor(byEnabled(c(context, KakoSlot.FIELD_TEXT)))
        v.setHintTextColor(c(context, KakoSlot.FIELD_HINT))
        v.highlightColor = c(context, KakoSlot.TEXT_SELECTION)
        if (ownLine) {
            v.backgroundTintList = ColorStateList(
                BY_FOCUSED,
                intArrayOf(
                    dim(c(context, KakoSlot.FIELD_FRAME)),
                    c(context, KakoSlot.FIELD_FRAME_FOCUSED),
                    c(context, KakoSlot.FIELD_FRAME),
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.textCursorDrawable = v.textCursorDrawable?.mutate()?.also { it.setTint(c(context, KakoSlot.TEXT_CURSOR)) }
        }
    }

    fun textInputLayout(v: TextInputLayout) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        val frame = c(context, KakoSlot.FIELD_FRAME)
        val focused = c(context, KakoSlot.FIELD_FRAME_FOCUSED)
        val hint = ColorStateList.valueOf(c(context, KakoSlot.FIELD_HINT))
        val error = ColorStateList.valueOf(c(context, KakoSlot.TEXT_ERROR))
        v.setBoxStrokeColorStateList(ColorStateList(BY_FOCUSED, intArrayOf(dim(frame), focused, frame)))
        v.setDefaultHintTextColor(hint)
        v.setHintTextColor(ColorStateList.valueOf(focused))
        v.setPlaceholderTextColor(hint)
        v.setHelperTextColor(hint)
        v.setCounterTextColor(hint)
        v.setStartIconTintList(hint)
        v.setEndIconTintList(hint)
        v.setErrorTextColor(error)
        v.setErrorIconTintList(error)
        v.setBoxStrokeErrorColor(error)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.setCursorColor(ColorStateList.valueOf(c(context, KakoSlot.TEXT_CURSOR)))
        }
        if (v.boxBackgroundMode == TextInputLayout.BOX_BACKGROUND_FILLED) {
            v.setBoxBackgroundColor(c(context, KakoSlot.FIELD_GROUND))
        }
    }

    // ---- the floating action button ------------------------------------------------------

    /** An outlined ring: the ground showing, a round frame, the icon in its own role. */
    fun fab(v: FloatingActionButton) {
        val context = v.context
        if (!KakoTheme.isEnabled(context)) return
        v.backgroundTintList = ColorStateList.valueOf(c(context, KakoSlot.FAB_GROUND))
        v.imageTintList = ColorStateList.valueOf(c(context, KakoSlot.FAB_ICON))
        v.setRippleColor(c(context, KakoSlot.BUTTON_RIPPLE))
        v.compatElevation = 0f
        v.shapeAppearanceModel = v.shapeAppearanceModel.withCornerSize(ShapeAppearanceModel.PILL)
        v.foreground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(px(context, FAB_RING_DP), c(context, KakoSlot.FAB_RING))
        }
    }

    private const val ROUNDING = 0.5f

    /** The add-on permission dialogs' framed rows, by the entry name of their id. */
    private val FRAMED_CHECK_ROWS = setOf(
        "permission_opt_in_item",
        "allow_in_private_browsing",
        "technical_and_interaction_data",
    )

    private val DELETE_LABELS = listOf(
        R.string.bookmark_menu_delete_button,
        R.string.home_bookmarks_menu_item_remove,
        R.string.preferences_delete_browsing_data_button,
        R.string.history_delete_all,
        R.string.collection_delete,
        R.string.search_engine_delete,
    )
}

/**
 * Marks a dialog that deletes, discards or resets: its confirming button keeps the main pill,
 * with frame and label in the delete colour. Works before and after the dialog is shown, for
 * platform, AppCompat and Material alert dialogs alike; returns the receiver for chaining.
 */
fun <T : Dialog> T.kakoDeleting(): T = apply {
    val decor = window?.decorView ?: return@apply
    decor.setTag(R.id.kako_pill_delete, true)
    decor.findViewById<Button>(android.R.id.button1)?.let { KakoPills.markDelete(it) }
}
