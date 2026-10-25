package com.termfold.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStorageMirrorStoreTest {

    @Test
    fun sanitizeGuestNameStripsUnsafeChars() {
        assertEquals("My-Downloads", DeviceStorageMirrorStore.sanitizeGuestName("My Downloads!"))
    }

    @Test
    fun uniqueGuestNameAvoidsCollision() {
        val existing = listOf(
            DeviceStorageMirror("1", "a", "uri", "Download"),
        )
        assertEquals("Download-2", DeviceStorageMounts.uniqueGuestName(existing, "Download"))
    }
}
