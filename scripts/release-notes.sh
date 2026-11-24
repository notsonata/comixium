#!/usr/bin/env bash
# Prints the release notes for one version: its CHANGELOG.md section plus a short build footer.
# usage: scripts/release-notes.sh <version> [commit-sha]
set -euo pipefail
version="$1"
sha="${2:-}"
notes="$(awk -v v="$version" '/^## /{p = ($2 == v); next} p' "$(dirname "$0")/../CHANGELOG.md" | sed '/./,$!d')"
if [ -z "${notes//[[:space:]]/}" ]; then
  echo "CHANGELOG.md has no section for $version" >&2
  exit 1
fi
printf '%s\n\n---\n' "$notes"
printf 'Install `comixium-%s.apk`; it updates over earlier versions and keeps my login.' "$version"
if [ -n "$sha" ]; then printf ' Built from %s.' "${sha:0:7}"; fi
printf '\n'
