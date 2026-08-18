# Changelog — 白い熊 火狐 (`shiroikuma.kako`)

Everything built on top of stock Firefox (release channel) — the Android browser
(Fenix) and, since 153.0.4+019, the GNU/Linux desktop build. Tags are
`<upstream-base>+<build>`; the fork commits live on `custom`, rebased onto each
adopted `FIREFOX_*_RELEASE` tag, and one tag covers both products.

## 154.0+002 — 2026-08-18

A desktop-only release on the same Firefox **154.0** base. The Android build is
unchanged and stays at `154.0+001`; the APK attached here is that same one.

### Unsigned extensions actually install now

Unsetting `MOZ_REQUIRE_SIGNING` in the mozconfig, which 154.0+001 shipped, turns
out to be half a change. It moves the decision from a compile-time constant to
the `xpinstall.signatures.required` preference — the add-on settings module reads
that preference precisely when the constant is off — but Firefox's own default
for it is `true`, so enforcement simply stayed on and the browser went on
refusing unsigned xpis exactly as stock does. The preference is now set to
`false` in the fork's branding defaults, which is what makes a self-modified
add-on installable without a round trip through AMO. The desktop counterpart of
the Nightly gates opened on Fenix is finally what it claimed to be.

The branding file wins over Firefox's own defaults on purpose, and by a rule
worth writing down: both the loose-directory loader and the one that reads
`omni.ja` sort the default-preference filenames and then walk them **backwards**,
so `firefox.js` is parsed before `firefox-branding.js` and the branding value is
the one left standing.

An unsigned xpi must still declare its own id in `manifest.json`
(`browser_specific_settings.gecko.id`): with no signature there is no certificate
common name to fall back to, and only a temporary install generates one.

## 154.0+001 — 2026-08-18

The base moves to Firefox **154.0** (`FIREFOX_154_0_RELEASE`) — the first major
bump since 153, where the three releases before this one were point releases on
the same branch. Both products are rebuilt on it: the Android APK and the
`amd64` `.deb` carry the same version, as they will from now on.

### The fork announces itself in full

The branding files still carried the bare "火狐" in the two short-name slots, and
Firefox reaches for those wherever space is tight — the about dialog, update
banners, the default-browser prompt. The fork was introducing itself under the
upstream nickname in exactly the places a user looks to find out which browser
this is. All the name slots now read 白い熊 火狐.

### What the major bump moved under us

Sixty-one fork commits replayed onto the new tag; six needed re-shaping, because
upstream's redesign work landed on the same surfaces this fork rebuilds.

The summarize feature grew a toolbar button, adding its case exactly where the
fork adds the two of its own — the pinned-extension action and the long-press
that opens the 白い熊 UI page — so the three now sit side by side. Homepage
wallpapers extend edge-to-edge behind the chrome, which wraps the trailing
toolbar actions in a colour-scheme override; the fork's second toolbar row is
wrapped in it too, so both rows tint alike instead of the lower one drifting. The
tab strip gained its own customisable shortcut, replacing direct reads of the
simple-toolbar shortcut key with a strip-aware one that the fork's row builder
now reads through as well.

Three upstream removals took fork code with them. The menu dialog moved its store
construction out of the composable, so the custom tab's open-in-app dispatch
follows it. The Quick Settings sheet was deleted outright, and with it the fork's
yellow frame on its clear-site-data dialog — that dialog is a Compose one in the
trust panel now, which the fork's theming already covers. And the Acorn palette
dropped its two "on colour" slots, whose last upstream call sites hardcode white,
leaving the fork's two mappings nothing to tint.

One fork patch shrinks outright, which is the happier kind of churn. Custom tabs
and regular tabs are now unified behind a single session state, so the menu's
selected tab already *is* the custom tab it was opened on — and the fork's own
custom-tab URL resolution, written because that used not to hold, is redundant
and gone. The external-app entry it fed is untouched: a link followed from
another app still offers that app in the custom tab menu.

