/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.base.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color

/**
 * Fork hooks for the 白い熊 火狐 UI: chrome styling that cannot be expressed through
 * [AcornColors] or the Material color scheme alone. Set by Fenix's KakoTheme whenever
 * the user changes a setting; consumed by the compose-base buttons and the browser
 * toolbar. Null values mean "stock behavior".
 */
object AcornForkOverrides {
    /** Traced outline of the address bar pill in the browser toolbar. */
    @Volatile
    var addressBarBorder: BorderStroke? = null

    /** Replacement style for filled buttons app-wide. */
    @Volatile
    var buttonStyle: ForkButtonStyle? = null
}

/**
 * Colors and outline applied to filled buttons when the fork theme is active.
 */
data class ForkButtonStyle(
    val containerColor: Color,
    val contentColor: Color,
    val border: BorderStroke?,
)
