package com.termfold.app.projectrules

import android.content.Context
import com.termfold.app.core.Folder
import java.io.File
import java.util.UUID

class ProjectRulesStore private constructor(context: Context) {
    private val file = File(context.applicationContext.filesDir, "project-rules/store.json")

    init {
        file.parentFile?.mkdirs()
    }

    fun read(): ProjectRulesData = synchronized(LOCK) {
        if (!file.isFile) return ProjectRulesData()
        ProjectRulesPersistence.decode(runCatching { file.readText() }.getOrNull())
    }

    fun write(data: ProjectRulesData) = synchronized(LOCK) {
        file.parentFile?.mkdirs()
        file.writeText(ProjectRulesPersistence.encode(data))
    }

    fun update(transform: (ProjectRulesData) -> ProjectRulesData) {
        write(transform(read()))
    }

    fun setRuleEnabled(
        sessionKey: String,
        ruleId: String,
        enabled: Boolean,
        folder: Folder?,
        folderOriginKey: String?,
    ) {
        update { data ->
            var profiles = data.profiles
            if (enabled && folder != null && folderOriginKey != null) {
                val located = ProjectRulesContextResolver.locateRule(data, ruleId)
                if (located != null && located.first.originKey == null) {
                    profiles = profiles.map { profile ->
                        if (profile.id == located.first.id) {
                            profile.copy(originKey = folderOriginKey)
                        } else {
                            profile
                        }
                    }
                }
            }
            val current = data.sessionBindings[sessionKey]?.toMutableList() ?: mutableListOf()
            if (enabled) {
                if (ruleId !in current) current += ruleId
            } else {
                current.removeAll { it == ruleId }
            }
            val bindings = data.sessionBindings.toMutableMap()
            if (current.isEmpty()) {
                bindings.remove(sessionKey)
            } else {
                bindings[sessionKey] = current
            }
            ProjectRulesData(profiles = profiles, sessionBindings = bindings)
        }
    }

    fun createProfile(displayName: String): ProjectRuleProfile {
        val profile = ProjectRuleProfile(
            id = UUID.randomUUID().toString(),
            displayName = displayName.trim(),
        )
        update { it.copy(profiles = it.profiles + profile) }
        return profile
    }

    fun saveProfile(profile: ProjectRuleProfile) {
        update { data ->
            val next = data.profiles.map { if (it.id == profile.id) profile else it }
            if (next.none { it.id == profile.id }) {
                data.copy(profiles = data.profiles + profile)
            } else {
                data.copy(profiles = next)
            }
        }
    }

    fun deleteProfile(profileId: String) {
        update { data ->
            val removed = data.profiles.find { it.id == profileId } ?: return@update data
            val ruleIds = removed.rules.map { it.id }.toSet()
            val bindings = data.sessionBindings
                .mapValues { (_, ids) -> ids.filter { it !in ruleIds } }
                .filterValues { it.isNotEmpty() }
            data.copy(
                profiles = data.profiles.filter { it.id != profileId },
                sessionBindings = bindings,
            )
        }
    }

    companion object {
        private val LOCK = Any()

        @Volatile
        private var instance: ProjectRulesStore? = null

        fun get(context: Context): ProjectRulesStore {
            val app = context.applicationContext
            return instance ?: synchronized(LOCK) {
                instance ?: ProjectRulesStore(app).also { instance = it }
            }
        }
    }
}
