<div align="center">

<img src="tools/kako/icon/icon-final-round.svg" width="120" alt="白い熊 火狐 icon" />

# 白い熊 火狐

**Firefox for Android in pure black & yellow, with extension freedom on the release channel.**

A fork of [Mozilla Firefox](https://github.com/mozilla-firefox/firefox) (Fenix, release channel) with **major additions**: custom AMO extension collections unlocked on release, a fully settable black/yellow UI with external fonts, pinned extension buttons on a two-row toolbar, whole-profile export & import, about:config, and a line-traced launcher fox.

Installs **side-by-side** with stock Firefox/Beta/Nightly (app id `shiroikuma.kako`).

**📥 Latest release: [`153.0+4`](https://github.com/ShiroiKuma0/shiroikuma-kako/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-kako/releases)

</div>

---

## 🧩 Extension freedom on the release channel
Stock Fenix locks custom AMO add-on collections behind Nightly. This fork opens those channel gates on release: point the browser at any AMO collection and install everything in it — collection extensions are AMO-signed, so release signing enforcement never objects. `about:config` and the full secret-settings menu (five taps on the About logo) are unlocked too.

---

## 🖤💛 The 白い熊 火狐 UI
A dedicated settings page (pinned at the top of Settings, or long-press the menu button) themes the whole browser from user-settable color slots — seeded pure black `#000000` / pure yellow `#FFFF00` — with live Compose restyling, external ttf/otf font import (family, weight, 70–160% size scale), and 0.5 dp-stepped border thickness sliders. Chrome is traced Nightly-style: outlined address pill, bordered tabs and menu cards, kako-framed alert dialogs, true-black windows.

---

## 💾 Export & import the whole profile
Pick a backup directory once; the page then shows the newest export in it every time you open it. One panel exports — or restores — the browser by category: the 白い熊 UI theme, imported fonts, **every installed extension** (re-installed from AMO on restore, with its enabled and private-browsing state, the pinned toolbar order, and your custom collection), Firefox's own settings, bookmarks, saved passwords, credit cards and addresses. The archive is a plain ZIP of readable JSON — no databases, no opaque blobs — so it stays inspectable and portable. Credentials necessarily travel as plaintext inside it, which the panel says out loud; treat the file like the passwords it holds.

---

## 📌 Pinned extensions & the two-row toolbar
Any extension with a browser action can be pinned to the toolbar from its settings screen, desktop-style — with its real multicolor icon, an icon-size slider, long-press reordering, and a drag-to-reorder dialog. A toggleable two-row layout keeps navigation and the address pill on top and moves new tab, pinned extensions, manage-extensions, tab counter and menu to their own row beneath.

---

## 🦊 The line-traced fox
The launcher icon is the Nightly fox redrawn as yellow line art on black — adaptive and legacy mipmaps rendered from the master SVGs in `tools/kako/icon/`.

---

## Built on Mozilla Firefox
A fork of [mozilla-firefox/firefox](https://github.com/mozilla-firefox/firefox) tracking the **release** channel tag-to-tag (app id `shiroikuma.kako`, so it coexists with the official builds). All credit to Mozilla for the browser itself; the code remains under the [Mozilla Public License 2.0](LICENSE).

Branch model: `release` is a byte-identical upstream mirror; **`custom`** carries every fork commit, rebased onto each adopted `FIREFOX_*_RELEASE` tag.

## Building
Artifact build (prebuilt GeckoView engine; only Kotlin/Java compiles locally), arm64-v8a:

```bash
git clone --branch custom git@github.com:ShiroiKuma0/shiroikuma-kako.git
cd shiroikuma-kako
./mach bootstrap          # choose "GeckoView/Firefox for Android Artifact Mode"

export MOZCONFIG=$PWD/tools/kako/mozconfig
./mach build
./mach gradle fenix:assembleRelease
# APK: objdir-kako/gradle/build/mobile/android/fenix/app/outputs/apk/release/fenix-arm64-v8a-release.apk
# then zipalign + apksigner with your own keystore
```
