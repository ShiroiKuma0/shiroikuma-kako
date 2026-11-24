/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Checkable
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.core.view.children
import kotlin.math.max
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R

/*
 * The parts of the 白い熊 火狐 UI page that are not the page itself: its sections, their stored
 * order and fold state, and the few views the page draws for itself.
 */

/**
 * One top-level section of the UI page. Icon and colour sit here, beside the title, so that the
 * heading and the chip of a section cannot drift apart: both draw [iconRes] in [colour].
 *
 * [colour] is one of the seven section-icon roles and says what kind of thing the section is:
 * the base (yellow), controls (green), words (light blue), the frame (heavy blue), reaching out
 * (orange), warning (red), a brief flash (rose, one section at most). The order below is the
 * default order of the page; no two neighbours in it share a colour.
 *
 * @property key The stable name the section is stored under in the order and fold lists.
 * @property roles The colour roles set in this section, or null for a section without any.
 */
enum class KakoPageSection(
    val key: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val colour: KakoSlot,
    val roles: KakoSection?,
) {
    EXIM("exim", R.string.kako_eim_heading, iconsR.drawable.mozac_ic_share_android_24, KakoSlot.SECTION_REACH, null),
    FOUNDATION(
        "foundation",
        R.string.kako_section_foundation,
        iconsR.drawable.mozac_ic_themes_24,
        KakoSlot.SECTION_BASE,
        KakoSection.FOUNDATION,
    ),
    TEXT("text", R.string.kako_section_text, iconsR.drawable.mozac_ic_text_size_24, KakoSlot.SECTION_WORDS, KakoSection.TEXT),
    BUTTONS(
        "buttons",
        R.string.kako_section_buttons,
        iconsR.drawable.mozac_ic_cursor_arrow_24,
        KakoSlot.SECTION_CONTROLS,
        KakoSection.BUTTONS,
    ),
    TOOLBAR(
        "toolbar",
        R.string.kako_section_toolbar,
        iconsR.drawable.mozac_ic_search_24,
        KakoSlot.SECTION_FRAME,
        KakoSection.TOOLBAR,
    ),
    MENU(
        "menu",
        R.string.kako_section_menu,
        iconsR.drawable.mozac_ic_ellipsis_vertical_24,
        KakoSlot.SECTION_REACH,
        KakoSection.MENU,
    ),
    TABS("tabs", R.string.kako_section_tabs, iconsR.drawable.mozac_ic_tab_24, KakoSlot.SECTION_FRAME, KakoSection.TABS),
    SWITCHES(
        "switches",
        R.string.kako_section_switches,
        iconsR.drawable.mozac_ic_swap_horizontal_24,
        KakoSlot.SECTION_CONTROLS,
        KakoSection.SWITCHES,
    ),
    DIALOGS(
        "dialogs",
        R.string.kako_section_dialogs,
        iconsR.drawable.mozac_ic_information_24,
        KakoSlot.SECTION_REACH,
        KakoSection.DIALOGS,
    ),
    MARKS(
        "marks",
        R.string.kako_section_marks,
        iconsR.drawable.mozac_ic_checkmark_24,
        KakoSlot.SECTION_CONTROLS,
        KakoSection.MARKS,
    ),
    LISTS(
        "lists",
        R.string.kako_section_lists,
        iconsR.drawable.mozac_ic_reading_list_24,
        KakoSlot.SECTION_FRAME,
        KakoSection.LISTS,
    ),
    FIELDS("fields", R.string.kako_section_fields, iconsR.drawable.mozac_ic_edit_24, KakoSlot.SECTION_WORDS, KakoSection.FIELDS),
    BARS(
        "bars",
        R.string.kako_section_bars,
        iconsR.drawable.mozac_ic_device_mobile_24,
        KakoSlot.SECTION_FRAME,
        KakoSection.BARS,
    ),
    PAGES("pages", R.string.kako_section_pages, iconsR.drawable.mozac_ic_settings_24, KakoSlot.SECTION_BASE, KakoSection.PAGES),
    TOASTS(
        "toasts",
        R.string.kako_section_toasts,
        iconsR.drawable.mozac_ic_notification_24,
        KakoSlot.SECTION_FLASH,
        KakoSection.TOASTS,
    ),
    SCREENS(
        "screens",
        R.string.kako_section_screens,
        iconsR.drawable.mozac_ic_wallpaper_24,
        KakoSlot.SECTION_BASE,
        KakoSection.SCREENS,
    ),
    MAINTENANCE(
        "maintenance",
        R.string.kako_section_maintenance,
        iconsR.drawable.mozac_ic_tool_24,
        KakoSlot.SECTION_WARNING,
        null,
    ),
}

/**
 * The arrangement of the UI page: the order of its sections and which of them are open. Two small
 * lists in the theme's own preferences file, so they travel in the export with the colours.
 * Every section is folded on a fresh install.
 */
object KakoPageState {
    private const val KEY_ORDER = "kako_ui_section_order"
    private const val KEY_OPEN = "kako_ui_sections_open"

