/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.fenix.R
import kotlin.system.exitProcess

/**
 * The Export/Import panel of the 白い熊 火狐 UI page, Kōjiki-flow with the
 * ArcaneChat button line: an alert dialog whose Cancel sits alone on the left
 * (the neutral slot) while the Import and Export pills group on the right.
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
    private var folderValueTv: TextView? = null
    private var statusTv: TextView? = null

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    fun show() {
        val text = KakoTheme.color(context, KakoSlot.TEXT)
        val dim = KakoTheme.color(context, KakoSlot.TEXT_SECONDARY)
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(4))
        }

        root.addView(
            TextView(context).apply {
                this.text = context.getString(R.string.kako_eim_title)
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
                textSize = 18f
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        root.addView(
            TextView(context).apply {
                this.text = context.getString(R.string.kako_eim_desc)
                setTextColor(text)
                alpha = 0.85f
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
            buttonTintList = ColorStateList.valueOf(accent)
            isChecked = true
            setPadding(dp(8), dp(7), 0, dp(7))
        }
        root.addView(selectAll)

        KakoExim.Cat.entries.forEach { cat ->
            val box = CheckBox(context).apply {
                this.text = context.getString(cat.labelRes)
                setTextColor(text)
                textSize = 15f
                buttonTintList = ColorStateList.valueOf(accent)
                isChecked = true
                setPadding(dp(8), dp(7), 0, dp(7))
            }
            checks[cat] = box
            root.addView(box)
            if (cat.sensitive) {
                root.addView(
                    TextView(context).apply {
                        this.text = context.getString(R.string.kako_eim_plaintext_warning)
                        setTextColor(KakoExim.WARN_COLOR)
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
        stylePills(dialog)

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
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(KakoTheme.color(context, KakoSlot.MENU_BACKGROUND))
                setStroke(dp(2), accent)
            }
            setOnClickListener { host.pickDirectory() }
        }
        box.addView(
            TextView(context).apply {
                text = context.getString(R.string.kako_eim_dir)
                setTextColor(accent)
                textSize = 12f
            },
        )
        folderValueTv = TextView(context).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }
        box.addView(folderValueTv)
        return box
    }

    /** Re-reads the directory + latest-export state into the box and status line. */
    fun refreshStatus() {
        val text = KakoTheme.color(context, KakoSlot.TEXT)
        val name = KakoExim.exportDir(context)?.name ?: KakoExim.dirUri(context)?.lastPathSegment
        folderValueTv?.text = name ?: context.getString(R.string.kako_eim_dir_unset)
        folderValueTv?.setTextColor(if (name == null) KakoExim.WARN_COLOR else text)

        val (msg, warn) = lastExportStatus()
        statusTv?.text = msg
        statusTv?.setTextColor(if (warn) KakoExim.WARN_COLOR else text)
        statusTv?.alpha = if (warn) 1f else 0.8f
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
            host.launchExportSaver(KakoExim.exportFileName(context))
        } else {
            val cats = selected()
            val name = KakoExim.exportFileName(context)
            host.scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = dir.createFile("application/zip", name)
                            ?: error("could not create file in folder")
                        context.contentResolver.openOutputStream(file.uri)?.use { os ->
                            KakoExim.export(context, cats, os)
                        } ?: error("no output stream")
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
                    } ?: error("no output stream")
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
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("no input stream")
                    require(KakoExim.categoriesIn(bytes).isNotEmpty()) {
                        context.getString(R.string.kako_eim_import_none)
                    }
                    KakoExim.import(context, bytes, cats)
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
        stylePills(done)
        done.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(KakoTheme.color(context, KakoSlot.ACCENT))
        done.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            done.dismiss()
            dismiss()
            host.closePage()
        }
    }

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
        stylePills(done)
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        done.findViewById<TextView>(android.R.id.message)?.setTextColor(accent)
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

    // ArcaneChat pill styling for every button of an alert dialog: black stadium
    // pills with an accent stroke; Cancel stays left in the neutral slot while the
    // built-in weighted spacer pushes the action buttons right.

    private fun stylePills(target: AlertDialog) {
        val accent = KakoTheme.color(context, KakoSlot.ACCENT)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .mapNotNull { target.getButton(it) }
            .forEach { button -> button.stylePill(accent) }
    }

    private fun Button.stylePill(accent: Int) {
        val density = context.resources.displayMetrics.density
        val pill = GradientDrawable().apply {
            setColor(KakoTheme.color(context, KakoSlot.BUTTON_BACKGROUND))
            cornerRadius = dp(50).toFloat()
            setStroke((1.5f * density).toInt(), accent)
        }
        background = RippleDrawable(
            ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
            pill,
            null,
        )
        setTextColor(accent)
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0
        setPadding(dp(20), dp(6), dp(20), dp(6))
        (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = dp(8)
            layoutParams = it
        }
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(KakoTheme.color(context, KakoSlot.ACCENT))
        alpha = 0.4f
    }

    private fun lpMatch() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(1),
    )

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
