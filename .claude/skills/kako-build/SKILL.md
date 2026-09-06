---
name: kako-build
description: Build, sign, and deploy 白い熊 火狐 (shiroikuma.kako) — the Firefox fork, both the Android APK and the desktop deb. Use for any build, signing, versioning, APK/deb output, or adb-push task in this repo.
---

# kako-build — 白い熊 火狐 build pipeline

## Two products (hard rule)

This tree builds **both** Firefox for Android and Firefox for Desktop. They share
one upstream tag, one version literal and one build counter, and differ only in
mozconfig and objdir:

| Product | mozconfig | objdir | Output in `~/tmp/` |
|---|---|---|---|
| Android (Fenix) | `tools/kako/mozconfig` | `objdir-kako` | `shiroikuma-kako_<ver>_arm64-v8a.apk` |
| Desktop (Linux amd64) | `tools/kako/mozconfig-desktop` | `objdir-kako-desktop` | `shiroikuma-kako_<ver>_amd64.deb` |

Build the product(s) the change touches — and on an **upstream adoption, always
build BOTH**, never the APK alone. Burn the counter once and pass the same
`<ver>` to the deb script with `--version`.

## Identity

| Fact | Value |
|---|---|
| Upstream | `mozilla-firefox/firefox` (monorepo; Fenix at `mobile/android/fenix`, desktop at `browser/`) |
| Channel tracked | **release** (tags `FIREFOX_<v>_RELEASE`, 4-week cadence) |
| `applicationId` | `shiroikuma.kako` |
| Label | 白い熊 火狐 |
| ABI | `arm64-v8a` only (Android); `amd64` (desktop) |
| Branches | `release` = upstream mirror; `custom` = all fork commits |
| Keystore | `~/.android-keystores/kako-custom.jks`, alias `kako`, pass `kako123` |
| Desktop profile | `~/.mozilla/kako` (via `MOZ_APP_BASENAME`), WM class `kako` |

## The customization commits on `custom`

Layered over the upstream release tag, in this order (keep them small — they
are the rebase watch-points):

1. **Side-by-side identity** — `applicationId shiroikuma.kako`, label
   白い熊 火狐, arm64-only. Files: `mobile/android/fenix/app/build.gradle`,
   Fenix `strings`/manifest placeholders.
2. **Channel gates open** — custom add-on collection setting, secret
   settings, about:config enabled on the release channel. Files: Fenix
   `Config`/`FeatureFlags` checks (`isNightlyOrDebug` sites) under
   `mobile/android/fenix/app/src/main/java/org/mozilla/fenix/`.
3. **Launcher icon** — black/yellow line-traced Nightly fox; master SVGs in
   `tools/kako/icon/`; rendered into Fenix mipmaps/adaptive-icon drawables.
4. **Versioning + tooling** — `tools/kako/` (bump script, build number file,
   this skill set, CLAUDE.md).

## Versioning (family convention — same as appmanager)

Two keys appended to the repo-root `gradle.properties`:

```
customBuildNumber=<n>        # bumped by tools/kako/bump-build.sh on every dev build
customBaseVersionName=151.0.4  # mirrors the upstream base tag; bump manually on each upstream adoption
```

`tools/kako/bump-build.sh` increments `customBuildNumber` by 1 and prints the
full version `<base>+<n>` (gaps from aborted builds are fine). The APK name
embeds both. The counter **resets on each upstream adoption**: when
`upstream-new-version` adopts a new tag, set `customBuildNumber=0` and update
`customBaseVersionName` by hand in the same commit that resolves the rebase, so
the new version's first build is `<newbase>+1`. (Android upgrade ordering uses
versionCode, which is upstream-derived, so resetting the counter is safe.)

## No trackers (hard rule, 白い熊 2026-09-06)

Adjust and Sentry are gone from the APK and must stay gone; Glean is stripped
from Fenix, android-components and longfox. One detection remains — Mozilla
Telemetry, from the prebuilt app-services/Nimbus AARs — and removing it needs
`--enable-appservices-in-tree`, which currently breaks the app (no
`libmegazord.so` in an artifact build; see CLAUDE.md).

**Install and launch every build before delivering it** — 155.0.1+006 scanned
clean and force-closed on startup. Then check the dex:

```bash
unzip -p ~/tmp/shiroikuma-kako_<ver>_arm64-v8a.apk 'classes*.dex' \
  | grep -c -a -o -F "mozilla/telemetry/glean"   # must be 0
```

Two traps, both hit on 2026-09-06: `~/.mozbuild/nimbus-fml/nimbus-fml` is a stub
that panics (rebuild it from
`third_party/application-services/components/support/nimbus-fml`), and deleting
telemetry statements by receiver silently eats real code — a `measure {}` block's
contents, or a name like `TabsTray` that is both a metrics category and a
composable. Audit removals against the original.

## Build pipeline (Gecko is prebuilt; app-services and Kotlin compile locally)

> Verified on the first build (151.0.4+1, 2026-06-10). Timings from that run:
> `./mach build` ≈ 5 min, `fenix:assembleRelease` ≈ 13 min.