### Both products, every adoption

Adopting an upstream version now means rebuilding **both** the APK and the deb,
written into the repository's own instructions rather than left to memory. The
build counter is burned once and reused, so a version number identifies one
commit of this tree regardless of which artifact it produced.

Recorded alongside it: `./mach build` exits successfully while refusing to build
on a merge-day clobber, so the log has to be read rather than the exit status;
upstream raises toolchain floors between majors, 154 wanting Android
cmdline-tools 21.0 over 20.0; and a rebase can replay without a single conflict
and still fail to compile, where fork code calls something upstream deleted.

## 153.0.4+019 — 2026-08-18

The fork gains a second product. The base is unchanged at Firefox **153.0.4**
(`FIREFOX_153_0_4_RELEASE`), and this release carries both an Android APK and a
GNU/Linux `amd64` `.deb`.

### A desktop browser, built from this same tree

Firefox desktop and Fenix share one upstream, one version literal and one set of
identity assets, so they now share this repository: `tools/kako/mozconfig-desktop`
selects `browser/` into its own objdir, and the two products never meet except in
the icon they are both drawn from. One `git fetch`, one rebase per upstream cycle,
one build counter, one release.

It is a full build rather than an artifact one, and it builds against the distro
toolchain. `mach bootstrap` is of no use here — on Linux it installs almost
nothing and every real toolchain comes from a taskcluster index lookup that will
not resolve from a release-tag checkout carrying its own commits — so bootstrap
is off and clang-20 is named explicitly. RLBox sandboxing of graphite, ogg,
hunspell and expat stays switched on rather than being disabled for convenience.

Add-on signature enforcement is off in the build, the desktop counterpart of the
Nightly gates opened on Fenix: every enforcement site is JavaScript inside
`omni.ja`, so self-modified extensions install without the engine being touched.
The browser installs beside stock Firefox with its own package name, its own
`/opt` prefix, its own WM class and — the part that took finding — its own
profile. `MOZ_APP_REMOTINGNAME` claims in its configure help to affect the profile
name and does not; the path comes from the vendor and basename, so without
`MOZ_APP_BASENAME` the fork would have quietly shared stock Firefox's
`~/.mozilla/firefox`.

The `.deb` is assembled with `dpkg-deb` from the `mach package` tarball, its
dependencies computed by `dpkg-shlibdeps` rather than a hand-written list that
would rot.

### The desktop wears the fork palette

Pure black and `#FFFF00` across the chrome, popups, sidebar and New Tab page,
from a theme that ships as a built-in add-on rather than a built-in theme —
built-in themes install at idle-startup, after the browser has already resolved
which theme is active, so a default pointing at one loses the race on a fresh
profile and the fallback is written as a user preference that wins from then on.

Three surfaces the theme API cannot reach get their own stylesheets. In-content
dialogs and preferences, through a globally registered user sheet, since the
default-browser prompt is its own document and nothing linked from the browser
window reaches it. The New Tab page, delivered the same way because the page
redefines every property involved and outranks a linked sheet. And the window
frame: 白い熊 火狐 draws its own titlebar, so no window manager will frame it, and the
frame is traced here instead — dimming when the window loses focus, matching what
the desktop's colour scheme does for every other application.

Sponsored shortcuts, sponsored stories and the weather widget are off by default.

### Add-ons install from a file

Fenix installs only what a configured AMO collection offers, and a collection can
only hold add-ons published and reviewed on AMO. Personal extensions were
therefore unreachable on the phone. The add-ons screen now takes an `.xpi` off the
device: push one to `/sdcard/tmp/`, pick it, done.

Nothing in the engine needed changing. GeckoView has always documented its
installer as accepting a local `file:` URI, and android-components already exposed
the call with a from-file installation method; Fenix simply never asked. The
picked document is staged into the app's own cache first, since the engine cannot
read the `content:` URI a picker returns, and the copy is removed once the install
settles.

