package com.termfold.app.acp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodeInstallHealthTest {

    @Test
    fun healthyWhenNpmProbePresent() {
        val root = Files.createTempDirectory("tf-node").toFile()
        try {
            val probe = File(
                root,
                "lib/node_modules/npm/node_modules/@npmcli/run-script/lib/package-envs.js",
            )
            probe.parentFile?.mkdirs()
            probe.writeText("// ok")
            File(root, "bin").mkdirs()
            File(root, "bin/npm").writeText("")
            File(root, "bin/npx").writeText("")
            assertTrue(AcpInstaller.nodeInstallHealthy(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unhealthyWhenProbeMissing() {
        val root = Files.createTempDirectory("tf-node-bad").toFile()
        try {
            File(root, "bin").mkdirs()
            File(root, "bin/npm").writeText("")
            File(root, "bin/npx").writeText("")
            assertFalse(AcpInstaller.nodeInstallHealthy(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
