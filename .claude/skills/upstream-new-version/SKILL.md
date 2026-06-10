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

## 4. Rebuild & verify

Run the full **kako-build** pipeline (bump, `./mach build`, gradle assemble,
sign, copy to `~/tmp/`). First build after a major bump re-downloads matching
GeckoView artifacts — slower, expected. Then ask 白い熊 about `adb push` as
usual.

## 5. Push (only with explicit go-ahead)

```bash
git push origin release          # plain ff push
git push --force-with-lease origin custom   # rebase rewrote history
```

Never push without 白い熊's explicit confirmation.
