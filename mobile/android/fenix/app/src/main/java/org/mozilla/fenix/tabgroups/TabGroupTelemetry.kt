/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabgroups

import org.mozilla.fenix.components.attribution.MetricsUtils.TabGroupAccessPoint
import org.mozilla.fenix.tabstray.data.TabGroupTheme
import org.mozilla.fenix.tabstray.data.TabsTrayItem
import org.mozilla.fenix.tabstray.redux.state.TabGroupFormState

/**
 * Class that handles Telemetry for the TabGroups feature.
 *
 * @param tabGroupAccessPoint - [TabGroupAccessPoint] where TabGroups feature was accessed
 */
class TabGroupTelemetry(@Suppress("UNUSED_PARAMETER") tabGroupAccessPoint: TabGroupAccessPoint) {
    /**
     * Context interface wrapper that allows different state objects to serve as the context for invoking TabGroup
     * telemetry actions.
     */
    interface TabGroupTelemetryContext {
        val groups: List<TabsTrayItem.TabGroup>
        val formState: TabGroupFormState?
    }

    /**
     * Records a TabGroup save action.
     *
     * @param context: [TabGroupTelemetryContext] context object
     */
    fun recordSaveClicked(context: TabGroupTelemetryContext) {
        val formState = context.formState ?: return
        if (formState.inEditState) {
            val originalGroup = context.groups.find { it.id == formState.tabGroupId }
            if (originalGroup != null && originalGroup.title != formState.name) {
            }
        } else {
        }
    }

    /**
     * Records a TabGroup theme change action.
     *
     * @param context: [TabGroupTelemetryContext] context object
     * @param theme: [TabGroupTheme] being recorded
     */
    fun recordThemeChanged(context: TabGroupTelemetryContext, theme: TabGroupTheme) {
        val formState = context.formState ?: return
        if (formState.inEditState) {
        } else {
        }
    }

    /**
     * Records a tab added to a group.
     *
     * @param tabCount: Number of tabs being added
     */
    fun recordTabAddedToGroup(tabCount: Int) {
    }

    /** Records a TabGroup creation from a menu. */
    fun recordCreatedFromMenu() {
    }

    /** Records a TabGroup creation from a Floating Action Button. */
    fun recordCreatedFromFab() {
    }

    /** Records a TabGroup creation from a Drag and Drop action. */
    fun recordCreatedFromDragAndDrop() {
    }

    /**
     * Enum class documenting the different modes that can be recorded for a tab group's creation.
     *
     * @property telemetryId String id recorded to telemetry.
     */
    enum class CreationMode(val telemetryId: String) {
        TAB_MENU("menu"), // Note: ID is correct, this shipped as "menu" in original launch
        BROWSER_MENU("browser_menu"),
        FAB("fab"),
        DRAG_AND_DROP("drag_and_drop"),
    }
}
