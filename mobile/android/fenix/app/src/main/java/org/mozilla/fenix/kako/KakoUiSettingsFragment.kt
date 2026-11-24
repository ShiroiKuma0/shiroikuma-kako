/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import mozilla.components.compose.base.theme.ForkIconRole
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.showToolbar

/**
 * The 白い熊 火狐 UI page: a folding accordion with a chip index on top.
 *
 * From the top: a thick pill that opens the app's own settings; a wrapping index of small chips,
 * one for every section — a tap unfolds the section and scrolls to it, a long-press drags the
 * chip, the chips reorder live under the finger and the sections follow on drop; then the
 * sections, every one folded on a fresh install. A heading carries its section's icon in the
 * section's colour, the same drawable and colour as its chip, and folds or unfolds its rows.
 *
 * Rows come in two kinds. A row that does something when tapped is an outlined pill that hugs
 * its label, its explanation beneath; a row with a control of its own — a switch, a swatch, a
 * slider, a value that opens a picker — stays plain. Every row has an icon coloured by what the
 * row does, as tall as its label's text line. A row with an explanation has a bold label, the
 * explanation in the note colour and a thin rule down its left edge.
 *
 * Every colour role is a swatch row in the section of the thing it colours; a tap opens the
 * picker, whose default button resets that one role. Every change persists immediately and
 * restyles both this page and, through [KakoTheme.revision], all live Compose surfaces.
 */
@Suppress("TooManyFunctions", "LargeClass")
class KakoUiSettingsFragment : Fragment() {

    private lateinit var holder: LinearLayout
    private lateinit var scroll: NestedScrollView

    /** The rows of the section being built. */
    private lateinit var body: LinearLayout
    private val sectionViews = mutableMapOf<KakoPageSection, View>()
    private val sectionOpeners = mutableMapOf<KakoPageSection, () -> Unit>()

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
            setPadding(0, dp(8f), 0, dp(24f))
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

    private fun c(slot: KakoSlot): Int = KakoTheme.color(requireContext(), slot)

    private fun icon(role: ForkIconRole): Int = KakoTheme.iconColor(requireContext(), role)

    private fun rebuild() {
        val context = requireContext()
        val scrolled = scroll.scrollY
        // Dialogs opened from this page read upstream's colour resources: keep those current.
        KakoPalette.apply(requireActivity())
        holder.removeAllViews()
        sectionViews.clear()
        sectionOpeners.clear()
        scroll.setBackgroundColor(c(KakoSlot.BACKGROUND))

        val order = KakoPageState.order(context)
        addSettingsLink()
        addChipIndex(order)
        order.forEach { addSection(it) }
        scroll.post { scroll.scrollTo(0, scrolled) }
    }

    // The top of the page: the link to the app's own settings, then the index.

    private fun addSettingsLink() {
        body = holder
        addActionRow(
            label = getString(R.string.kako_app_settings),
            description = getString(R.string.kako_app_settings_desc),
            iconRes = iconsR.drawable.mozac_ic_settings_24,
            role = ForkIconRole.VIEW,
            main = true,
            opens = true,
            level = 0,
        ) {
            findNavController().navigate(NavGraphDirections.actionGlobalSettingsFragment())
        }
    }

