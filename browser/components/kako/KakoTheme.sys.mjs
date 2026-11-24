/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * 白い熊 火狐 UI on the desktop: the colour-role table, its store and the
 * stylesheets that carry the roles into every chrome document.
 *
 * One role is one kind of thing 白い熊 can see, settable by itself on the UI
 * page (chrome://browser/content/kako/kako-ui.html). The table mirrors the
 * Android one (tools/kako/gen_roles.py) wherever the thing exists on the
 * desktop, with the same key and the same default.
 *
 * The two primaries are written here ONCE. No other file of the fork holds a
 * colour: the stylesheets name `var(--kako-<key>)`, and this module generates
 * the one sheet that declares them.
 */

const PRIMARIES = Object.freeze({
  BLACK: "#000000",
  YELLOW: "#FFFF00",
});

const PREF_BRANCH = "kako.ui.";
const PREF_ENABLED = "kako.ui.enabled";
const PREF_COLOR = "kako.ui.color.";
const PREF_ORDER = "kako.ui.sectionOrder";
const PREF_OPEN = "kako.ui.sectionsOpen";

// Observer topic sent after the stylesheets were rebuilt, and after the page state changed.
const TOPIC_CHANGED = "kako-ui-changed";

// The static USER sheets. They hold no colour, only var(--kako-…), so they
// are meaningless without the generated sheet and are registered with it.
const STATIC_SHEETS = [
  "chrome://branding/content/kako-dialog.css",
  "chrome://branding/content/kako-controls.css",
  "chrome://branding/content/kako-menus.css",
  "chrome://branding/content/kako-browser.css",
  // New Tab. Also linked from the newtab package, but the link loses to the
  // page's own rules; a user sheet outranks author !important and does not
  // depend on which document about:newtab happens to load.
  "chrome://branding/content/kako-newtab-user.css",
];

// Web pages never see the variables: the declaring rule is confined to the
// documents the fork styles.
const SCOPE =
  '@-moz-document url-prefix("chrome://"), url-prefix("about:"), ' +
  'url-prefix("resource://"), url-prefix("moz-extension://")';

const L10N_BASE = "chrome://branding/content/l10n/";
const L10N_LOCALES = ["en-US", "ja"];
const L10N_FILE = "kako.ftl";

/*
 * The sections of the UI page, in its default order. `colour` is one of the
 * seven section-icon roles and says what kind of thing the section is; no two
 * neighbours share one, and the rose is used once. Heading and chip both draw
 * `icon` in `colour`, so they cannot drift apart.
 */
const SECTIONS = Object.freeze(
  [
    // "App language" is first, so its chip is the first chip of the index --
    // also in an order stored before the section existed (see `order`).
    ["language", "chrome://global/skin/icons/defaultFavicon.svg", "section_words"],
    ["foundation", "chrome://browser/skin/customize.svg", "section_base"],
    ["text", "chrome://browser/skin/characterEncoding.svg", "section_words"],
    [
      "buttons",
      "chrome://global/skin/icons/cursor-arrow.svg",
      "section_controls",
    ],
    ["toolbar", "chrome://global/skin/icons/search-glass.svg", "section_frame"],
    ["menu", "chrome://browser/skin/menu.svg", "section_reach"],
    ["tabs", "chrome://browser/skin/tab.svg", "section_frame"],
    [
      "switches",
      "chrome://global/skin/icons/swap-horizontal-20.svg",
      "section_controls",
    ],
    ["dialogs", "chrome://global/skin/icons/info.svg", "section_reach"],
    ["marks", "chrome://global/skin/icons/check.svg", "section_controls"],
    ["lists", "chrome://browser/skin/library.svg", "section_frame"],
    ["fields", "chrome://global/skin/icons/edit.svg", "section_words"],
    ["window", "chrome://browser/skin/window.svg", "section_frame"],
    ["pages", "chrome://global/skin/icons/settings.svg", "section_base"],
    ["newtab", "chrome://browser/skin/new-tab.svg", "section_frame"],
    ["toasts", "chrome://global/skin/icons/lightbulb.svg", "section_flash"],
    [
      "maintenance",
      "chrome://global/skin/icons/developer.svg",
      "section_warning",
    ],
  ].map(([key, icon, colour]) => Object.freeze({ key, icon, colour }))
);

/*
 * The role table: [key, section, default].
 *
 * default: BLACK | YELLOW (the two primaries) | #RRGGBB (a colour that carries
 * meaning) | another role | mix(a,b,f) = f of the way from a to b, opaque |
 * alpha(a,f) = a at opacity f. A default that names a role follows that role's
 * current value, so a derived colour moves with what it is derived from.
 */
const ROLE_TABLE = [
  // Foundation — everything else inherits from these.
  ["background", "foundation", "BLACK"],
  ["text", "foundation", "YELLOW"],
  ["text_secondary", "foundation", "alpha(text,0.6)"],
  ["accent", "foundation", "YELLOW"],
  ["border", "foundation", "accent"],
  ["text_on_accent", "foundation", "background"],
  ["focus_outline", "foundation", "accent"],

  // Text.
  ["text_heading", "text", "accent"],
  ["text_link", "text", "accent"],
  ["text_disabled", "text", "alpha(text,0.38)"],
  ["text_error", "text", "#FF0000"],
  ["text_cursor", "text", "accent"],
  ["text_selection", "text", "accent"],
  ["text_selection_text", "text", "text_on_accent"],

  // Buttons.
  ["button_background", "buttons", "background"],
  ["button_text", "buttons", "accent"],
  ["button_border", "buttons", "border"],
  ["button_main_text", "buttons", "button_text"],
  ["button_main_border", "buttons", "button_border"],
  ["button_delete", "buttons", "#FF0000"],
  ["button_ripple", "buttons", "alpha(accent,0.3)"],
  ["icon_button", "buttons", "accent"],

  // Toolbar and address bar.
  ["toolbar_fill", "toolbar", "background"],
  ["toolbar_label", "toolbar", "text"],
  ["toolbar_icon", "toolbar", "accent"],
  ["toolbar_top_border", "toolbar", "border"],
  ["toolbar_separator", "toolbar", "border"],
  ["toolbar_button_hover", "toolbar", "mix(background,accent,0.2)"],
  ["toolbar_button_active", "toolbar", "mix(background,accent,0.3)"],
  ["addressbar_fill", "toolbar", "background"],
  ["addressbar_border", "toolbar", "border"],
  ["addressbar_border_focused", "toolbar", "accent"],
  ["toolbar_text", "toolbar", "text"],
  ["toolbar_hint", "toolbar", "text_secondary"],
  ["urlbar_highlight", "toolbar", "mix(background,accent,0.2)"],
  ["urlbar_highlight_text", "toolbar", "text"],

  // Menus.
  ["menu_background", "menu", "background"],
  ["menu_border", "menu", "border"],
  ["menu_row_frame", "menu", "border"],
  ["menu_label", "menu", "text"],
  ["menu_header", "menu", "mix(background,text,0.784)"],
  ["menu_divider", "menu", "mix(background,accent,0.7)"],
  ["menu_highlight", "menu", "mix(background,accent,0.2)"],
  ["menu_icon_connect", "menu", "#00A550"],
  ["menu_icon_message", "menu", "#0066FF"],
  ["menu_icon_contact", "menu", "#00B0FF"],
  ["menu_icon_share", "menu", "#FF8000"],
  ["menu_icon_view", "menu", "accent"],
  ["menu_icon_block", "menu", "#FF3D7F"],
  ["menu_delete", "menu", "#FF0000"],

  // Tabs.
  ["tab_selected", "tabs", "menu_background"],
  ["tab_active_border", "tabs", "accent"],
  ["tab_text", "tabs", "text"],
  ["tab_text_inactive", "tabs", "text"],
  ["tab_close", "tabs", "text_secondary"],
  ["tab_loading", "tabs", "accent"],

  // Switches.
  ["switch_on", "switches", "accent"],
  ["switch_on_dot", "switches", "switch_on"],
  ["switch_off", "switches", "mix(background,text,0.55)"],
  ["switch_off_dot", "switches", "switch_off"],

  // Dialogs and panels.
  ["dialog_background", "dialogs", "background"],
  ["dialog_border", "dialogs", "border"],
  ["dialog_title", "dialogs", "text_heading"],
  ["dialog_text", "dialogs", "text"],
  ["panel_background", "dialogs", "menu_background"],
  ["panel_border", "dialogs", "menu_border"],
  ["panel_text", "dialogs", "text"],
  ["scrim", "dialogs", "alpha(BLACK,0.6)"],

  // Check boxes, radio buttons, sliders — selection marks, never switches.
  ["check_on", "marks", "accent"],
  ["check_off", "marks", "switch_off"],
  ["check_mark", "marks", "background"],
  ["radio_on", "marks", "accent"],
  ["radio_off", "marks", "switch_off"],
  ["slider_active", "marks", "accent"],
  ["slider_inactive", "marks", "switch_off"],
  ["progress", "marks", "accent"],

  // Lists and cards.
  ["list_text", "lists", "text"],
  ["list_divider", "lists", "mix(background,accent,0.7)"],
  ["list_selected", "lists", "mix(background,accent,0.25)"],
  ["list_selected_text", "lists", "text"],
  ["list_hover", "lists", "mix(background,accent,0.2)"],
  ["card_background", "lists", "menu_background"],
  ["card_border", "lists", "menu_border"],

  // Text fields and dropdowns.
  ["field_text", "fields", "text"],
  ["field_hint", "fields", "text_secondary"],
  ["field_ground", "fields", "background"],
  ["field_frame", "fields", "mix(background,accent,0.7)"],
  ["field_frame_focused", "fields", "accent"],

  // The window — desktop only.
  ["window_frame", "window", "background"],
  ["window_frame_inactive", "window", "window_frame"],
  ["window_ring", "window", "accent"],
  ["window_ring_inactive", "window", "mix(background,accent,0.25)"],
  ["sidebar_background", "window", "background"],
  ["sidebar_text", "window", "text"],
  ["sidebar_border", "window", "border"],
  ["scrollbar_thumb", "window", "mix(background,accent,0.55)"],
  ["scrollbar_track", "window", "background"],

  // Settings pages.
  ["note", "pages", "mix(background,text,0.784)"],
  ["page_heading", "pages", "accent"],
  ["page_divider", "pages", "mix(background,accent,0.7)"],
  ["page_rule", "pages", "note"],
  ["chip_frame", "pages", "border"],
  ["chip_text", "pages", "text"],
  ["section_base", "pages", "accent"],
  ["section_controls", "pages", "#00A550"],
  ["section_words", "pages", "#00B0FF"],
  ["section_frame", "pages", "#0066FF"],
  ["section_reach", "pages", "#FF8000"],
  ["section_warning", "pages", "#FF0000"],
  ["section_flash", "pages", "#FF3D7F"],

  // New Tab.
  ["newtab_background", "newtab", "background"],
  ["newtab_text", "newtab", "text"],
  ["newtab_secondary_text", "newtab", "text"],
  ["newtab_wordmark", "newtab", "accent"],
  ["newtab_icon", "newtab", "accent"],
  ["newtab_card_background", "newtab", "background"],
  ["newtab_card_border", "newtab", "border"],
  ["newtab_search_ground", "newtab", "background"],
  ["newtab_search_border", "newtab", "border"],

  // Notification bars, badges and tooltips.
  ["toast_background", "toasts", "menu_background"],
  ["toast_border", "toasts", "menu_border"],
  ["toast_text", "toasts", "text"],
  ["tooltip_background", "toasts", "menu_background"],
  ["tooltip_border", "toasts", "menu_border"],
  ["tooltip_text", "toasts", "text"],
  ["badge_frame", "toasts", "mix(background,accent,0.7)"],
  ["badge_text", "toasts", "text"],
  ["badge_filled", "toasts", "accent"],
  ["badge_filled_text", "toasts", "text_on_accent"],
];

const ROLES = Object.freeze(
  ROLE_TABLE.map(([key, section, def]) =>
    Object.freeze({
      key,
      section,
      default: def,
      l10nId: "kako-role-" + key.replaceAll("_", "-"),
    })
  )
);
const ROLE_BY_KEY = new Map(ROLES.map(role => [role.key, role]));

const CALL = /^(mix|alpha)\((.*)\)$/;
const HEX = /^#([0-9a-f]{6})([0-9a-f]{2})?$/i;

// "#RRGGBB" or "#RRGGBBAA" → [r, g, b, a], or null.
function parseHex(text) {
  let match = HEX.exec(String(text).trim());
  if (!match) {
    return null;
  }
  let n = parseInt(match[1], 16);
  let a = match[2] === undefined ? 1 : parseInt(match[2], 16) / 255;
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff, a];
}

