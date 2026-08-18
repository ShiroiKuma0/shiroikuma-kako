#!/usr/bin/env bash
# Build the 白い熊 火狐 desktop .deb for GNU/Linux amd64 (Tuxedo OS).
#
# Input  : the ./mach package tarball in objdir-kako-desktop/dist/
# Output : ~/tmp/shiroikuma-kako_<base>+<NNN>_amd64.deb
#
# Hand-rolled with dpkg-deb rather than driving Mozilla's
# python/mozbuild/mozbuild/repackaging/deb.py, which is CI-only and is not
# exposed as a `mach repackage` subcommand (only dmg/pkg/msi/msix/mar are).
# browser/installer/linux/app/debian/ remains a useful reference for the
# maintainer-script content.
#
# The build counter is SHARED with the Android product: both call
# tools/kako/bump-build.sh, so a version number identifies one commit of this
# tree regardless of which artifact it produced. Pass --version to reuse an
# existing number instead of burning a new one (e.g. when re-packaging).

set -euo pipefail

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
note() { printf '\033[1m==>\033[0m %s\n' "$*"; }

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../.." && pwd)
OBJDIR="$REPO_ROOT/objdir-kako-desktop"
BRANDING="$REPO_ROOT/browser/branding/kako"

PKG=shiroikuma-kako
PREFIX=/opt/$PKG
APPNAME=kako            # MOZ_APP_NAME: the binary inside the tarball
WMCLASS=kako            # MOZ_APP_REMOTINGNAME: g_set_prgname / StartupWMClass

VERSION=""
[[ ${1:-} == --version ]] && { VERSION=${2:-}; [[ -n $VERSION ]] || die "--version needs a value"; }

[[ -d "$OBJDIR" ]] || die "objdir not found: $OBJDIR (run ./mach build first)"

TARBALL=$(ls -1t "$OBJDIR"/dist/"$APPNAME"-*.linux-x86_64.tar.xz 2>/dev/null | head -1 || true)
[[ -n "$TARBALL" ]] || die "no package tarball in $OBJDIR/dist -- run ./mach package"

if [[ -z $VERSION ]]; then
  VERSION=$("$SCRIPT_DIR/../bump-build.sh")
fi
note "version $VERSION"
note "tarball  $(basename "$TARBALL")"

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

# ---- payload -------------------------------------------------------------
install -d "$STAGE$PREFIX"
tar -xf "$TARBALL" -C "$STAGE$PREFIX" --strip-components=1
[[ -x "$STAGE$PREFIX/$APPNAME" ]] || die "binary $APPNAME missing from tarball payload"

install -d "$STAGE/usr/bin"
ln -s "$PREFIX/$APPNAME" "$STAGE/usr/bin/$PKG"

# ---- desktop entry -------------------------------------------------------
install -d "$STAGE/usr/share/applications"
cat > "$STAGE/usr/share/applications/$PKG.desktop" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=白い熊 火狐
GenericName=Web Browser
Comment=Browse the World Wide Web
Exec=$PREFIX/$APPNAME %u
Icon=$PKG
Terminal=false
StartupNotify=true
StartupWMClass=$WMCLASS
Categories=Network;WebBrowser;
MimeType=text/html;text/xml;application/xhtml+xml;x-scheme-handler/http;x-scheme-handler/https;
Actions=new-window;new-private-window;

[Desktop Action new-window]
Name=New Window
Exec=$PREFIX/$APPNAME --new-window %u

[Desktop Action new-private-window]
Name=New Private Window
Exec=$PREFIX/$APPNAME --private-window %u
EOF

# ---- icons ---------------------------------------------------------------
for s in 16 32 48 64 128; do
  src="$BRANDING/default$s.png"
  [[ -f $src ]] || die "branding icon missing: $src"
  install -d "$STAGE/usr/share/icons/hicolor/${s}x${s}/apps"
  install -m644 "$src" "$STAGE/usr/share/icons/hicolor/${s}x${s}/apps/$PKG.png"
