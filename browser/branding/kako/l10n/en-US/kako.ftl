# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

### 白い熊 火狐 -- every string the fork adds on the desktop.
###
### Served by KakoTheme.sys.mjs from chrome://branding/content/l10n/{locale}/.
### The UI page reads both languages from a registry of its own; the other
### documents get English from the application's registry, and Japanese where
### a Japanese language pack is installed. The two files must hold exactly the
### same message ids.


## Settings, Privacy: extensions on restricted sites

kako-extensions-group =
    .label = Extensions
    .description = A handful of sites normally block every extension at once — no exceptions, whichever extension it is and however you installed it.
kako-ignore-restricted-domains =
    .label = Run extensions on restricted sites
    .description = Lets extensions work on addons.mozilla.org, support.mozilla.org and the account and sync servers as they do on any other site.

## The extensions button’s context menu

# “Install Add-on From File…”, first entry on the extensions button’s context
# menu. Wording matches about:addons’ own menu item (addon-install-from-file)
# so the two read as one command in two places.
kako-toolbar-context-menu-install-addon-from-file =
    .label = Install Add-on From File…
    .accesskey = I
# Title of the file picker that entry opens.
kako-toolbar-context-menu-install-addon-from-file-dialog-title = Select add-on to install
# Name of the file-type filter in that picker.
kako-toolbar-context-menu-install-addon-from-file-filter-name = Add-ons

## Add-ons Manager

# The installed version, shown under every add-on in the list instead of only
# inside the expanded card.
# Variables:
#   $version (string) - The version of the installed add-on.
kako-addon-card-version = Version { $version }

## Entry points of the UI page

# The application menu, next to Settings.
kako-appmenu-ui =
    .label = 白い熊 火狐 UI
# The pill at the top of Settings.
kako-prefs-ui-link = 白い熊 火狐 UI
kako-prefs-ui-link-desc = Colours of the browser’s own interface, each settable by itself.

## The UI page

kako-ui-title = 白い熊 火狐 UI
kako-ui-app-settings = App settings
kako-ui-app-settings-desc = Search, privacy, add-ons and everything else that belongs to the browser itself.
kako-ui-index =
    .aria-label = Sections of this page
kako-ui-master-switch = Use the black-yellow theme
kako-ui-master-switch-desc = Off, the browser looks the way its theme draws it, and the colours below are not used.
kako-ui-foundation-note = Every other colour follows these until it is given a value of its own.
kako-ui-menu-icons = Icons, by what the row does
kako-ui-section-icons = Section icons of this page
kako-ui-role-changed =
    .title = Changed from its default
kako-ui-role-pick =
    .title = Choose a colour
kako-ui-role-hex =
    .title = Colour value: #RRGGBB, or #RRGGBBAA with opacity
    .aria-label = Colour value
kako-ui-role-reset =
    .title = Back to the default
    .aria-label = Back to the default
kako-ui-reset-order = Reset section order
kako-ui-reset-order-desc = Puts the sections of this page back in their first order.
kako-ui-reset-colors = Reset all colours
kako-ui-reset-colors-desc = Puts every colour back to its default. The order of this page stays.
kako-ui-reset-colors-question = Put every colour back to its default?
kako-ui-reset-colors-confirm = Reset
kako-ui-cancel = Cancel

## Sections of the UI page

kako-section-foundation = Foundation
kako-section-text = Text
kako-section-buttons = Buttons
kako-section-toolbar = Toolbar and address bar
kako-section-menu = Menus
kako-section-tabs = Tabs
kako-section-switches = Switches
kako-section-dialogs = Dialogs and panels
kako-section-marks = Check boxes, radio buttons and sliders
kako-section-lists = Lists and cards
kako-section-fields = Text fields and dropdowns
kako-section-window = Window
kako-section-pages = Settings pages
kako-section-newtab = New Tab
kako-section-toasts = Notification bars, badges and tooltips
kako-section-maintenance = Maintenance

## Colour roles

