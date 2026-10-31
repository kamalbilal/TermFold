package com.termfold.app.tasks

import androidx.annotation.StringRes
import com.termfold.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class TaskStatus(@param:StringRes val labelRes: Int, val wire: String) {
    IN_PROGRESS(R.string.task_status_in_progress, "in_progress"),
    TODO(R.string.task_status_todo, "todo"),
    BLOCKED(R.string.task_status_blocked, "blocked"),
    DONE(R.string.task_status_done, "done"),
    ;

    companion object {
        val displayOrder = listOf(IN_PROGRESS, TODO, BLOCKED, DONE)

        fun fromWire(raw: String?): TaskStatus =
            entries.firstOrNull { it.wire == raw } ?: TODO
    }
}

data class TaskAttachment(
    val displayName: String,
    val path: String,
)

data class TaskItem(
    val id: String,
    val folderId: String,
    /** Local calendar day in ISO form (`yyyy-MM-dd`). */
    val dayKey: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val attachments: List<TaskAttachment> = emptyList(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

data class TasksData(
    val tasks: List<TaskItem> = emptyList(),
)

object TaskDay {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun key(date: LocalDate): String = date.format(formatter)

    fun parse(key: String): LocalDate? = runCatching { LocalDate.parse(key, formatter) }.getOrNull()

    fun todayKey(): String = key(LocalDate.now())
}
