/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// This file contains branding-specific prefs.

pref("startup.homepage_override_url", "");
pref("startup.homepage_welcome_url", "");
pref("startup.homepage_welcome_url.additional", "");
// The time interval between checks for a new version (in seconds)
pref("app.update.interval", 86400); // 24 hours
// Give the user x seconds to react before showing the big UI. default=24 hours
pref("app.update.promptWaitTime", 86400);
// URL user can browse to manually if for some reason all update installation
// attempts fail.
pref("app.update.url.manual", "https://github.com/ShiroiKuma0/shiroikuma-kako");
// A default value for the "More information about this update" link
// supplied in the "An update is available" page of the update wizard.
pref("app.update.url.details", "https://github.com/ShiroiKuma0/shiroikuma-kako");

// The number of days a binary is permitted to be old
// without checking for an update.  This assumes that
// app.update.checkInstallTime is true.
pref("app.update.checkInstallTime.days", 2);

// Give the user x seconds to reboot before showing a badge on the hamburger
// button. default=immediately
pref("app.update.badgeWaitTime", 0);

// Number of usages of the web console.
// If this is less than 5, then pasting code into the web console is disabled
pref("devtools.selfxss.count", 5);

// ---------------------------------------------------------------------------
// 白い熊 火狐 fork defaults
// ---------------------------------------------------------------------------

// Wear the fork's own black/pure-yellow theme out of the box, so a fresh profile
// looks like the Android fork rather than stock Firefox. See
// browser/themes/addons/kako/ and BuiltInThemeConfig.sys.mjs.
pref("extensions.activeThemeID", "kako-theme@shiroikuma");

// No advertising on the new tab page. The Android fork has never shown sponsored
// tiles or sponsored stories and the desktop one will not either.
pref("browser.newtabpage.activity-stream.showSponsored", false);
pref("browser.newtabpage.activity-stream.showSponsoredTopSites", false);
pref("browser.newtabpage.activity-stream.system.showSponsored", false);
pref("browser.newtabpage.activity-stream.feeds.section.topstories", false);

// No weather widget -- it asks for location on first run and is not part of the
// fork's home screen.
pref("browser.newtabpage.activity-stream.showWeather", false);
