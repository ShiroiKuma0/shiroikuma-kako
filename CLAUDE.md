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

## The backup carries the extensions' own data

**This app is the only thing that reads this app's data, and that is the design,
not a limitation** (白い熊, 2026-09-09). 応用管理 does not need to reach inside
`/data/data/shiroikuma.kako` and is not supposed to — the app-supplied data door
exists precisely so that kako reads its own internal data and hands it over. Do
not read the "Permission denied" from a shell `du` as a problem to solve; nothing
about a backup gets better by giving the caller more access.

Which means every shortfall in a backup is **ours**, and there is nowhere else to
look. On 2026-09-09 `KakoExim` exported 229.5 kB of a **2.78 GB** profile: the
backup finished in 6.8 s and reported success, because the exporter had never been
told the extension storage existed. (Measured with `dumpsys diskstats`: main phone
2.78 GB of app data, the phone restored onto 0.09 GB. The 116-byte `data0.tar.zst`
in 応用管理's log is `/Android/data/shiroikuma.kako`, which this app leaves empty —
it is not where anything lives.)

Nearly all of the missing bulk is extension storage, which Gecko keeps in the
profile under quota-manager origin directories:

| Path under the profile | What | Category |
|---|---|---|
| `storage/default/moz-extension+++<uuid>^userContextId=4294967295/` | `storage.local` — each add-on's own options | `EXT_SETTINGS` |
| `browser-extension-data/<id>/` | the pre-IndexedDB JSON backend, same thing | `EXT_SETTINGS` |
| `storage/default/moz-extension+++<uuid>/` | everything in `indexedDB` — **the bulk**, one 1.94 GiB yomitan `.sqlite` | `EXT_DATABASES` |

Split into two categories on 白い熊's instruction (2026-09-09) so a routine backup
can leave the gigabytes out; both default on.

**Both live under `default`, and `^userContextId` is the ONLY thing separating
them.** `ExtensionStorageIDB` opens `storage.local` with an ordinary storage
principal — `storage/permanent` is empty on this phone — isolated into the
reserved context `WEBEXT_STORAGE_USER_CONTEXT_ID` = `-1 >>> 0` = 4294967295
(`ExtensionStorageIDB.sys.mjs:25`). Splitting on the persistence directory, as the
first cut did, put every add-on's settings into the gigabytes category, so
unticking the dictionaries would have silently dropped the settings too. `-shm`
files are skipped (scratch, rebuilt from the `-wal`); `-wal` files travel.

**The `moz-extension` UUID map is load-bearing.** Those directory names are per
install — Gecko mints a fresh UUID per extension per profile and records the map
in `extensions.webextensions.uuids`. Restore the directories without it and they
belong to nobody: the add-on comes back, asks for storage under a *new* UUID, and
finds an empty origin with gigabytes sitting beside it referenced by nothing. So
`Cat.EXT_SETTINGS` carries that pref even though `KakoGeckoPrefs` refuses it as an
`about:config` value, and the `ExtensionStorageIDB.migrated.*` flags with it.

**Nothing may be written into a profile Gecko is using** — these are live SQLite
databases. An import unpacks to `filesDir/kako_pending_extdata/` and
`KakoExtData.applyPending` moves the tree into the profile from
`FenixApplication.onCreate` **before `setupEarlyMain()`**, which is the line that
creates the engine. The move is a rename inside `filesDir`, so it needs no second
copy of 2.7 GB. No profile yet (installed, never opened) → the staging is left for
the next start.

**The import streams; it must never hold the archive.** `KakoExim.import` takes an
`InputStream` and makes ONE pass: small entries into a map, bulk entries straight
to staging. The old `ByteArray` + spool-file path, and its 512 MB
`MAX_IMPORT_BYTES` cap, are gone — both were an instant OOM at this size. There is
therefore no pre-pass to ask what the archive contains (a pipe cannot be rewound),
so the reply counts what was *restored* rather than what the archive claimed.
`IMPORT_TIMEOUT_MS` is 45 min: 2.7 GB is minutes of pure I/O, and a restore that
works but is declared timed out is the worst of both.

**Never call `ZipOutputStream.setLevel` mid-archive.** The first cut stored the
bulk entries uncompressed, on the theory that IndexedDB compresses its own
records. Both halves were wrong. The data deflates **6:1** (1.94 GiB of yomitan
dictionaries → a 337 MB archive), and `ZipOutputStream` shares one `Deflater`
across the whole archive, so changing its level between entries makes the next
`deflate()` call `deflateParams` and flush into the bitstream. Sizes and CRCs stay
correct, so the central directory looks perfect and `ZipFile` is happy; only a
reader that inflates sees it — `ZipException: invalid block type`. That is
`KakoExim.import`'s own reader, so the archive could not have been restored.

