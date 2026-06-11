---
name: kako-build
description: Build, sign, and deploy 白い熊 火狐 (shiroikuma.kako) — the Firefox for Android fork. Use for any build, signing, versioning, APK output, or adb-push task in this repo.
---

# kako-build — 白い熊 火狐 build pipeline

## Identity

| Fact | Value |
|---|---|
| Upstream | `mozilla-firefox/firefox` (monorepo; Fenix at `mobile/android/fenix`) |
| Channel tracked | **release** (tags `FIREFOX_<v>_RELEASE`, 4-week cadence) |
| `applicationId` | `shiroikuma.kako` |
| Label | 白い熊 火狐 |
| ABI | `arm64-v8a` only |
| Branches | `release` = upstream mirror; `custom` = all fork commits |
| Keystore | `~/.android-keystores/kako-custom.jks`, alias `kako`, pass `kako123` |
| Output | `shiroikuma-kako_<upstreamver>+<n>_arm64-v8a.apk` → `~/tmp/` |

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

## Build pipeline (artifact mode — Kotlin/Java only compiles locally)

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

# Deploy (ONLY after 白い熊's explicit OK). adb must ALSO run outside the
# Bash sandbox — a sandboxed adb daemon cannot see USB devices and reports
# "no devices/emulators found" even with the phone connected and authorized.
# Always push to /sdcard/tmp (白い熊's chosen drop directory on the device).
adb push "$OUT" /sdcard/tmp/
```

## Standing rules

- **Always build after changes** — bump, assemble, sign, verify, copy to
  `~/tmp/` — without being asked. A task is unfinished until the signed APK
  is on disk. Build failure → stop, surface the error verbatim.
- **Always ask before `adb push`** — after every successful build report, ask
  whether to push **via the AskUserQuestion tool** (structured question mode,
  options e.g. "Push to device" / "Not now"), not as a trailing sentence in
  prose; never push (or skip silently) without 白い熊's answer.
  Device: Huawei Mate XT over USB debugging.
- **Never `git push`** without an explicit go-ahead.
- Engine (C++/Rust) changes are out of scope for artifact builds — flag
  before attempting; they force a full compile.
