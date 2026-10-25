package com.termfold.app.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationReadOnlyPolicyTest {
    @Test
    fun blocksPathsUnderLinkedGuestRoot() {
        val root = "/root/projects/my-app"
        assertTrue(AutomationReadOnlyPolicy.isUnderReadOnlyRoot(root, "/root/projects/my-app/README.md"))
        assertTrue(AutomationReadOnlyPolicy.isUnderReadOnlyRoot(root, "root/projects/my-app/src"))
        assertFalse(AutomationReadOnlyPolicy.isUnderReadOnlyRoot(root, "/root/.termfold/automation/p1/sessions/s1/docs/a.md"))
        assertFalse(AutomationReadOnlyPolicy.isUnderReadOnlyRoot(null, "/root/projects/x"))
    }
}
