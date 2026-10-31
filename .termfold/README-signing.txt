# Release signing (private repo — tracked in git for clone + in-place APK updates)

On **this** private fork, the release keystore and passwords live in git under `.termfold/` so a fresh clone can build `com.termfold.app` with the **same certificate** as your installed app (no uninstall, data kept).

| File | Purpose |
|------|---------|
| `termfold-release.jks` | Release keystore (`alias` **termfold**) |
| `signing.properties` | `termfold.storePassword` / `termfold.keyPassword` for Gradle + `tools/build-production-apk.sh` |

## If you replace the key (new phone / Play upload key)

Only swap these two files together with a keystore that **already** signs your installed app. A new random key from `tools/make-keystore.sh` will **not** update an existing install in place.

## One-time on a new machine (no clone)

1. `bash tools/make-keystore.sh` (only when creating a **new** key pair)
2. `bash tools/save-signing-config.sh` if you change passwords
3. `bash tools/build-production-apk.sh`

Gradle also reads `termfold-release.jks` at repo root and passwords in `local.properties` (gitignored) if you prefer that layout.

Do **not** copy these files into public forks, chat logs, or session memory.
