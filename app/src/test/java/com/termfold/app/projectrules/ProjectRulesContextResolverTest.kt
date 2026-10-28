package com.termfold.app.projectrules

import com.termfold.app.core.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRulesContextResolverTest {
    private val folder = Folder(id = "f1", name = "Termfold", path = "/p", tint = 0)

    @Test
    fun sameNameDifferentOriginNotEligible() {
        val data = ProjectRulesData(
            profiles = listOf(
                ProjectRuleProfile("p", "Termfold", originKey = "gitlab.com/acme/api"),
            ),
        )
        assertTrue(
            ProjectRulesContextResolver.eligibleProfiles(data, folder, null).isEmpty(),
        )
        assertFalse(
            ProjectRulesContextResolver.isEligible(
                data.profiles.single(),
                folder,
                "github.com/other/repo",
            ),
        )
    }

    @Test
    fun sheetShowsLeftoverEnabled() {
        val data = ProjectRulesData(
            profiles = listOf(
                ProjectRuleProfile(
                    "p",
                    "Old",
                    rules = listOf(ProjectRule("r1", "R", "body")),
                ),
            ),
            sessionBindings = mapOf("s" to listOf("r1")),
        )
        val rows = ProjectRulesContextResolver.sheetRows(
            data,
            "s",
            Folder(id = "x", name = "Other", path = "/x", tint = 0),
            null,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.single().enabled)
        assertFalse(rows.single().eligible)
    }
}