done

# ---- dependencies --------------------------------------------------------
# dpkg-shlibdeps insists on a debian/control relative to cwd, so give it a
# throwaway one rather than hardcoding a library list that will silently rot.
note "computing dependencies with dpkg-shlibdeps"
DEPWORK="$STAGE/.depwork"
install -d "$DEPWORK/debian"
cat > "$DEPWORK/debian/control" <<EOF
Source: $PKG
Package: $PKG
Architecture: amd64
EOF
: > "$DEPWORK/debian/substvars"
mapfile -t SOFILES < <(find "$STAGE$PREFIX" -maxdepth 1 -type f \( -name '*.so' -o -name "$APPNAME" \) )
DEPENDS=""
if ( cd "$DEPWORK" && dpkg-shlibdeps --ignore-missing-info -Tdebian/substvars "${SOFILES[@]}" >/dev/null 2>&1 ); then
  DEPENDS=$(grep -oP '^shlibs:Depends=\K.*' "$DEPWORK/debian/substvars" || true)
fi
if [[ -z $DEPENDS ]]; then
  note "dpkg-shlibdeps produced nothing; falling back to a conservative list"
  DEPENDS="libc6 (>= 2.34), libgtk-3-0 (>= 3.24), libdbus-glib-1-2, libx11-6, libxcb1, libatk1.0-0, libpango-1.0-0, libcairo2, libfontconfig1, libfreetype6, libasound2t64"
fi

# ---- control + maintainer scripts ---------------------------------------
INSTALLED_KB=$(du -sk "$STAGE$PREFIX" "$STAGE/usr" | awk '{t+=$1} END {print t}')
install -d "$STAGE/DEBIAN"
cat > "$STAGE/DEBIAN/control" <<EOF
Package: $PKG
Version: $VERSION
Architecture: amd64
Maintainer: 白い熊 <claude.ai@sumou.com>
Installed-Size: $INSTALLED_KB
Depends: $DEPENDS
Provides: gnome-www-browser, www-browser
Section: web
Priority: optional
Description: 白い熊 火狐 -- personal Firefox fork
 A personal fork of Firefox for Desktop, built from the mozilla-firefox/firefox
 monorepo and installed side by side with stock Firefox: its own package name,
 its own /opt prefix, its own WM class ($WMCLASS) and its own profile directory
 (~/.mozilla/$APPNAME). Add-on signature enforcement is disabled so that
 self-modified extensions can be installed.
EOF

cat > "$STAGE/DEBIAN/postinst" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = configure ]; then
  [ -x /usr/bin/update-desktop-database ] && update-desktop-database -q /usr/share/applications || true
  [ -x /usr/bin/gtk-update-icon-cache ] && gtk-update-icon-cache -q -f /usr/share/icons/hicolor || true
fi
exit 0
EOF

cat > "$STAGE/DEBIAN/postrm" <<'EOF'
#!/bin/sh
set -e
if [ "$1" = remove ] || [ "$1" = purge ]; then
  [ -x /usr/bin/update-desktop-database ] && update-desktop-database -q /usr/share/applications || true
  [ -x /usr/bin/gtk-update-icon-cache ] && gtk-update-icon-cache -q -f /usr/share/icons/hicolor || true
fi
exit 0
EOF

chmod 755 "$STAGE/DEBIAN/postinst" "$STAGE/DEBIAN/postrm"
rm -rf "$DEPWORK"

# ---- build ---------------------------------------------------------------
OUT="$HOME/tmp/${PKG}_${VERSION}_amd64.deb"
install -d "$HOME/tmp"
note "building $OUT"
dpkg-deb --build --root-owner-group "$STAGE" "$OUT" >/dev/null

note "done"
dpkg-deb --info "$OUT" | sed -n '1,12p'
printf '\n%s\n' "$OUT"
