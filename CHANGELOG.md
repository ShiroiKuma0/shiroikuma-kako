# Changelog — 白い熊 火狐 (`shiroikuma.kako`)

Everything built on top of stock Firefox for Android (Fenix, release channel).
Tags are `<upstream-base>+<build>`; the fork commits live on `custom`, rebased
onto each adopted `FIREFOX_*_RELEASE` tag.

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
