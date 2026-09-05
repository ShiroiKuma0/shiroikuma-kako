/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.summarization

/**
 * The kind of network connection the device is currently using.
 *
 * Upstream took this enum from the generated Glean metrics; 白い熊 火狐 ships without Glean,
 * so it is declared here instead.
 */
enum class ConnectionType {
    NONE,
    WIFI,
    CELLULAR,
    OTHER,
}
