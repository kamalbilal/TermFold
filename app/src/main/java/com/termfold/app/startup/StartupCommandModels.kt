package com.termfold.app.startup

data class StartupCommand(
    val id: String,
    val name: String,
    /** [com.termfold.app.core.Folder.id] where the command runs. */
    val folderId: String,
    val command: String,
)

data class StartupCommandGroup(
    val id: String,
    val name: String,
    /** Ordered [StartupCommand.id] values started together. */
    val commandIds: List<String>,
)

data class StartupCommandsData(
    val commands: List<StartupCommand> = emptyList(),
    val groups: List<StartupCommandGroup> = emptyList(),
) {
    fun ungroupedCommands(): List<StartupCommand> {
        val grouped = groups.flatMap { it.commandIds }.toSet()
        return commands.filter { it.id !in grouped }
    }

    fun commandsInGroup(groupId: String): List<StartupCommand> {
        val group = groups.firstOrNull { it.id == groupId } ?: return emptyList()
        val byId = commands.associateBy { it.id }
        return group.commandIds.mapNotNull { byId[it] }
    }
}
