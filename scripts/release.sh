#!/usr/bin/env bash
# Cut a Lumen release: tag, push, build the APK, publish a GitHub release.
# Version is derived from the git tag by app/build.gradle.kts — nothing to edit.
#
#   scripts/release.sh 0.5.0
#
set -euo pipefail

VERSION="${1:?usage: scripts/release.sh X.Y.Z}"
TAG="v${VERSION#v}"
APK_NAME="lumen_player_${TAG}.apk"
REPO="pusansen99/lumen-player"

cd "$(dirname "$0")/.."

[ -z "$(git status --porcelain)" ] || { echo "working tree is dirty — commit or stash first"; exit 1; }
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || { echo "not on main"; exit 1; }
git rev-parse "$TAG" >/dev/null 2>&1 && { echo "tag $TAG already exists"; exit 1; }

PREV_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
if [ -n "$PREV_TAG" ]; then
  NOTES="$(git log "${PREV_TAG}..HEAD" --no-merges --pretty='- %s' | grep -v '^- Bump version' || true)"
  RANGE_NOTE="Changes since ${PREV_TAG}:"
else
  NOTES="$(git log --no-merges --pretty='- %s')"
  RANGE_NOTE="Initial release."
fi

echo "==> tagging $TAG"
git tag -a "$TAG" -m "Lumen ${VERSION#v}"
# main is protected and advances only through merged PRs; push just the tag.
git push origin "$TAG"

echo "==> building release APK (version comes from the tag)"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}" \
  ./gradlew :app:assembleRelease

OUT="$(mktemp -d)/${APK_NAME}"
cp app/build/outputs/apk/release/app-release.apk "$OUT"

echo "==> creating GitHub release $TAG"
printf '%s\n\n%s\n' "$RANGE_NOTE" "$NOTES" | \
  gh release create "$TAG" "$OUT" --repo "$REPO" --title "Lumen ${VERSION#v}" --notes-file -

gh release view "$TAG" --repo "$REPO" --json url,assets -q '.url + "  " + ([.assets[].name]|join(","))'
