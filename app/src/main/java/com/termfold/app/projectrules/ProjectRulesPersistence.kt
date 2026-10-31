package com.termfold.app.projectrules

import org.json.JSONArray
import org.json.JSONObject

object ProjectRulesPersistence {
    fun decode(raw: String?): ProjectRulesData {
        if (raw.isNullOrBlank()) return ProjectRulesData()
        return runCatching {
            val root = JSONObject(raw)
            val profiles = root.optJSONArray("profiles") ?: JSONArray()
            val bindings = root.optJSONObject("sessionBindings") ?: JSONObject()
            val bindingMap = buildMap {
                bindings.keys().forEach { key ->
                    val arr = bindings.optJSONArray(key) ?: JSONArray()
                    put(
                        key,
                        List(arr.length()) { i -> arr.getString(i) },
                    )
                }
            }
            ProjectRulesData(
                profiles = List(profiles.length()) { i -> profiles.getJSONObject(i).toProfile() },
                sessionBindings = bindingMap,
            )
        }.getOrElse { ProjectRulesData() }
    }

    fun encode(data: ProjectRulesData): String {
        val profiles = JSONArray()
        data.profiles.forEach { profiles.put(it.toJson()) }
        val bindings = JSONObject()
        data.sessionBindings.forEach { (key, ids) ->
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            bindings.put(key, arr)
        }
        return JSONObject()
            .put("profiles", profiles)
            .put("sessionBindings", bindings)
            .toString()
    }

    private fun JSONObject.toProfile(): ProjectRuleProfile {
        val rules = optJSONArray("rules") ?: JSONArray()
        val origin = optString("originKey", "").ifBlank { null }
        val folderIdsArr = optJSONArray("folderIds") ?: JSONArray()
        val folderIds = List(folderIdsArr.length()) { i -> folderIdsArr.getString(i) }
        val kind = when (optString("kind", "").lowercase()) {
            "global" -> ProjectRuleProfileKind.GLOBAL
            else -> ProjectRuleProfileKind.PROJECT
        }
        return ProjectRuleProfile(
            id = getString("id"),
            displayName = getString("displayName"),
            kind = kind,
            originKey = origin,
            folderIds = folderIds,
            rules = List(rules.length()) { i -> rules.getJSONObject(i).toRule() },
        )
    }

    private fun JSONObject.toRule(): ProjectRule =
        ProjectRule(
            id = getString("id"),
            title = getString("title"),
            body = optString("body", ""),
            sortOrder = optInt("sortOrder", 0),
        )

    private fun ProjectRuleProfile.toJson(): JSONObject {
        val ruleArr = JSONArray()
        rules.sortedBy { it.sortOrder }.forEach { rule ->
            ruleArr.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("title", rule.title)
                    .put("body", rule.body)
                    .put("sortOrder", rule.sortOrder),
            )
        }
        return JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("kind", if (kind == ProjectRuleProfileKind.GLOBAL) "global" else "project")
            .put("originKey", originKey ?: "")
            .put("folderIds", JSONArray().also { arr -> folderIds.forEach { arr.put(it) } })
            .put("rules", ruleArr)
    }
}
