# Changelog — 白い熊 火狐 (`shiroikuma.kako`)

Everything built on top of stock Firefox for Android (Fenix, release channel).
Tags are `<upstream-base>+<build>`; the fork commits live on `custom`, rebased
onto each adopted `FIREFOX_*_RELEASE` tag.

## 153.0+5 — 2026-07-25

The browser joins 白い熊's cross-app backup batch: a sister automation app can
now trigger this export headlessly and be told exactly what was written. Base:
Firefox **153.0** (`FIREFOX_153_0_RELEASE`).

### Major features

- **The sister-app state-export automation contract.** A new exported broadcast
  receiver answers `shiroikuma.kako.action.EXPORT_STATE` and
  `shiroikuma.kako.action.LIST_CATEGORIES`, the wire shape every 白い熊 app
  speaks, so 自由作業盤's 保存復元 project can back up the whole family in one
  run. `EXPORT_STATE` runs the existing category ZIP export with no Activity and
  no interaction; `LIST_CATEGORIES` enumerates what can be exported so the
  caller can render a picker. One request writes exactly one ZIP.
- **A token is the gate, because the caller cannot hold a permission.** A master
  switch — **off** until it is turned on — plus a 24-byte `SecureRandom` secret,
  hex-encoded, generated lazily on first read and compared in constant time with
  `MessageDigest.isEqual`. It lives in its own SharedPreferences file, which is
  not one of the files the export reads, so the token can never travel inside a
  backup ZIP. A disabled switch and a wrong token are reported as distinct
  errors, because they debug differently.
- **The reply is a fresh broadcast, and nothing else.** EMUI will not reliably
  carry a live Binder into another app's manifest receiver and severs the
  ordered-broadcast result channel between third-party apps, so `ResultReceiver`,
  `PendingIntent`, `Messenger` and `setResultData` are all unusable here. The
  receiver replies with a plain broadcast carrying
  `FLAG_INCLUDE_STOPPED_PACKAGES` — without which a backgrounded caller never
  hears the answer — echoing the caller's correlation id verbatim and reporting
  `OK:<absolute path>|<bytes>|<human size>|<n> categories`. Exactly one terminal
  reply per request, guarded by an `AtomicBoolean` so an asynchronous success and
  a synchronous error can never both fire. The byte count and the display size
  are computed here, since the caller cannot stat the file.
- **Progress reports real counts, never a percentage.** While exporting, the
  receiver broadcasts `区分 3/8 — Fonts` alongside structured `current`, `total`
  and `unit` extras, throttled to at most one every 500 ms with the closing one
  always sent.
- **The export core is called, not copied.** `KakoExim.export()` gained an
  `onProgress(done, total, label)` hook and is now driven by two thin callers —
  the Export/Import panel and this receiver — rather than being duplicated for
  automation. Broadcast delivery is held open with `goAsync()` while the work
  runs on an IO dispatcher, since the export walks Places, the logins store and
  the autofill store before writing.
- **The two automation rows live inside the Export/Import section**, directly
  below the export-directory box and the panel row: the master switch with a
  one-line explanation, and a token row showing the secret abbreviated
  (`80922d8c…4c49a87c`) that copies it in full on tap and carries a
  **Regenerate** action warning that pasted copies must be updated. It is a
  backup feature, so it sits where backup already sits — identically in every
  sister app.

### Export format & behavior

- **Backups follow the family file-name convention.** Every archive the fork
  writes — from the panel as much as from automation — is now
  `shiroikuma-kako_<yyyy-MM-dd_HH-mm-ss>.zip`: no version, no `-export` infix,
  no decoration. 白い熊 keeps every app's backups in one directory, so they must
  sort and read uniformly. The "last export" query still recognises the older
  `shiroikuma-kako-<version>-export_*.zip` names, so previously written backups
  stay visible.
- **Category ids are the stable ones the archive already used.** A category's id
  is now the bare `kako_ui` / `fonts` / `extensions` / `app_settings` /
  `bookmarks` / `logins` / `credit_cards` / `addresses` that `manifest.json`
  lists and the automation `items` extra accepts, with the `<id>.json` ZIP entry
  derived from it. The entry names are unchanged, so archives written by earlier
  builds import exactly as before.
- **The manifest records the app version** that produced the archive, alongside
  the format, version, package, timestamp and category list.
- **Categories are written in a fixed order** — the declaration order, not the
  order the caller happened to select them in — so two exports of the same
  selection read identically.

### Packaging

