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

/**
 * Stamps the kako black/yellow frame onto alert-style dialogs the fork can't
 * reach at their call site — chiefly the web-content prompts from
 * mozilla-components feature-prompts (JS alert/confirm/prompt, the "Resend data"
 * repost warning, HTTP auth, choice menus, …), which live in a module that can't
 * depend on the kako helpers.
 *
 * Installed once from [org.mozilla.fenix.FenixApplication]; for every
 * FragmentActivity it watches the (recursive) fragment manager, and whenever a
 * DialogFragment backed by an [AlertDialog] starts, applies [applyKakoBorder].
 * BottomSheet-style prompts (login / credit-card save bars, password generator,
 * …) are AppCompatDialogs rather than AlertDialogs, so they are left untouched.
 * Fenix's own AlertDialog fragments already carry the border from their call
 * site; re-stamping the identical drawable here is harmless.
 */
object KakoDialogBorder {

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(activityWatcher)
    }

    private val fragmentWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            val dialog = (f as? DialogFragment)?.dialog
            if (dialog is AlertDialog) {
                dialog.applyKakoBorder()
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
