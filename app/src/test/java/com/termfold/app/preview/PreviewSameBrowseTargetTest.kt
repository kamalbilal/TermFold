package com.termfold.app.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSameBrowseTargetTest {
    @Test
    fun youtuBeMatchesWatchUrl() {
        assertTrue(
            Preview.sameBrowseTarget(
                "https://youtu.be/46GTthtgHMY",
                "https://www.youtube.com/watch?v=46GTthtgHMY",
            ),
        )
    }

    @Test
    fun differentVideosAreNotTheSame() {
        assertFalse(
            Preview.sameBrowseTarget(
                "https://youtu.be/aaaa",
                "https://youtu.be/bbbb",
            ),
        )
    }
}
