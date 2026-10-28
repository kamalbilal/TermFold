package com.termfold.app.projectrules

data class ProjectRule(
    val id: String,
    val title: String,
    val body: String,
    val sortOrder: Int = 0,
)

data class ProjectRuleProfile(
    val id: String,
    val displayName: String,
    val originKey: String? = null,
    val rules: List<ProjectRule> = emptyList(),
)

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
