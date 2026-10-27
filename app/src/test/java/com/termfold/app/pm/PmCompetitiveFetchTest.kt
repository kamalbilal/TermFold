package com.termfold.app.pm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PmCompetitiveFetchTest {
    @Test
    fun htmlToTextDropsTags() {
        val text = PmCompetitiveFetch.htmlToText("<html><script>x</script><p>Hello&nbsp;world</p></html>")
        assertTrue(text.contains("Hello world"))
        assertFalse(text.contains("<p>"))
        assertFalse(text.contains("script"))
    }
}
