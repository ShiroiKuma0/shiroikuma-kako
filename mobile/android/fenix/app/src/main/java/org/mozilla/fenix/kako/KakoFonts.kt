/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import mozilla.components.compose.base.theme.AcornTypography
import mozilla.components.compose.base.theme.acornTypographyOverride
import mozilla.components.compose.base.theme.defaultTypography
import org.mozilla.fenix.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * External font support, sister-repo style: ttf/otf files live in the app-private
 * fonts directory, the picker renders every option in its own glyphs, and the
 * chosen family/weight/size scale is applied to the whole Compose typography.
 */

const val KAKO_MONOSPACE_FONT = "@monospace"
private val FONT_EXTENSIONS = setOf("ttf", "otf")
private const val DEFAULT_FONT_SCALE = 100

/** One pickable font: [displayName] for the row label, [fileName] as the stored value. */
data class KakoFontOption(val displayName: String, val fileName: String)

private val typefaceCache = ConcurrentHashMap<String, Typeface>()

object KakoFonts {

    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    fun availableFontOptions(context: Context): List<KakoFontOption> {
        val options = mutableListOf(
            KakoFontOption(context.getString(R.string.kako_font_system_default), ""),
            KakoFontOption(context.getString(R.string.kako_font_monospace), KAKO_MONOSPACE_FONT),
        )
        fontsDir(context).listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in FONT_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { options.add(KakoFontOption(it.nameWithoutExtension, it.name)) }
        return options
    }

    fun fontDisplayName(context: Context, fileName: String): String = when {
        fileName.isEmpty() -> context.getString(R.string.kako_font_system_default)
        fileName == KAKO_MONOSPACE_FONT -> context.getString(R.string.kako_font_monospace)
        else -> File(fileName).nameWithoutExtension
    }

    fun fontTypeface(context: Context, fileName: String): Typeface = when {
        fileName.isEmpty() -> Typeface.DEFAULT
        fileName == KAKO_MONOSPACE_FONT -> Typeface.MONOSPACE
        else -> typefaceCache.getOrPut(fileName) {
            try {
                Typeface.createFromFile(File(fontsDir(context), fileName))
            } catch (e: RuntimeException) {
                Typeface.DEFAULT
            }
        }
    }

    /**
     * Copies the font behind [uri] into the fonts directory.
     * Returns the stored file name, or null if the file is not a ttf/otf.
     */
    fun importFont(context: Context, uri: Uri): String? {
        val name = fontFileName(context, uri) ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in FONT_EXTENSIONS) {
            return null
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        File(fontsDir(context), name).writeBytes(bytes)
        typefaceCache.remove(name)
        return name
    }

    private fun fontFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    // The global font selection backing the Compose typography.

    fun fontFamily(context: Context): String =
        KakoTheme.prefs(context).getString(KAKO_FONT_FAMILY_KEY, "").orEmpty()

    fun setFontFamily(context: Context, fileName: String) {
        KakoTheme.prefs(context).edit { putString(KAKO_FONT_FAMILY_KEY, fileName) }
        refresh(context)
    }

    /** 0 means "inherit each style's own weight". */
    fun fontWeight(context: Context): Int =
        KakoTheme.prefs(context).getInt(KAKO_FONT_WEIGHT_KEY, 0)

    fun setFontWeight(context: Context, weight: Int) {
        KakoTheme.prefs(context).edit { putInt(KAKO_FONT_WEIGHT_KEY, weight) }
        refresh(context)
    }

    /** Percentage applied to every text size; 100 = stock. */
    fun fontScale(context: Context): Int =
        KakoTheme.prefs(context).getInt(KAKO_FONT_SCALE_KEY, DEFAULT_FONT_SCALE)

    fun setFontScale(context: Context, percent: Int) {
        KakoTheme.prefs(context).edit { putInt(KAKO_FONT_SCALE_KEY, percent) }
        refresh(context)
    }

    /** The typeface of the current global selection, for live samples in the UI page. */
    fun selectedTypeface(context: Context): Typeface {
        val base = fontTypeface(context, fontFamily(context))
        val weight = fontWeight(context)
        return if (weight > 0) {
            Typeface.create(base, weight, false)
        } else {
            base
        }
    }

    /**
     * Rebuilds the app-wide typography override from the stored selection and pokes
     * the theme revision so open Compose surfaces restyle. Call once at startup and
     * after every font change.
     */
    fun refresh(context: Context) {
        acornTypographyOverride = buildTypography(context)
        KakoTheme.bump()
    }

    private fun buildTypography(context: Context): AcornTypography? {
        if (!KakoTheme.isEnabled(context)) return null
        val family = fontFamily(context)
        val weight = fontWeight(context)
        val scale = fontScale(context)
        if (family.isEmpty() && weight == 0 && scale == DEFAULT_FONT_SCALE) {
            return null
        }

        val fontFamily = when {
            family.isEmpty() -> null
            family == KAKO_MONOSPACE_FONT -> FontFamily.Monospace
            else -> runCatching {
                FontFamily(Font(File(fontsDir(context), family)))
            }.getOrNull()
        }
        val fontWeight = if (weight > 0) FontWeight(weight) else null
        val factor = scale / 100f

        fun TextStyle.kako(): TextStyle = copy(
            fontFamily = fontFamily ?: this.fontFamily,
            fontWeight = fontWeight ?: this.fontWeight,
            fontSize = fontSize * factor,
            lineHeight = lineHeight * factor,
        )

        val d = defaultTypography
        return AcornTypography(
            headline5 = d.headline5.kako(),
            headline6 = d.headline6.kako(),
            headline7 = d.headline7.kako(),
            headline8 = d.headline8.kako(),
            subtitle1 = d.subtitle1.kako(),
            subtitle2 = d.subtitle2.kako(),
            body1 = d.body1.kako(),
            body2 = d.body2.kako(),
            button = d.button.kako(),
            caption = d.caption.kako(),
            overline = d.overline.kako(),
        )
    }
}