kako-role-background = Background
kako-role-text = Text
kako-role-text-secondary = Secondary text
kako-role-accent = Accent
kako-role-border = Borders
kako-role-text-on-accent = Text on accent
kako-role-focus-outline = Focus ring
kako-role-text-heading = Headings
kako-role-text-link = Links
kako-role-text-disabled = Disabled text
kako-role-text-error = Error text
kako-role-text-cursor = Cursor
kako-role-text-selection = Text selection
kako-role-text-selection-text = Selected text
kako-role-button-background = Button ground
kako-role-button-text = Button label
kako-role-button-border = Button frame
kako-role-button-main-text = Main button label
kako-role-button-main-border = Main button frame
kako-role-button-delete = Deleting button
kako-role-button-ripple = Highlight under the pointer and when pressed
kako-role-icon-button = Icon buttons
kako-role-toolbar-fill = Toolbar ground
kako-role-toolbar-label = Toolbar labels
kako-role-toolbar-icon = Toolbar icons
kako-role-toolbar-top-border = Toolbar edge lines
kako-role-toolbar-separator = Separators between buttons
kako-role-toolbar-button-hover = Toolbar button under the pointer
kako-role-toolbar-button-active = Pressed toolbar button
kako-role-addressbar-fill = Address bar ground
kako-role-addressbar-border = Address bar frame
kako-role-addressbar-border-focused = Address bar frame while typing
kako-role-toolbar-text = Address text
kako-role-toolbar-hint = Address bar hint
kako-role-urlbar-highlight = Chosen suggestion ground
kako-role-urlbar-highlight-text = Chosen suggestion text
kako-role-menu-background = Menu ground
kako-role-menu-border = Menu frame
kako-role-menu-row-frame = Menu row frame
kako-role-menu-label = Menu row label
kako-role-menu-header = Menu header line
kako-role-menu-divider = Menu group divider
kako-role-menu-highlight = Menu row under the pointer
kako-role-menu-icon-connect = Icon: connect, reload, sync
kako-role-menu-icon-message = Icon: write, edit, translate
kako-role-menu-icon-contact = Icon: account, logins
kako-role-menu-icon-share = Icon: copy, share, save
kako-role-menu-icon-view = Icon: view, open, settings
kako-role-menu-icon-block = Icon: block, private, lock
kako-role-menu-delete = Deleting row
kako-role-tab-selected = Selected tab ground
kako-role-tab-active-border = Selected tab line
kako-role-tab-text = Selected tab title
kako-role-tab-text-inactive = Other tabs title
kako-role-tab-close = Tab close button
kako-role-tab-loading = Tab loading indicator
kako-role-switch-on = On: outline
kako-role-switch-on-dot = On: dot
kako-role-switch-off = Off: outline
kako-role-switch-off-dot = Off: dot
kako-role-dialog-background = Dialog ground
kako-role-dialog-border = Dialog frame
kako-role-dialog-title = Dialog title
kako-role-dialog-text = Dialog text
kako-role-panel-background = Panel ground
kako-role-panel-border = Panel frame
kako-role-panel-text = Panel text
kako-role-scrim = Shade behind dialogs
kako-role-check-on = Check box: ticked
kako-role-check-off = Check box: empty
kako-role-check-mark = Check box: the tick
kako-role-radio-on = Radio button: chosen
kako-role-radio-off = Radio button: not chosen
kako-role-slider-active = Slider: filled part and thumb
kako-role-slider-inactive = Slider: remaining part
kako-role-progress = Progress indicators
kako-role-list-text = Row title
kako-role-list-divider = List divider
kako-role-list-selected = Selected row ground
kako-role-list-selected-text = Selected row text
kako-role-list-hover = Row under the pointer
kako-role-card-background = Card ground
kako-role-card-border = Card frame
kako-role-field-text = Entered text
kako-role-field-hint = Hint and label
kako-role-field-ground = Field ground
kako-role-field-frame = Field frame
kako-role-field-frame-focused = Field frame while typing
kako-role-window-frame = Title bar and tab strip ground
kako-role-window-frame-inactive = Title bar ground of a window in the background
kako-role-window-ring = Window ring
kako-role-window-ring-inactive = Ring of a window in the background
kako-role-sidebar-background = Sidebar ground
kako-role-sidebar-text = Sidebar text
kako-role-sidebar-border = Sidebar edge line
kako-role-scrollbar-thumb = Scrollbar thumb
kako-role-scrollbar-track = Scrollbar groove
kako-role-note = Notes and explanations
kako-role-page-heading = Section headings
kako-role-page-divider = Section dividers
kako-role-page-rule = Left rule of explained rows
kako-role-chip-frame = Index chip frame
kako-role-chip-text = Index chip label
kako-role-section-base = Section icon: the base
kako-role-section-controls = Section icon: controls
kako-role-section-words = Section icon: words
kako-role-section-frame = Section icon: the frame
kako-role-section-reach = Section icon: reaching out
kako-role-section-warning = Section icon: warning
kako-role-section-flash = Section icon: a brief flash
kako-role-newtab-background = New Tab ground
kako-role-newtab-text = New Tab text
kako-role-newtab-secondary-text = New Tab secondary text
kako-role-newtab-wordmark = Name beside the logo
kako-role-newtab-icon = New Tab icons
kako-role-newtab-card-background = Tile and card ground
kako-role-newtab-card-border = Tile and card frame
kako-role-newtab-search-ground = Search box ground
kako-role-newtab-search-border = Search box frame
kako-role-toast-background = Notification bar ground
kako-role-toast-border = Notification bar frame
kako-role-toast-text = Notification bar text
kako-role-tooltip-background = Tooltip ground
kako-role-tooltip-border = Tooltip frame
kako-role-tooltip-text = Tooltip text
kako-role-badge-frame = Badge frame
kako-role-badge-text = Badge label
kako-role-badge-filled = Filled badge ground
kako-role-badge-filled-text = Filled badge label
