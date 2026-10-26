package com.termfold.app.preview.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkRuleKeysTest {
    @Test
    fun codingComposeKeyIsPrefixed() {
        assertEquals(
            "coding-folder/ws/session",
            NetworkRuleKeys.normalize("folder/ws/session", automationKey = null, codingComposeKey = null),
        )
    }

    @Test
    fun automationColonKeyIsUnchanged() {
        assertEquals(
            "proj:sess",
            NetworkRuleKeys.normalize("proj:sess", automationKey = null, codingComposeKey = "other"),
        )
    }

    @Test
    fun alreadyPrefixedKeysPassThrough() {
        assertEquals("coding-x", NetworkRuleKeys.normalize("coding-x", null, null))
        assertEquals("auto-y", NetworkRuleKeys.normalize("auto-y", null, null))
    }

    @Test
    fun emptyUsesBoundKeys() {
        assertEquals("proj:sess", NetworkRuleKeys.normalize("", "proj:sess", "folder/ws/s"))
        assertEquals("coding-folder/ws/s", NetworkRuleKeys.normalize("", null, "folder/ws/s"))
        assertNull(NetworkRuleKeys.normalize("", null, null))
    }
}
