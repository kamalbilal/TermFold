package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerTriggersTest {
    @Test
    fun atStartOfField() {
        val t = ComposerTriggers.activeToken("@src", 4, 4)
        assertEquals('@', t?.trigger)
        assertEquals("src", t?.query)
        assertEquals(0, t?.start)
    }

    @Test
    fun atMidSentence() {
        val text = "please check @src/App thanks"
        val caret = text.indexOf(" thanks")
        val t = ComposerTriggers.activeToken(text, caret, caret)
        assertEquals('@', t?.trigger)
        assertEquals("src/App", t?.query)
        assertEquals("please check ".length, t?.start)
    }

    @Test
    fun slashMidSentence() {
        val text = "run /init now"
        val caret = text.indexOf(" now")
        val t = ComposerTriggers.activeToken(text, caret, caret)
        assertEquals('/', t?.trigger)
        assertEquals("init", t?.query)
    }

    @Test
    fun ignoresEmailLikeToken() {
        val text = "mail user@host.com"
        val caret = text.length
        assertNull(ComposerTriggers.activeToken(text, caret, caret))
    }
}