This removes the requirement to be *listed*, not the requirement to be *signed* —
the release engine still demands a Mozilla signature, and an unlisted one
satisfies it. Extensions can now be private and unreviewed and still run on both
products.

### A sync that never starts says so

The toolbar account button could spin for a full minute with several tabs open and
then return to the avatar in silence, having synced nothing — which reads exactly
like a sync that worked.

The account manager drops a sync request with nothing but a log line when it is
not in a connected state, and its state cannot be inspected beforehand. Nothing
then moves the sync status, so the button had no way of knowing. It now checks
shortly afterwards whether a sync actually started, which catches every cause of a
dropped request rather than one guessed at, and a lapsed deadline reports a
failure with a message instead of pretending success. The deadline also stopped
sharing a job with the flash it was supposed to outlive.

## 153.0.4+003 — 2026-08-17

Fork work only; the base is unchanged at Firefox **153.0.4**
(`FIREFOX_153_0_4_RELEASE`).

### The account button's long press opens the account screen

The toolbar account button added in `+002` ran a sync and nothing else, so the
account itself — settings, device name, which engines sync, sign out — stayed
three taps deep in the menu even though its avatar now sat on the toolbar. A long
press goes there, to the same screen the menu's account row opens for the state
we are in: the account settings when signed in, the re-authentication screen for
a session that broke, sign-in when signed out.

That routing was already needed for the tap — which has nothing to sync with
until an account exists — so tap and long press now share one function instead of
repeating the branches. Every look of the button carries the long press: the
avatar, the generic glyph, and the sync, checkmark and warning states, so it
answers mid-sync and during the flash too.

## 153.0.4+002 — 2026-08-17

Fork work only; the base is unchanged at Firefox **153.0.4**
(`FIREFOX_153_0_4_RELEASE`).

### Sync now from an account button on the toolbar

The Mozilla-account avatar — the same picture the menu's account row wears —
takes a seat on the second toolbar row, immediately left of the menu, and a tap
on it runs the same user-triggered sync the account settings page offers. Stock
puts that three taps away (menu → account row → Sync now) for something wanted
several times a day.

The button is also the report on the sync it starts, since a sync otherwise
gives no sign of itself:

- **while it runs** — the avatar becomes a sync glyph;
- **when it lands** — a checkmark held for 1.5 s on the button, plus the
  fork's snackbar flash, 「同期しました」;
- **when it fails** — a warning glyph and 「同期に失敗しました」.

Only a sync started from this button is announced: background and startup syncs
pass unremarked, so the flash always means *the tap you just made finished*. A
sync that never reports an outcome — debounced away, or its observer paused —
gives up after 60 s rather than leaving the glyph up until the next tap. Signed
out, or holding a session that needs re-authenticating, the button opens the
matching account screen instead of being a no-op.