**Verify an archive with BOTH readers, and make them agree.** Three checks, three
disjoint blind spots (learned the hard way alongside the 辞書 chat, 2026-09-09,
whose mirror-image bug was in the per-entry data descriptors):

| check | sees | blind to |
|---|---|---|
| `cen_off + cen_size == EOCD` | central directory placement | descriptors, deflate streams |
| `ZipFile` | central directory, EOCD/ZIP64 arithmetic | descriptors, deflate streams |
| `ZipInputStream`, every entry read to EOF | local headers, descriptors, **that bytes inflate** | central directory |

The gate: run both readers on-device and require the same entry count and byte
total. `.claude/skills/` has no harness for this; the throwaway is a 40-line
`AndroidZipTest.java` → `d8` → `adb shell CLASSPATH=classes.dex app_process /`.
A desktop reader cannot stand in for either — OpenJDK 21, `unzip`, Python
`zipfile` and 7z all accept archives Android's native `ZipFile.open` rejects.

**The add-ons themselves travel, not just their names.** The archive used to
record only *which* extensions were installed and re-download each from AMO at
restore time, so a restore needed the network, needed AMO to answer, needed every
add-on to still be listed, and could only ever return whatever version AMO offers
today. When any of that failed the add-ons simply did not come back — 白い熊,
2026-09-09, "Plugins not restored", after a restore whose Extensions category ran
for 16 seconds. `Cat.EXTENSIONS` now carries each XPI from
`<profile>/extensions/` and installs from the staged file, with AMO as the
fallback for older archives. The XPIs stage to `kako_pending_addons/`, NOT into
the profile: an XPI dropped into `<profile>/extensions/` is invisible to Gecko,
which knows only what its own extension database says.

**`dumpsys diskstats` "App Data Sizes" is CACHED — do not diagnose with it.** It
reported 0.11 GB for a profile that had just taken a 2 GB restore, which sent a
whole round of diagnosis in the wrong direction (2026-09-09). Use free space
instead: `stat -f -c %f /data` before and after, times the 4096-byte block. That
restore showed **2.92 GB consumed** and settled the question in one command.

**Progress during the unpacking pass reports position 1, not the entry's
category.** The import streams once and applies afterwards, so reporting whatever
entry is going past announced "19 of 19" nine seconds in and then began again at
3. 応用管理 noticed and said so in its log: *"the app started its count again —
pass 1 over the same data"*. The label says what is happening; the number stays
where the work has not started.

**Every bulk entry is named for the category that owns it** — `extension_databases/…`,
not `extdb/…`. A consumer totting up a category's size by entry name otherwise
finds only the `<id>.json` side-car, and 応用管理 duly reported "Extension
databases — **40 bytes**" over 1.89 GiB of dictionaries that were all present in
the archive (白い熊, 2026-09-09). Every byte was there and the report was still
wrong; an archive should not need outside knowledge to say what is in it. The
prefixes are derived from `Cat.id` so they cannot drift, and the import still
accepts the old `extdb/` and `extstore/` names so that archives written by
155.0.1+023 and +024 restore in full.

**A category that takes minutes must keep talking.** 応用管理 abandons an app silent
for ten minutes, and the per-category progress the rest of the export leans on
cannot help inside one category — `EXT_DATA_PROGRESS_BYTES` (32 MB) is how often
export and import repeat their position.

**Why the Android ZIP64 bug that broke shiroikuma-jisho cannot reach us**
(jisho chat, 2026-09-09): Android's `ZipFile.open` is native and follows the ZIP64
locator only when the ordinary EOCD holds `0xFFFFFFFF` sentinels, so a writer that
emits the trailer without the sentinels produces an archive desktop readers accept
and the phone rejects. Two reasons we are clear: `KakoExim.kt` is the *only*
archive writer in this tree (no native writer, no desktop-side exporter) and
`java.util.zip.ZipOutputStream` derives `hasZip64` from the same values it clamps;
and we read with `ZipInputStream`, which walks local file headers and never reads
the central directory at all. Worth re-checking after any change to either:
`cen_off + cen_size` must equal the EOCD offset.

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
