/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.compose.base.theme.ForkIconRole
import org.mozilla.fenix.R
import kotlin.system.exitProcess
import mozilla.components.ui.icons.R as iconsR

/**
 * The Export/Import panel of the 白い熊 火狐 UI page, Kōjiki-flow with the
 * ArcaneChat button line: an alert dialog whose Cancel sits alone on the left
 * (the neutral slot) while the Import and Export pills group on the right.
 * Export is the main pill of the panel (3 dp, bold); Import and Cancel are regular.
 * Every colour is a role; the category boxes are selection marks and stay marks.
 *
 * Success chains close everything beneath them — the info dialog, this panel,
 * and the UI page itself (via [Host.closePage]); failures toast and leave the
 * panel open so the selection can be retried.
 */
class KakoEximDialog(
    private val context: Context,
    private val host: Host,
) {

    /** The owning fragment: SAF launchers, coroutine scope, and page closing. */
    interface Host {
        val scope: CoroutineScope
        fun pickDirectory()
        fun launchImportPicker()
        fun launchExportSaver(fileName: String)
        fun closePage()
    }

    private lateinit var dialog: AlertDialog
    private val checks = LinkedHashMap<KakoExim.Cat, CheckBox>()
    private var directoryValueTv: TextView? = null
    private var statusTv: TextView? = null

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    fun show() {
        val text = KakoTheme.color(context, KakoSlot.DIALOG_TEXT)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(4))
        }

        root.addView(
            TextView(context).apply {
                this.text = context.getString(R.string.kako_eim_title)
                setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TITLE))
                setTypeface(typeface, Typeface.BOLD)
                textSize = 18f
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        root.addView(
            TextView(context).apply {
                this.text = context.getString(R.string.kako_eim_desc)
                setTextColor(KakoTheme.color(context, KakoSlot.NOTE))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(6))
            },
        )

        root.addView(
            buildDirBox(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6); bottomMargin = dp(2) },
        )
        statusTv = TextView(context).apply {
            textSize = 14f
            setPadding(dp(2), dp(2), 0, dp(8))
        }
        root.addView(statusTv)

        root.addView(divider(), lpMatch())

        val selectAll = CheckBox(context).apply {
            this.text = context.getString(R.string.kako_eim_select_all)
            setTextColor(text)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
            isChecked = KakoExim.Cat.entries.all { it.defaultOn }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(2), 0, dp(2))
        }
        KakoPills.check(selectAll)
        root.addView(selectAll)

        // Seeded from the same [KakoExim.Cat.defaultOn] the automation contract's
        // LIST_CATEGORIES reply sends, so this sheet and a sister app's picker open
        // on the same answer.
        KakoExim.Cat.entries.forEach { cat ->
            val box = CheckBox(context).apply {
                this.text = context.getString(cat.labelRes)
                setTextColor(text)
                textSize = 15f
                isChecked = cat.defaultOn
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(8), dp(2), 0, dp(2))
            }
            KakoPills.check(box)
            checks[cat] = box
            root.addView(box)
            if (cat.sensitive) {
                root.addView(
                    TextView(context).apply {
                        this.text = context.getString(R.string.kako_eim_plaintext_warning)
                        setTextColor(KakoExim.warnColor(context))
                        textSize = 12f
                        setPadding(dp(38), 0, 0, dp(4))
                    },
                )
            }
        }
        selectAll.setOnCheckedChangeListener { _, checked ->
            checks.values.forEach { it.isChecked = checked }
        }

        root.addView(divider(), lpMatch().apply { topMargin = dp(8) })

        dialog = AlertDialog.Builder(context)
            .setView(ScrollView(context).apply { addView(root) })
            .setPositiveButton(R.string.kako_eim_export, null)
            .setNegativeButton(R.string.kako_eim_import, null)
            .setNeutralButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setBackgroundDrawable(context.kakoDialogBackground())
        dialog.show()
        // Export, the positive button, is the main pill; Import and Cancel are regular ones.
        KakoPills.dialogButtons(dialog)

        // Claim the action buttons after show() so they do not auto-dismiss the panel.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { onExportClicked() }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { onImportClicked() }

        refreshStatus()
    }

    fun dismiss() {
        if (::dialog.isInitialized) dialog.dismiss()
    }

    // The bordered, tappable export-directory box (Kōjiki style).

    private fun buildDirBox(): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            // A row that opens the directory picker: a regular frame, the ground showing.
            background = KakoPills.pill(
                context,
                main = false,
                frame = KakoTheme.color(context, KakoSlot.BUTTON_BORDER),
                ground = null,
                radiusDp = DIR_BOX_RADIUS_DP,
            )
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { host.pickDirectory() }
        }
        val caption = TextView(context).apply {
            text = context.getString(R.string.kako_eim_dir)
            setTextColor(KakoTheme.color(context, KakoSlot.NOTE))
            textSize = 12f
        }
        box.addView(caption)
        directoryValueTv = TextView(context).apply {
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
        }.also {
            // Upstream's drawable for a directory carries the word in its name.
            KakoRowIcons.setLabelIcon(
                it,
                iconsR.drawable.mozac_ic_folder_24,
                KakoTheme.iconColor(context, ForkIconRole.SHARE),
            )
        }
        box.addView(directoryValueTv)
        return box
    }

    /** Re-reads the directory + latest-export state into the box and status line. */
    fun refreshStatus() {
        val warning = KakoExim.warnColor(context)
        val name = KakoExim.exportDir(context)?.name ?: KakoExim.dirUri(context)?.lastPathSegment
        directoryValueTv?.text = name ?: context.getString(R.string.kako_eim_dir_unset)
        directoryValueTv?.setTextColor(if (name == null) warning else KakoTheme.color(context, KakoSlot.DIALOG_TEXT))

        val (msg, warn) = lastExportStatus()
        statusTv?.text = msg
        statusTv?.setTextColor(if (warn) warning else KakoTheme.color(context, KakoSlot.NOTE))
    }

    private fun lastExportStatus(): Pair<String, Boolean> {
        KakoExim.exportDir(context) ?: return context.getString(R.string.kako_eim_warn_nodir) to true
        val newest = KakoExim.latestExport(context)
            ?: return context.getString(R.string.kako_eim_warn_none) to true
        return context.getString(R.string.kako_eim_last, KakoExim.fmtTs(newest.lastModified())) to false
    }

    // Export

    private fun selected(): Set<KakoExim.Cat> = checks.filterValues { it.isChecked }.keys

    private fun onExportClicked() {
        if (selected().isEmpty()) {
            toast(context.getString(R.string.kako_eim_none_selected))
            return
        }
        val dir = KakoExim.exportDir(context)
        if (dir == null) {
            host.launchExportSaver(KakoExim.exportFileName())
        } else {
            val cats = selected()
            val name = KakoExim.exportFileName()
            host.scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = dir.createFile("application/zip", name)
                            ?: error(context.getString(R.string.kako_eim_err_create))
                        context.contentResolver.openOutputStream(file.uri)?.use { os ->
                            KakoExim.export(context, cats, os)
                        } ?: error(context.getString(R.string.kako_eim_err_write))
                        name
                    }
                }
                result.fold(
                    onSuccess = { fileName ->
                        refreshStatus()
                        showExportDone(fileName)
                    },
                    onFailure = { e ->
                        toast(context.getString(R.string.kako_eim_export_fail, e.message ?: ""))
                    },
                )
            }
        }
    }

    /** Save-as fallback target picked when no directory is set. */
    fun onExportTarget(uri: Uri) {
        val cats = selected()
        if (cats.isEmpty()) return
        host.scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        KakoExim.export(context, cats, os)
                    } ?: error(context.getString(R.string.kako_eim_err_write))
                }
            }
            result.fold(
                onSuccess = { summary -> showExportDone(summary) },
                onFailure = { e ->
                    toast(context.getString(R.string.kako_eim_export_fail, e.message ?: ""))
                },
            )
        }
    }

    // Import

    private fun onImportClicked() {
        if (selected().isEmpty()) {
            toast(context.getString(R.string.kako_eim_none_selected))
            return
        }
        host.launchImportPicker()
    }

    fun onImportPicked(uri: Uri) {
        val cats = selected()
        // Restoring extensions re-downloads them from AMO — this is not instant.
        toast(context.getString(R.string.kako_eim_importing))
        host.scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Opened twice, and never read whole: an archive carrying the extension
                    // databases is gigabytes, so the check reads entry names on one pass and the
                    // import streams on another rather than both sharing one array in memory.
                    val present = context.contentResolver.openInputStream(uri)?.use {
                        KakoExim.categoriesIn(it)
                    } ?: error(context.getString(R.string.kako_eim_err_read))
                    require(present.isNotEmpty()) {
                        context.getString(R.string.kako_eim_import_none)
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        KakoExim.import(context, it, cats)
                    } ?: error(context.getString(R.string.kako_eim_err_read))
                }
            }
            result.fold(
                onSuccess = { summary -> showImportDone(summary) },
                onFailure = { e ->
                    toast(context.getString(R.string.kako_eim_import_fail, e.message ?: ""), long = true)
                },
            )
        }
    }

    // Result dialogs — black panels ringed in the kako yellow border. Acknowledging
    // success closes the whole chain: info dialog, this panel, and the UI page.

    private fun showExportDone(fileName: String) {
        val done = AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.kako_eim_export_ok, fileName))
            .setPositiveButton(android.R.string.ok, null)
            .create()
        done.window?.setBackgroundDrawable(context.kakoDialogBackground())
        done.show()
        KakoPills.dialogButtons(done)
        done.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TEXT))
        done.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            done.dismiss()
            dismiss()
            host.closePage()
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun showImportDone(summary: String) {
        val done = AlertDialog.Builder(context)
            .setTitle(R.string.kako_eim_import_done_title)
            .setMessage(context.getString(R.string.kako_eim_import_done_body, summary))
            .setPositiveButton(R.string.kako_eim_restart_now, null)
            .setNegativeButton(R.string.kako_eim_restart_later, null)
            .setCancelable(false)
            .create()
        done.window?.setBackgroundDrawable(context.kakoDialogBackground())
        done.show()
        KakoPills.dialogButtons(done)
        done.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TEXT))
        // The platform's alert title has no public id.
        val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) {
            done.findViewById<TextView>(titleId)?.setTextColor(KakoTheme.color(context, KakoSlot.DIALOG_TITLE))
        }
        done.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { restartApp() }
        done.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            done.dismiss()
            dismiss()
            host.closePage()
        }
    }

    /** Relaunch the launcher activity as a fresh task, then hard-kill the process. */
    private fun restartApp() {
        val appContext = context.applicationContext
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        appContext.startActivity(Intent.makeRestartActivityTask(intent.component))
        exitProcess(0)
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(KakoTheme.color(context, KakoSlot.LIST_DIVIDER))
    }

    private fun lpMatch() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(1),
    )

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val DIR_BOX_RADIUS_DP = 10f
    }
}