- **`MANAGE_EXTERNAL_STORAGE` is declared**, as in the sister apps, so the
  contract's directory override can be honoured with plain file I/O. Android
  only grants it by hand, so turning the automation switch on offers to open
  that settings screen; until it is granted the export falls back to the
  configured SAF directory, and says `no-storage-access` rather than writing
  somewhere the caller did not ask for.

## 153.0+4 — 2026-07-25

New upstream major and the fork's first backup feature. Base: Firefox
**153.0** (`FIREFOX_153_0_RELEASE`), adopted from 152.0.6.

### Major features

- **Export / Import — the whole profile, by category.** A new first section on
  the 白い熊 火狐 UI page. A SAF export directory is chosen once and kept in its
  own SharedPreferences file, so the setting itself never travels inside an
  export; the page queries that directory on every opening for the newest
  `shiroikuma-kako-*.zip` and shows its timestamp. The panel lists eight
  categories, all selected by default: 白い熊 UI (colours · borders · toolbar),
  fonts, extensions, Firefox settings, bookmarks, saved passwords, credit cards
  and addresses.
- **The archive is plain, readable JSON in a ZIP** — one pretty-printed file per
  category plus a `manifest.json` and the raw font files. No databases, no
  serialized objects, no opaque blobs. Preferences serialize as a typed
  `key → {t, v}` map (boolean/int/long/float/string/string-set).
- **Restores merge, never wipe.** A preference absent from the export keeps its
  current value, so a partial archive cannot strip settings it never carried.
  Device-local and ephemeral keys (telemetry ids, experiment state, install and
  migration stamps, CFR counters, the one-time yellow-migration flag) are
  excluded in both directions.
- **Every installed extension travels, not just the pinned ones.** Each
  non-built-in add-on is recorded with its enabled and private-browsing state,
  alongside the pinned toolbar order and the custom AMO collection (both moved
  out of the general settings into this category). A restore re-installs each
  missing add-on from its AMO listing — looked up by id, so it works whatever
  collection is configured — and re-applies the recorded state.
- **The restore answers Gecko's install permission prompt itself.** Gecko parks
  that prompt in the browser store and stalls the install until it is answered;
  Fenix's own prompt UI is bound to the browser and home screens, which are
  stopped while the UI page is up, so nothing else ever would. The grant is the
  one the backup recorded — data-collection consent is never given on the user's
  behalf. Installs run one at a time (the store holds a single prompt) on the
  main thread, because the AddonManager calls land in GeckoView's
  `WebExtensionController`, which asserts a Handler thread.
- **Personal data stores.** Bookmarks export as one subtree per Places root and
  merge back under the matching live root — whole folders through the bulk
  `insertTree`, loose items individually. Saved logins restore through `addMany`
  and credit cards through the storage's own encryption, so a single malformed
  row cannot abort a restore. Passwords, card numbers and postal addresses can
  only travel as plaintext inside the ZIP; each carries a red warning under its
  checkbox saying so.

### UI & theming

- **The UI page adopts the kxkb look.** Section headings are 20 sp bold accent
  titles underlined exactly as wide as their own text (a `match_parent` rule
  inside a `wrap_content` column), sub-headings 17 sp with thinner underlines.
  Top-level sections are separated by 1 px full-width hairlines — none before
  the first. Rows follow the 36/54/72/90 dp indent ladder at 16 sp with 13 sp
  values, and colour swatches become 38 dp rounded squares.
- **Export/Import panel styling** follows the Kōjiki flow with the ArcaneChat
  button line: a bordered, tappable directory box, thin 40 %-alpha dividers, and
  black stadium pills with an accent stroke — Cancel alone on the left in the
  neutral slot, Import and Export grouped on the right, claimed after `show()`
  so they never auto-dismiss the panel.
- **The no-directory message is red** and becomes ordinary text once a directory
  is set; "no export yet in this directory" is red too.
- **Success closes the whole chain.** The yellow-framed result dialogs dismiss
  everything beneath them: OK after an export, and "Later" after an import,
  close the info dialog, the panel and the UI page together; "Restart now"
  relaunches the launcher task and ends the process so every cache is rebuilt.
  Failures ("Export failed…", "No categories selected.") toast and leave the
  panel open with the selection intact.

### Upstream adoption

