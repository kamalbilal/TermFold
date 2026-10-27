package com.termfold.app.pm

import org.junit.Assert.assertEquals
import org.junit.Test

class PmUiTest {
    @Test
    fun forStringResourceArgEscapesPercent() {
        assertEquals("main", "main".forStringResourceArg())
        assertEquals("100%%", "100%".forStringResourceArg())
        assertEquals("a%%b", "a%b".forStringResourceArg())
    }
}
