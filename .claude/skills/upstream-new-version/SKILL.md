---
name: upstream-new-version
description: Sync 白い熊 火狐 to a newer upstream Firefox release — fetch upstream, fast-forward the release mirror, rebase custom onto the new FIREFOX_*_RELEASE tag, rebuild. Use when 白い熊 asks to update/sync/rebase to a new Firefox version.
---

# upstream-new-version — sync 白い熊 火狐 to a new Firefox release

Upstream ships a new major every ~4 weeks (plus point releases on the same
branch). We rebase tag-to-tag; skipping a cycle and jumping two tags is fine.

## 1. Check what's new

```bash
git fetch upstream --tags
# newest release tags, newest last:
git tag --list 'FIREFOX_*_RELEASE' --sort=creatordate | tail -5
# current base of custom:
git merge-base custom upstream/release | xargs git describe --tags
```

If no newer `FIREFOX_<v>_RELEASE` tag than the current base: report "already
current" and stop.

## 2. Fast-forward the mirror

```bash
git checkout release
git merge --ff-only upstream/release     # mirror must stay byte-identical
```

If `--ff-only` fails the mirror was contaminated — STOP and investigate;
never force-push or merge-commit on `release`.

## 3. Rebase `custom`

```bash
git checkout custom
git rebase FIREFOX_<v>_RELEASE
```

Conflict watch-points (our commits are deliberately small):

- `mobile/android/fenix/app/build.gradle` — applicationId/ABI/version wiring.
- Fenix `Config`/`FeatureFlags` channel-gate sites (custom collection,
  secret settings, about:config) — upstream moves/renames these
  occasionally; re-find the `isNightlyOrDebug` checks and re-apply intent,
  don't force the old patch shape.
- Launcher icon resources (mipmaps / adaptive icon XML) — regenerate from
  `tools/kako/icon/icon-final.svg` if upstream restructured drawables.
- `tools/kako/` — ours alone, conflicts only if upstream adds same path
  (won't).

Resolve minimally, keeping each fork commit's scope intact. After the
rebase: `git log --oneline FIREFOX_<v>_RELEASE..custom` must list exactly
the fork commits, nothing more.

Then update `customBaseVersionName` in the repo-root `gradle.properties` to
the new upstream version and reset `customBuildNumber=0` (family convention:
base mirrors upstream, bumped manually on adoption; the build counter restarts
each adoption, so the new version's first build is `<newbase>+1`).

## 4. Rebuild BOTH products & verify (hard rule)

An adoption is **not** done until **both** the Android APK and the desktop deb
have been rebuilt on the new tag. Never ship the APK alone and call the version
adopted — the two products are one fork, and a desktop left on the previous base
is a silently stale browser 白い熊 goes on using. Both artifacts carry the *same*
`<base>+<nnn>`, so bump once and reuse the number.

```bash
export MOZBUILD_STATE_PATH=$HOME/.mozbuild

# --- Android -------------------------------------------------------------
export MOZCONFIG=$PWD/tools/kako/mozconfig
FULLVER=$(tools/kako/bump-build.sh)        # e.g. 154.0+001 — burn the number ONCE
./mach build && ./mach gradle fenix:assembleRelease
# …zipalign + apksigner + copy to ~/tmp/ per the kako-build skill…

# --- Desktop -------------------------------------------------------------
export MOZCONFIG=$PWD/tools/kako/mozconfig-desktop
./mach build && ./mach package
tools/kako/deb/build-deb.sh --version "$FULLVER"   # --version REUSES the number
```

Watch-points on a major bump — both hit on the 154 adoption:

- **Merge-day clobber.** `./mach build` refuses with "The CLOBBER file has been
  updated" and does nothing (it still exits 0 — read the log, not the status).
  Run `./mach clobber` and rebuild. This applies to **each objdir separately**.
- **Toolchain floor.** Upstream raises requirements between majors; 154 wanted
  Android cmdline-tools 21.0 over 20.0. Install just the SDK with
  `./mach python python/mozboot/mozboot/android.py --artifact-mode
  --no-interactive` — full `mach bootstrap` is not needed and touches more.
- **Fork code against removed APIs.** The rebase can replay cleanly and still
  fail to compile, because a conflict-free hunk may call something upstream
  deleted. Read the `e:` lines and migrate to the replacement rather than
  reverting the fork patch — sometimes the fork's workaround has become
  redundant and correctly shrinks.

Then deliver via the global `/after-build` skill (adb-push if the phone is
reachable, else scp to skhw — no prompt), per kako-build's standing rule. The
deb is not pushed to the phone; it stays in `~/tmp/`.

## 5. Push (only with explicit go-ahead)

```bash
git push origin release          # plain ff push
git push --force-with-lease origin custom   # rebase rewrote history
```

Never push without 白い熊's explicit confirmation.
