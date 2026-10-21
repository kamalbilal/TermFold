package com.termfold.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

/**
 * The guest has no tzdata until the user installs it, and glibc silently treats an unknown zone
 * name as UTC, so the TZ handed to the guest must work either way.
 */
class ProotCommandTest {

    @Test
    fun `positive offset without tzdata becomes an inverted POSIX string`() {
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
        assertEquals("<+0530>-05:30", ProotCommand.guestTimeZone(rootfs = null, zone = kolkata))
    }

    @Test
    fun `negative offset without tzdata becomes an inverted POSIX string`() {
        val fixed = TimeZone.getTimeZone("GMT-03:00")
        assertEquals("<-0300>+03:00", ProotCommand.guestTimeZone(rootfs = null, zone = fixed))
    }

    @Test
    fun `guest environment sets tmp and node caches`() {
        val env = ProotCommand.guestEnvironment(null)
        assertEquals(ShellPaths.GUEST_TMP, env["TMPDIR"])
        assertEquals(ShellPaths.GUEST_NODE_COMPILE_CACHE, env["NODE_COMPILE_CACHE"])
        assertEquals(ShellPaths.GUEST_NPM_CACHE, env["npm_config_cache"])
    }

    @Test
    fun `extra env overrides guest defaults`() {
        val argv = ProotCommand.guestEnvArgv(
            extraEnv = mapOf("npm_config_cache" to "/custom/npm"),
        )
        val lastNpm = argv.last { it.startsWith("npm_config_cache=") }
        assertEquals("npm_config_cache=/custom/npm", lastNpm)
    }

    @Test
    fun `zone name is used once the guest has tzdata`() {
        val rootfs = Files.createTempDirectory("rootfs").toFile()
        try {
            File(rootfs, "usr/share/zoneinfo/Asia").mkdirs()
            File(rootfs, "usr/share/zoneinfo/Asia/Kolkata").writeText("TZif")
            val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
            assertEquals("Asia/Kolkata", ProotCommand.guestTimeZone(rootfs, kolkata))
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
