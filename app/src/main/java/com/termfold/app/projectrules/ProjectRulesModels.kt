package com.termfold.app.projectrules

enum class ProjectRuleProfileKind {
    /** Matched by folder name, origin, or explicit folder ids. */
    PROJECT,
    /** Visible in every project; enabled per session only. */
    GLOBAL,
}

data class ProjectRule(
    val id: String,
    val title: String,
    val body: String,
    val sortOrder: Int = 0,
)

data class ProjectRuleProfile(
    val id: String,
    val displayName: String,
    val kind: ProjectRuleProfileKind = ProjectRuleProfileKind.PROJECT,
    val originKey: String? = null,
    /** When non-empty, rules apply to these folders only (overrides name/origin matching). */
    val folderIds: List<String> = emptyList(),
    val rules: List<ProjectRule> = emptyList(),
) {
    val isGlobal: Boolean get() = kind == ProjectRuleProfileKind.GLOBAL
}

data class ProjectRulesData(
    val profiles: List<ProjectRuleProfile> = emptyList(),
    /** sessionKey → enabled rule ids (order preserved). */
    val sessionBindings: Map<String, List<String>> = emptyMap(),
)

data class ProjectRuleSheetRow(
    val profile: ProjectRuleProfile,
    val rule: ProjectRule,
    val enabled: Boolean,
    val eligible: Boolean,
)
