package com.termfold.app.acp.checkpoints

import android.content.Context
import android.util.Log
import com.termfold.app.acp.AcpInstaller
import java.security.MessageDigest

/**
 * Cline-style shadow Git: a separate repo whose [core.worktree] is the agent workspace.
 * Works the same for projects with or without their own `.git`.
 */
class ShadowCheckpointStore(
    private val storageKey: String,
    private val worktreeGuestDir: String,
) {

    private val guestShadowRoot: String =
        "/root/.termfold/checkpoints/${sha1(storageKey)}"

    fun commit(context: Context): String? {
        val script = shellInit() + checkpointBody()
        val (code, out) = runGuest(context, script, timeoutSeconds = 300)
        val hash = out.lines().lastOrNull { it.matches(HEX40) }?.trim()?.lowercase()
        if (hash == null) {
            Log.w(TAG, "checkpoint commit failed (code=$code): ${out.take(800)}")
            return null
        }
        if (code != 0) {
            Log.w(TAG, "checkpoint commit nonzero exit (code=$code) but got head $hash")
        }
        return hash
    }

    /** True when the current worktree differs from [commitHash]. */
    fun workspaceDiffersFrom(context: Context, commitHash: String): Boolean {
        val normalized = commitHash.lowercase()
        if (!normalized.matches(HEX40)) return false
        val script = shellInit() + """
            if $GIT diff --quiet ${shellQuote(normalized)} 2>/dev/null; then
              echo unchanged
            else
              echo changed
            fi
            """.trimIndent() + "\n"
        val (code, out) = runGuest(context, script, timeoutSeconds = 120)
        if (code != 0) return true
        return out.lines().lastOrNull { it == "changed" || it == "unchanged" } == "changed"
    }

    fun restore(context: Context, commitHash: String): Boolean {
        val normalized = commitHash.lowercase()
        if (!normalized.matches(HEX40)) return false
        val script = shellInit() + """
            $GIT reset --hard ${shellQuote(normalized)} 2>&1
            $GIT rev-parse HEAD
            """.trimIndent() + "\n"
        val (code, out) = runGuest(context, script, timeoutSeconds = 300)
        val head = out.lines().lastOrNull { it.matches(HEX40) }?.trim()?.lowercase()
        return code == 0 && head == normalized
    }

    private fun checkpointBody(): String = """
        $GIT add -A 2>/dev/null || true
        if $GIT diff --cached --quiet 2>/dev/null && $GIT diff --quiet 2>/dev/null; then
          $GIT commit --allow-empty --no-verify -m "checkpoint-empty" >/dev/null 2>&1 || true
        else
          $GIT commit --no-verify -m "checkpoint" >/dev/null 2>&1 || true
        fi
        $GIT rev-parse HEAD
        """.trimIndent() + "\n"

    private fun shellInit(): String {
        val work = shellQuote(worktreeGuestDir.trimEnd('/'))
        val root = shellQuote(guestShadowRoot)
        return """
            if ! test -x $GIT; then
              export DEBIAN_FRONTEND=noninteractive
              apt-get update -qq >/dev/null 2>&1 || true
              apt-get install -y -qq git >/dev/null 2>&1 || { echo "termfold-checkpoint: git not installed"; exit 10; }
            fi
            mkdir -p $root
            export GIT_DIR=$root/.git
            export GIT_WORK_TREE=$work
            if [ ! -d $root/.git ]; then
              $GIT init -q
              $GIT config core.worktree $work
              $GIT config commit.gpgsign false
              $GIT config user.email "checkpoint@termfold.app"
              $GIT config user.name "TermFold Checkpoint"
              $GIT config --global --add safe.directory $work 2>/dev/null || true
              mkdir -p $root/.git/info
              cat > $root/.git/info/exclude <<'EOF'
.git
**/.git
node_modules
**/node_modules
.gradle
**/.gradle
build
**/build
.termfold
EOF
              $GIT add -A 2>/dev/null || true
              $GIT commit --allow-empty --no-verify -m "init" >/dev/null 2>&1 || true
            fi
            cd $work || exit 11
            """.trimIndent() + "\n"
    }

    private fun runGuest(context: Context, script: String, timeoutSeconds: Long): Pair<Int, String> =
        AcpInstaller.runCapture(
            context,
            script,
            guestCwd = worktreeGuestDir.trimEnd('/'),
            timeoutSeconds = timeoutSeconds,
        )

    private companion object {
        private const val TAG = "ShadowCheckpoint"
        private val HEX40 = Regex("(?i)[0-9a-f]{40}")
        private const val GIT = "/usr/bin/git"

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }

        fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\\''") + "'"
    }
}
