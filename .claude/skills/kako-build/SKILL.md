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
full version `<base>+<n>` (monotonic; gaps from aborted builds are fine). The
APK name embeds both. When `upstream-new-version` adopts a new tag, update
`customBaseVersionName` by hand in the same commit that resolves the rebase.

## Build pipeline (artifact mode — Kotlin/Java only compiles locally)

> First-build note: task names/output paths below were planned before the
> first build; if a step disagrees with reality, fix the step HERE after
> confirming, so the skill converges on the truth.

```bash
# from repo root
export MOZBUILD_STATE_PATH=$HOME/.mozbuild
export MOZCONFIG=$PWD/tools/kako/mozconfig

FULLVER=$(tools/kako/bump-build.sh)       # bumps customBuildNumber, prints e.g. 151.0.4+7

./mach build                              # fetches prebuilt GeckoView, builds local maven

# Fenix release APK (confirm exact task on first build: `./mach gradle` lists them)
./mach gradle fenix:assembleRelease

APK=$(find objdir-*/gradle/build/mobile/android/fenix -name '*release*unsigned*.apk' 2>/dev/null | head -1)
# fall back: find mobile/android/fenix/app/build/outputs/apk -name '*.apk'

OUT=~/tmp/shiroikuma-kako_${FULLVER}_arm64-v8a.apk

zipalign -p -f 4 "$APK" /tmp/kako-aligned.apk
apksigner sign --ks ~/.android-keystores/kako-custom.jks \
    --ks-key-alias kako --ks-pass pass:kako123 --key-pass pass:kako123 \
    --out /tmp/kako-signed.apk /tmp/kako-aligned.apk
apksigner verify /tmp/kako-signed.apk
cp /tmp/kako-signed.apk "$OUT"
ls -la "$OUT"
```

## Standing rules

- **Always build after changes** — bump, assemble, sign, verify, copy to
  `~/tmp/` — without being asked. A task is unfinished until the signed APK
  is on disk. Build failure → stop, surface the error verbatim.
- **Always ask before `adb push`** — end every successful build report with
  the push question; never push (or skip silently) without 白い熊's answer.
  Device: Huawei Mate XT over USB debugging.
- **Never `git push`** without an explicit go-ahead.
- Engine (C++/Rust) changes are out of scope for artifact builds — flag
  before attempting; they force a full compile.