- **Adopt upstream Firefox 153.0** — new major; the rebase re-applied each fork
  commit's intent onto upstream's refactors rather than forcing the old patch
  shape:
  - `SecretSettingsFragment`: upstream hoisted `context.settings()` into a local
    and added tab-groups drag-and-drop and onboarding gates (opened like the
    rest); the removed `allow_settings_search` block was dropped.
  - `app/build.gradle`: upstream moved version wiring to the new `onVariants`
    API — the kako `versionName` override now lives in that block.
  - Menu and tab-strip chrome: upstream renamed the card shape and fill tokens
    (`MaterialTheme.shapes.extraSmall`, `surfaceBright`); `kakoMenuCard` and the
    banner border were re-applied on them.
  - `AcornColors` was slimmed upstream — `layer3`, `ripple`, `tabActive` and
    `tabInactive` are gone. Tab-strip fills now come from the fork slots through
    a new `TabStripColors.default()` override, with upstream's gradient as the
    fallback when the 白い熊 UI is switched off.
  - The `Context.settings()` extension was removed upstream; every fork call
    site now uses `components.settings`.
  - `FindInPageIntegration`: upstream added its own `findInPageHeight`
    parameter, so the single-row height moved to the `BaseBrowserFragment` call
    site.
  - `NeutralButton`: the surface/outline restyle re-applied on upstream's new
    Material 3 outlined-button parent.

## 152.0.6+1 — 2026-07-16

Upstream-base update; the fork feature set is unchanged from 152.0.4+1. Base:
Firefox **152.0.6** (`FIREFOX_152_0_6_RELEASE`), adopted via 152.0.5.

### Upstream adoptions

- **Adopt upstream Firefox 152.0.5** — point release; one rebase conflict
  (upstream added an import next to the kako-border import in
  SecretSettingsFragment; both kept).
- **Adopt upstream Firefox 152.0.6** — point release; conflict-free rebase.
- Both carry upstream's security and stability fixes on the 152 branch; the
  `release` mirror stays byte-identical to upstream.

## 152.0.4+1 — 2026-07-03

First published release. Base: Firefox **152.0.4** (`FIREFOX_152_0_4_RELEASE`).

### Major features

- **Custom AMO extension collections on the release channel** — the fork's
  defining patch. `customExtensionCollectionFeature` is forced on, so the
  custom-collection override appears in settings and is honored when building
  the AMO add-ons provider. Collection extensions are AMO-signed, so release
  GeckoView signing enforcement is never tripped.
- **about:config enabled** on release (`aboutConfigEnabled(true)` in
  GeckoProvider).
- **Full secret-settings menu** on release: the per-preference
  `isNightlyOrDebug` visibility gates in SecretSettingsFragment are opened
  (still reached the stock way — five taps on the About logo). Gates that
  change *behavior* rather than availability (telemetry endpoints,
  secure-state-at-rest, force-insecure, font enumeration) are deliberately
  left stock.
- **The 白い熊 火狐 UI page** — pinned at the top of Settings and opened by
  long-pressing the menu button (home or browser screen):
  - Settable color-slot theme, seeded pure black `#000000` / pure yellow
    `#FFFF00`, with two-tier resolution (explicit per-slot override, else
    inheritance from foundation slots). Compose surfaces (home, toolbar,
    menus, tab tray) restyle live via a revision-counted dynamic palette;
    classic XML screens get a static theme overlay.
  - External font import (ttf/otf via the system file picker): family,
    weight, and a 70–160% size scale rebuild the app-wide typography through
    a new android-components `acornTypographyOverride` hook. Font options
    render in their own glyphs; weights in their own weight.
  - Border-thickness sliders for all five border kinds, stepping by 0.5 dp.
  - Confirm-guarded reset to the seeded defaults.
- **Pinned extension buttons on the toolbar** — desktop-style: each
  extension with a browser action gains an add-to/remove-from-toolbar row in
  its settings screen. Toolbar buttons show the extension's real
  browser-action icon (multicolor preserved, puzzle-piece fallback,
  tab-specific overrides, disabled/private-tab exclusions), sized by a
  16–48 dp icon-size slider. Reorder by long-press (move left/right/remove
  popup) or a drag-to-reorder dialog in the UI page; taps dispatch the real
  browser action (listener or popup).
- **Two-row toolbar** (toggleable, default on): navigation, reload and the
  address pill on the first row; new tab, pinned extensions, a
  manage-extensions button, tab counter and menu on a right-aligned second
  row carried as first-class toolbar state. Viewport height and fragment
  margins account for the extra 48 dp; both rows hug the screen with 4 dp
  edge padding and the back/forward/reload targets overlap 8 dp into one
  tight cluster.

