package ch.puc.blocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import ch.puc.blocker.BlockerApp
import ch.puc.blocker.R
import ch.puc.blocker.data.TaskItem
import ch.puc.blocker.domain.QuickTask
import ch.puc.blocker.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Quick task capture and check-off straight from notifications. */
object TaskNotifications {
    private const val CHANNEL = "tasks"
    const val ID_REMINDER = 3
    const val ACTION_ADD = "ch.puc.blocker.TASK_ADD"
    const val ACTION_DONE = "ch.puc.blocker.TASK_DONE"
    const val KEY_TEXT = "task_text"
    const val EXTRA_ID = "task_id"

    fun channel() = NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT)

    /** "Add task" button with an inline text field: type "Essay 2h" and send. */
    fun addAction(ctx: Context): Notification.Action {
        val input = RemoteInput.Builder(KEY_TEXT).setLabel("Task, e.g. Essay 2h").build()
        val pi = PendingIntent.getBroadcast(
            ctx, 10, Intent(ctx, TaskActionReceiver::class.java).setAction(ACTION_ADD),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, // mutable: the system fills in the typed text
        )
        return Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "➕ Add task", pi)
            .addRemoteInput(input).build()
    }

    private fun doneAction(ctx: Context, task: TaskItem): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, 20, Intent(ctx, TaskActionReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "✓ Done", pi).build()
    }

    private fun next(open: List<TaskItem>) = open.minWithOrNull(compareBy({ it.dueEpochDay }, { it.createdMs }))

    /** "Next: Essay · 120 min", or null when nothing is open. */
    fun nextLine(open: List<TaskItem>): String? = next(open)?.let { "Next: ${it.title} · ${it.estimateMin} min" }

    /** "2 task(s) due today" / "3 task(s) coming up" / "No open tasks". */
    fun headline(open: List<TaskItem>): String {
        val due = open.count { it.dueEpochDay <= LocalDate.now().toEpochDay() }
        return when {
            open.isEmpty() -> "No open tasks"
            due > 0 -> "$due task(s) due today"
            else -> "${open.size} task(s) coming up"
        }
    }

    /** One line per task for expanded notifications: "Essay · 120 min · due today". */
    fun line(t: TaskItem): String {
        val days = t.dueEpochDay - LocalDate.now().toEpochDay()
        val due = when {
            days < 0 -> "overdue"
            days == 0L -> "due today"
            days == 1L -> "due tomorrow"
            else -> "due in $days days"
        }
        return "• ${t.title} · ${t.estimateMin} min · $due"
    }

    /** Shows the next open task with Done and Add buttons. [note] confirms the last action. */
    fun showReminder(ctx: Context, open: List<TaskItem>, note: String? = null) {
        val next = next(open)
        val title = note ?: headline(open)
        val text = nextLine(open) ?: "Add something you need to get done."
        val openApp = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(note != null) // confirmations update silently
            .apply { next?.let { addAction(doneAction(ctx, it)) } }
            .addAction(addAction(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_REMINDER, n)
    }
}

/** Handles the notification buttons, then re-posts both notifications (clears the reply spinner). */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BlockerApp
        val dao = app.db.dao()
        val pending = goAsync()
        app.scope.launch {
            try {
                val note = when (intent.action) {
                    TaskNotifications.ACTION_ADD -> {
                        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(TaskNotifications.KEY_TEXT)?.toString()
                        QuickTask.parse(typed.orEmpty())?.let { p ->
                            dao.upsertTask(TaskItem(title = p.title, category = "OTHER", dueEpochDay = LocalDate.now().toEpochDay(),
                                estimateMin = p.estimateMin, createdMs = System.currentTimeMillis()))
                            "Added “${p.title}” (${p.estimateMin} min)"
                        }
                    }
                    TaskNotifications.ACTION_DONE -> dao.task(intent.getLongExtra(TaskNotifications.EXTRA_ID, -1))?.let { t ->
                        dao.upsertTask(t.copy(doneMs = System.currentTimeMillis()))
                        "Done: ${t.title} ✓"
                    }
                    else -> null
                }
                TaskNotifications.showReminder(context, dao.openTasks(), note)
                BlockerService.start(context, BlockerService.ACTION_REFRESH_NOTIFICATION)
            } finally {
                pending.finish()
            }
        }
    }
}
