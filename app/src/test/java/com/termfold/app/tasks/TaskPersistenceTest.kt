package com.termfold.app.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPersistenceTest {

    @Test
    fun roundTripTaskWithAttachments() {
        val task = TaskItem(
            id = "t1",
            folderId = "f1",
            dayKey = "2026-10-30",
            title = "Ship PDF tools",
            description = "QA on device",
            status = TaskStatus.IN_PROGRESS,
            attachments = listOf(TaskAttachment("spec.pdf", "/data/spec.pdf")),
            updatedAtEpochMs = 123L,
        )
        val data = TasksData(tasks = listOf(task))
        val decoded = TaskPersistence.decode(TaskPersistence.encode(data))
        assertEquals(1, decoded.tasks.size)
        assertEquals(task, decoded.tasks.single())
    }

    @Test
    fun statusFromWire() {
        assertEquals(TaskStatus.DONE, TaskStatus.fromWire("done"))
        assertEquals(TaskStatus.TODO, TaskStatus.fromWire(null))
    }

    @Test
    fun dayKeyFormat() {
        assertEquals("2026-01-05", TaskDay.key(java.time.LocalDate.of(2026, 1, 5)))
    }
}
