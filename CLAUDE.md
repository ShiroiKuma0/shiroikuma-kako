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

## Project

- Fork of [mozilla-firefox/firefox](https://github.com/mozilla-firefox/firefox)
  — the Firefox monorepo (canonical Git home since 2025-04-30; the old
  Mercurial at hg.mozilla.org is a read-only mirror).
- **Firefox for Android (Fenix) lives in `mobile/android/fenix`**, alongside
  `mobile/android/android-components` and GeckoView. We track the **release
  channel** (not Beta/Nightly): one upstream cycle every 4 weeks, tags like
  `FIREFOX_151_0_RELEASE`.
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

**Artifact-build constraint:** Fenix/Android-Components-side (Kotlin/Java/
resources) changes only. An engine (C++/Rust/Gecko) patch would force a full
multi-hour compile — avoid; none planned.

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

Output APK: `shiroikuma-kako_<upstreamver>+<n>_arm64-v8a.apk` → always copied
to `~/tmp/`.

**Always build after changes** — finish every set of working-tree edits by
running the full pipeline (bump, assemble, sign, verify, copy to `~/tmp/`)
without waiting to be asked. A task is unfinished until a fresh signed APK is
on disk; if the build fails, stop and surface the error.

**Always ask before `adb push`.** Never push to the device without 白い熊's
explicit confirmation; end every successful build report with that question.

**Never `git push` without 白い熊's explicit go-ahead.**

## Git conventions

- All fork work on `custom`; `release` stays a byte-identical upstream mirror.
- No `Co-Authored-By` / "Generated with Claude Code" trailers in commits or PRs.
- Commit messages: imperative subject, body explains the why.