### UI & theming

- **Traced chrome instead of grey fills** (Nightly-style separation, drawn
  as yellow line art on black): outlined address pill, a settable rule along
  the toolbar's top edge, traced menu cards and library tiles, and
  slot-driven buttons (black fill, accent text, traced outline).
- **Tab-strip block design**: inactive tabs merge into one rectangular block
  with continuous rails and single shared separators; only the active tab
  carries a discrete thicker accent border.
- **Menu groups share separators** — one traced outline per group, one line
  between neighbours, instead of stacked double borders.
- **Kako-framed alert dialogs everywhere**: a 2 dp yellow ring with
  Material's own corner radius stamped exactly on the dialog panel, applied
  across ~30 Fenix call sites and — via an app-wide fragment watcher — on
  the web-content prompts from mozilla-components (JS alert/confirm, repost
  warning, HTTP auth, choice menus) that no call site can reach.
- **Snackbars restyled to the kako flash look** — menu-background fill,
  slot-driven text/action colors, traced outline (was a solid yellow M3
  inverse-surface bar).
- **True-black windows**: the homepage edge-to-edge feature no longer resets
  the window background to grey behind the theme overlay; window and
  status-bar strip stay on the background slot.
- **Kako branding on home and About**: the line-traced logo replaces the
  wordmark fox, the app name is drawn in the text slot, and the
  private-mode mask is yellow-on-black.
- **Destructive buttons stay monochrome** (`colorError` maps to yellow) and
  NeutralButton is restyled to surface colors with a 1 dp outline stroke.
- **Pure-yellow default with one-time migration**: defaults flipped from
  material yellow `#FFEB3B` to pure `#FFFF00`; installs that had persisted
  the old yellow are migrated once at startup, preserving alpha.
- Add-on install/permission dialogs keep the KakoButton style instead of a
  yellow-on-yellow confirm pill; toolbar menu icons tinted with textPrimary.

### Identity & packaging

- `applicationId shiroikuma.kako`, display label **白い熊 火狐** — installs
  side-by-side with stock Firefox/Beta/Nightly. Upstream's shared user id is
  dropped (it would fail signature checks against Mozilla-signed builds).
- `arm64-v8a` only.
- **Launcher icon**: the Nightly fox line-traced in yellow on black
  (master SVGs in `tools/kako/icon/`), rendered to adaptive per-density
  foreground PNGs and legacy round/square webp mipmaps; fox scaled to 63% of
  the canvas for breathing room. The monochrome themed-icon layer keeps
  upstream's silhouette.
- **Honest versionName**: release builds report `<base>+<build>` (e.g.
  `152.0.4+1`) to the package manager instead of upstream's channel-suffixed
  string, matching the APK filename.
- Family versioning: `customBaseVersionName` mirrors the upstream base tag;
  `customBuildNumber` increments per build and resets on each upstream
  adoption.

### Fixes & behavior

- **Fixed an intermittent background crash** (IllegalStateException:
  “Fragment BrowserFragment not attached to a context”) when closing all
  tabs, exiting, or backgrounding: the toolbar middleware's extension
  icon-load suspends through a non-cancellable GeckoResult await; it now
  honors cancellation right after the load instead of running on into a
  detached fragment.
- **Fixed pinned extension buttons missing after cold start**: the toolbar
  middleware now observes the extension registry and rebuilds the trailing
  actions on every change (also covering install/uninstall/enable/disable).
- **Fixed extension toolbar icons flooded solid yellow**: untinted compose
  toolbar ActionButtons now resolve to an unspecified tint so multicolor
  extension icons render as themselves.
- **`./mach build` no longer aborts on a case-sensitive
  `~/.gradle/gradle.properties`**: the 152.0.4 bootstrap parses it with
  configparser, which lowercases keys (collides keys differing only in case)
  and reflows the file on write; the fork reads it case-sensitively and skips
  rewriting when nothing changed.

### Upstream adoptions

- 151.0.4 → 152.0 → 152.0.1 → **152.0.4**, rebased tag-to-tag with the
  `release` branch kept a byte-identical upstream mirror.

### Tooling (repo-side, not in the APK)

- `tools/kako/`: mozconfig (artifact mode, aarch64), build-number bump
  script, icon masters.
- Claude Code skills: the build+sign+deliver pipeline (`kako-build`), the
  upstream adoption procedure (`upstream-new-version`), and auto-delivery of
  fresh builds to the connected phone or the `skhw` host (`/after-build`).