```bash
# from repo root.
# ./mach build and ./mach gradle write to ~/.mozbuild and ~/.gradle, which the
# Bash sandbox keeps read-only — run BOTH OUTSIDE the sandbox, or mach dies
# with "OSError: [Errno 30] Read-only file system: ~/.mozbuild/...".
export MOZBUILD_STATE_PATH=$HOME/.mozbuild
export MOZCONFIG=$PWD/tools/kako/mozconfig

FULLVER=$(tools/kako/bump-build.sh)       # bumps customBuildNumber, prints e.g. 151.0.4+7

./mach build                              # fetches prebuilt GeckoView, builds local maven

./mach gradle fenix:assembleRelease       # Fenix release APK (task name confirmed)

# Iterating on Kotlin/Java/resources at the SAME upstream revision: skip
# `./mach build` and run only the gradle task above — android-components and
# Fenix compile from source inside it (confirmed on build 151.0.4+2, ~6 min
# warm). `./mach build` is needed again after an upstream rebase (new GeckoView
# artifacts) or mozconfig change.

# Fixed output path (confirmed — there is no '*unsigned*' variant: Gradle
# debug-signs local release builds, and apksigner below replaces that signature).
APK=objdir-kako/gradle/build/mobile/android/fenix/app/outputs/apk/release/fenix-arm64-v8a-release.apk

OUT=~/tmp/shiroikuma-kako_${FULLVER}_arm64-v8a.apk

zipalign -p -f 4 "$APK" "$TMPDIR/kako-aligned.apk"
apksigner sign --ks ~/.android-keystores/kako-custom.jks \
    --ks-key-alias kako --ks-pass pass:kako123 --key-pass pass:kako123 \
    --out "$TMPDIR/kako-signed.apk" "$TMPDIR/kako-aligned.apk"
apksigner verify "$TMPDIR/kako-signed.apk"
cp "$TMPDIR/kako-signed.apk" "$OUT"
ls -la "$OUT"

# Sanity check — expect shiroikuma.kako / 白い熊 火狐 / arm64-v8a:
~/.mozbuild/android-sdk-linux/build-tools/36.1.0/aapt2 dump badging "$OUT" \
    | grep -E "^package|application-label:|native-code"

# Deliver automatically (no prompt) — invoke the global /after-build skill once
# the signed APK is in ~/tmp/. It runs /adb-check UNSANDBOXED (a sandboxed adb
# daemon cannot see USB devices and reports "no devices/emulators found" even
# with the phone connected), then /adb-push to /sdcard/tmp/ if the phone is
# connected, otherwise /scp to skhw — announcing what landed. Never ask first.
```

## Desktop pipeline (full compile — NOT artifact mode)

```bash
export MOZBUILD_STATE_PATH=$HOME/.mozbuild
export MOZCONFIG=$PWD/tools/kako/mozconfig-desktop   # own objdir: objdir-kako-desktop

./mach build          # full compile against the DISTRO toolchain (clang-20)
./mach package        # → objdir-kako-desktop/dist/kako-*.linux-x86_64.tar.xz

# Reuse the number the APK already burned; omit --version to take a fresh one.
tools/kako/deb/build-deb.sh --version "$FULLVER"
# → ~/tmp/shiroikuma-kako_<ver>_amd64.deb
```

Why it is not an artifact build: `mach bootstrap` cannot help here — every real
toolchain comes from a taskcluster index lookup that will not resolve from a
release-tag checkout carrying our own commits. Hence `--disable-bootstrap` and an
explicit `clang-20` (Tuxedo's default clang is 18; Firefox wants ≥ 19). Engine
changes are therefore **in** scope for desktop, unlike Android.

The deb is a desktop artifact: 白い熊 installs it with `dpkg -i` / `apt install`.
It is **never** pushed to the phone — `/after-build` delivery covers the APK only.

## Standing rules

- **Always build after changes** — bump, assemble, sign, verify, copy to
  `~/tmp/` — without being asked. A task is unfinished until the artifact is
  on disk. Build failure → stop, surface the error verbatim.
- **An upstream adoption builds BOTH products.** Never rebuild only the APK
  and report the version adopted; the desktop must not be left on an older
  base than the Android one.
- **Read the log, not the exit status, after `./mach build`.** A merge-day
  clobber requirement prints "The CLOBBER file has been updated", builds
  nothing, and still exits 0. Fix with `./mach clobber`, per objdir.
- **Run `./mach` with `env -u CLAUDECODE`** — under Claude Code mach suppresses
  the real compiler errors, so a failure reads as an empty success.
- **Deliver automatically via `/after-build`** — after every successful build,
  invoke the global `/after-build` skill without asking. It runs `/adb-check`
  UNSANDBOXED, then `/adb-push` to `/sdcard/tmp/` if the phone is connected,
  otherwise `/scp` to `skhw`, announcing what landed. No transfer prompt, no
  "phone connected?" question. Device: Huawei Mate XT over USB debugging.
- **Never `git push`** without an explicit go-ahead.
- Engine (C++/Rust) changes are out of scope for artifact builds — flag
  before attempting; they force a full compile.
