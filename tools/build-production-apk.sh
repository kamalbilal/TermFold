#!/usr/bin/env bash
# Builds com.termfold.app release APK signed with termfold-release.jks (in-place update over store/GitHub install).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

resolve_keystore() {
  for p in "$ROOT/.termfold/termfold-release.jks" "$ROOT/termfold-release.jks"; do
    [[ -f "$p" ]] && echo "$p" && return 0
  done
  return 1
}

KEYSTORE="$(resolve_keystore)" || {
  echo "Missing keystore. Copy to .termfold/termfold-release.jks or termfold-release.jks at repo root."
  exit 1
}

has_secret() {
  [[ -n "${TERMFOLD_STORE_PASSWORD:-}" ]] && return 0
  grep -q 'termfold.storePassword' "$ROOT/local.properties" 2>/dev/null && return 0
  grep -q 'termfold.storePassword' "$ROOT/.termfold/signing.properties" 2>/dev/null
}

if ! has_secret; then
  echo "Missing termfold.storePassword in .termfold/signing.properties, local.properties, or TERMFOLD_STORE_PASSWORD."
  echo "Run: bash tools/save-signing-config.sh"
  exit 1
fi

AAPT2="${AAPT2:-$ROOT/.tools/aapt2}"
GRADLE=(./gradlew :app:assembleAppRelease --no-configuration-cache)
if [[ -x "$AAPT2" ]] || [[ -f "$AAPT2" ]]; then
  GRADLE+=(-Pandroid.aapt2FromMavenOverride="$AAPT2")
fi

"${GRADLE[@]}"
APK="$ROOT/app/build/outputs/apk/app/release/TermFold-app-release.apk"
echo ""
echo "Built: $APK (keystore: $KEYSTORE)"
if command -v apksigner >/dev/null; then
  echo "Certificate (must match installed com.termfold.app):"
  apksigner verify --print-certs "$APK" 2>/dev/null | head -6
fi
echo ""
echo "Install without losing data: adb install -r \"$APK\""
