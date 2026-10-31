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
    fun folderIdsLinkMultipleProjects() {
        val data = ProjectRulesData(
            profiles = listOf(
                ProjectRuleProfile(
                    "p",
                    "Shared",
                    folderIds = listOf("f1", "f2"),
                    rules = listOf(ProjectRule("r1", "R", "body")),
                ),
            ),
        )
        val f1 = Folder(id = "f1", name = "A", path = "/a", tint = 0)
        val f2 = Folder(id = "f2", name = "B", path = "/b", tint = 0)
        val other = Folder(id = "x", name = "C", path = "/c", tint = 0)
        assertEquals(1, ProjectRulesContextResolver.eligibleProfiles(data, f1, null).size)
        assertEquals(1, ProjectRulesContextResolver.eligibleProfiles(data, f2, null).size)
        assertTrue(ProjectRulesContextResolver.eligibleProfiles(data, other, null).isEmpty())
    }

    @Test
    fun globalProfileEligibleForAnyFolder() {
        val data = ProjectRulesData(
            profiles = listOf(
                ProjectRuleProfile(
                    "g",
                    "Standards",
                    kind = ProjectRuleProfileKind.GLOBAL,
                    rules = listOf(ProjectRule("r1", "R", "body")),
                ),
            ),
        )
        val other = Folder(id = "x", name = "Other", path = "/x", tint = 0)
        assertEquals(1, ProjectRulesContextResolver.eligibleProfiles(data, other, null).size)
        val rows = ProjectRulesContextResolver.sheetRows(data, "s1", other, null)
        assertEquals(1, rows.size)
        assertFalse(rows.single().enabled)
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
