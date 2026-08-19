/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
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
import org.mozilla.fenix.R
import java.util.Locale
import com.google.android.material.R as materialR

/**
 * Plain programmatic dialogs for the 白い熊 火狐 UI page — kept free of Fenix's
 * preference machinery so they can be styled entirely from the kako palette.
 */

private fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

/**
 * The fork's signature dialog frame: a black rounded panel ringed in the kako
 * yellow border. Shared by both the programmatic dialogs below and every stock
 * Material alert dialog (via [applyKakoBorder]) so every dialog looks the same.
 */
internal fun Context.kakoDialogBackground(): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dp(8).toFloat()
    setColor(KakoTheme.color(this@kakoDialogBackground, KakoSlot.MENU_BACKGROUND))
    setStroke(dp(1), KakoTheme.color(this@kakoDialogBackground, KakoSlot.BORDER))
}

/**
 * Give any (Material) alert dialog the kako yellow frame; returns the receiver for
 * chaining.
 *
 * The visible rounded panel of a Material alert dialog is a [MaterialShapeDrawable]
 * — but not on the window/decor (which is larger than the panel and floats it in the
 * middle, so a decor-level background or foreground border lands off the panel). So we
 * find that shape in the view tree (the one on the largest view, to skip button shapes)
 * and stroke it in place: the border then sits exactly on the panel edge with Material's
 * own corner radius. The shape only exists once the dialog is laid out, so we stroke in a
 * [Dialog.setOnShowListener] and on the next frame; the immediate pass covers the
 * already-shown prompt-hook path. No call site claims the show listener, so taking it here
 * is safe.
 */
internal fun <T : Dialog> T.applyKakoBorder(): T = apply {
    val w = window ?: return@apply
    val decor = w.decorView as? FrameLayout ?: return@apply
    fun stamp() {
        // parentPanel is the dialog's content root; the window background's 10dp inset is
        // applied to it as padding, so its bounds coincide with the visible rounded panel
        // (the decor itself is larger). We draw the ring as the decor's foreground — which
        // is painted on top and provably visible — but inset to parentPanel's measured
        // position so the border lands exactly on the panel rather than out at the decor edge.
        val panel = w.findViewById<View>(androidx.appcompat.R.id.parentPanel) ?: return
        if (panel.width == 0 || panel.height == 0) return
        val pos = IntArray(2).also { panel.getLocationInWindow(it) }
        val origin = IntArray(2).also { decor.getLocationInWindow(it) }
        val left = pos[0] - origin[0]
        val top = pos[1] - origin[1]
        val ring = GradientDrawable().apply {
            cornerRadius = context.dp(MATERIAL_DIALOG_CORNER_DP).toFloat()
            setStroke(context.dp(2), KakoTheme.color(context, KakoSlot.BORDER))
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
    stamp()
    setOnShowListener { stamp() }
    decor.post { stamp() }
}

/**
 * The same frame for a bottom sheet: a ring on the top and the two sides, with the
 * sheet's own rounded top corners.
 *
 * A sheet cannot use [applyKakoBorder]. That one hangs the ring off the decor inset to
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
    val w = window ?: return@apply
    fun stamp() {
        val sheet = w.findViewById<View>(materialR.id.design_bottom_sheet) as? FrameLayout ?: return
        val stroke = context.dp(2)
        val radius = context.dp(MATERIAL_SHEET_CORNER_DP).toFloat()
        val ring = GradientDrawable().apply {
            // Top corners only; the bottom two are square and off-screen anyway.
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setStroke(stroke, KakoTheme.color(context, KakoSlot.BORDER))
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
            window?.setBackgroundDrawable(context.kakoDialogBackground())
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
        val textColor = KakoTheme.color(context, KakoSlot.TEXT)

        if (rows.isEmpty()) {
            kakoDialog(
                context,
                context.getString(R.string.kako_extension_order_row),
                TextView(context).apply {
                    text = context.getString(R.string.kako_extension_order_empty)
                    setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
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
                    setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
                    textSize = OPTION_TEXT_SIZE_SP
                    setPadding(context.dp(24), 0, context.dp(16), 0)
                }
                val label = TextView(context).apply {
                    setTextColor(textColor)
                    textSize = OPTION_TEXT_SIZE_SP
                    setPadding(0, 0, context.dp(24), 0)
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = context.dp(48)
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT,
                    )
                    setBackgroundResource(android.R.drawable.list_selector_background)
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
            setPadding(0, context.dp(8), 0, context.dp(8))
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
        const val OPTION_TEXT_SIZE_SP = 18f
        const val HANDLE_GLYPH = "☰"
    }
}

private const val TITLE_TEXT_SIZE_SP = 18f

/** Material's own alert-dialog corner radius (@dimen/material_dialog_corner_radius). */
private const val MATERIAL_DIALOG_CORNER_DP = 28

/**
 * Material's own bottom-sheet corner radius -- the shape both Widget.Material3.BottomSheet
 * and the Compose sheets' MaterialTheme.shapes.extraLarge round their top corners with.
 */
private const val MATERIAL_SHEET_CORNER_DP = 28
