package com.termfold.app.projectrules

import android.content.Context
import com.termfold.app.core.Folder
import com.termfold.app.gitworkspace.GitWorkspace
import com.termfold.app.gitworkspace.WorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProjectRulesContextResolver {
    fun eligibleProfiles(data: ProjectRulesData, folder: Folder, folderOriginKey: String?): List<ProjectRuleProfile> =
        data.profiles.filter { profile -> isEligible(profile, folder, folderOriginKey) }

    fun isEligible(profile: ProjectRuleProfile, folder: Folder, folderOriginKey: String?): Boolean {
        val origin = profile.originKey
        if (origin != null) {
            return folderOriginKey != null && origin == folderOriginKey
        }
        return profile.displayName.trim().equals(folder.name.trim(), ignoreCase = true)
    }

    suspend fun folderNetworkOrigin(context: Context, folder: Folder): String? = withContext(Dispatchers.IO) {
        val golden = WorkspaceStore.goldenGuestPath(context, folder) ?: return@withContext null
        val raw = GitWorkspace.originUrl(context, golden) ?: return@withContext null
        RemoteOriginKey.normalize(raw)
    }

    fun sheetRows(
        data: ProjectRulesData,
        sessionKey: String,
        folder: Folder,
        folderOriginKey: String?,
    ): List<ProjectRuleSheetRow> {
        val enabled = data.sessionBindings[sessionKey]?.toSet() ?: emptySet()
        val eligible = eligibleProfiles(data, folder, folderOriginKey)
        val rows = mutableListOf<ProjectRuleSheetRow>()
        eligible.forEach { profile ->
            profile.rules.sortedBy { it.sortOrder }.forEach { rule ->
                rows += ProjectRuleSheetRow(
                    profile = profile,
                    rule = rule,
                    enabled = rule.id in enabled,
                    eligible = true,
                )
            }
        }
        val shownIds = rows.map { it.rule.id }.toSet()
        enabled.filter { it !in shownIds }.forEach { ruleId ->
            val located = locateRule(data, ruleId) ?: return@forEach
            rows += ProjectRuleSheetRow(
                profile = located.first,
                rule = located.second,
                enabled = true,
                eligible = false,
            )
        }
        return rows
    }

    fun locateRule(data: ProjectRulesData, ruleId: String): Pair<ProjectRuleProfile, ProjectRule>? {
        for (profile in data.profiles) {
            val rule = profile.rules.firstOrNull { it.id == ruleId } ?: continue
            return profile to rule
        }
        return null
    }

    fun enabledRulesForPrompt(data: ProjectRulesData, sessionKey: String): List<ProjectRule> {
        val ids = data.sessionBindings[sessionKey] ?: return emptyList()
        val profileOrder = data.profiles.mapIndexed { index, profile -> profile.id to index }.toMap()
        return ids.mapNotNull { id ->
            locateRule(data, id)?.let { pair -> pair.first to pair.second }
        }
            .filter { (_, rule) -> rule.body.isNotBlank() || rule.title.isNotBlank() }
            .sortedWith(
                compareBy<Pair<ProjectRuleProfile, ProjectRule>>(
                    { profileOrder[it.first.id] ?: Int.MAX_VALUE },
                    { it.second.sortOrder },
                ),
            )
            .map { it.second }
    }
}
