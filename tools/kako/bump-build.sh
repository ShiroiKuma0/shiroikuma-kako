#!/usr/bin/env bash
# Bump customBuildNumber in the repo-root gradle.properties by 1.
# Print the new full version "X.Y.Z+NNN" (counter zero-padded to three digits,
# so that build lists sort in build order; the stored property stays a plain int).
#
# Family convention (same as the other shiroikuma-* forks): every dev build
# gets a fresh build number, surfaced in the APK filename and app version
# (versionName "<base>+<n>"). Within one upstream version the counter
# increments per build (gaps from aborted builds are fine); it RESETS to 0 on
# each upstream adoption, so the first build of a new version is "<base>+1".
# customBaseVersionName mirrors the upstream base tag; both it and the reset to
# 0 are applied by hand when adopting a new upstream version. (Android upgrade
# ordering is by versionCode, which is upstream-derived, not by this counter.)

set -euo pipefail

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
GP="$REPO_ROOT/gradle.properties"

[[ -f "$GP" ]] || die "gradle.properties not found: $GP"

current=$(grep -oP '^customBuildNumber=\K\d+' "$GP" || true)
[[ -n "$current" ]] || die "customBuildNumber=<int> line not found in $GP"

new=$((current + 1))
sed -i "s/^customBuildNumber=${current}\$/customBuildNumber=${new}/" "$GP"

base=$(grep -oP '^customBaseVersionName=\K.+' "$GP" || true)
[[ -n "$base" ]] || die "customBaseVersionName=<string> line not found in $GP"

printf '%s+%03d\n' "$base" "$new"