    /** The sections in the stored order; a section the stored list does not know keeps its default place. */
    fun order(context: Context): List<KakoPageSection> {
        val stored =
            KakoTheme.prefs(context).getString(KEY_ORDER, null)?.split(',')?.mapNotNull { key ->
                KakoPageSection.entries.firstOrNull { it.key == key }
            } ?: return KakoPageSection.entries
        val result = stored.distinct().toMutableList()
        KakoPageSection.entries.forEachIndexed { index, section ->
            if (section !in result) result.add(index.coerceAtMost(result.size), section)
        }
        return result
    }

    fun setOrder(context: Context, order: List<KakoPageSection>) {
        KakoTheme.prefs(context).edit { putString(KEY_ORDER, order.joinToString(",") { it.key }) }
    }

    fun resetOrder(context: Context) {
        KakoTheme.prefs(context).edit { remove(KEY_ORDER) }
    }

    private fun open(context: Context): Set<String> =
        KakoTheme.prefs(context).getString(KEY_OPEN, null)?.split(',')?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    fun isOpen(context: Context, section: KakoPageSection): Boolean = section.key in open(context)

    fun setOpen(context: Context, section: KakoPageSection, isOpen: Boolean) {
        val keys = open(context).toMutableSet()
        if (isOpen) keys.add(section.key) else keys.remove(section.key)
        KakoTheme.prefs(context).edit { putString(KEY_OPEN, keys.joinToString(",")) }
    }
}

internal fun Context.kakoDp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt().coerceAtLeast(1)

/**
 * The pill: fully round, an outline, the ground showing through, never filled. The main action of
 * a block has the thick frame; a deleting one lends the delete colour to its frame.
 */
internal fun Context.kakoPillBackground(main: Boolean, destructive: Boolean = false): Drawable {
    val frame =
        when {
            destructive -> KakoTheme.color(this, KakoSlot.BUTTON_DELETE)
            main -> KakoTheme.color(this, KakoSlot.BUTTON_MAIN_BORDER)
            else -> KakoTheme.color(this, KakoSlot.BUTTON_BORDER)
        }
    val radius = kakoDp(PILL_RADIUS_DP).toFloat()
    val shape =
        GradientDrawable().apply {
            cornerRadius = radius
            setColor(KakoTheme.color(this@kakoPillBackground, KakoSlot.BUTTON_BACKGROUND))
            setStroke(kakoDp(if (main) KakoTheme.PILL_THICK_DP else KakoTheme.PILL_THIN_DP), frame)
        }
    val mask = GradientDrawable().apply { cornerRadius = radius; setColor(frame) }
    return RippleDrawable(ColorStateList.valueOf(KakoTheme.color(this, KakoSlot.BUTTON_RIPPLE)), shape, mask)
}

/** The label colour that goes with [kakoPillBackground]. */
internal fun Context.kakoPillInk(main: Boolean, destructive: Boolean = false): Int =
    when {
        destructive -> KakoTheme.color(this, KakoSlot.BUTTON_DELETE)
        main -> KakoTheme.color(this, KakoSlot.BUTTON_MAIN_TEXT)
        else -> KakoTheme.color(this, KakoSlot.BUTTON_TEXT)
    }

/**
 * Gives a label its icon: as tall as the label's text line (1.25 × the text size), never under
 * 18 dp, in [color], with a gap of half the icon, and centred on the label's first line rather
 * than on the whole view, so that it stays beside a label that wraps.
 */
internal fun TextView.setKakoLabelIcon(@DrawableRes iconRes: Int, color: Int) {
    val icon = AppCompatResources.getDrawable(context, iconRes)?.mutate() ?: return
    val size = max((textSize * LABEL_ICON_SCALE).toInt(), context.kakoDp(LABEL_ICON_MIN_DP))
    icon.setTint(color)
    val wrapped = FirstLineIcon(icon, this)
    wrapped.setBounds(0, 0, size, size)
    setCompoundDrawablesRelative(wrapped, null, null, null)
    compoundDrawablePadding = size / 2
}

/** How far the text of a label with an icon from [setKakoLabelIcon] stands from the label's start. */
internal fun TextView.kakoLabelIconGutter(): Int {
    val size = max((textSize * LABEL_ICON_SCALE).toInt(), context.kakoDp(LABEL_ICON_MIN_DP))
    return size + size / 2
}

private class FirstLineIcon(icon: Drawable, private val label: TextView) : DrawableWrapper(icon) {
    override fun draw(canvas: Canvas) {
        val content = label.height - label.compoundPaddingTop - label.compoundPaddingBottom
        val shift = if (label.lineCount > 1) (content - label.lineHeight) / 2f else 0f
        canvas.save()
        canvas.translate(0f, -shift)
        super.draw(canvas)
        canvas.restore()
    }
}

