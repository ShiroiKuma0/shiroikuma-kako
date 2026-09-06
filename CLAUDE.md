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

**Application-services stays prebuilt. The in-tree build does not work (2026-09-06).**
`--enable-appservices-in-tree` builds `third_party/application-services` from source,
which would let its Glean be removed and take the APK to zero trackers. Gecko itself
builds that way in ~16 minutes on this machine and is sound — `geckoview_example`
from the same objdir runs correctly on device — but **Fenix built against in-tree
app-services force-closes at startup** with a native crash just after `GeckoLibLoad`,
and the device yields no backtrace (Huawei suppresses tombstones and the crash
buffer; a release APK cannot use `run-as`).

That crash is **not** ours: 155.0.1+011, built with `third_party/application-services`
checked out pristine and Glean fully present, crashes identically. The path is
unfinished upstream — its `nimbus-fml` is a placeholder that panics with *"a
place-holder for the app-services monorepo migration"*, and its `sync_manager` and
`nimbus` build files ask the catalog for `libs.androidx.core.ktx`, which this tree
does not have. `tools/kako/mozconfig-android-src` keeps the working configuration as
a record; do not ship from it.

**Verify by launching, never by scanning.** 155.0.1+006 scanned clean against all 588
Exodus signatures and force-closed on launch; 155.0.1+008 did the same. A pid a few
seconds after `monkey` is not proof either — check that no crash notification fired:

```bash
adb install -r ~/tmp/shiroikuma-kako_<ver>_arm64-v8a.apk
adb shell am force-stop shiroikuma.kako && adb logcat -c
adb shell am start -n shiroikuma.kako/org.mozilla.fenix.HomeActivity
sleep 15
adb logcat -d | grep -c 'mozac.lib.crash.notification'   # must be 0
adb shell pidof shiroikuma.kako                          # must print a pid
```

## No trackers, ever (hard rule)

Adjust and Sentry are removed at source and must stay gone. Glean is stripped
from everything the fork owns — Fenix, android-components, longfox — and is never
initialised, so nothing is collected or uploaded. **One detection remains and is
currently the floor: Mozilla Telemetry**, from Glean classes inside the prebuilt
application-services and Nimbus AARs. Removing it needs app-services built from
source, which does not work (above). Do not spend time on it again without first
checking whether upstream has finished the monorepo migration.

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
