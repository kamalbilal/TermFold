package com.termfold.app.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewNormalizeTest {
    @Test
    fun normalizeFileUriToGuestFileUrl() {
        val url = Preview.normalize("file:///tmp/demo.html", null)
        assertTrue(url.startsWith("http://${Preview.FILES_HOST}/"))
        assertEquals("/tmp/demo.html", Preview.guestPathOf(url))
    }

    @Test
    fun normalizeAbsoluteGuestPath() {
        val url = Preview.normalize("/tmp/demo.html", null)
        assertEquals("/tmp/demo.html", Preview.guestPathOf(url))
    }
}
