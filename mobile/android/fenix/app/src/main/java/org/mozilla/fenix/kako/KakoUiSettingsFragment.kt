/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.showToolbar

/**
 * The 白い熊 火狐 UI page — kxkb-styled: every heading is a bold accent title with a
 * text-wide underline, top-level sections are separated by thin full-width hairlines,
 * and rows follow the kxkb indent ladder (36/54/72/90dp). The first section is
 * Export / Import (Kōjiki flow): a settable export directory queried on opening for
 * the latest export, and a category panel with the ArcaneChat pill button line.
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

    // Export / Import wiring (SAF launchers live on the fragment; the panel calls back).

    private var eximDialog: KakoEximDialog? = null

    private val dirPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        KakoExim.setDirUri(requireContext(), uri)
        eximDialog?.refreshStatus()
        rebuild()
    }

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        eximDialog?.onImportPicked(uri)
    }

    private val exportSaver = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        eximDialog?.onExportTarget(uri)
    }

    private val eximHost = object : KakoEximDialog.Host {
        override val scope: CoroutineScope get() = lifecycleScope
        override fun pickDirectory() = dirPicker.launch(KakoExim.dirUri(requireContext()))
        override fun launchImportPicker() =
            importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        override fun launchExportSaver(fileName: String) = exportSaver.launch(fileName)
        override fun closePage() {
            eximDialog = null
            findNavController().popBackStack()
        }
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
        // Re-query the export directory for the latest export on every opening.
        rebuild()
    }

    override fun onDestroyView() {
        eximDialog?.dismiss()
        eximDialog = null
        super.onDestroyView()
    }

    private fun rebuild() {
        val context = requireContext()
        holder.removeAllViews()
        scroll.setBackgroundColor(KakoTheme.color(context, KakoSlot.BACKGROUND))

        addEximSection()
        addMasterSwitch()

        KakoSection.entries.forEach { section ->
            addSectionHeader(getString(section.labelRes))
            val slots = KakoSlot.entries.filter { it.section == section }
            if (section == KakoSection.FOUNDATION) {
                addSubgroupHeader(getString(R.string.kako_subgroup_colors))
                slots.forEach { addColorRow(it, indent = 2) }
                addSubgroupHeader(getString(R.string.kako_subgroup_font))
                addFontRows(indent = 2)
            } else {
                if (section == KakoSection.TOOLBAR) {
                    addToolbarTwoRowsSwitch(indent = 1)
                }
                slots.forEach { addColorRow(it, indent = 1) }
                KakoDimen.entries.filter { it.section == section }.forEach { addDimenRow(it, indent = 1) }
                if (section == KakoSection.TOOLBAR) {
                    addExtensionIconSizeRow(indent = 1)
                    addExtensionOrderRow(indent = 1)
                }
            }
        }

        addResetButton()
    }

    // Export / Import — the first separated section (Kōjiki flow).

    private fun addEximSection() {
        val context = requireContext()
        addSectionHeader(getString(R.string.kako_eim_heading), first = true)

        // The bordered, clearly-tappable export-directory box — red value while unset.
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        val dirName = KakoExim.exportDir(context)?.name ?: KakoExim.dirUri(context)?.lastPathSegment
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(KakoTheme.color(context, KakoSlot.MENU_BACKGROUND))
                    setStroke(dp(2), accent)
                }
                setOnClickListener { eximHost.pickDirectory() }
                addView(
                    TextView(context).apply {
                        text = getString(R.string.kako_eim_dir)
                        setTextColor(accent)
                        textSize = 12f
                    },
                )
                addView(
                    TextView(context).apply {
                        text = dirName ?: getString(R.string.kako_eim_dir_unset)
                        setTextColor(
                            if (dirName == null) KakoExim.WARN_COLOR else KakoTheme.color(context, KakoSlot.TEXT),
                        )
                        setTypeface(typeface, Typeface.BOLD)
                        textSize = 15f
                    },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(ROW_START_DP)
                marginEnd = dp(END_MARGIN_DP)
                topMargin = dp(8)
            },
        )

        // The latest export in the chosen directory — queried on every page opening.
        val (statusMsg, warn) = when {
            KakoExim.exportDir(context) == null ->
                getString(R.string.kako_eim_warn_nodir) to true
            else -> KakoExim.latestExport(context)?.let {
                getString(R.string.kako_eim_last, KakoExim.fmtTs(it.lastModified())) to false
            } ?: (getString(R.string.kako_eim_warn_none) to true)
        }
        holder.addView(
            TextView(context).apply {
                text = statusMsg
                textSize = 14f
                setTextColor(if (warn) KakoExim.WARN_COLOR else KakoTheme.color(context, KakoSlot.TEXT))
                alpha = if (warn) 1f else 0.8f
                setPaddingRelative(dp(ROW_START_DP) + dp(2), dp(4), dp(END_MARGIN_DP), dp(4))
            },
        )

        // The row opening the category panel.
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(ROW_START_DP), dp(5), dp(END_MARGIN_DP), dp(5))
                setBackgroundResource(android.R.drawable.list_selector_background)
                addView(
                    TextView(context).apply {
                        text = getString(R.string.kako_eim_heading)
                        setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                        textSize = ITEM_TEXT_SIZE_SP
                    },
                )
                addView(
                    TextView(context).apply {
                        text = getString(R.string.kako_eim_row_summary)
                        setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
                        textSize = VALUE_TEXT_SIZE_SP
                        setPadding(0, dp(3), 0, 0)
                    },
                )
                setOnClickListener {
                    eximDialog = KakoEximDialog(requireContext(), eximHost).also { it.show() }
                }
            },
        )

        addAutomationRows()
    }

    /**
     * The sister-app automation contract's three rows, appended directly below the existing
     * export rows — this is a backup feature, so it lives where backup lives, and every
     * sister app puts them in the same place (never a section of its own).
     *
     * In contract v2 the master switch ships **on** and the token is opt-in, so the third row
     * is hidden until the second asks for it. See [KakoAutomation] for why.
     */
    private fun addAutomationRows() {
        val context = requireContext()
        val text = KakoTheme.color(context, KakoSlot.TEXT)
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)

        val toggle = SwitchCompat(context).apply {
            isChecked = KakoAutomation.enabled(context)
            setOnCheckedChangeListener { _, checked ->
                KakoAutomation.setEnabled(context, checked)
                if (checked) promptForAllFilesAccess()
            }
        }
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(dp(ROW_START_DP), dp(5), dp(END_MARGIN_DP), dp(5))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            TextView(context).apply {
                                this.text = getString(R.string.kako_automation_switch)
                                setTextColor(text)
                                textSize = ITEM_TEXT_SIZE_SP
                            },
                        )
                        addView(
                            TextView(context).apply {
                                this.text = getString(R.string.kako_automation_switch_desc)
                                setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
                                textSize = VALUE_TEXT_SIZE_SP
                                setPadding(0, dp(3), 0, 0)
                            },
                        )
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(toggle)
                setOnClickListener { toggle.toggle() }
            },
        )

        // Row 3, built first because row 2's switch shows and hides it: the token itself —
        // tap to copy the whole secret, Regenerate on the right.
        val tokenValue = TextView(context).apply {
            this.text = KakoAutomation.abbreviated(KakoAutomation.token(context))
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            textSize = VALUE_TEXT_SIZE_SP
            setPadding(0, dp(3), 0, 0)
        }
        val tokenRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(ROW_START_DP), dp(5), dp(END_MARGIN_DP), dp(5))
            setBackgroundResource(android.R.drawable.list_selector_background)
            // Hidden unless it is actually being asked for: a 48-character secret sitting
            // under an off switch invites 白い熊 to paste it somewhere it will do nothing.
            visibility = if (KakoAutomation.requireToken(context)) View.VISIBLE else View.GONE
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(context).apply {
                            this.text = getString(R.string.kako_automation_token_row)
                            setTextColor(text)
                            textSize = ITEM_TEXT_SIZE_SP
                        },
                    )
                    addView(tokenValue)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(context).apply {
                    this.text = getString(R.string.kako_automation_regenerate)
                    setTextColor(KakoExim.WARN_COLOR)
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = VALUE_TEXT_SIZE_SP
                    setPaddingRelative(dp(12), dp(8), 0, dp(8))
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        val fresh = KakoAutomation.regenerateToken(context)
                        tokenValue.text = KakoAutomation.abbreviated(fresh)
                        toast(getString(R.string.kako_automation_regenerated), long = true)
                    }
                },
            )
            setOnClickListener {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("token", KakoAutomation.token(context)),
                )
                toast(getString(R.string.kako_automation_token_copied))
            }
        }

        // Row 2: whether a caller must present the token at all. Default OFF — a pasted secret
        // cannot survive the wipe this feature exists to recover from, and the door that
        // actually moves data identifies its caller instead (KakoAutomationCallers).
        val requireToggle = SwitchCompat(context).apply {
            isChecked = KakoAutomation.requireToken(context)
            setOnCheckedChangeListener { _, checked ->
                KakoAutomation.setRequireToken(context, checked)
                tokenRow.visibility = if (checked) View.VISIBLE else View.GONE
            }
        }
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(dp(ROW_START_DP), dp(5), dp(END_MARGIN_DP), dp(5))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            TextView(context).apply {
                                this.text = getString(R.string.kako_automation_require_token)
                                setTextColor(text)
                                textSize = ITEM_TEXT_SIZE_SP
                            },
                        )
                        addView(
                            TextView(context).apply {
                                this.text = getString(R.string.kako_automation_require_token_desc)
                                setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
                                textSize = VALUE_TEXT_SIZE_SP
                                setPadding(0, dp(3), 0, 0)
                            },
                        )
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(requireToggle)
                setOnClickListener { requireToggle.toggle() }
            },
        )

        holder.addView(tokenRow)
    }

    /**
     * Writing to the directory an automation task names needs All-files access; the export
     * degrades to the configured SAF directory without it, so this only offers the grant.
     */
    private fun promptForAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (Environment.isExternalStorageManager()) return
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(R.string.kako_automation_files_title)
            .setMessage(R.string.kako_automation_files_body)
            .setPositiveButton(R.string.kako_automation_files_grant) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .applyKakoBorder()
            .show()
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(
            requireContext(),
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
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
                setPaddingRelative(dp(HEADING_START_DP), dp(12), dp(END_MARGIN_DP), dp(12))
                setOnCheckedChangeListener { _, checked ->
                    KakoTheme.setEnabled(context, checked)
                    KakoFonts.refresh(context)
                    rebuild()
                }
            },
        )
    }

    /**
     * kxkb-style top-level heading: a thin full-width hairline separates it from the
     * previous section (skipped for the first), then a bold accent title underlined
     * exactly as wide as its text — the underline is a match_parent View inside a
     * wrap_content column, so it measures to the title.
     */
    private fun addSectionHeader(label: String, first: Boolean = false) {
        val context = requireContext()
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, if (first) dp(12) else dp(10), 0, dp(2))
                if (!first) {
                    addView(
                        View(context).apply { setBackgroundColor(accent) },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1),
                    )
                }
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPaddingRelative(dp(HEADING_START_DP), dp(8), 0, 0)
                        addView(
                            TextView(context).apply {
                                text = label
                                setTextColor(accent)
                                textSize = SECTION_TEXT_SIZE_SP
                                setTypeface(typeface, Typeface.BOLD)
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                        addView(
                            View(context).apply { setBackgroundColor(accent) },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dpF(2.5f),
                            ).apply { topMargin = dp(2) },
                        )
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    /** kxkb sub-heading: smaller bold accent title, text-wide 1.5dp underline, no hairline. */
    private fun addSubgroupHeader(label: String) {
        val context = requireContext()
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(SUB_START_DP), dp(10), 0, dp(2))
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(accent)
                        textSize = SUBGROUP_TEXT_SIZE_SP
                        setTypeface(typeface, Typeface.BOLD)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    View(context).apply { setBackgroundColor(accent) },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpF(1.5f),
                    ).apply { topMargin = dp(2) },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun addColorRow(slot: KakoSlot, indent: Int) {
        val context = requireContext()
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(indentPx(indent), dp(5), dp(END_MARGIN_DP), dp(5))
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
                            cornerRadius = dp(4).toFloat()
                            setColor(KakoTheme.color(context, slot))
                            setStroke(dpF(1.5f), KakoTheme.color(context, KakoSlot.BORDER))
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

    private fun addToolbarTwoRowsSwitch(indent: Int) {
        val context = requireContext()
        holder.addView(
            SwitchCompat(context).apply {
                text = getString(R.string.kako_toolbar_two_rows)
                isChecked = KakoTheme.toolbarTwoRows(context)
                setTextColor(KakoTheme.color(context, KakoSlot.TEXT))
                textSize = ITEM_TEXT_SIZE_SP
                setPaddingRelative(indentPx(indent), dp(8), dp(END_MARGIN_DP), dp(8))
                setOnCheckedChangeListener { _, checked ->
                    KakoTheme.setToolbarTwoRows(context, checked)
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
                setPaddingRelative(indentPx(indent), dp(5), dp(END_MARGIN_DP), dp(5))
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
                        textSize = VALUE_TEXT_SIZE_SP
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
            textSize = VALUE_TEXT_SIZE_SP
        }
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(indent), dp(4), dp(END_MARGIN_DP), dp(4))
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

    private fun addDimenRow(dimen: KakoDimen, indent: Int) {
        val context = requireContext()
        val valueView = TextView(context).apply {
            text = getString(R.string.kako_dimen_value, KakoTheme.dimenDp(context, dimen))
            setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
            textSize = VALUE_TEXT_SIZE_SP
        }
        // The slider works in half-dp steps (0.5dp minimum visible thickness).
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(indent), dp(4), dp(END_MARGIN_DP), dp(4))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(context).apply {
                                text = getString(dimen.labelRes)
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
                        max = DIMEN_MAX_DP * 2
                        progress = KakoTheme.dimenHalfUnits(context, dimen)
                        setOnSeekBarChangeListener(
                            object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                                    valueView.text = getString(R.string.kako_dimen_value, value / 2f)
                                }

                                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                                override fun onStopTrackingTouch(bar: SeekBar?) {
                                    KakoTheme.setDimenHalfUnits(
                                        context,
                                        dimen,
                                        bar?.progress ?: (dimen.defaultDp * 2),
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    private fun addExtensionIconSizeRow(indent: Int) {
        val context = requireContext()
        val valueView = TextView(context).apply {
            text = getString(R.string.kako_extension_icon_size_value, KakoTheme.extensionIconSizeDp(context))
            setTextColor(KakoTheme.color(context, KakoSlot.TEXT_SECONDARY))
            textSize = VALUE_TEXT_SIZE_SP
        }
        holder.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(indent), dp(4), dp(END_MARGIN_DP), dp(4))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(context).apply {
                                text = getString(R.string.kako_extension_icon_size_row)
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
                        max = EXTENSION_ICON_MAX_DP - EXTENSION_ICON_MIN_DP
                        progress = KakoTheme.extensionIconSizeDp(context) - EXTENSION_ICON_MIN_DP
                        setOnSeekBarChangeListener(
                            object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                                    valueView.text = getString(
                                        R.string.kako_extension_icon_size_value,
                                        value + EXTENSION_ICON_MIN_DP,
                                    )
                                }

                                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                                override fun onStopTrackingTouch(bar: SeekBar?) {
                                    KakoTheme.setExtensionIconSizeDp(
                                        context,
                                        (bar?.progress ?: 0) + EXTENSION_ICON_MIN_DP,
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    private fun addExtensionOrderRow(indent: Int) {
        val context = requireContext()
        val ids = context.components.settings.toolbarPinnedExtensions.split(",").filter { it.isNotEmpty() }
        addValueRow(
            label = getString(R.string.kako_extension_order_row),
            value = ids.size.toString(),
            valueTypeface = Typeface.DEFAULT,
            indent = indent,
        ) {
            val extensions = requireComponents.core.store.state.extensions
            val entries = ids.map { id -> id to (extensions[id]?.name ?: id) }
            KakoExtensionOrderDialog(context, entries) { newOrder ->
                context.components.settings.toolbarPinnedExtensions = newOrder.joinToString(",")
            }.show()
        }
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
                setPaddingRelative(indentPx(indent), dp(4), dp(END_MARGIN_DP), dp(8))
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
                        .create()
                        .applyKakoBorder()
                        .show()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(dp(END_MARGIN_DP), dp(24), dp(END_MARGIN_DP), dp(8))
            },
        )
    }

    // Geometry — the kxkb indent ladder: heading 36dp, sub-heading 54dp, rows 72/90dp.

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private fun dpF(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt().coerceAtLeast(1)

    private fun indentPx(level: Int): Int =
        dp(ROW_START_DP + (level - 1) * (ROW_L2_START_DP - ROW_START_DP))

    private companion object {
        const val HEADING_START_DP = 36
        const val SUB_START_DP = 54
        const val ROW_START_DP = 72
        const val ROW_L2_START_DP = 90
        const val END_MARGIN_DP = 16
        const val SWATCH_SIZE_DP = 38
        const val SECTION_TEXT_SIZE_SP = 20f
        const val SUBGROUP_TEXT_SIZE_SP = 17f
        const val ITEM_TEXT_SIZE_SP = 16f
        const val VALUE_TEXT_SIZE_SP = 13f
        const val SAMPLE_TEXT_SIZE_SP = 18f
        const val FONT_SCALE_MIN = 70
        const val FONT_SCALE_MAX = 160
        const val EXTENSION_ICON_MIN_DP = 16
        const val EXTENSION_ICON_MAX_DP = 48
        const val DIMEN_MAX_DP = 8
    }
}
