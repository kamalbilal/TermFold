package com.termfold.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.termfold.app.core.Ids
import com.termfold.app.gitworkspace.SessionKeys
import com.termfold.app.shell.TerminalHost

data class AuxTerminalTab(val id: String, val title: String)

private const val MAX_AUX_TABS = 8

private val tabsByParent = mutableMapOf<String, SnapshotStateList<AuxTerminalTab>>()
private val selectedTabByParent = mutableMapOf<String, String>()

object AuxTerminalTabsStore {
    fun parentKey(folderId: String, workspaceId: String, acpSessionId: String): String =
        SessionKeys.compose(folderId, workspaceId, acpSessionId)

    fun tabs(parentKey: String): SnapshotStateList<AuxTerminalTab> =
        tabsByParent.getOrPut(parentKey) {
            mutableStateListOf(AuxTerminalTab(Ids.new(), "1"))
        }

    fun selectedTabId(parentKey: String): String? = selectedTabByParent[parentKey]

    fun setSelectedTabId(parentKey: String, tabId: String) {
        selectedTabByParent[parentKey] = tabId
    }

    fun addTab(parentKey: String): AuxTerminalTab? {
        val list = tabs(parentKey)
        if (list.size >= MAX_AUX_TABS) return null
        val tab = AuxTerminalTab(Ids.new(), (list.size + 1).toString())
        list.add(tab)
        setSelectedTabId(parentKey, tab.id)
        return tab
    }

    fun closeTab(
        folderId: String,
        workspaceId: String,
        acpSessionId: String,
        tabId: String,
    ): Boolean {
        val parentKey = parentKey(folderId, workspaceId, acpSessionId)
        val list = tabs(parentKey)
        if (list.size <= 1) return false
        val tab = list.firstOrNull { it.id == tabId } ?: return false
        val tabKey = TerminalHost.sessionKey(
            folderId,
            workspaceId,
            auxTerminalSessionId(acpSessionId, tab.id),
        )
        TerminalHost.closeSession(tabKey)
        list.remove(tab)
        if (selectedTabByParent[parentKey] == tabId) {
            setSelectedTabId(parentKey, list.first().id)
        }
        return true
    }
}

@Composable
fun rememberAuxTerminalTabs(parentKey: String): SnapshotStateList<AuxTerminalTab> =
    remember(parentKey) { AuxTerminalTabsStore.tabs(parentKey) }
