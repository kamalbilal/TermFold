package com.termfold.app.preview.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkCaptureHubTest {
    private val key = "hub-test"

    @Before
    fun setup() {
        NetworkCaptureStore.synchronousIngestForTests = true
        NetworkCaptureStore.clear(key)
        NetworkCaptureStore.setPreserve(key, false)
        NetworkCaptureHub.setUiSubscribed(key, false)
    }

    @org.junit.After
    fun restoreIngest() {
        NetworkCaptureStore.synchronousIngestForTests = false
    }

    @After
    fun teardown() {
        NetworkCaptureHub.setUiSubscribed(key, false)
        NetworkCaptureStore.clear(key)
    }

    @Test
    fun bodyCaptureOffByDefault() {
        assertFalse(NetworkCaptureHub.bodyCaptureEnabled(key))
    }

    @Test
    fun uiSubscribeEnablesBodyCapture() {
        NetworkCaptureHub.setUiSubscribed(key, true)
        assertTrue(NetworkCaptureHub.bodyCaptureEnabled(key))
        NetworkCaptureHub.setUiSubscribed(key, false)
        assertFalse(NetworkCaptureHub.bodyCaptureEnabled(key))
    }

    @Test
    fun preserveEnablesBodyCaptureWithoutUi() {
        NetworkCaptureStore.setPreserve(key, true)
        assertTrue(NetworkCaptureHub.bodyCaptureEnabled(key))
    }

    @Test
    fun captureModeLiteByDefault() {
        assertEquals(NetworkCaptureHub.MODE_LITE, NetworkCaptureHub.captureMode(key))
    }

    @Test
    fun captureModeFullWhenUiSubscribed() {
        NetworkCaptureHub.setUiSubscribed(key, true)
        assertEquals(NetworkCaptureHub.MODE_FULL, NetworkCaptureHub.captureMode(key))
    }

    @Test
    fun captureModeFullWhenPreserve() {
        NetworkCaptureStore.setPreserve(key, true)
        assertEquals(NetworkCaptureHub.MODE_FULL, NetworkCaptureHub.captureMode(key))
    }
}
