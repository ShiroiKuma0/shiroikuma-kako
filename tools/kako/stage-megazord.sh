#!/usr/bin/env bash
# Stage the self-contained application-services megazord for the Android source build.
#
# Why this exists: the in-tree Gecko build links libmegazord.so against libmozglue.so,
# because Gecko links its own allocator into every shared library it builds. That is
# right for code running inside a Gecko process, but app-services is driven from Kotlin
# over JNA, where allocations come from bionic. Freeing those through mozjemalloc
# segfaults in arena_dalloc (mozjemalloc.cpp) the moment places.sqlite is opened, which
# is what made 155.0.1+008/+011/+014 force-close at startup.
#
# Mozilla's published megazord is built self-contained -- NEEDED is only libdl, libc,
# liblog, libm -- so it uses the system allocator and does not have the problem. Both
# are the same application-services version, so the UniFFI checksums match.
#
# Run after `./mach build` and before `./mach gradle fenix:assembleRelease`.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/../.." && pwd)
OBJDIR="${1:-$REPO/objdir-kako-src}"
DEST="$OBJDIR/dist/geckoview/lib/arm64-v8a/libmegazord.so"

AAR=$(find "$HOME/.gradle/caches" -name "full-megazord-*.aar" 2>/dev/null | sort | tail -1)
[[ -n "$AAR" ]] || { echo "error: no full-megazord AAR in the gradle cache" >&2; exit 1; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
unzip -q -o "$AAR" 'jni/arm64-v8a/libmegazord.so' -d "$TMP"
SRC="$TMP/jni/arm64-v8a/libmegazord.so"

if readelf -d "$SRC" | grep -q mozglue; then
    echo "error: $AAR's megazord links mozglue; refusing to stage it" >&2
    exit 1
fi

cp "$SRC" "$DEST"
echo "staged $(basename "$AAR") megazord -> $DEST"
readelf -d "$DEST" | grep NEEDED
