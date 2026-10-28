# Release signing (persists on this machine across Cursor sessions)

**Git never sees these** — `.termfold/` is gitignored except this README.

## One-time setup

1. Copy your **production** keystore (same as GitHub/Play):
   ```
   .termfold/termfold-release.jks
   ```

2. Save passwords (pick one):

   **A — script (recommended)**
   ```bash
   export TERMFOLD_STORE_PASSWORD='your-store-password'
   export TERMFOLD_KEY_PASSWORD='your-key-password'   # optional; defaults to store password
   bash tools/save-signing-config.sh
   ```

   **B — hand-edit** `.termfold/signing.properties`:
   ```properties
   termfold.storePassword=...
   termfold.keyPassword=...
   ```

3. Build store-matching release:
   ```bash
   bash tools/build-production-apk.sh
   ```

Gradle also accepts `termfold-release.jks` at repo root and passwords in `local.properties`.

Do **not** put passwords in chat, Cursor rules, or OpenSpec.
