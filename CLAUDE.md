# CLAUDE.md — 白い熊 火狐 (`shiroikuma.kako`)

This file is loaded automatically by Claude Code. It captures the fork's facts,
build pipeline, and operating conventions so a fresh session can pick up
exactly where the previous one left off. For anything about **building,
signing, versioning, sideloading, or syncing to a new upstream version**, the
authoritative guidance is the skills under `.claude/skills/`:

- **`kako-build`** — identity, branch/remote model, the customization commits,
  versioning, signing, and the build + sign + `adb push` pipeline.
- **`upstream-new-version`** — check for a newer upstream release tag,
  fast-forward `release`, rebase `custom` onto it, and rebuild.

## Two products, one tree (hard rule)

This fork ships **both** Firefox for Android and Firefox for Desktop from the
same tree, the same upstream tag and the same build counter:

| Product | mozconfig | objdir | Artifact in `~/tmp/` |
|---|---|---|---|
| **Android** (Fenix) | `tools/kako/mozconfig` | `objdir-kako` | `shiroikuma-kako_<ver>_arm64-v8a.apk` |
| **Desktop** (GNU/Linux amd64) | `tools/kako/mozconfig-desktop` | `objdir-kako-desktop` | `shiroikuma-kako_<ver>_amd64.deb` |

**Adopting a new upstream version means updating and building BOTH.** Never
rebuild only the APK and call the adoption done, and never leave the desktop
product on an older base than the Android one — they are one fork, and a version
number identifies one commit of this tree regardless of which artifact it
produced. Both therefore carry the *same* `<ver>` (`--version` on the deb script
reuses the number the APK already burned).

## Project