    /**
     * The index: one small outlined chip for every section, each as wide as its icon and label.
     * A tap jumps; a long-press drags, and the chips reorder under the finger as it moves.
     */
    private fun addChipIndex(order: List<KakoPageSection>) {
        val context = requireContext()
        val flow = KakoFlowLayout(context).apply {
            setPadding(dp(PAGE_START_DP), dp(8f), dp(END_MARGIN_DP), dp(8f))
        }
        order.forEach { section ->
            flow.addView(
                TextView(context).apply {
                    tag = section
                    text = getString(section.labelRes)
                    textSize = CHIP_TEXT_SIZE_SP
                    maxLines = 1
                    setTextColor(c(KakoSlot.CHIP_TEXT))
                    setKakoLabelIcon(section.iconRes, c(section.colour))
                    setPadding(dp(9f), dp(4f), dp(11f), dp(4f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(CHIP_RADIUS_DP).toFloat()
                        setColor(c(KakoSlot.BACKGROUND))
                        setStroke(dp(KakoTheme.PILL_THIN_DP), c(KakoSlot.CHIP_FRAME))
                    }
                    setOnClickListener { jumpTo(section) }
                    setOnLongClickListener { chip ->
                        chip.startDragAndDrop(null, View.DragShadowBuilder(chip), chip, 0)
                        chip.alpha = DRAGGED_ALPHA
                        true
                    }
                },
            )
        }
        flow.setOnDragListener { _, event -> onChipDrag(flow, event) }
        holder.addView(flow)
    }

    private fun onChipDrag(flow: KakoFlowLayout, event: DragEvent): Boolean {
        val chip = event.localState as? View ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return chip.parent === flow
            DragEvent.ACTION_DRAG_LOCATION -> {
                // Moving the chip in the tree does not end the drag: the shadow drives it.
                val target = flow.indexAt(event.x, event.y)
                val current = flow.indexOfChild(chip)
                if (target >= 0 && target != current) {
                    flow.removeView(chip)
                    flow.addView(chip, target)
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                chip.alpha = 1f
                val context = context ?: return true
                val dropped = flow.children.mapNotNull { it.tag as? KakoPageSection }.toList()
                if (dropped != KakoPageState.order(context)) {
                    KakoPageState.setOrder(context, dropped)
                    // The sections below follow the chips; fold state is kept by key.
                    flow.post { if (isAdded) rebuild() }
                }
            }
        }
        return true
    }

    private fun jumpTo(section: KakoPageSection) {
        sectionOpeners[section]?.invoke()
        val view = sectionViews[section] ?: return
        scroll.post { scroll.smoothScrollTo(0, view.top) }
    }

    // Sections

    /**
     * One folding section: the thin divider (kept whether the section is folded or open), the
     * heading with its icon, and the rows, which are built only when the section is first opened.
     */
    private fun addSection(section: KakoPageSection) {
        val context = requireContext()
        var open = KakoPageState.isOpen(context, section)
        var filled = false
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6f))
        }
        val chevron = TextView(context).apply {
            setTextColor(c(KakoSlot.PAGE_HEADING))
            textSize = SECTION_TEXT_SIZE_SP
        }

        fun show() {
            if (open && !filled) {
                body = rows
                fillSection(section)
                filled = true
            }
            rows.visibility = if (open) View.VISIBLE else View.GONE
            chevron.text = if (open) CHEVRON_OPEN else CHEVRON_FOLDED
        }

        val title = TextView(context).apply {
            text = getString(section.labelRes)
            setTextColor(c(KakoSlot.PAGE_HEADING))
            textSize = SECTION_TEXT_SIZE_SP
            setTypeface(typeface, Typeface.BOLD)
        }
        val iconSize = (title.textSize * ICON_SCALE).toInt()
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(PAGE_START_DP), dp(7f), dp(END_MARGIN_DP), dp(5f))
            addView(
                ImageView(context).apply {
                    setImageDrawable(tinted(section.iconRes, c(section.colour)))
                },
                LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = iconSize / 2 },
            )
            // The underline is a match_parent view in a wrap_content column, so it is as wide as the title.
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title)
                    addView(
                        View(context).apply { setBackgroundColor(c(KakoSlot.PAGE_HEADING)) },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2.5f)).apply {
                            topMargin = dp(2f)
                        },
                    )
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(chevron)
            setOnClickListener {
                open = !open
                KakoPageState.setOpen(context, section, open)
                show()
            }
        }

        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                View(context).apply { setBackgroundColor(c(KakoSlot.PAGE_DIVIDER)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1),
            )
            addView(header)
            addView(rows)
        }
        sectionViews[section] = wrap
        sectionOpeners[section] = {
            if (!open) {
                open = true
                KakoPageState.setOpen(context, section, true)
                show()
            }
        }
        show()
        holder.addView(wrap)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun fillSection(section: KakoPageSection) {
        when (section) {
            KakoPageSection.EXIM -> fillExim()
            KakoPageSection.FOUNDATION -> {
                addMasterSwitch()
                addRoleRows(section)
            }
            KakoPageSection.TEXT -> {
                addSubgroupHeader(getString(R.string.kako_subgroup_font))
                addFontRows()
                addSubgroupHeader(getString(R.string.kako_subgroup_colors))
                addRoleRows(section, level = 2)
            }
            KakoPageSection.TOOLBAR -> {
                addSubgroupHeader(getString(R.string.kako_subgroup_layout))
                addToolbarTwoRowsSwitch()
                addExtensionIconSizeRow()
                addExtensionOrderRow()
                addSubgroupHeader(getString(R.string.kako_subgroup_colors))
                addRoleRows(section, level = 2)
                addDimenRows(section)
            }
            KakoPageSection.MENU,
            KakoPageSection.TABS -> {
                addSubgroupHeader(getString(R.string.kako_subgroup_colors))
                addRoleRows(section, level = 2)
                addDimenRows(section)
            }
            KakoPageSection.MAINTENANCE -> fillMaintenance()
            else -> addRoleRows(section)
        }
    }

    /** A swatch row for every colour role that belongs to [section]. */
    private fun addRoleRows(section: KakoPageSection, level: Int = 1) {
        val roles = section.roles ?: return
        KakoSlot.entries.filter { it.section == roles }.forEach { addColorRow(it, level) }
    }

    private fun addDimenRows(section: KakoPageSection) {
        val dimens = KakoDimen.entries.filter { it.section == section.roles }
        if (dimens.isEmpty()) return
        addSubgroupHeader(getString(R.string.kako_subgroup_widths))
        dimens.forEach { addDimenRow(it) }
    }

    // Export / Import — the Kōjiki flow.

    private fun fillExim() {
        val context = requireContext()
        val dirName = KakoExim.exportDir(context)?.name ?: KakoExim.dirUri(context)?.lastPathSegment

        // The latest export in the chosen directory — queried on every page opening.
        val (status, warn) = when {
            KakoExim.exportDir(context) == null -> getString(R.string.kako_eim_warn_nodir) to true
            else -> KakoExim.latestExport(context)?.let {
                getString(R.string.kako_eim_last, KakoExim.fmtTs(it.lastModified())) to false
            } ?: (getString(R.string.kako_eim_warn_none) to true)
        }

        addActionRow(
            label = getString(R.string.kako_eim_heading),
            description = getString(R.string.kako_eim_row_summary),
            iconRes = iconsR.drawable.mozac_ic_share_android_24,
            role = ForkIconRole.SHARE,
            main = true,
            opens = true,
        ) {
            eximDialog = KakoEximDialog(requireContext(), eximHost).also { it.show() }
        }
        addActionRow(
            label = dirName?.let { getString(R.string.kako_eim_dir_named, it) }
                ?: getString(R.string.kako_eim_dir_choose),
            description = status,
            descriptionColor = if (warn) c(KakoSlot.TEXT_ERROR) else c(KakoSlot.NOTE),
            iconRes = iconsR.drawable.mozac_ic_folder_24,
            role = ForkIconRole.VIEW,
            opens = true,
        ) {
            eximHost.pickDirectory()
        }

        addAutomationRows()
    }

    /**
     * The sister-app automation contract's three rows, appended directly below the existing
     * export rows — this is a backup feature, so it lives where backup lives, and every
     * sister app puts them in the same place (never a section of its own).
     *
     * In contract v2 the master switch ships **on** and the token is opt-in, so the token rows
     * are hidden until the second switch asks for them. See [KakoAutomation] for why.
     */
    private fun addAutomationRows() {
        val context = requireContext()
        addSubgroupHeader(getString(R.string.kako_subgroup_automation))

        addSwitchRow(
            label = getString(R.string.kako_automation_switch),
            description = getString(R.string.kako_automation_switch_desc),
            iconRes = iconsR.drawable.mozac_ic_sync_24,
            role = ForkIconRole.CONNECT,
            checked = KakoAutomation.enabled(context),
            level = 2,
        ) { checked ->
            KakoAutomation.setEnabled(context, checked)
            if (checked) promptForAllFilesAccess()
        }

        // Built before the switch that shows and hides them. Hidden unless the token is actually
        // being asked for: a 48-character secret sitting under an off switch invites 白い熊 to
        // paste it somewhere it will do nothing.
        val tokenRows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (KakoAutomation.requireToken(context)) View.VISIBLE else View.GONE
        }

        // Whether a caller must present the token at all. Default OFF — a pasted secret cannot
        // survive the wipe this feature exists to recover from, and the door that actually moves
        // data identifies its caller instead (KakoAutomationCallers).
        addSwitchRow(
            label = getString(R.string.kako_automation_require_token),
            description = getString(R.string.kako_automation_require_token_desc),
            iconRes = iconsR.drawable.mozac_ic_lock_24,
            role = ForkIconRole.BLOCK,
            checked = KakoAutomation.requireToken(context),
            level = 2,
        ) { checked ->
            KakoAutomation.setRequireToken(context, checked)
            tokenRows.visibility = if (checked) View.VISIBLE else View.GONE
        }

        val outer = body
        outer.addView(tokenRows)
        body = tokenRows
        // The token itself: a tap copies the whole secret.
        val tokenValue = addValueRow(
            label = getString(R.string.kako_automation_token_row),
            value = KakoAutomation.abbreviated(KakoAutomation.token(context)),
            iconRes = iconsR.drawable.mozac_ic_copy_24,
            role = ForkIconRole.SHARE,
            valueTypeface = Typeface.MONOSPACE,
            level = 2,
        ) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("token", KakoAutomation.token(context)))
            toast(getString(R.string.kako_automation_token_copied))
        }
        addActionRow(
            label = getString(R.string.kako_automation_regenerate),
            description = getString(R.string.kako_automation_regenerate_desc),
            iconRes = iconsR.drawable.mozac_ic_arrow_clockwise_24,
            role = ForkIconRole.DELETE,
            destructive = true,
            level = 2,
        ) {
            val fresh = KakoAutomation.regenerateToken(context)
            tokenValue.text = KakoAutomation.abbreviated(fresh)
            toast(getString(R.string.kako_automation_regenerated), long = true)
        }
        body = outer
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

    // Maintenance

    private fun fillMaintenance() {
        val context = requireContext()
        addActionRow(
            label = getString(R.string.kako_reset_section_order),
            description = getString(R.string.kako_reset_section_order_desc),
            iconRes = iconsR.drawable.mozac_ic_arrow_counter_clockwise_24,
            role = ForkIconRole.CONNECT,
        ) {
            KakoPageState.resetOrder(context)
            rebuild()
        }
        addActionRow(
            label = getString(R.string.kako_reset_colors),
            description = getString(R.string.kako_reset_colors_desc),
            iconRes = iconsR.drawable.mozac_ic_themes_24,
            role = ForkIconRole.CONNECT,
        ) {
            confirm(getString(R.string.kako_reset_colors_message)) {
                KakoTheme.resetAllColors(context)
                rebuild()
            }
        }
        addActionRow(
            label = getString(R.string.kako_reset),
            description = getString(R.string.kako_reset_desc),
            iconRes = iconsR.drawable.mozac_ic_delete_24,
            role = ForkIconRole.DELETE,
            destructive = true,
        ) {
            confirm(getString(R.string.kako_reset_message), deleting = true) {
                KakoTheme.resetAll(context)
                KakoFonts.refresh(context)
                rebuild()
            }
        }
    }

    private fun confirm(message: String, deleting: Boolean = false, onConfirmed: () -> Unit) {
        val dialog =
            AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed() }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .applyKakoBorder()
        // A dialog that wipes confirms with a red main pill.
        if (deleting) dialog.kakoDeleting()
        dialog.show()
    }

    // The rows of the sections

    private fun addMasterSwitch() {
        val context = requireContext()
        addSwitchRow(
            label = getString(R.string.kako_master_switch),
            description = getString(R.string.kako_master_switch_desc),
            iconRes = iconsR.drawable.mozac_ic_themes_24,
            role = ForkIconRole.VIEW,
            checked = KakoTheme.isEnabled(context),
        ) { checked ->
            KakoTheme.setEnabled(context, checked)
            KakoFonts.refresh(context)
            rebuild()
        }
    }

    private fun addToolbarTwoRowsSwitch() {
        val context = requireContext()
        addSwitchRow(
            label = getString(R.string.kako_toolbar_two_rows),
            description = getString(R.string.kako_toolbar_two_rows_desc),
            iconRes = iconsR.drawable.mozac_ic_app_menu_24,
            role = ForkIconRole.VIEW,
            checked = KakoTheme.toolbarTwoRows(context),
            level = 2,
        ) { checked ->
            KakoTheme.setToolbarTwoRows(context, checked)
        }
    }

    private fun addFontRows() {
        val context = requireContext()

        addValueRow(
            label = getString(R.string.kako_font_row),
            value = KakoFonts.fontDisplayName(context, KakoFonts.fontFamily(context)),
            iconRes = iconsR.drawable.mozac_ic_text_size_24,
            role = ForkIconRole.MESSAGE,
            valueTypeface = KakoFonts.fontTypeface(context, KakoFonts.fontFamily(context)),
            level = 2,
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
            iconRes = iconsR.drawable.mozac_ic_edit_24,
            role = ForkIconRole.MESSAGE,
            valueTypeface = KakoFonts.selectedTypeface(context),
            level = 2,
        ) {
            KakoFontWeightPickerDialog(context) { picked ->
                KakoFonts.setFontWeight(context, picked)
                rebuild()
            }.show()
        }

        addSliderRow(
            label = getString(R.string.kako_font_scale_row),
            iconRes = iconsR.drawable.mozac_ic_text_size_24,
            role = ForkIconRole.MESSAGE,
            max = FONT_SCALE_MAX - FONT_SCALE_MIN,
            progress = KakoFonts.fontScale(context) - FONT_SCALE_MIN,
            valueText = { getString(R.string.kako_font_scale_value, it + FONT_SCALE_MIN) },
        ) { value ->
            KakoFonts.setFontScale(context, value + FONT_SCALE_MIN)
            rebuild()
        }

        // The sample line.
        body.addView(
            TextView(context).apply {
                text = getString(R.string.kako_font_sample)
                typeface = KakoFonts.selectedTypeface(context)
                setTextColor(c(KakoSlot.TEXT))
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    SAMPLE_TEXT_SIZE_SP * KakoFonts.fontScale(context) / PERCENT,
                )
                setPaddingRelative(indentPx(level = 2), dp(ROW_GAP_DP), dp(END_MARGIN_DP), dp(4f))
            },
        )
    }

    private fun addDimenRow(dimen: KakoDimen) {
        val context = requireContext()
        // The slider works in half-dp steps (0.5dp minimum visible thickness).
        addSliderRow(
            label = getString(dimen.labelRes),
            iconRes = iconsR.drawable.mozac_ic_subtract_24,
            role = ForkIconRole.VIEW,
            max = DIMEN_MAX_DP * 2,
            progress = KakoTheme.dimenHalfUnits(context, dimen),
            valueText = { getString(R.string.kako_dimen_value, it / 2f) },
        ) { value ->
            KakoTheme.setDimenHalfUnits(context, dimen, value)
        }
    }

    private fun addExtensionIconSizeRow() {
        val context = requireContext()
        addSliderRow(
            label = getString(R.string.kako_extension_icon_size_row),
            iconRes = iconsR.drawable.mozac_ic_extension_24,
            role = ForkIconRole.VIEW,
            max = EXTENSION_ICON_MAX_DP - EXTENSION_ICON_MIN_DP,
            progress = KakoTheme.extensionIconSizeDp(context) - EXTENSION_ICON_MIN_DP,
            valueText = { getString(R.string.kako_extension_icon_size_value, it + EXTENSION_ICON_MIN_DP) },
        ) { value ->
            KakoTheme.setExtensionIconSizeDp(context, value + EXTENSION_ICON_MIN_DP)
        }
    }

    private fun addExtensionOrderRow() {
        val context = requireContext()
        val ids = context.components.settings.toolbarPinnedExtensions.split(",").filter { it.isNotEmpty() }
        addValueRow(
            label = getString(R.string.kako_extension_order_row),
            value = ids.size.toString(),
            iconRes = iconsR.drawable.mozac_ic_extension_cog_24,
            role = ForkIconRole.VIEW,
            description = getString(R.string.kako_extension_order_desc),
            level = 2,
        ) {
            val extensions = requireComponents.core.store.state.extensions
            val entries = ids.map { id -> id to (extensions[id]?.name ?: id) }
            KakoExtensionOrderDialog(context, entries) { newOrder ->
                context.components.settings.toolbarPinnedExtensions = newOrder.joinToString(",")
                rebuild()
            }.show()
        }
    }

    // Row kinds

    /**
     * A sub-heading inside a section: smaller bold title, underlined as wide as its text; its
     * rows are indented one further step.
     */
    private fun addSubgroupHeader(label: String) {
        val context = requireContext()
        val heading = c(KakoSlot.PAGE_HEADING)
        body.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(level = 1), dp(8f), 0, dp(2f))
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(heading)
                        textSize = SUBGROUP_TEXT_SIZE_SP
                        setTypeface(typeface, Typeface.BOLD)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    View(context).apply { setBackgroundColor(heading) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1.5f)).apply {
                        topMargin = dp(2f)
                    },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun label(text: String, @DrawableRes iconRes: Int, role: ForkIconRole, bold: Boolean): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(c(KakoSlot.TEXT))
            textSize = ITEM_TEXT_SIZE_SP
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setKakoLabelIcon(iconRes, icon(role))
        }

    private fun note(text: String, color: Int = c(KakoSlot.NOTE)): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(color)
            textSize = NOTE_TEXT_SIZE_SP
        }

    /**
     * Wraps the content of a row that carries an explanation: a thin rule in the note colour down
     * the whole item's left edge, a short gap, then the content.
     */
    private fun ruled(content: View, level: Int): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(indentPx(level) - dp(RULE_WIDTH_DP + RULE_GAP_DP), dp(ROW_GAP_DP), dp(END_MARGIN_DP), dp(ROW_GAP_DP))
            addView(
                View(context).apply { setBackgroundColor(c(KakoSlot.PAGE_RULE)) },
                LinearLayout.LayoutParams(dp(RULE_WIDTH_DP), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(RULE_GAP_DP)
                },
            )
            addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

    /**
     * A row that does something when tapped: an outlined pill that hugs its label, the row's icon
     * inside it at the left, a › when it opens something, and its explanation beneath, aligned
     * with the label. The main action of a section is the thick pill with a bold label.
     */
    @Suppress("LongParameterList")
    private fun addActionRow(
        label: String,
        description: String?,
        @DrawableRes iconRes: Int,
        role: ForkIconRole,
        main: Boolean = false,
        opens: Boolean = false,
        destructive: Boolean = false,
        level: Int = 1,
        descriptionColor: Int = c(KakoSlot.NOTE),
        onClick: () -> Unit,
    ) {
        val context = requireContext()
        val pill = TextView(context).apply {
            text = if (opens) "$label  $CHEVRON_FOLDED" else label
            textSize = ITEM_TEXT_SIZE_SP
            if (main) setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.kakoPillInk(main, destructive))
            background = context.kakoPillBackground(main, destructive)
            setPadding(dp(PILL_PAD_X_DP), dp(PILL_PAD_Y_DP), dp(PILL_PAD_X_DP + 2f), dp(PILL_PAD_Y_DP))
            setKakoLabelIcon(iconRes, if (destructive) c(KakoSlot.BUTTON_DELETE) else icon(role))
            setOnClickListener { onClick() }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                pill,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(PILL_GAP_DP)
                    bottomMargin = dp(PILL_GAP_DP)
                },
            )
            if (description != null) {
                // Aligned with the label's text, not with the pill's edge.
                addView(
                    note(description, descriptionColor).apply {
                        setPaddingRelative(dp(PILL_PAD_X_DP) + pill.kakoLabelIconGutter(), 0, 0, dp(2f))
                    },
                )
            }
        }
        if (description != null) {
            body.addView(ruled(column, level))
        } else {
            column.setPaddingRelative(indentPx(level), 0, dp(END_MARGIN_DP), 0)
            body.addView(column)
        }
    }

    /**
     * A row with a switch: bold label with its icon, the explanation beneath, the outlined switch
     * at the right. The whole row toggles.
     */
    @Suppress("LongParameterList")
    private fun addSwitchRow(
        label: String,
        description: String?,
        @DrawableRes iconRes: Int,
        role: ForkIconRole,
        checked: Boolean,
        level: Int = 1,
        onChanged: (Boolean) -> Unit,
    ) {
        val context = requireContext()
        val toggle = KakoSwitchView(context).apply {
            isChecked = checked
            onCheckedChange = onChanged
        }
        val title = label(label, iconRes, role, bold = description != null)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title)
                    if (description != null) {
                        addView(note(description).apply { setPaddingRelative(title.kakoLabelIconGutter(), dp(1f), 0, 0) })
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8f)
                },
            )
            addView(toggle)
            setOnClickListener { toggle.toggle() }
        }
        if (description != null) {
            body.addView(ruled(row, level))
        } else {
            row.setPaddingRelative(indentPx(level), dp(ROW_GAP_DP), dp(END_MARGIN_DP), dp(ROW_GAP_DP))
            body.addView(row)
        }
    }

    /**
     * A row whose value opens a picker: plain, the value at the right. Returns the value's view,
     * for a row whose value changes without the page being rebuilt.
     */
    @Suppress("LongParameterList")
    private fun addValueRow(
        label: String,
        value: String,
        @DrawableRes iconRes: Int,
        role: ForkIconRole,
        valueTypeface: Typeface? = null,
        description: String? = null,
        level: Int = 1,
        onClick: () -> Unit,
    ): TextView {
        val context = requireContext()
        val valueView = TextView(context).apply {
            text = value
            valueTypeface?.let { typeface = it }
            setTextColor(c(KakoSlot.TEXT_SECONDARY))
            textSize = NOTE_TEXT_SIZE_SP
        }
        val title = label(label, iconRes, role, bold = description != null)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title)
                    if (description != null) {
                        addView(note(description).apply { setPaddingRelative(title.kakoLabelIconGutter(), dp(1f), 0, 0) })
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8f)
                },
            )
            addView(valueView)
            setOnClickListener { onClick() }
        }
        if (description != null) {
            body.addView(ruled(row, level))
        } else {
            row.setPaddingRelative(indentPx(level), dp(ROW_GAP_DP), dp(END_MARGIN_DP), dp(ROW_GAP_DP))
            body.addView(row)
        }
        return valueView
    }

    /** A colour role: its label, a mark when it is overridden, and its swatch. A tap opens the picker. */
    private fun addColorRow(slot: KakoSlot, level: Int) {
        val context = requireContext()
        body.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(indentPx(level), dp(ROW_GAP_DP), dp(END_MARGIN_DP), dp(ROW_GAP_DP))
                addView(
                    label(
                        text = buildString {
                            append(getString(slot.labelRes))
                            if (KakoTheme.isOverridden(context, slot)) append("  ●")
                        },
                        iconRes = iconsR.drawable.mozac_ic_themes_24,
                        role = ForkIconRole.VIEW,
                        bold = false,
                    ),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(8f)
                    },
                )
                addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4f).toFloat()
                            setColor(KakoTheme.color(context, slot))
                            setStroke(dp(1.5f), c(KakoSlot.BORDER))
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

    /** A slider with its label and current value above it. [onStop] gets the value the finger left it at. */
    @Suppress("LongParameterList")
    private fun addSliderRow(
        label: String,
        @DrawableRes iconRes: Int,
        role: ForkIconRole,
        max: Int,
        progress: Int,
        valueText: (Int) -> String,
        level: Int = 2,
        onStop: (Int) -> Unit,
    ) {
        val context = requireContext()
        val valueView = TextView(context).apply {
            text = valueText(progress)
            setTextColor(c(KakoSlot.TEXT_SECONDARY))
            textSize = NOTE_TEXT_SIZE_SP
        }
        body.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(indentPx(level), dp(ROW_GAP_DP), dp(END_MARGIN_DP), dp(ROW_GAP_DP))
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            label(label, iconRes, role, bold = false),
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(valueView)
                    },
                )
                addView(
                    SeekBar(context).apply {
                        this.max = max
                        this.progress = progress
                        progressTintList = ColorStateList.valueOf(c(KakoSlot.SLIDER_ACTIVE))
                        thumbTintList = ColorStateList.valueOf(c(KakoSlot.SLIDER_ACTIVE))
                        progressBackgroundTintList = ColorStateList.valueOf(c(KakoSlot.SLIDER_INACTIVE))
                        setOnSeekBarChangeListener(
                            object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                                    valueView.text = valueText(value)
                                }

                                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                                override fun onStopTrackingTouch(bar: SeekBar?) {
                                    onStop(bar?.progress ?: progress)
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    private fun tinted(@DrawableRes iconRes: Int, color: Int) =
        AppCompatResources.getDrawable(requireContext(), iconRes)?.mutate()?.apply { setTint(color) }

    // Geometry: the heading at the page's start, rows one step in, rows under a sub-heading two.

    private fun dp(value: Float): Int = requireContext().kakoDp(value)

    private fun indentPx(level: Int): Int = dp(PAGE_START_DP + level * INDENT_STEP_DP)

    internal companion object {
        const val PAGE_START_DP = 14f
        const val INDENT_STEP_DP = 14f
        const val END_MARGIN_DP = 16f
        const val ROW_GAP_DP = 2f
        const val RULE_WIDTH_DP = 2f
        const val RULE_GAP_DP = 10f
        const val PILL_PAD_X_DP = 14f
        const val PILL_PAD_Y_DP = 9f
        const val PILL_GAP_DP = 3f
        const val CHIP_RADIUS_DP = 100f
        const val SWATCH_SIZE_DP = 28f
        const val SECTION_TEXT_SIZE_SP = 20f
        const val SUBGROUP_TEXT_SIZE_SP = 17f
        const val ITEM_TEXT_SIZE_SP = 16f
        const val NOTE_TEXT_SIZE_SP = 13f
        const val CHIP_TEXT_SIZE_SP = 13f
        const val SAMPLE_TEXT_SIZE_SP = 18f
        const val ICON_SCALE = 1.25f
        const val DRAGGED_ALPHA = 0.35f
        const val PERCENT = 100f
        const val FONT_SCALE_MIN = 70
        const val FONT_SCALE_MAX = 160
        const val EXTENSION_ICON_MIN_DP = 16
        const val EXTENSION_ICON_MAX_DP = 48
        const val DIMEN_MAX_DP = 8
        const val CHEVRON_FOLDED = "›"
        const val CHEVRON_OPEN = "⌄"
    }
}
