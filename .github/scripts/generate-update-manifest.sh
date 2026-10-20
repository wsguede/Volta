#!/usr/bin/env bash
# Generates the update.json manifest read by the in-app updater (ADR 0018) and prints it
# to stdout.
#
# Usage: generate-update-manifest.sh <output-metadata.json> <tag> <owner/repo> <notes-file>
#
# Version fields come from Gradle's output-metadata.json so they always match the built APK.
# Schema changes must stay backward compatible: installed builds that can't parse the
# manifest can never update again.
set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo "usage: $0 <output-metadata.json> <tag> <owner/repo> <notes-file>" >&2
  exit 2
fi

METADATA="$1"
TAG="$2"
REPO="$3"
NOTES_FILE="$4"

ELEMENT_COUNT="$(jq '.elements | length' "$METADATA")"
if [ "$ELEMENT_COUNT" -ne 1 ]; then
  echo "error: expected exactly 1 APK output in $METADATA, found $ELEMENT_COUNT" >&2
  exit 1
fi

MIN_SDK="$(jq -r '.minSdkVersionForDexing // empty' "$METADATA")"
if [ -z "$MIN_SDK" ]; then
  echo "error: minSdkVersionForDexing missing from $METADATA" >&2
  exit 1
fi

APK_NAME="$(jq -r '.elements[0].outputFile' "$METADATA")"
APK_PATH="$(dirname "$METADATA")/$APK_NAME"
if [ ! -f "$APK_PATH" ]; then
  echo "error: APK not found at $APK_PATH" >&2
  exit 1
fi

SHA256="$(sha256sum "$APK_PATH" | cut -d' ' -f1)"

jq -n \
  --slurpfile metadata "$METADATA" \
  --argjson minSdk "$MIN_SDK" \
  --arg sha256 "$SHA256" \
  --arg apkUrl "https://github.com/$REPO/releases/download/$TAG/$APK_NAME" \
  --rawfile notes "$NOTES_FILE" \
  '{
    schemaVersion: 1,
    versionCode: $metadata[0].elements[0].versionCode,
    versionName: $metadata[0].elements[0].versionName,
    minSdk: $minSdk,
    sha256: $sha256,
    apkUrl: $apkUrl,
    notes: ($notes | rtrimstr("\n"))
  }'