- Fork of [mozilla-firefox/firefox](https://github.com/mozilla-firefox/firefox)
  — the Firefox monorepo (canonical Git home since 2025-04-30; the old
  Mercurial at hg.mozilla.org is a read-only mirror).
- **Firefox for Android (Fenix) lives in `mobile/android/fenix`**, alongside
  `mobile/android/android-components` and GeckoView. We track the **release
  channel** (not Beta/Nightly): one upstream cycle every 4 weeks, tags like
  `FIREFOX_151_0_RELEASE`.
- **Firefox for Desktop lives in `browser/`**, branded from
  `browser/branding/kako` with the palette shipped as the built-in add-on
  `browser/extensions/kako-theme`. Same upstream tag as Android.
- `applicationId`: `shiroikuma.kako` (installs side-by-side with stock
  Firefox/Beta/Nightly).
- Display label: **白い熊 火狐**.
- Remotes: `origin` → `ShiroiKuma0/shiroikuma-kako` (GitHub fork),
  `upstream` → `mozilla-firefox/firefox`.
- Branches: `release` is a pure upstream mirror of the upstream release
  branch; **`custom`** carries all fork work, rebased onto each upstream
  release tag. Pushes go to `origin/custom`.

## Why this fork exists (the key requirement)

白い熊 installs extensions from a **custom AMO collection** — a feature stock
Firefox hides behind `Config.channel.isNightlyOrDebug` (Nightly-only). The
fork's defining patch flips those Fenix-side channel gates open on the release
channel (custom add-on collection setting, secret settings, about:config).
Extensions from a collection are AMO-signed, so the signing enforcement baked
into the prebuilt release GeckoView never bites — which is what lets us use
fast **artifact builds** (prebuilt engine; only Kotlin/Java compiles locally).

**Artifact-build constraint (Android only):** Gecko itself is still a prebuilt
artifact — an engine (C++/Rust/Gecko) patch would force a full multi-hour
compile, so avoid it; none planned. The desktop product has no such constraint:
it is a full compile from source either way.

**Android builds from source, and application-services with it (2026-09-06).**
`tools/kako/mozconfig-android-src` is the Android build: Gecko compiles from source
(~16 min) so that `--enable-appservices-in-tree` works, which is what lets Glean be
removed from application-services at source and takes the APK to **zero detected
trackers**. `tools/kako/mozconfig` is the old artifact build — faster, but one
tracker, because prebuilt app-services AARs carry Glean.

**The megazord must be staged, or the app force-closes.** Between `./mach build` and
`./mach gradle fenix:assembleRelease`, run `tools/kako/stage-megazord.sh`. Gecko
links `libmegazord.so` against `libmozglue.so` — it links its allocator into every
shared library it builds — but app-services is driven from Kotlin over JNA, where
allocations come from bionic. Freeing those through mozjemalloc segfaults in
`arena_dalloc` (`mozjemalloc.cpp`) the moment `places.sqlite` is opened. The script
stages Mozilla's self-contained megazord, whose `NEEDED` is only libdl/libc/liblog/
libm, and refuses to stage one that links mozglue. 155.0.1+008, +011 and +014 all
force-closed for want of this step.

**Verify by launching, never by scanning.** 155.0.1+006 and +008 scanned clean and
force-closed. A pid seconds after `monkey` is not proof either — check that no crash
notification fired, and load a page, which exercises the places storage that crashes:

```bash
adb install -r ~/tmp/shiroikuma-kako_<ver>_arm64-v8a.apk
adb shell am force-stop shiroikuma.kako && adb logcat -c
adb shell am start -n shiroikuma.kako/org.mozilla.fenix.HomeActivity
sleep 18
adb logcat -d | grep -c 'mozac.lib.crash.notification'   # must be 0
adb shell pidof shiroikuma.kako                          # must print a pid
```

Native crashes give nothing on this phone (Huawei suppresses tombstones and the
crash buffer). To debug one, set `debuggable = true` on the release build type, then
`adb shell "run-as shiroikuma.kako cat 'files/mozilla/<profile>/minidumps/<id>.dmp'"`
and walk it with `~/.mozbuild/minidump-stackwalk`; resolve offsets against the
unstripped libraries in `objdir-kako-src/dist/bin`. That is how the crash above was
found. Remove the flag before shipping.

## No trackers, ever (hard rule)

The APK contains **zero trackers** as of 155.0.1+019. Adjust and Sentry are deleted
at source; Glean is stripped from Fenix, android-components, the longfox module
**and** the vendored application-services, and the Glean SDK is not in the APK at
all. `lib-crash`'s two crash-upload services are deleted as well — see below.

**155.0.1+017 was NOT tracker-free, whatever the line here used to claim.**
応用管理 showed it as "2 trackers" all along, both from `lib-crash`:
`mozilla.components.lib.crash.service.SendCrashReportService` and
`SendCrashTelemetryService`. The claim survived because the only check ever run was
the Glean grep below, and a Glean grep is not a tracker check — it answers one
signature out of Exodus's 588. Deleted in +019 together with `CrashReporter`'s
`sendCrashReport`/`sendCrashTelemetry` and their manifest entries; crash *handling*,
the local crash database and `CrashNotification` all stay, and `MozillaSocorroService`
stays registered because `CrashReporter`'s `init` requires a non-empty service list —
so the crash prompt's own Report button is the one remaining route to Mozilla, and it
takes an explicit tap (白い熊, 2026-09-09).

An upstream adoption will drag all of it back in. Strip it again, and check the dex
and the manifest rather than trusting the source — **every name below, not just
Glean**:

```bash
APK=~/tmp/shiroikuma-kako_<ver>_arm64-v8a.apk
for c in mozilla/telemetry/glean com/adjust/sdk io/sentry \
         mozilla/components/lib/crash/service/SendCrashReportService \
         mozilla/components/lib/crash/service/SendCrashTelemetryService; do
  printf '%-58s %s\n' "$c" "$(unzip -p "$APK" 'classes*.dex' | grep -c -a -o -F "$c")"
done                                                  # every count must be 0
```

And confirm against the tool that actually judges it: 応用管理 → the app's page →
the trackers pill. A dex grep only finds what it was told to look for; that pill is
the number 白い熊 sees.

The `metrics.yaml` files under `third_party/application-services` are **kept**: the
Rust `build.rs` of each component runs `glean_parser` over them and the build fails
without them. They no longer reach the dex, because the Glean Gradle plugin that
turned them into Kotlin is gone from those modules.

That means, in Fenix, android-components, the longfox module *and* the vendored
application-services: no `libs.mozilla.glean`, no `glean-gradle-plugin`, no
`metrics.yaml`/`pings.yaml`, and no `GleanMetrics` call sites. An upstream
adoption will drag all of it back in; strip it again before shipping, and check
the dex rather than trusting the source:

```bash
unzip -p ~/tmp/shiroikuma-kako_<ver>_arm64-v8a.apk 'classes*.dex' \
  | grep -c -a -o -F "mozilla/telemetry/glean"      # must be 0
```

**When stripping telemetry, real code hides inside it.** Deleting statements by
their receiver silently removed `PlacesConnection.runMaintenance`'s vacuum,
optimize and checkpoint calls, Nimbus's `fetchExperiments()`, and — worst — the
whole `TabsTray(...)` composable, because `TabsTray` is both a Glean metrics
category and the tab manager's Compose function. Audit every removal: anything
inside a `measure {}` wrapper, and any name used in call form (`Name(`) rather
than as `Name.metric.record(...)`.

**`nimbus-fml` in `~/.mozbuild` is a stub** that panics with "a place-holder for
the app-services monorepo migration"; builds only passed while Gradle had
`nimbusValidate` cached. Build the real one from the vendored source and install
it over the stub:

```bash
cargo build --release --manifest-path \
  third_party/application-services/components/support/nimbus-fml/Cargo.toml
cp target/release/nimbus-fml ~/.mozbuild/nimbus-fml/nimbus-fml
```

The monorepo also forces `allWarningsAsErrors` on every Kotlin task, which the
UniFFI-generated bindings trip; `ProjectPlugin.kt` exempts the vendored
application-services and uniffi-bindgen trees.

## Target device & environment

- Huawei Mate XT (tri-fold, Android 13, non-rooted). ABI shipped: `arm64-v8a` only.
- Dev machine: Tuxedo OS Prague (TZ Europe/Prague, Japanese locale — Java
  tools print Japanese).
- Mozilla toolchain: `~/.mozbuild` (created by `./mach bootstrap`, GeckoView
  Artifact Mode choice).
- Keystore: `~/.android-keystores/kako-custom.jks`
  - alias: `kako`
  - keystore + key password: `kako123`

## Icon

Black/yellow line-traced Nightly fox (#000000 bg, #FFFF00 strokes, width 1.8
on the 77.4×80 viewBox, paths black-filled and stroked in reordered z-order so
ear/muzzle/cheek/tail contours stay visible). Master SVGs:
`tools/kako/icon/icon-final.svg` (+ `-round`). Previews mirrored at
`~/tmp/shiroikuma-kako-icon-preview{,-round}.png`.

## 白い熊 UI default palette

The 白い熊 火狐 UI (KakoTheme slots + `kako_theme.xml` overlay) seeds and resets to black
`#000000` + **pure yellow `#FFFF00`** (`KAKO_PALETTE_BLACK` / `KAKO_PALETTE_YELLOW` in
`fenix/kako/KakoTheme.kt`; the alpha-variant resources in `kako_theme.xml` share the same base).
Never use material yellow `#FFEB3B` for fork UI defaults.

## What the app-data backup covers (and the trap in it)

`KakoExim.Cat` is the whole contract — **seventeen** categories, archive format
**3** (155.0.1+021, 2026-09-09). It was eight and format 1 that morning, twelve
and format 2 by the afternoon, and a restore onto a fresh phone still came up
with default settings, the wrong search engine and no extensions.

**Every one of those failures was a store nobody had looked in.** The rule that
keeps coming back: *a Fenix preference key that names something is not where that
something is stored* (`pref_key_search_engine` exists and is always empty;
`app_settings.json` used to carry `pref_key_open_tabs_count = 6` over a profile
with no tabs in it). Before trusting a category, export one and read the JSON.

Where the settings actually live, all of it now exported:

- **Fenix has TWO preferences files.** `Settings` reads `fenix_preferences`, but
  only `AccessibilityFragment` points `preferenceManager` at it — every other
  settings screen leaves the AndroidX default, so each switch also persists to
  `<package>_preferences`, and a few settings (external download manager,
  automatic download clean-up, what's-new) live *only* there. Measured on 白い熊's
  phone 2026-09-09: `fenix_preferences` 82 keys, `shiroikuma.kako_preferences`
  **112** — and only the first was ever backed up. `Cat.APP_SETTINGS` now sweeps
  **every** file in `shared_prefs/` bar a deny-list, so a file upstream adds next
  cycle travels without anyone noticing it exists.
- **Three kinds of file must never be swept.** `*_kp_pre_m` / `*_kp_post_m` hold
  the key to the logins and autofill databases (plaintext on release), and
  `loginsCrypto` / `autofillCrypto` hold the `canaryPhrase` encrypted under it.
  The entries themselves travel in `Cat.LOGINS`/`Cat.CARDS` and are re-encrypted
  under the new phone's key; a restored canary then fails to decrypt and
  application-services treats the whole store as unreadable. `kako_automation`
  holds this app's automation token — restoring another phone's breaks the
  pairing 応用管理 has, and exporting it puts the token in the archive. And any
  migration stamp (`pref_search_migrated`, `logins_undecryptable_cleaned`) tells
  the new phone that work it has never done is already done.
- **A custom search engine is a file, not a preference** —
  `filesDir/search-engines/<base64 id>.xml`. Restoring
  `mozac_feature_search_metadata` alone writes the *id* of an engine the new
  phone has never heard of, and the middleware silently falls back to Google.
- **`about:config` is Gecko's, in `prefs.js` inside the salted profile
  directory.** Export parses it; import cannot write it (Gecko owns the file, and
  on a phone that has never been opened the profile does not exist yet), so it
  stages the prefs and `FenixApplication` hands them to the engine on the next
  start through `setBrowserPref`, which the dispatcher queues until Gecko is up.
  `prefs.js` is mostly *not* settings: of 52 user-branch prefs on 白い熊's phone,
  four were his (`intl.accept_languages`, `intl.locale.requested`,
  `browser.translations.neverTranslateLanguages`, one ETP flag) and the rest were
  GPU caches, blocklist stamps and per-extension migration flags. The deny-list in
  `KakoGeckoPrefs` is what separates them, and it drops names that state a *time*
  or a *schema* — never a fragment like "cache", which would take
  `browser.cache.disk.enable` with it.
- **Pinned shortcuts, site permissions, the never-save-a-password list and tab
  collections are four Room databases.** Site permissions are read and written
  through `OnDiskSitePermissionsStorage`, never `geckoSitePermissionsStorage` —
  the latter's `all()` waits on the runtime, and a backup runs headlessly in a
  service where Gecko may never start.

**An import must flush every preferences file, not the ones it remembers.**
`edit {}` is `apply()`; 応用管理 force-stops the app the instant the import replies
OK, and `SIGKILL` drains no queued write. `KakoExim.flushPrefs` enumerates
`shared_prefs/` and `commit()`s each. The search-engine choice was lost exactly
this way on every restore before +021.

**An export must wait for the engine before listing extensions.**
`store.state.extensions` is filled a few hundred milliseconds after the process
starts, so a headless export sampled it empty — and an empty list is not a failed
backup, it is a successful backup of no extensions. `KakoAddons.installedJson`
awaits `WebExtensionSupport.awaitInitialization()` now, as `restore` already did.

Four things still do not survive a round trip:

- **Visit timestamps.** `HistoryStorage` has no timestamped write, so restored
  history is dated to the restore. URLs and titles travel; frecency rebuilds.
- **Per-tab engine state.** Scroll position, form contents and per-tab session
  history are Gecko's opaque blob; restored tabs come back unloaded.
- **Tracking-protection and cookie-banner exceptions.** `add()` on
  `TrackingProtectionExceptionStorage` takes an `EngineSession`, not a URL —
  there is no way to restore one without loading the site.
- **An extension's own configuration** (`storage.local` — uBlock's filter lists,
  say). GeckoView exposes no export for it; the add-ons come back, their settings
  do not.

**The archive contains the Firefox Account session** (白い熊's explicit decision,
2026-09-09, after being told what it means): the refresh token is in
`account.json`, in a plain ZIP unless 応用管理's encryption is on. Anything that can
read a backup can sign in as him. Never write one to shared storage as scratch.

## Build & deploy pipeline (summary — see `kako-build` skill)

Outputs, both always copied to `~/tmp/`:

- Android: `shiroikuma-kako_<upstreamver>+<nnn>_arm64-v8a.apk`
- Desktop: `shiroikuma-kako_<upstreamver>+<nnn>_amd64.deb`

**Always build after changes** — finish every set of working-tree edits by
running the full pipeline (bump, assemble, sign, verify, copy to `~/tmp/`)
without waiting to be asked. Build **the product(s) the changes touch**; a
change under `mobile/android/` needs the APK, one under `browser/` needs the
deb, and anything shared — an upstream adoption above all — needs **both**. A
task is unfinished until the fresh artifact(s) are on disk; if a build fails,
stop and surface the error.

**Deliver automatically via `/after-build`** — do not ask "shall I push?".
The skill walks the reachability chain and ships once. (This supersedes the old
"always ask before `adb push`" rule, dropped 2026-07-09.) The deb is a desktop
artifact: it stays in `~/tmp/` for 白い熊 to install, it is not pushed to the
phone.

**Never `git push` without 白い熊's explicit go-ahead.**

## Git conventions

- All fork work on `custom`; `release` stays a byte-identical upstream mirror.
- No `Co-Authored-By` / "Generated with Claude Code" trailers in commits or PRs.
- Commit messages: imperative subject, body explains the why.
