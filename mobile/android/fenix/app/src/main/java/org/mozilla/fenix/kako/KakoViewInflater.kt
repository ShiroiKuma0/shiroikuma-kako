/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * 白い熊 火狐 UI: the theme overlay's `viewInflaterClass` — the one hook every inflated widget
 * passes through. A button, switch, check box, radio button, slider, progress bar or text field
 * inflated anywhere under the overlay — a screen, a list row, a preference's widget, a sheet, a
 * dialog's own layout and its button bar — takes the black-yellow shape and its colour roles at
 * once, with no call site touched and no upstream layout edited.
 *
 * The view keeps the class its layout asked for; only widgets named by their exact class are
 * seen here, so a subclass of upstream's own (a custom text input layout, say) keeps the colours
 * the overlay's attributes give it.
 *
 * Named by the overlay only, so with the theme off this class is never created.
 */
@Keep
class KakoViewInflater : MaterialComponentsViewInflater() {

    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        super.createButton(context, attrs).styled { KakoPills.button(it) }

    override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox =
        super.createCheckBox(context, attrs).styled { KakoPills.check(it) }

    override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton =
        super.createRadioButton(context, attrs).styled { KakoPills.radio(it) }

    override fun createCheckedTextView(context: Context, attrs: AttributeSet): AppCompatCheckedTextView =
        super.createCheckedTextView(context, attrs).styled { KakoPills.checkedText(it) }

    override fun createSeekBar(context: Context, attrs: AttributeSet): AppCompatSeekBar =
        super.createSeekBar(context, attrs).styled { KakoPills.seekBar(it) }

    override fun createEditText(context: Context, attrs: AttributeSet): AppCompatEditText =
        super.createEditText(context, attrs).styled { KakoPills.editText(it, ownLine = namesNoBackground(attrs)) }

    @Suppress("CyclomaticComplexMethod")
    override fun createView(context: Context, name: String, attrs: AttributeSet): View? =
        when (name) {
            MaterialButton::class.java.name -> MaterialButton(context, attrs).styled { KakoPills.button(it) }
            AppCompatButton::class.java.name -> AppCompatButton(context, attrs).styled { KakoPills.button(it) }
            ExtendedFloatingActionButton::class.java.name ->
                ExtendedFloatingActionButton(context, attrs).styled { KakoPills.button(it) }
            FloatingActionButton::class.java.name -> FloatingActionButton(context, attrs).styled { KakoPills.fab(it) }
            MaterialSwitch::class.java.name -> MaterialSwitch(context, attrs).styled { KakoPills.switch(it) }
            SwitchCompat::class.java.name -> SwitchCompat(context, attrs).styled { KakoPills.switch(it) }
            MaterialCheckBox::class.java.name -> MaterialCheckBox(context, attrs).styled { KakoPills.check(it) }
            AppCompatCheckBox::class.java.name -> AppCompatCheckBox(context, attrs).styled { KakoPills.check(it) }
            MaterialRadioButton::class.java.name -> MaterialRadioButton(context, attrs).styled { KakoPills.radio(it) }
            AppCompatRadioButton::class.java.name -> AppCompatRadioButton(context, attrs).styled { KakoPills.radio(it) }
            Slider::class.java.name -> Slider(context, attrs).styled { KakoPills.slider(it) }
            AppCompatSeekBar::class.java.name -> AppCompatSeekBar(context, attrs).styled { KakoPills.seekBar(it) }
            PROGRESS_BAR -> ProgressBar(context, attrs).styled { KakoPills.progress(it) }
            LinearProgressIndicator::class.java.name ->
                LinearProgressIndicator(context, attrs).styled { KakoPills.progress(it) }
            CircularProgressIndicator::class.java.name ->
                CircularProgressIndicator(context, attrs).styled { KakoPills.progress(it) }
            // Inside a text input layout the field's background is the layout's box.
            TextInputEditText::class.java.name ->
                TextInputEditText(context, attrs).styled { KakoPills.editText(it, ownLine = false) }
            AppCompatEditText::class.java.name ->
                AppCompatEditText(context, attrs).styled { KakoPills.editText(it, ownLine = namesNoBackground(attrs)) }
            TextInputLayout::class.java.name -> TextInputLayout(context, attrs).styled { KakoPills.textInputLayout(it) }
            else -> super.createView(context, name, attrs)
        }

    /**
     * Styles a freshly created widget. A fault in the fork's styling must never cost the screen
     * that is being inflated: the widget is handed on as upstream made it.
     */
    private inline fun <T : View> T.styled(style: (T) -> Unit): T {
        try {
            style(this)
        } catch (e: RuntimeException) {
            Log.e("KakoViewInflater", "could not style " + javaClass.name, e)
        }
        return this
    }

    private fun namesNoBackground(attrs: AttributeSet): Boolean =
        attrs.getAttributeValue(ANDROID_NAMESPACE, "background") == null

    private companion object {
        /** The platform's widgets are named without a package in a layout. */
        const val PROGRESS_BAR = "ProgressBar"
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
