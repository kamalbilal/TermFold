package com.termfold.app.preview.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewUrlRouterTest {
    @Test
    fun projectFileHostDetected() {
        assertTrue(PreviewUrlRouter.isProjectFileUrl("http://files.termfold/index.html"))
        assertFalse(PreviewUrlRouter.isProjectFileUrl("https://example.com"))
    }

    @Test
    fun geckoPreviewAlways() {
        assertTrue(PreviewEngineConfig.useGeckoPreview())
        assertTrue(PreviewEngineConfig.useGeckoPreview("http://files.termfold/x"))
    }

    @Test
    fun sessionKeys() {
        assertTrue(PreviewUrlRouter.sessionKeyForAutomation("abc").startsWith("auto-"))
        assertTrue(PreviewUrlRouter.sessionKeyForCoding("p:ws:s").startsWith("coding-"))
    }
}