/**
 * The outlined switch: a round outline with the ground showing through and a solid dot inside it,
 * right and in the on colour when on, left and in the off colour when off. Nothing is ever filled.
 * It reads its four colours from the switch roles each time it draws.
 */
@SuppressLint("ViewConstructor")
internal class KakoSwitchView(context: Context, compact: Boolean = false) : View(context), Checkable {
    private val widthPx = context.kakoDp(if (compact) COMPACT_WIDTH_DP else WIDTH_DP)
    private val heightPx = context.kakoDp(if (compact) COMPACT_HEIGHT_DP else HEIGHT_DP)
    private val stroke = context.kakoDp(OUTLINE_DP).toFloat()
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var position = 0f
    private var checked = false
    private var animator: ValueAnimator? = null

    /** Called when a press changed the state; a change made through [setChecked] is not reported. */
    var onCheckedChange: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(widthPx, heightPx)

    override fun isChecked(): Boolean = checked

    /** Sets the state without the travel and without reporting it. */
    override fun setChecked(value: Boolean) {
        animator?.cancel()
        checked = value
        position = if (value) 1f else 0f
        invalidate()
    }

    override fun toggle() {
        checked = !checked
        animator?.cancel()
        animator =
            ValueAnimator.ofFloat(position, if (checked) 1f else 0f).apply {
                duration = TRAVEL_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        onCheckedChange?.invoke(checked)
    }

    override fun onDraw(canvas: Canvas) {
        val on = KakoTheme.color(context, KakoSlot.SWITCH_ON)
        val off = KakoTheme.color(context, KakoSlot.SWITCH_OFF)
        outline.color = KakoTheme.mix(off, on, position)
        dot.color =
            KakoTheme.mix(
                KakoTheme.color(context, KakoSlot.SWITCH_OFF_DOT),
                KakoTheme.color(context, KakoSlot.SWITCH_ON_DOT),
                position,
            )
        val alpha = if (isEnabled) FULL_ALPHA else (FULL_ALPHA * KakoTheme.DISABLED_ALPHA).toInt()
        outline.alpha = alpha
        dot.alpha = alpha
        val half = stroke / 2f
        val radius = (height - stroke) / 2f
        canvas.drawRoundRect(half, half, width - half, height - half, radius, radius, outline)
        val dotRadius = height * DOT_FRACTION / 2f
        val gap = (height - dotRadius * 2f) / 2f
        val left = gap + dotRadius
        val right = width - gap - dotRadius
        canvas.drawCircle(left + (right - left) * position, height / 2f, dotRadius, dot)
    }

    // The int-valued checked state that replaces this exists from API 36 only.
    @Suppress("DEPRECATION")
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checked
    }

    private companion object {
        const val WIDTH_DP = 52f
        const val HEIGHT_DP = 28f
        const val COMPACT_WIDTH_DP = 44f
        const val COMPACT_HEIGHT_DP = 24f
        const val OUTLINE_DP = 2f
        const val DOT_FRACTION = 0.46f
        const val TRAVEL_MS = 180L
        const val FULL_ALPHA = 255
    }
}

/**
 * Lays its children out in rows, each child as wide as its own content, wrapping to the next row
 * when one is full. The chip index of the UI page: chips of variable length, never a column count.
 */
internal class KakoFlowLayout(context: Context) : ViewGroup(context) {
    private val gapX = context.kakoDp(CHIP_GAP_X_DP)
    private val gapY = context.kakoDp(CHIP_GAP_Y_DP)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        children.forEach { child ->
            if (child.visibility == GONE) return@forEach
            child.measure(
                MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            if (x > 0 && x + child.measuredWidth > available) {
                x = 0
                y += rowHeight + gapY
                rowHeight = 0
            }
            x += child.measuredWidth + gapX
            rowHeight = max(rowHeight, child.measuredHeight)
        }
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(y + rowHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val available = right - left - paddingLeft - paddingRight
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var x = 0
        var y = paddingTop
        var rowHeight = 0
        children.forEach { child ->
            if (child.visibility == GONE) return@forEach
            if (x > 0 && x + child.measuredWidth > available) {
                x = 0
                y += rowHeight + gapY
                rowHeight = 0
            }
            val start = if (rtl) paddingLeft + available - x - child.measuredWidth else paddingLeft + x
            child.layout(start, y, start + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gapX
            rowHeight = max(rowHeight, child.measuredHeight)
        }
    }

    /** The index a view dropped at ([x], [y]) would take, or -1 when the point is over no chip. */
    fun indexAt(x: Float, y: Float): Int {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (x >= child.left - gapX / 2f && x < child.right + gapX / 2f &&
                y >= child.top - gapY / 2f && y < child.bottom + gapY / 2f
            ) {
                return index
            }
        }
        return -1
    }

    private companion object {
        const val CHIP_GAP_X_DP = 6f
        const val CHIP_GAP_Y_DP = 6f
    }
}

private const val PILL_RADIUS_DP = 100f
private const val LABEL_ICON_SCALE = 1.25f
private const val LABEL_ICON_MIN_DP = 18f
