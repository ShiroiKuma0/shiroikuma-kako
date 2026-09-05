/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.library.recentlyclosed

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.RecentlyClosedAction
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.recentlyclosed.RecentlyClosedTabsStorage
import mozilla.components.feature.tabs.TabsUseCases
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.share.ShareSheetChooserAction
import org.mozilla.fenix.components.share.ShareSource
import org.mozilla.fenix.components.usecases.ShareUseCases
import org.mozilla.fenix.ext.openToBrowser

@Suppress("TooManyFunctions")
interface RecentlyClosedController {
    /**
     * [TabState] to get the state of the tab.
     */
    fun handleOpen(tab: TabState)

    /**
     * [TabState] to get the state of the tabs.
     */
    fun handleOpen(tabs: Set<TabState>)
    fun handleDelete(tab: TabState)
    fun handleDelete(tabs: Set<TabState>)
    fun handleShare(tabs: Set<TabState>)
    fun handleNavigateToHistory()
    fun handleRestore(item: TabState)
    fun handleSelect(tab: TabState)
    fun handleDeselect(tab: TabState)
    fun handleBackPressed(): Boolean
}

@Suppress("TooManyFunctions", "LongParameterList")
class DefaultRecentlyClosedController(
    private val appStore: AppStore,
    private val navController: NavController,
    private val browserStore: BrowserStore,
    private val recentlyClosedStore: RecentlyClosedFragmentStore,
    private val recentlyClosedTabsStorage: RecentlyClosedTabsStorage,
    private val tabsUseCases: TabsUseCases,
    private val shareUseCases: ShareUseCases,
    private val lifecycleScope: CoroutineScope,
    private val openToBrowser: (url: String) -> Unit,
) : RecentlyClosedController {
    override fun handleOpen(tab: TabState) {
        openToBrowser(tab.url)
    }

    override fun handleOpen(tabs: Set<TabState>) {
        if (appStore.state.mode == BrowsingMode.Normal) {
        } else if (appStore.state.mode == BrowsingMode.Private) {
        }
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.DeselectAll)
        tabs.forEach { handleOpen(it) }
    }

    override fun handleSelect(tab: TabState) {
        if (recentlyClosedStore.state.selectedTabs.isEmpty()) {
        }
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.Select(tab))
    }

    override fun handleDeselect(tab: TabState) {
        if (recentlyClosedStore.state.selectedTabs.size == 1) {
        }
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.Deselect(tab))
    }

    override fun handleDelete(tab: TabState) {
        browserStore.dispatch(RecentlyClosedAction.RemoveClosedTabAction(tab))
    }

    override fun handleDelete(tabs: Set<TabState>) {
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.DeselectAll)
        tabs.forEach { tab ->
            browserStore.dispatch(RecentlyClosedAction.RemoveClosedTabAction(tab))
        }
    }

    override fun handleNavigateToHistory() {
        navController.navigate(
            RecentlyClosedFragmentDirections.actionGlobalHistoryFragment(),
            NavOptions.Builder().setPopUpTo(R.id.historyFragment, true).build(),
        )
    }

    override fun handleShare(tabs: Set<TabState>) {

        val shareData = tabs.map { ShareData(url = it.url, title = it.title) }
        shareUseCases.shareItems(
            items = shareData,
            source = ShareSource.RECENTLY_CLOSED,
            chooserActions = if (tabs.size == 1) {
                listOf(
                    ShareSheetChooserAction.SEND_TO_DEVICES,
                    ShareSheetChooserAction.QR_CODE,
                )
            } else {
                listOf(ShareSheetChooserAction.SEND_TO_DEVICES)
            },
            navigateToShareFragment = {
                navController.navigate(
                    RecentlyClosedFragmentDirections.actionGlobalShareFragment(
                        data = shareData.toTypedArray(),
                    ),
                )
            },
        )
    }

    /**
     * Handles the restoration of a recently closed tab.
     *
     * If the current browsing mode is Normal, the tab is restored using [TabsUseCases.restore]
     * and deleted from the storage. The browser is then opened to this restored tab.
     *
     * If the current browsing mode is Private, then a new tab is opened in the current
     * browsing mode using [handleOpen] and it is not deleted from the storage.
     * The new tab is not restored from the disk.
     *
     * @param item The [TabState] of the tab to restore.
     */
    override fun handleRestore(item: TabState) {
        lifecycleScope.launch {
            val isPrivate = appStore.state.mode.isPrivate
            if (!isPrivate) {
                tabsUseCases.restore(item, recentlyClosedTabsStorage.engineStateStorage())
                browserStore.dispatch(RecentlyClosedAction.RemoveClosedTabAction(item))
                navController.openToBrowser()
            } else {
                handleOpen(item)
            }
        }
    }

    override fun handleBackPressed(): Boolean {
        return if (recentlyClosedStore.state.selectedTabs.isNotEmpty()) {
            recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.DeselectAll)
            true
        } else {
            false
        }
    }
}
