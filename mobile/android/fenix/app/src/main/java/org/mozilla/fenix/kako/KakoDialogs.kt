/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog as AppCompatAlertDialog
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import mozilla.components.compose.base.theme.ForkIconRole
import org.mozilla.fenix.R
import java.util.Locale
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR
import mozilla.components.ui.icons.R as iconsR

/**
 * Plain programmatic dialogs for the 白い熊 火狐 UI page — kept free of Fenix's
 * preference machinery so they can be styled entirely from the kako palette.
 */

private fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

/**
 * The fork's dialog panel: the dialog ground, rounded, ringed in the dialog frame — both from
 * their roles. The window background of the dialogs built below.
 */
internal fun Context.kakoDialogBackground(): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dp(8).toFloat()
    setColor(KakoTheme.color(this@kakoDialogBackground, KakoSlot.DIALOG_BACKGROUND))
    setStroke(dp(1), KakoTheme.color(this@kakoDialogBackground, KakoSlot.DIALOG_BORDER))
}

/** The press highlight of a row built in code: a ripple in its role, nothing else drawn. */
internal fun Context.kakoRowRipple(): Drawable {
    val ripple = KakoTheme.color(this, KakoSlot.BUTTON_RIPPLE)
    return RippleDrawable(ColorStateList.valueOf(ripple), null, ColorDrawable(KakoTheme.alpha(ripple, 1f)))
}

/**
 * Gives a (Material or AppCompat) alert dialog the fork's frame and text colours; returns the
 * receiver for chaining. Kept for the call sites that have it; every alert dialog whose button
 * bar is inflated under the theme gets the same from [KakoPills] without asking, so a new call
 * site needs none.
 */
internal fun <T : Dialog> T.applyKakoBorder(): T = apply {
    val decor = window?.decorView ?: return@apply
    kakoStampDialog(decor)
    decor.post { kakoStampDialog(decor) }
}

/**
 * Frames the panel of the alert dialog whose decor view is [decor] and colours its title and
 * message, all from the dialog roles. Does nothing with the theme off, or for a window that
 * holds no AppCompat alert panel.
 *
 * The visible rounded panel is not the window: the decor is larger and floats the panel in the
 * middle, so a decor-level background or border lands off the panel. `parentPanel` is the
 * dialog's content root and its bounds coincide with the visible panel, so the ring is drawn as
 * the decor's foreground — painted on top, provably visible — inset to where the panel was
 * measured. The panel is watched, so the ring follows when the dialog changes size (a keyboard,
 * a list that fills in).
 */
internal fun kakoStampDialog(decor: View) {
    val context = decor.context
    if (decor !is FrameLayout || !KakoTheme.isEnabled(context)) return
    val panel = decor.findViewById<View>(appcompatR.id.parentPanel) ?: return
    if (panel.getTag(R.id.kako_dialog_watched) == null) {
        panel.setTag(R.id.kako_dialog_watched, true)
        // Only when the panel really moved: stamping asks for a layout itself.
        panel.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (l != oldL || t != oldT || r != oldR || b != oldB) kakoStampDialog(decor)
        }
    }
    panel.findViewById<TextView>(appcompatR.id.alertTitle)
        ?.setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TITLE))
    panel.findViewById<TextView>(android.R.id.message)
        ?.setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TEXT))
    if (panel.width == 0 || panel.height == 0) return
    val pos = IntArray(2).also { panel.getLocationInWindow(it) }
    val origin = IntArray(2).also { decor.getLocationInWindow(it) }
    val left = pos[0] - origin[0]
    val top = pos[1] - origin[1]
    val ring = GradientDrawable().apply {
        cornerRadius = context.dp(MATERIAL_DIALOG_CORNER_DP).toFloat()
        setStroke(context.dp(2), KakoTheme.color(context, KakoSlot.DIALOG_BORDER))
    }
    decor.foregroundGravity = Gravity.FILL
    decor.foreground = InsetDrawable(
        ring,
        left,
        top,
        decor.width - left - panel.width,
        decor.height - top - panel.height,
    )
}

/**
 * The same frame for a bottom sheet: a ring on the top and the two sides, with the
 * sheet's own rounded top corners.
 *
 * A sheet cannot use [kakoStampDialog]. That one hangs the ring off the decor inset to
 * `parentPanel`, which a sheet has no equivalent of, and the ring would then be frozen
 * where the sheet happened to be when it was stamped -- a sheet slides in, is dragged,
 * and settles at half or full height. Here the ring is the sheet view's own foreground
 * instead: a FrameLayout paints its foreground over every child, so it beats both the
 * Material shape underneath and the Compose Surface that most of these sheets fill
 * themselves with, and it follows the view for free through the slide and every drag.
 *
 * The bottom edge is pushed outside the sheet's bounds (and so clipped away): a sheet
 * sits on the bottom of the screen, where a line is either invisible or a stray rule
 * across the navigation bar.
 *
 * Unlike [applyKakoBorder] this does NOT take the show listener -- bottom sheet call
 * sites do claim it (FenixDialogFragment expands the sheet from there), and taking it
 * would leave those sheets collapsed.
 */
