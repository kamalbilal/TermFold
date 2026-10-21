package com.termfold.app.acp

import org.junit.Assert.assertTrue
import org.junit.Test

class AcpInitializeTest {
    @Test
    fun initializeAdvertisesParameterizedModelPicker() {
        val meta = acpInitializeParams()
            .getJSONObject("clientCapabilities")
            .getJSONObject("_meta")
        assertTrue(meta.getBoolean("parameterizedModelPicker"))
    }
}
