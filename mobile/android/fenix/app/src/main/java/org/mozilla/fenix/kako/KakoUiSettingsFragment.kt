/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.showToolbar

/**
 * The 白い熊 火狐 UI page — built like the sister repos' ThemeActivity: programmatic
 * sections with accent headers, subgroups, and deeply indented individual rows.
 * Every change persists immediately and restyles both this page and (through
 * [KakoTheme.revision]) all live Compose surfaces.
 */
class KakoUiSettingsFragment : Fragment() {

    private lateinit var holder: LinearLayout
    private lateinit var scroll: NestedScrollView

    private val fontImporter = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val imported = KakoFonts.importFont(requireContext(), uri)
        if (imported != null) {
            KakoFonts.setFontFamily(requireContext(), imported)
        }
        rebuild()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        holder = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        scroll = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(holder)
        }
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.kako_ui_title))
    }

    private fun rebuild() {
        val context = requireContext()
        holder.removeAllViews()
        scroll.setBackgroundColor(KakoTheme.color(context, KakoSlot.BACKGROUND))

        addMasterSwitch()

        KakoSection.entries.forEach { section ->
            addSectionHeader(getString(section.labelRes))
            val slots = KakoSlot.entries.filter { it.section == section }
            if (section == KakoSection.FOUNDATION) {
                addSubgroupHeader(getString(R.string.kako_subgroup_colors), indent = 1)
                slots.forEach { addColorRow(it, indent = 2) }
                addSubgroupHeader(getString(R.string.kako_subgroup_font), indent = 1)
                addFontRows(indent = 2)
            } else {
                slots.forEach { addColorRow(it, indent = 1) }
            }
        }

        addResetButton()
    }

    // Rows

    private fun addMasterSwitch() {
        val context = requireContext()
        holder.addView(
            SwitchCompat(context).apply {
                text = getString(R.string.kako_master_switch)
                isChecked = KakoTheme.isEnabled(context)
                setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                textSize = ITEM_TEXT_SIZE_SP
                setPaddingRelative(dp(BASE_MARGIN_DP), dp(12), dp(BASE_MARGIN_DP), dp(12))
                setOnCheckedChangeListener { _, checked ->
                    KakoTheme.setEnabled(context, checked)
                    KakoFonts.refresh(context)
                    rebuild()
                }
            },
        )
    }

    private fun addSectionHeader(label: String) {
        val context = requireContext()
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(BASE_MARGIN_DP), dp(20), dp(BASE_MARGIN_DP), dp(4))
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(accent)
                        textSize = SECTION_TEXT_SIZE_SP
                        setTypeface(typeface, Typeface.BOLD)
                    },
                )
                addView(
                    View(context).apply {
                        setBackgroundColor(accent)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(2),
                        ).apply { topMargin = dp(6) }
                    },
                )
            },
        )
    }

    private fun addSubgroupHeader(label: String, indent: Int) {
        val context = requireContext()
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(indent), dp(12), dp(BASE_MARGIN_DP), dp(2))
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(accent)
                        textSize = SUBGROUP_TEXT_SIZE_SP
                        setTypeface(typeface, Typeface.BOLD)
                    },
                )
                addView(
                    View(context).apply {
                        setBackgroundColor(accent)
                        layoutParams = LinearLayout.LayoutParams(dp(SUBGROUP_RULE_WIDTH_DP), dp(2))
                            .apply { topMargin = dp(4) }
                    },
                )
            },
        )
    }

    private fun addColorRow(slot: KakoSlot, indent: Int) {
        val context = requireContext()
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPaddingRelative(indentPx(indent), dp(4), dp(BASE_MARGIN_DP), dp(4))
                setBackgroundResource(android.R.drawable.list_selector_background)
                addView(
                    TextView(context).apply {
                        text = buildString {
                            append(getString(slot.labelRes))
                            if (KakoTheme.isOverridden(context, slot)) append("  ●")
                        }
                        setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                        textSize = ITEM_TEXT_SIZE_SP
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(KakoTheme.color(context, slot))
                            setStroke(dp(2), KakoTheme.color(context, KakoSlot.BORDER))
                        }
                    },
                    LinearLayout.LayoutParams(dp(SWATCH_SIZE_DP), dp(SWATCH_SIZE_DP)),
                )
                setOnClickListener {
                    KakoColorPickerDialog(context, slot) { picked ->
                        if (picked == null) {
                            KakoTheme.clearColor(context, slot)
                        } else {
                            KakoTheme.setColor(context, slot, picked)
                        }
                        rebuild()
                    }.show()
                }
            },
        )
    }

    private fun addFontRows(indent: Int) {
        val context = requireContext()

        addValueRow(
            label = getString(R.string.kako_font_row),
            value = KakoFonts.fontDisplayName(context, KakoFonts.fontFamily(context)),
            valueTypeface = KakoFonts.fontTypeface(context, KakoFonts.fontFamily(context)),
            indent = indent,
        ) {
            KakoFontPickerDialog(
                context,
                onAddFont = { fontImporter.launch(arrayOf("*/*")) },
                onPick = { fileName ->
                    KakoFonts.setFontFamily(context, fileName)
                    rebuild()
                },
            ).show()
        }

        val weight = KakoFonts.fontWeight(context)
        addValueRow(
            label = getString(R.string.kako_font_weight_row),
            value = if (weight > 0) weight.toString() else getString(R.string.kako_font_weight_inherit),
            valueTypeface = KakoFonts.selectedTypeface(context),
            indent = indent,
        ) {
            KakoFontWeightPickerDialog(context) { picked ->
                KakoFonts.setFontWeight(context, picked)
                rebuild()
            }.show()
        }

        addFontScaleRow(indent)
        addFontSample(indent + 1)
    }

    private fun addValueRow(
        label: String,
        value: String,
        valueTypeface: Typeface,
        indent: Int,
        onClick: () -> Unit,
    ) {
        val context = requireContext()
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPaddingRelative(indentPx(indent), dp(4), dp(BASE_MARGIN_DP), dp(4))
                setBackgroundResource(android.R.drawable.list_selector_background)
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                        textSize = ITEM_TEXT_SIZE_SP
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    TextView(context).apply {
                        text = value
                        typeface = valueTypeface
                        setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
                        textSize = ITEM_TEXT_SIZE_SP
                    },
                )
                setOnClickListener { onClick() }
            },
        )
    }

    private fun addFontScaleRow(indent: Int) {
        val context = requireContext()
        val valueView = TextView(context).apply {
            text = getString(R.string.kako_font_scale_value, KakoFonts.fontScale(context))
            setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
            textSize = ITEM_TEXT_SIZE_SP
        }
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(indent), dp(4), dp(BASE_MARGIN_DP), dp(4))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(context).apply {
                                text = getString(R.string.kako_font_scale_row)
                                setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                                textSize = ITEM_TEXT_SIZE_SP
                            },
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(valueView)
                    },
                )
                addView(
                    SeekBar(context).apply {
                        max = FONT_SCALE_MAX - FONT_SCALE_MIN
                        progress = KakoFonts.fontScale(context) - FONT_SCALE_MIN
                        setOnSeekBarChangeListener(
                            object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                                    valueView.text =
                                        getString(R.string.kako_font_scale_value, value + FONT_SCALE_MIN)
                                }

                                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                                override fun onStopTrackingTouch(bar: SeekBar?) {
                                    KakoFonts.setFontScale(context, (bar?.progress ?: 0) + FONT_SCALE_MIN)
                                    rebuild()
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    private fun addFontSample(indent: Int) {
        val context = requireContext()
        holder.addView(
            TextView(context).apply {
                text = getString(R.string.kako_font_sample)
                typeface = KakoFonts.selectedTypeface(context)
                setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    SAMPLE_TEXT_SIZE_SP * KakoFonts.fontScale(context) / 100f,
                )
                setPaddingRelative(indentPx(indent), dp(4), dp(BASE_MARGIN_DP), dp(8))
            },
        )
    }

    private fun addResetButton() {
        val context = requireContext()
        holder.addView(
            Button(context).apply {
                text = getString(R.string.kako_reset)
                setTextColor(KakoTheme.color(context, KakoSlot.TEXT_ON_ACCENT))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(KakoTheme.color(context, KakoSlot.ACCENT))
                }
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setMessage(R.string.kako_reset_message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            KakoTheme.resetAll(context)
                            KakoFonts.refresh(context)
                            rebuild()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(dp(BASE_MARGIN_DP), dp(24), dp(BASE_MARGIN_DP), dp(8))
            },
        )
    }

    // Geometry

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private fun indentPx(level: Int): Int = dp(BASE_MARGIN_DP) + level * dp(INDENT_STEP_DP)

    private companion object {
        const val BASE_MARGIN_DP = 16
        const val INDENT_STEP_DP = 32
        const val SWATCH_SIZE_DP = 28
        const val SUBGROUP_RULE_WIDTH_DP = 120
        const val SECTION_TEXT_SIZE_SP = 18f
        const val SUBGROUP_TEXT_SIZE_SP = 16f
        const val ITEM_TEXT_SIZE_SP = 15f
        const val SAMPLE_TEXT_SIZE_SP = 18f
        const val FONT_SCALE_MIN = 70
        const val FONT_SCALE_MAX = 160
    }
}
