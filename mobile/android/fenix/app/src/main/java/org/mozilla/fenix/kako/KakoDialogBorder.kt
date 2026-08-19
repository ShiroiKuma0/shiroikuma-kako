/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.kako

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Stamps the kako black/yellow frame onto dialogs the fork can't reach at their
 * call site — chiefly the web-content prompts from mozilla-components
 * feature-prompts (JS alert/confirm/prompt, the "Resend data" repost warning,
 * HTTP auth, choice menus, …), which live in a module that can't depend on the
 * kako helpers.
 *
 * Installed once from [org.mozilla.fenix.FenixApplication]; for every
 * FragmentActivity it watches the (recursive) fragment manager, and whenever a
 * DialogFragment starts it frames whichever kind of dialog is behind it:
 *
 *  - an [AlertDialog] gets [applyKakoBorder], a ring around the floating panel;
 *  - a [BottomSheetDialog] gets [applyKakoSheetBorder], a ring on the top and
 *    sides of the sheet.
 *
 * Catching them here rather than at each call site is what makes the sheets
 * uniform: the pull-ups come from three unrelated places — Fenix's own
 * BottomSheetDialogFragments (translations, main menu, trust panel, tab history,
 * protections, summarization, microsurvey, share, …), FenixDialogFragment when
 * its gravity is BOTTOM, and the mozilla-components prompt sheets (save login,
 * save credit card, save address, password generator, app-link redirect) — and
 * every one of them is a DialogFragment carrying a BottomSheetDialog.
 *
 * Fenix's own AlertDialog fragments already carry the border from their call
 * site; re-stamping the identical drawable here is harmless.
 */
object KakoDialogBorder {

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(activityWatcher)
    }

    private val fragmentWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            when (val dialog = (f as? DialogFragment)?.dialog) {
                is AlertDialog -> dialog.applyKakoBorder()
                is BottomSheetDialog -> dialog.applyKakoSheetBorder()
                else -> Unit
            }
        }
    }

    private val activityWatcher = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            (activity as? FragmentActivity)?.supportFragmentManager
                ?.registerFragmentLifecycleCallbacks(fragmentWatcher, true)
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