function hex2(value) {
  return Math.max(0, Math.min(255, Math.round(value)))
    .toString(16)
    .padStart(2, "0")
    .toUpperCase();
}

// [r, g, b, a] → "#RRGGBB", or "#RRGGBBAA" when it is not opaque.
function toHex([r, g, b, a]) {
  let out = "#" + hex2(r) + hex2(g) + hex2(b);
  return a >= 1 ? out : out + hex2(a * 255);
}

function mix(from, to, fraction) {
  let channel = i => Math.floor(from[i] + (to[i] - from[i]) * fraction + 0.5);
  return [channel(0), channel(1), channel(2), 1];
}

export const KakoTheme = {
  PRIMARIES,
  TOPIC_CHANGED,
  L10N_FILE,
  roles: ROLES,
  sections: SECTIONS,

  _initialized: false,
  _sheetURI: null,
  _staticRegistered: false,
  _applyPending: false,
  _shadowSources: new Set(),
  _pageRegistry: null,

  // Called once, from the fork block in BrowserGlue._beforeUIStartup.
  init() {
    if (this._initialized) {
      return;
    }
    this._initialized = true;
    try {
      this._registerL10n();
    } catch (e) {
      console.error("kako: failed to register the fork's strings", e);
    }
    this.apply();
    Services.prefs.addObserver(PREF_BRANCH, this);
    Services.obs.addObserver(this, "webextension-langpack-startup");
    Services.obs.addObserver(this, "webextension-langpack-shutdown");
  },

  observe(subject, topic, data) {
    if (topic == "nsPref:changed") {
      if (data == PREF_ENABLED || data.startsWith(PREF_COLOR)) {
        this._scheduleApply();
      } else {
        Services.obs.notifyObservers(null, TOPIC_CHANGED, data);
      }
      return;
    }
    let langpackId = subject?.wrappedJSObject?.langpack?.langpackId;
    if (!langpackId) {
      return;
    }
    if (topic == "webextension-langpack-startup") {
      this._registerShadow(langpackId);
    } else if (this._shadowSources.delete(langpackId)) {
      L10nRegistry.getInstance().removeSources(["kako-" + langpackId]);
    }
  },

  // ----- strings ---------------------------------------------------------

  _l10nSource(name, metasource, locales) {
    return new L10nFileSource(
      name,
      metasource,
      locales,
      L10N_BASE + "{locale}/",
      {},
      // The index spares the registry a failed load of every other file of
      // the browser from this source.
      locales.map(locale => `${L10N_BASE}${locale}/${L10N_FILE}`)
    );
  },

  /*
   * The fork's own strings in the application's registry.
   *
   * English goes in as a source of the application itself. Japanese does NOT:
   * the registry reports every locale of every source to the locale service
   * as available, so a Japanese source here would make Japanese an application
   * locale on a build that has no Japanese for anything else -- and the
   * registry builds a bundle from the sources of ONE metasource, so such a
   * build could not use it in any document that also needs an upstream file.
   *
   * A language pack is a metasource of its own. Where a Japanese one is
   * installed, the fork's Japanese is registered inside that pack's
   * metasource, as the New Tab add-on does for its own file
   * (AboutNewTabResourceMapping); then a document that links kako.ftl beside
   * upstream's files gets one Japanese bundle holding both.
   *
   * The UI page does not depend on any of this: see pageRegistry().
   */
  _registerL10n() {
    // A multi-locale package carries Japanese inside the application itself;
    // there the fork's Japanese belongs beside it, in the same metasource.
    let locales = ["en-US"];
    if (Services.locale.packagedLocales.includes("ja")) {
      locales.push("ja");
    }
    L10nRegistry.getInstance().registerSources([
      this._l10nSource("kako", "app", locales),
    ]);
    const { Langpack } = ChromeUtils.importESModule(
      "resource://gre/modules/Extension.sys.mjs"
    );
    for (let langpackId of Langpack.activeLangpackIds ?? []) {
      this._registerShadow(langpackId);
    }
  },

  // @param {string} langpackId "langpack-<locale>-<product>", the pack's metasource
  _registerShadow(langpackId) {
    if (
      this._shadowSources.has(langpackId) ||
      !/^langpack-ja(-|$)/.test(langpackId)
    ) {
      return;
    }
    try {
      L10nRegistry.getInstance().registerSources([
        this._l10nSource("kako-" + langpackId, langpackId, ["ja"]),
      ]);
      this._shadowSources.add(langpackId);
    } catch (e) {
      console.error("kako: failed to register strings for", langpackId, e);
    }
  },

  /*
   * A registry of the fork's own, holding kako.ftl in English and Japanese and
   * nothing else. The UI page reads its strings from it, so the page is
   * Japanese for a Japanese request whether or not a language pack is
   * installed, and without changing the application's locale.
   */
  pageRegistry() {
    if (!this._pageRegistry) {
      let registry = new L10nRegistry();
      registry.registerSources([this._l10nSource("kako", "app", L10N_LOCALES)]);
      this._pageRegistry = registry;
    }
    return this._pageRegistry;
  },

  /*
   * The locales the UI page asks for: what 白い熊 requested, then what the
   * application runs in, then English.
   */
  locales() {
    let wanted = [
      ...Services.locale.requestedLocales,
      ...Services.locale.appLocalesAsBCP47,
    ].map(tag => (tag.toLowerCase().startsWith("ja") ? "ja" : "en-US"));
    return [...new Set([...wanted, "en-US"])];
  },

  // ----- roles -----------------------------------------------------------

  get enabled() {
    return Services.prefs.getBoolPref(PREF_ENABLED, true);
  },

  set enabled(value) {
    Services.prefs.setBoolPref(PREF_ENABLED, !!value);
  },

  role(key) {
    return ROLE_BY_KEY.get(key);
  },

  // True while the role has a value of its own rather than its default.
  isSet(key) {
    return !!this._stored(key);
  },

  _stored(key) {
    return parseHex(Services.prefs.getStringPref(PREF_COLOR + key, ""));
  },

  _evaluate(expr, depth, defaultsOnly) {
    expr = expr.trim();
    if (expr in PRIMARIES) {
      return parseHex(PRIMARIES[expr]);
    }
    if (expr.startsWith("#")) {
      return parseHex(expr);
    }
    let call = CALL.exec(expr);
    if (call) {
      let args = call[2].split(",");
      let first = this._evaluate(args[0], depth, defaultsOnly);
      if (call[1] == "mix") {
        let second = this._evaluate(args[1], depth, defaultsOnly);
        return mix(first, second, parseFloat(args[2]));
      }
      return [first[0], first[1], first[2], parseFloat(args[1])];
    }
    return this._rgba(expr, depth + 1, defaultsOnly);
  },

  _rgba(key, depth = 0, defaultsOnly = false) {
    let role = ROLE_BY_KEY.get(key);
    if (!role || depth > 16) {
      throw new Error("kako: unknown or circular colour role " + key);
    }
    return (
      (!defaultsOnly && this._stored(key)) ||
      this._evaluate(role.default, depth, false)
    );
  },

  // The role's colour as "#RRGGBB", or "#RRGGBBAA" when it is not opaque.
  get(key) {
    return toHex(this._rgba(key));
  },

  // What the role shows while unset. Follows the roles its default names.
  getDefault(key) {
    return toHex(this._rgba(key, 0, true));
  },

  // @param {string} value "#RRGGBB" or "#RRGGBBAA"
  set(key, value) {
    let rgba = ROLE_BY_KEY.has(key) && parseHex(value);
    if (!rgba) {
      throw new Error(`kako: cannot set ${key} to ${value}`);
    }
    Services.prefs.setStringPref(PREF_COLOR + key, toHex(rgba));
  },

  reset(key) {
    Services.prefs.clearUserPref(PREF_COLOR + key);
  },

  resetAll() {
    for (let role of ROLES) {
      Services.prefs.clearUserPref(PREF_COLOR + role.key);
    }
  },

  // ----- the arrangement of the UI page ------------------------------------

  // Section keys in the stored order; one the stored list does not know keeps its default place.
  get order() {
    let known = SECTIONS.map(section => section.key);
    let stored = Services.prefs
      .getStringPref(PREF_ORDER, "")
      .split(",")
      .filter(key => known.includes(key));
    let result = [...new Set(stored)];
    if (!result.length) {
      return known;
    }
    known.forEach((key, index) => {
      if (!result.includes(key)) {
        result.splice(Math.min(index, result.length), 0, key);
      }
    });
    return result;
  },

  set order(keys) {
    Services.prefs.setStringPref(PREF_ORDER, keys.join(","));
  },

  resetOrder() {
    Services.prefs.clearUserPref(PREF_ORDER);
  },

  // ----- the app's language -----------------------------------------------

  /*
   * One stored value, the one Settings > Language writes too:
   * intl.locale.requested. Empty means "follow the system's language".
   */
  get language() {
    return Services.prefs.getStringPref("intl.locale.requested", "").split(",")[0].trim();
  },

  set language(tag) {
    Services.prefs.setStringPref("intl.locale.requested", tag);
  },

  // The languages this package carries.
  get shippedLanguages() {
    return [...Services.locale.packagedLocales];
  },

  isOpen(key) {
    return Services.prefs.getStringPref(PREF_OPEN, "").split(",").includes(key);
  },

  setOpen(key, open) {
    let keys = new Set(
      Services.prefs.getStringPref(PREF_OPEN, "").split(",").filter(Boolean)
    );
    if (open) {
      keys.add(key);
    } else {
      keys.delete(key);
    }
    Services.prefs.setStringPref(PREF_OPEN, [...keys].join(","));
  },

  // ----- stylesheets -------------------------------------------------------

  // The generated sheet: every role as a custom property on the root of the fork's documents.
  css() {
    let lines = ROLES.map(role => {
      let [r, g, b, a] = this._rgba(role.key);
      let value =
        a >= 1
          ? toHex([r, g, b, 1])
          : `rgba(${r}, ${g}, ${b}, ${+a.toFixed(3)})`;
      return `    --kako-${role.key}: ${value};`;
    });
    return (
      `${SCOPE} {\n  :root {\n    --kako-enabled: 1;\n` +
      lines.join("\n") +
      "\n  }\n}\n"
    );
  },

  _scheduleApply() {
    if (this._applyPending) {
      return;
    }
    this._applyPending = true;
    Services.tm.dispatchToMainThread(() => {
      this._applyPending = false;
      this.apply();
    });
  },

  /*
   * Registers the generated sheet and the static ones as USER sheets, or takes
   * them all away when the master switch is off. The style sheet service keeps
   * a sheet per URI and the generated one is a data: URI, so the previous one
   * is unregistered by the URI it was registered under.
   */
  apply() {
    try {
      const sss = Cc["@mozilla.org/content/style-sheet-service;1"].getService(
        Ci.nsIStyleSheetService
      );
      let enabled = this.enabled;
      let uri = enabled
        ? Services.io.newURI(
            "data:text/css;charset=utf-8," + encodeURIComponent(this.css())
          )
        : null;
      let previous = this._sheetURI;
      if (uri && previous?.equals(uri)) {
        uri = previous;
      } else {
        // The new sheet first, so that no document is ever without one.
        if (uri) {
          sss.loadAndRegisterSheet(uri, sss.USER_SHEET);
        }
        if (previous && sss.sheetRegistered(previous, sss.USER_SHEET)) {
          sss.unregisterSheet(previous, sss.USER_SHEET);
        }
        this._sheetURI = uri;
      }
      for (let spec of STATIC_SHEETS) {
        let sheet = Services.io.newURI(spec);
        let registered = sss.sheetRegistered(sheet, sss.USER_SHEET);
        if (enabled && !registered) {
          sss.loadAndRegisterSheet(sheet, sss.USER_SHEET);
        } else if (!enabled && registered) {
          sss.unregisterSheet(sheet, sss.USER_SHEET);
        }
      }
    } catch (e) {
      console.error("kako: failed to apply the UI colours", e);
    }
    Services.obs.notifyObservers(null, TOPIC_CHANGED, PREF_COLOR);
  },
};
