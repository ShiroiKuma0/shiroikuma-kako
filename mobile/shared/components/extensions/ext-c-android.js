/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

"use strict";

extensions.registerModules({
  // The module name differs from both namespaces it serves so that the
  // "menus" and "contextMenus" permissions resolve per namespace -- the same
  // reason the desktop registration gives.
  menusInternal: {
    url: "chrome://geckoview/content/ext-c-menus.js",
    scopes: ["addon_child"],
    paths: [["contextMenus"], ["menus"]],
  },
  tabs: {
    url: "chrome://geckoview/content/ext-c-tabs.js",
    scopes: ["addon_child"],
    paths: [["tabs"]],
  },
});
