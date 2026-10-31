package com.termfold.app.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupCommandsPersistenceTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val data = StartupCommandsData(
            commands = listOf(
                StartupCommand(
                    id = "c1",
                    name = "Dev server",
                    folderId = "f1",
                    command = "npm run dev",
                ),
                StartupCommand(
                    id = "c2",
                    name = "API",
                    folderId = "f1",
                    command = "npm run api",
                ),
            ),
            groups = listOf(
                StartupCommandGroup(
                    id = "g1",
                    name = "Full stack",
                    commandIds = listOf("c1", "c2"),
                ),
            ),
        )
        val decoded = StartupCommandsPersistence.decode(StartupCommandsPersistence.encode(data))
        assertEquals(data, decoded)
    }

    @Test
    fun ungroupedCommandsExcludesGroupMembers() {
        val data = StartupCommandsData(
            commands = listOf(
                StartupCommand("c1", "A", "f", "a"),
                StartupCommand("c2", "B", "f", "b"),
            ),
            groups = listOf(StartupCommandGroup("g1", "G", listOf("c1"))),
        )
        assertEquals(listOf("c2"), data.ungroupedCommands().map { it.id })
        assertEquals(listOf("c1"), data.commandsInGroup("g1").map { it.id })
    }
}
