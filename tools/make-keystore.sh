#!/usr/bin/env bash
# Generates the self-signed release keystore (same defaults as make-keystore.ps1).
# Prefer keeping the committed .termfold/termfold-release.jks on this private repo clone.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="$ROOT/.termfold/termfold-release.jks"
mkdir -p "$ROOT/.termfold"

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore already exists: $KEYSTORE"
  exit 0
fi

STORE="${TERMFOLD_STORE_PASSWORD:-termfold}"
KEY="${TERMFOLD_KEY_PASSWORD:-$STORE}"

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias termfold \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$STORE" \
  -keypass "$KEY" \
  -dname "CN=TermFold, OU=App, O=TermFold, L=Unknown, ST=Unknown, C=IN"

echo "Wrote $KEYSTORE"
echo "Passwords: store/key = value used for -storepass (default: termfold). See .termfold/signing.properties on this repo."