The avatar is a network image, while the trailing toolbar actions are rebuilt on
every extension, tab and sync change, so it is fetched once per URL and display
size and every rebuild is then served from memory — a rebuild never waits on the
network. It is scaled to the same settable size as the pinned extension icons and
stamped with the device density (the toolbar takes an action's size from its
drawable's intrinsic size), then circle-cropped like the menu's avatar. Until the
picture arrives — or if the profile fetch failed — the button wears the generic
avatar glyph.

## 153.0.4+001 — 2026-08-11

A pure upstream adoption: nothing on the fork side changed, so everything here
comes from Mozilla. Base: Firefox **153.0.4** (`FIREFOX_153_0_4_RELEASE`),
adopted from 153.0.3 — the third point release on the same 153 branch.

### The address pill stops crashing on a domain span that doesn't fit

Two hardening fixes land in the toolbar's highlighted URL, both of them guards
against an index that no longer matches the text it points into.

The first is in the scroll measurement. `computeDomainEndScrollValue` handed the
registrable domain's start and end straight to `getPathForRange`, which throws
if either index is out of range or the start is past the end — so a caller
supplying a domain span inconsistent with the URL actually on screen took the
toolbar down rather than merely mispainting it. Both indices are now coerced so
`0 ≤ start ≤ end ≤ length` always holds.

The second is one layer down, in the shared `LinkText` composable. When a link's
substring was blank or absent from the surrounding text, `buildUrlAnnotatedString`
logged the problem and then added the annotation anyway, with the `-1` that
`indexOf` had returned as its start; the crash arrived later, in the text layout
pass, far from the cause. Such links are now skipped, so the text still renders —
minus the broken clickable span. The realistic trigger is a localised string
whose link substring has drifted out of the translated sentence, which makes this
a fix that only ever fires in non-English builds.

The URL-highlighting code is the one part of the toolbar the fork rewrites, and
this is the second release running that upstream has touched it. It replayed
against the two-row layout, the outlined address pill and the pinned extension
buttons without a conflict.

### Tab groups lose their placeholder labels

The tab tray's group rows shipped with stand-in text on the overflow button and
its menu. The four strings are real now — a content description for the three-dot
button, and **Edit**, **Delete** and **Close** on the menu it opens — which also
means they are translatable, where the placeholders were not.

### What else upstream fixed

- **Walking session history can no longer loop forever** — when the entry list
  holds duplicates, the search for an adjacent entry could resolve back to the
  entry it started from and hand callers a cycle. It now skips itself when
  matching a parent's children by docshell ID.
- **The Nintendo webcompat intervention** is widened from a single host to every
  Nintendo domain.
- **Remote settings v2** becomes the default sync version on desktop, and the
  dropped v1 server URL is gone from `AppConstants`.
- **The Sports Widget toggle grew a test seam** — the World Cup end check in
  `HomeSettingsFragment` is now an injectable lambda, so the preference's
  visibility can be tested without moving the clock. Behaviour is unchanged.
- **A localisation sweep** across 48 locale string files in the Fenix app alone,
  plus a translation import from beta and refreshed remote-settings, mobile
  experiment and Merino manifest dumps.

Of the 16 upstream commits in this range, the remainder are desktop-only
(search-telemetry test coverage for single-page-app navigations) or CI
housekeeping (a flaky DuckDuckGo search UI test disabled). Everything
engine-side arrives as prebuilt GeckoView, so an artifact build takes it without
compiling.

### Upstream adoption

- **All 49 fork commits replayed onto the new tag without a conflict**, so every
  patch keeps the shape it had on 153.0.3 — nothing needed re-applying against an
  upstream refactor.
- The build counter resets on adoption, so this is `+001` on the new base.

## 153.0.3+005 — 2026-08-09

A single fork-side feature; the upstream base is unchanged at Firefox **153.0.3**
(`FIREFOX_153_0_3_RELEASE`). Builds `+003` and `+004` were development steps on
the way to this one and were never released.

### Local HTML files open in the browser

Tapping an `.html` file in a file manager never offered 白い熊 火狐 as somewhere to open
it. Two separate things stood in the way, at opposite ends of the browser.

The first is the manifest. Fenix claims `text/html` only for `http`/`https`
URLs, so the `ACTION_VIEW` intent a file manager sends — a `content://` URI from
its FileProvider, with the type set — matched no filter at all and the browser
never reached the chooser. Only `application/pdf` had a `content://` filter. The
fork now claims `text/html` and `application/xhtml+xml` over `content://` too,
written next to the PDF one it mirrors.

The second is the engine, and it is why the manifest change alone would have
produced an entry in the list that opens an empty tab. GeckoView's `content://`
protocol handler is deliberately PDF-only: the channel asks for the stream with
`Allow::PDFOnly`, and the Java side closes anything whose first bytes are not
`%PDF-`. That check is what the fork widens — it now also accepts a document the
provider declares as `text/html` or `application/xhtml+xml`. Everything else is
untouched: the channel never states a content type for PDFs either, so Gecko
sniffs the bytes exactly as before, and the caller's URI grant remains the only
access check.

The C++ that passes `Allow::PDFOnly` is out of reach — an artifact build ships a
prebuilt engine, and changing it would force a multi-hour compile. The check it
asks for, though, lives in the `geckoview` Java module, which compiles from
source alongside Fenix, so the fix stays inside artifact-build territory.

Routing the document through the `content://` URI rather than resolving it to a
`file://` path is a deliberate choice, not a shortcut. A `file://` URL would
have needed all-files access, and would have let any app on the phone name a
path for the browser to read; Android Components blocks the scheme outright for
that reason. Through `content://`, a caller has to hold the document and grant
read access to it, which is the permission model the feature should have.

Relative references inside the page — images, stylesheets, sibling pages —
resolve against the `content://` URI, so a saved page that carries a `_files`
directory beside it will show its text but not necessarily its images. A
self-contained page renders whole.

## 153.0.3+002 — 2026-08-07

A single fork-side fix; the upstream base is unchanged at Firefox **153.0.3**
(`FIREFOX_153_0_3_RELEASE`).

### Being signed in survives a failed profile fetch

Firefox Accounts publishes its signed-in state through `SyncStore`, and the
observer that fills that store dispatched the state only *after* fetching the
account profile — behind the same early return, so a null profile skipped both.
One failed fetch, and a cold start on a flaky network is enough, therefore left
the store at its initial `Unknown` for the whole life of the process. Every
consumer reads that as signed out.

What it looked like: the menu offered **Sign in**, and tapping it closed the
menu and did nothing else. That navigation goes to `TurnOnSyncFragment`, which
pops itself straight back off when an account already exists — and one did, the
whole time, in the account manager the menu never asked. Settings told the same
story from the other side, showing the **Mozilla account** layout placeholder
where the email belongs, next to the generic avatar. Nothing short of restarting
the browser could clear it, and a restart on the same bad network reproduced it.

The account is authenticated whether or not its profile can be fetched, so the
state is now dispatched before that network call. A failed fetch costs the
avatar and the email, not the session. A second change reopens the way back:
the profile-updated observer used to drop its update whenever the store held no
account yet — the exact state a failed fetch leaves behind — and now builds the
account from the profile instead, so a later refresh repairs the display in
place rather than needing the app restarted.

Every consumer of the store was checked before the old "authenticated implies a
known account" assumption was broken. The menu row and its avatar already fall
back to a generic label and icon; recent synced tabs, the bookmarks sync gate
and onboarding all key on the account object rather than the state, and are
unchanged. IP Protection reads the state alone, and now reaches *authenticated*
instead of sitting at *warming up*, which is the correct reading.

The fix lives in Android Components (`SyncStoreSupport`), so it is Kotlin-only
and stays inside artifact-build territory. Its unit tests move with it: the case
that asserted the old behaviour now asserts the new one, and a new case covers a
profile update against an empty store — 15 tests, no failures.

## 153.0.3+001 — 2026-08-06

A pure upstream adoption: nothing on the fork side changed, so everything here
comes from Mozilla. Base: Firefox **153.0.3** (`FIREFOX_153_0_3_RELEASE`),
adopted from 153.0.1 — 153.0.2 was never tagged on the release branch, so the
jump is straight from `.1` to `.3`.

### The address pill's domain highlighting stays in sync

The toolbar emphasizes the domain and fades the rest of the URL, and it measured
that text only when the field changed size. A URL whose text, text style or
viewport width changed without a resize therefore had the new domain's character
range applied to the previous layout, putting the emphasis and the fade in the
wrong place. The layout is now remeasured whenever any of the three changes, and
the scroll that brings the domain's end into view also re-runs when the
highlighted range itself moves.

This is the one upstream change that lands in the part of the toolbar the fork
rewrites; it replayed against the two-row layout, the outlined address pill and
the pinned extension buttons without a conflict.

### What else upstream fixed

- **Media from a Blob plays and seeks again** — same-process blob URL data is
  fetched directly instead of being shipped over IPC, which fixes decoding and
  seeking a Blob read from CacheStorage, and playing a video rebuilt from an
  `ArrayBuffer`.
- **The crash helper exits cleanly** when its Android rendezvous is missed,
  instead of lingering after the process it was waiting for is gone.
- **Google Lens** drops its `uploadByUrl` endpoint, so image search uploads take
  a single path.
- **The devtools inspector** no longer crashes when its listener lookup returns
  dead wrappers.
- **A broad localisation sweep** through the Fenix strings — around 60 locales
  touched, with Uyghur and Tajik gaining substantial coverage — plus refreshed
  add-on blocklist bloom filters and remote-settings dumps.

The rest of the 37 upstream commits are desktop-only (the Smart Window
assistant, Windows drag-and-drop and MAR update packaging) and never reach an
Android build. Everything engine-side arrives as prebuilt GeckoView, so an
artifact build takes it without compiling.

### Upstream adoption

- **All 43 fork commits replayed onto the new tag without a conflict**, so every
  patch keeps the shape it had on 153.0.1 — nothing needed re-applying against an
  upstream refactor.
- The build counter resets on adoption, so this is `+001` on the new base.

## 153.0.1+004 — 2026-08-04

First published build on the 153.0.1 base, and the one that fixes handing a link
to another app. Base: Firefox **153.0.1** (`FIREFOX_153_0_1_RELEASE`), adopted
from 153.0.

### The system chooser no longer loops

Following an `f-droid.org` link with Droid-ify installed raised the system's
"choose an app" dialog two or three times in a row, and the page that finally
arrived was blank. Freezing the other app was the only way through.

Android Components identifies the platform's activity chooser by the literal
package name `android`. EMUI answers with
`com.huawei.android.internal.app/.HwResolverActivity` instead, which fails that
comparison and is therefore taken for a real handler: its component is written
into the intent, and its application label — **System Share** — is offered as
the app to open the page in. Launching that component draws the chooser, which
lists 白い熊 火狐 as a destination for a link the browser is already showing;
picking it there re-enters through the intent receiver into a new tab, whose
redirect hop resolves exactly the same way and asks again. The load cancelled
for the handover is left with nothing to resume it, and that is the blank page.

The chooser is now identified by the absence of an `IntentFilter` match — every
genuine handler carries one and no chooser does — so any vendor's chooser is
caught, not just AOSP's. The best real handler is then targeted directly, with
our own package excluded from that choice; the existing self-exclusion only
guarded the branch where a default app had already been set. A link with one
external handler opens straight in it, and nothing offers the browser a page it
is already displaying.

### Open in app, from the custom tab menu

A link followed from another app lands in a custom tab, whose menu could only
pass the page onwards to the browser itself. The app that actually claims the
link now appears there by name, so it is one tap either way instead of a detour
through 白い熊 火狐 and the main menu.

A custom tab carries its own URL, unrelated to the browser's selected tab, so
the menu item and the middleware behind it both resolve the app link by custom
tab session id rather than reading whichever tab the browser had selected.

### Build counter padded to three digits

Version names, and so APK filenames, now read `153.0.1+004` rather than
`153.0.1+4`, so builds sort in build order in `~/tmp`, in the phone's file
manager and on the releases page. The stored property stays a plain integer, and
releases already published keep the names they went out under.

### Upstream adoption

- **All 38 fork commits replayed onto the new tag without a conflict**, so every
  patch keeps the shape it had on 153.0 — nothing needed re-applying against an
  upstream refactor this time.
- **Upstream's fixes are engine-side**: screen handling reworked across every
  platform widget backend (`ScreenHelperAndroid` among them), the vendored
  `memmap2` crate updated, and app-provided search-engine configuration — plus a
  broad localisation sweep through the Fenix strings. An artifact build takes
  all of that as prebuilt GeckoView rather than compiling it.

## 153.0+6 — 2026-07-31

Two follow-ups to the automation contract: the browser now states which backup
items start ticked instead of leaving the caller's picker to assume it, and a
running export can be stopped from outside. Base: Firefox **153.0**
(`FIREFOX_153_0_RELEASE`).

### The picker's `on` / `off` column

- **`LIST_CATEGORIES` answers a fourth field.** Each line is now
  `id⇥label⇥parent⇥on|off`, the contract's optional positional field for whether
  an item starts ticked in the caller's picker — which 自由作業盤's 保存復元
  project redraws from this reply every time it is opened. The field is optional
  and absent means `on`, so nothing that already read the old two-field reply
  breaks; the ids, labels and their order are unchanged.
- **Every category here is `on`.** The `off` flag is for something large,
  derived and re-creatable — a regenerable thumbnail cache, downloaded map tiles
  — and this browser exports none of that. Sending the field anyway is the
  point: it is the app stating a default rather than the picker assuming one,
  and any category added later inherits a field that is already there.
- **Nothing here nests**, so the parent field goes out empty — the third field
  is present but blank, as the positional format requires.
- **The in-app Export / Import sheet seeds its checkboxes from the same flag**,
  so the panel 白い熊 sees on the phone and a sister app's picker open on one
  answer rather than two independently maintained ones.

### Cancelling a running export

- **`shiroikuma.kako.action.CANCEL_EXPORT`** is a third action on the same
  exported receiver. It was added to the contract after a cancelled export in
  another app carried on to the end and delivered a backup that had been
  stopped: a 中止 button that only stops listening does not stop anything.
- **It is on the receiver, not on a service, because a service cannot be
  reached.** A stop path living on an app's own service is
  `android:exported="false"` — correctly so — and a third-party caller cannot
  start it, which is how an app ends up with working stop buttons that the
  automation batch cannot press.
- **Token-gated like the others, and fire-and-forget.** It answers nothing at
  all — not `OK:`, and not even a bad-token error. Arriving when nothing is
  running, or after the export has already finished, it is a silent no-op:
  not an error, not a reply, not a crash. An optional correlation id selects
  which run to stop; absent, it stops whatever is running.
- **The export unwinds at the next category boundary.** The cancel flips a
  `@Volatile` flag that the write loop reads between categories, so nothing is
  killed, no thread is interrupted mid-`write()`, and no process exits.
- **The cancelled run answers its own request with `ERROR:cancelled`**, through
  the normal reply channel and under the same single-fire guard that prevents a
  success and an error both firing. It is sent even though the canceller may
  have stopped listening, because that reply is what proves the run ended
  rather than carrying on unseen.

### The archive is now written under a `.part` name

- **Exports write `<name>.zip.part` and take the final name only once the ZIP is
  whole.** A cancel — or any failure — deletes the partial in the same `finally`
  that handles every other error, so a stopped export leaves the backup
  directory **exactly as it found it**: no short archive that looks like a
  backup, no stray `.part`. The "last export" query never matches a `.part`
  either. A cancel landing in the closing moments still counts as a cancel:
  the flag is checked once more before the file is put into place.
- **The partial is created as `application/octet-stream`.** Given a display name
  that does not end in `.zip`, a storage provider appends the MIME type's own
  extension and hands back `<name>.zip.part.zip` — which is not the file
  anybody asked for.
- **A provider that refuses to rename falls back to a copy.** Renaming is
  optional for a document provider, so rather than losing an export that ran all
  the way to the end, the finished `.part` is copied to the final name and
  removed.

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
