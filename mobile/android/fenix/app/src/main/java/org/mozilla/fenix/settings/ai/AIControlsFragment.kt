/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch
import org.mozilla.fenix.R
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.SupportUtils
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * A fragment displaying the AI Controls settings screen.
 */
class AIControlsFragment : Fragment(), SystemInsetsPaddedFragment {
    private val args by navArgs<AIControlsFragmentArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = content {
        val registry = requireComponents.aiFeatureRegistry
        val features = remember { registry.getFeatures().sortedForDisplay() }
        val featureBlock = requireComponents.aiControlsFeatureBlock
        val scope = rememberCoroutineScope()

        val aiBlockUiController = remember {
            AIBlockUIController.default(
                featureBlock = featureBlock,
                scope = scope,
            )
        }

        val showDialog = aiBlockUiController.showDialogFlow.collectAsState()
        val isBlocked = featureBlock.isBlocked.collectAsState(initial = false)

        FirefoxTheme {
            AIControlsScreen(
                registeredFeatures = features,
                showDialog = showDialog.value,
                isBlocked = isBlocked.value,
                itemToScrollTo = args.preferenceToScrollTo,
                onDialogDismiss = {
                    aiBlockUiController.onDialogDismiss()
                },
                onDialogConfirm = {
                    aiBlockUiController.onDialogConfirm()
                },
                onToggle = { currentlyBlocked ->
                    if (!currentlyBlocked) {
                    }
                    aiBlockUiController.onToggle(currentlyBlocked)
                },
                onFeatureToggle = { feature, enabled ->
                    scope.launch { feature.set(enabled) }
                },
                onFeatureNavLinkClick = { destination, featureId ->
                    destination.nav(this)
                },
                onBannerLearnMoreClick = {
                    openAiControlsSumoPage()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensures the toolbar shows when navigating to this fragment via Global Directions.
        showToolbar(getString(R.string.preferences_ai_controls))
    }

    private fun openAiControlsSumoPage() {
        val context = requireContext()
        SupportUtils.launchSandboxCustomTab(
            context = context,
            url = SupportUtils.getSumoURLForTopic(
                context = context,
                topic = SupportUtils.SumoTopic.AI_CONTROLS,
                useMobilePage = true,
            ),
        )
    }
}