internal fun <T : Dialog> T.applyKakoSheetBorder(): T = apply {
    if (!KakoTheme.isEnabled(context)) return@apply
    val w = window ?: return@apply
    fun stamp() {
        val sheet = w.findViewById<View>(materialR.id.design_bottom_sheet) as? FrameLayout ?: return
        val stroke = context.dp(2)
        val radius = context.dp(MATERIAL_SHEET_CORNER_DP).toFloat()
        val ring = GradientDrawable().apply {
            // Top corners only; the bottom two are square and off-screen anyway.
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setStroke(stroke, KakoTheme.color(context, KakoSlot.SHEET_BORDER))
        }
        sheet.foregroundGravity = Gravity.FILL
        sheet.foreground = InsetDrawable(ring, 0, 0, 0, -stroke)
    }
    stamp()
    // The sheet view exists only once the dialog has built its container; if the stamp
    // above ran too early, this one catches it. Re-stamping is idempotent.
    w.decorView.post { stamp() }
}

/** [MaterialAlertDialogBuilder.create] plus the kako border. */
internal fun MaterialAlertDialogBuilder.createKako(): AppCompatAlertDialog = create().applyKakoBorder()

/** [MaterialAlertDialogBuilder.show]-equivalent that adds the kako border first. */
internal fun MaterialAlertDialogBuilder.showKako(): AppCompatAlertDialog = createKako().also { it.show() }

/**
 * A dialog of the UI page: its own title line, [content], and Cancel. Panel, title and buttons
 * come from the dialog and button roles; the buttons are pills, the positive one the main pill.
 */
private fun kakoDialog(context: Context, title: CharSequence, content: View): AlertDialog {
    val titleView = TextView(context).apply {
        this.text = title
        setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TITLE))
        setTypeface(typeface, Typeface.BOLD)
        textSize = TITLE_TEXT_SIZE_SP
        setPadding(context.dp(24), context.dp(16), context.dp(24), context.dp(6))
    }
    return AlertDialog.Builder(context)
        .setCustomTitle(titleView)
        .setView(content)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
        .apply {
            window?.setBackgroundDrawable(context.kakoDialogBackground())
            setOnShowListener { KakoPills.dialogButtons(this) }
        }
}

/**
 * One row of a pure choice list (a font, a weight): the choice drawn as itself, no icon, no
 * minimum height — only as tall as its text and a little air.
 */
