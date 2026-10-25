#!/usr/bin/env bash
# Tests for generate-update-manifest.sh. Run from any directory:
#   .github/scripts/test-generate-update-manifest.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GENERATE="$SCRIPT_DIR/generate-update-manifest.sh"
TESTDATA="$SCRIPT_DIR/testdata"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

FAILURES=0

fail() {
  echo "FAIL: $1"
  FAILURES=$((FAILURES + 1))
}

assert_eq() {
  local name="$1" expected="$2" actual="$3"
  if [ "$expected" != "$actual" ]; then
    fail "$name: expected '$expected', got '$actual'"
  fi
}

# The APK sits next to output-metadata.json, as in app/build/outputs/apk/release/.
APK_DIR="$WORK/release"
mkdir -p "$APK_DIR"
cp "$TESTDATA/output-metadata.json" "$APK_DIR/output-metadata.json"
printf 'fake apk bytes' > "$APK_DIR/app-release.apk"
EXPECTED_SHA="$(sha256sum "$APK_DIR/app-release.apk" | cut -d' ' -f1)"

# --- Valid input produces a v1 manifest ---
MANIFEST="$("$GENERATE" "$APK_DIR/output-metadata.json" v2026.10.3 wsguede/Volta "$TESTDATA/notes.md")"

assert_eq "schemaVersion" "1" "$(jq -r '.schemaVersion' <<<"$MANIFEST")"
assert_eq "versionCode" "202610003" "$(jq -r '.versionCode' <<<"$MANIFEST")"
assert_eq "versionCode type" "number" "$(jq -r '.versionCode | type' <<<"$MANIFEST")"
assert_eq "versionName" "2026.10.3" "$(jq -r '.versionName' <<<"$MANIFEST")"
assert_eq "minSdk" "30" "$(jq -r '.minSdk' <<<"$MANIFEST")"
assert_eq "minSdk type" "number" "$(jq -r '.minSdk | type' <<<"$MANIFEST")"
assert_eq "sha256" "$EXPECTED_SHA" "$(jq -r '.sha256' <<<"$MANIFEST")"
assert_eq "apkUrl" \
  "https://github.com/wsguede/Volta/releases/download/v2026.10.3/app-release.apk" \
  "$(jq -r '.apkUrl' <<<"$MANIFEST")"
assert_eq "notes" "$(cat "$TESTDATA/notes.md")" "$(jq -r '.notes' <<<"$MANIFEST")"
assert_eq "keys" \
  '["apkUrl","minSdk","notes","schemaVersion","sha256","versionCode","versionName"]' \
  "$(jq -c 'keys' <<<"$MANIFEST")"

# --- Invalid inputs fail instead of publishing a bad manifest ---
expect_failure() {
  local name="$1"
  shift
  if "$GENERATE" "$@" >/dev/null 2>&1; then
    fail "$name: expected non-zero exit"
  fi
}

MISSING_APK_DIR="$WORK/missing-apk"
mkdir -p "$MISSING_APK_DIR"
cp "$TESTDATA/output-metadata.json" "$MISSING_APK_DIR/output-metadata.json"
expect_failure "missing APK" \
  "$MISSING_APK_DIR/output-metadata.json" v2026.10.3 wsguede/Volta "$TESTDATA/notes.md"

MULTI_DIR="$WORK/multiple"
mkdir -p "$MULTI_DIR"
cp "$TESTDATA/output-metadata-multiple.json" "$MULTI_DIR/output-metadata.json"
cp "$APK_DIR/app-release.apk" "$MULTI_DIR/app-release.apk"
expect_failure "multiple outputs" \
  "$MULTI_DIR/output-metadata.json" v2026.10.3 wsguede/Volta "$TESTDATA/notes.md"

NO_MINSDK_DIR="$WORK/no-minsdk"
mkdir -p "$NO_MINSDK_DIR"
cp "$TESTDATA/output-metadata-no-minsdk.json" "$NO_MINSDK_DIR/output-metadata.json"
cp "$APK_DIR/app-release.apk" "$NO_MINSDK_DIR/app-release.apk"
expect_failure "missing minSdk" \
  "$NO_MINSDK_DIR/output-metadata.json" v2026.10.3 wsguede/Volta "$TESTDATA/notes.md"

expect_failure "missing arguments" "$APK_DIR/output-metadata.json"

if [ "$FAILURES" -ne 0 ]; then
  echo "$FAILURES failure(s)"
  exit 1
fi
echo "All update manifest tests passed"
