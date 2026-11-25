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
# 1.7.0 moved to a new signing key (the old one had been committed), so it can't install over older APKs.
newkey=1.7.0
cert="$(tr -d ' \n' < "$(dirname "$0")/../app/signing/release-cert.sha256")"
if [ "$(printf '%s\n%s\n' "$newkey" "$version" | sort -V | head -1)" != "$newkey" ]; then
  printf -- '- Install `comixium-%s.apk`, updates in place over earlier versions\n' "$version"
else
  if [ "$version" = "$newkey" ]; then
    printf -- '- Install `comixium-%s.apk`. Uninstall any earlier version first: this one is signed with a new key\n' "$version"
  else
    printf -- '- Install `comixium-%s.apk`, updates in place over %s and later\n' "$version" "$newkey"
  fi
  printf -- '- Signing certificate SHA-256: `%s`\n' "$cert"
fi
if ! grep -qxF "$version" "$(dirname "$0")/../.github/tested-versions"; then
  printf -- '- Pre-release: not tested on a phone yet. The latest release is the newest tested build\n'
fi
if [ -n "$sha" ]; then printf -- '- Built from %s\n' "${sha:0:7}"; fi