private fun choiceRow(context: Context, label: CharSequence, typeface: Typeface, onClick: () -> Unit): TextView =
    TextView(context).apply {
        text = label
        this.typeface = typeface
        setTextColor(KakoTheme.color(context, KakoSlot.LIST_TEXT))
        textSize = OPTION_TEXT_SIZE_SP
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(24), context.dp(ROW_PADDING_DP), context.dp(24), context.dp(ROW_PADDING_DP))
        background = context.kakoRowRipple()
        setOnClickListener { onClick() }
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
        val current = KakoTheme.color(context, slot)

        val hexField = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(String.format(Locale.ROOT, "#%08X", current))
        }
        KakoPills.editText(hexField, ownLine = true)

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
                        setStroke(context.dp(2), KakoTheme.color(context, KakoSlot.DIALOG_BORDER))
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

        // The swatches offered to pick from are data, not the colour of anything: they are
        // what a role can be set to, and stay literal.
        val PRESETS = listOf(
            0xFF000000, 0xFF101010, 0xFF202020, 0xFF404040, 0xFF808080, 0xFFFFFFFF,
            0xFFFFFF00, 0xFFFFD600, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFFF44336,
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
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(4), 0, context.dp(4))
        }

        lateinit var dialog: AlertDialog
        // A pure choice list: every font is drawn as itself and no row has an icon.
        KakoFonts.availableFontOptions(context).forEach { option ->
            holder.addView(
                choiceRow(context, option.displayName, KakoFonts.fontTypeface(context, option.fileName)) {
                    dialog.dismiss()
                    onPick(option.fileName)
                },
            )
        }
        // The one action under the choices: a pill, with its icon, opening the file picker.
        holder.addView(
            TextView(context).apply {
                text = context.getString(R.string.kako_font_add)
                textSize = OPTION_TEXT_SIZE_SP
                gravity = Gravity.CENTER_VERTICAL
                minHeight = context.dp(ACTION_ROW_HEIGHT_DP)
                setOnClickListener {
                    dialog.dismiss()
                    onAddFont()
                }
                KakoMenuRows.actionRow(this, iconsR.drawable.mozac_ic_import_data_24, ForkIconRole.SHARE, opens = true)
            },
        )

        dialog = kakoDialog(
            context,
            context.getString(R.string.kako_font_row),
            ScrollView(context).apply { addView(holder) },
        )
        dialog.show()
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
        val base = KakoFonts.fontTypeface(context, KakoFonts.fontFamily(context))
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(4), 0, context.dp(4))
        }

        lateinit var dialog: AlertDialog
        // A pure choice list: every weight is drawn in itself and no row has an icon.
        WEIGHTS.forEach { (weight, labelRes) ->
            holder.addView(
                choiceRow(
                    context,
                    context.getString(labelRes),
                    if (weight > 0) Typeface.create(base, weight, false) else base,
                ) {
                    dialog.dismiss()
                    onPick(weight)
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

/**
 * Drag-to-reorder list of the pinned extension toolbar buttons. Rows are dragged
 * by long-press (or the ☰ handle); every completed drag reports the new id order
 * through [onReordered], which persists it — so dismissing the dialog never loses
 * a rearrangement.
 */
class KakoExtensionOrderDialog(
    private val context: Context,
    entries: List<Pair<String, String>>,
    private val onReordered: (List<String>) -> Unit,
) {
    private val rows = entries.toMutableList()

    fun show() {
        if (rows.isEmpty()) {
            kakoDialog(
                context,
                context.getString(R.string.kako_extension_order_row),
                TextView(context).apply {
                    text = context.getString(R.string.kako_extension_order_empty)
                    setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TEXT))
                    textSize = OPTION_TEXT_SIZE_SP
                    setPadding(context.dp(24), context.dp(8), context.dp(24), context.dp(16))
                },
            ).show()
            return
        }

        val adapter = object : RecyclerView.Adapter<RowHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
                val handle = TextView(context).apply {
                    text = HANDLE_GLYPH
                    setTextColor(KakoTheme.color(context, KakoSlot.LIST_SECONDARY))
                    textSize = OPTION_TEXT_SIZE_SP
                    setPadding(context.dp(24), 0, context.dp(16), 0)
                }
                val label = TextView(context).apply {
                    setTextColor(KakoTheme.color(context, KakoSlot.LIST_TEXT))
                    textSize = OPTION_TEXT_SIZE_SP
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, context.dp(24), 0)
                }
                // Every row is an extension: one icon column, in the role of a thing looked at.
                KakoRowIcons.setLabelIcon(
                    label,
                    iconsR.drawable.mozac_ic_extension_24,
                    KakoTheme.iconColor(context, ForkIconRole.VIEW),
                )
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, context.dp(ROW_PADDING_DP), 0, context.dp(ROW_PADDING_DP))
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT,
                    )
                    background = context.kakoRowRipple()
                    addView(handle)
                    addView(label)
                }
                return RowHolder(row, label)
            }

            override fun getItemCount(): Int = rows.size

            override fun onBindViewHolder(holder: RowHolder, position: Int) {
                holder.label.text = rows[position].second
            }
        }

        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            setPadding(0, context.dp(4), 0, context.dp(4))
        }

        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    rows.add(to, rows.removeAt(from))
                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    onReordered(rows.map { it.first })
                }
            },
        ).attachToRecyclerView(recycler)

        kakoDialog(context, context.getString(R.string.kako_extension_order_title), recycler).show()
    }

    private class RowHolder(row: View, val label: TextView) : RecyclerView.ViewHolder(row)

    private companion object {
        const val HANDLE_GLYPH = "☰"
    }
}

private const val TITLE_TEXT_SIZE_SP = 18f
private const val OPTION_TEXT_SIZE_SP = 18f

/** Above and below the text of a row built here: rows are only as tall as their content. */
private const val ROW_PADDING_DP = 6

/** The height of the one pill row under a choice list — a pill needs room to be hit. */
private const val ACTION_ROW_HEIGHT_DP = 44

/** Material's own alert-dialog corner radius (@dimen/material_dialog_corner_radius). */
private const val MATERIAL_DIALOG_CORNER_DP = 28

/**
 * Material's own bottom-sheet corner radius -- the shape both Widget.Material3.BottomSheet
 * and the Compose sheets' MaterialTheme.shapes.extraLarge round their top corners with.
 */
private const val MATERIAL_SHEET_CORNER_DP = 28
