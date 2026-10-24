package ch.puc.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.puc.blocker.data.TaskItem
import ch.puc.blocker.data.TrackedApp
import ch.puc.blocker.domain.TaskRewards
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class Category(val label: String, val emoji: String) {
    ASSIGNMENT("Assignment", "📝"), HOMEWORK("Homework", "📚"), PROJECT("Project", "🧩"), OTHER("Other", "✅");
    companion object { fun of(key: String) = entries.find { it.name == key } ?: OTHER }
}

private fun dayLabel(epochDay: Long, today: Long): String = when (epochDay - today) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
}

@Composable
internal fun TasksTab(vm: SettingsViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val reward by vm.rewards.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var redeeming by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()

    val open = tasks.filter { it.doneMs == 0L }
    val done = tasks.filter { it.doneMs != 0L }.sortedByDescending { it.doneMs }
    // Date-wise groups: overdue first, then each day in order.
    val groups = open.groupBy { if (it.dueEpochDay < today) "Overdue" else dayLabel(it.dueEpochDay, today) }

    Screen {
        item {
            // Title with a round "+" instead of a full-width button.
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tasks", style = MaterialTheme.typography.headlineMedium)
                    Text("Finish tasks, earn app time", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                FilledIconButton(onClick = { adding = true }, Modifier.size(48.dp)) { Icon(Icons.Default.Add, "New task") }
            }
        }
        item { RewardCard(reward, now, rows.map { it.app }) { redeeming = true } }
        if (open.isEmpty()) item {
            Text("Nothing to do. Add assignments, homework or projects with a deadline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        groups.forEach { (label, list) ->
            item {
                Text(label.uppercase(), Modifier.padding(start = 16.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (label == "Overdue") Danger else MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
            }
            item { TaskList(list, today, onToggle = vm::toggleDone) { editing = it } }
        }
        if (done.isNotEmpty()) {
            item {
                TextButton(onClick = { showDone = !showDone }) {
                    Text(if (showDone) "Hide completed" else "Show completed (${done.size})")
                }
            }
            if (showDone) item { TaskList(done.take(30), today, onToggle = vm::toggleDone) { editing = it } }
        }
    }
    if (adding || editing != null) TaskSheet(editing, onDismiss = { adding = false; editing = null }, onSave = vm::saveTask, onDelete = vm::deleteTask)
    if (redeeming) RedeemSheet(rows.map { it.app }.filter { it.enabled }, onDismiss = { redeeming = false }) { vm.redeem(it); redeeming = false }
}

@Composable
private fun RewardCard(r: RewardState, now: Long, apps: List<TrackedApp>, onRedeem: () -> Unit) {
    val progress = (r.balanceMin % TaskRewards.COST_MIN) / TaskRewards.COST_MIN.toFloat()
    val ready = TaskRewards.canRedeem(r.balanceMin)
    WhiteCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎁 Reward progress", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                // Small tonal chip, so the card doesn't carry a second big call-to-action.
                if (ready) Text("Use ▸", Modifier.clip(RoundedCornerShape(50)).background(AccentSoft).clickable(onClick = onRedeem)
                    .padding(horizontal = 14.dp, vertical = 6.dp), color = Accent, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (ready) "${r.balanceMin / TaskRewards.COST_MIN} unlock(s) ready" else "${hm(r.balanceMin.toLong())} of 2h",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 4.dp),
            )
            SmoothBar(if (ready) 1f else progress, height = 6.dp)
            Text(
                "Every 2 hours of finished tasks unlocks one app for 10 minutes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
            r.active.forEach { (pkg, until) ->
                val label = apps.find { it.packageName == pkg }?.label ?: pkg
                Text("▶ $label unlocked · ${countdown(until - now)}", fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun TaskList(list: List<TaskItem>, today: Long, onToggle: (TaskItem) -> Unit, onOpen: (TaskItem) -> Unit) = WhiteCard {
    Column {
        list.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TaskRow(t, today, onToggle, onOpen)
        }
    }
}

@Composable
private fun TaskRow(t: TaskItem, today: Long, onToggle: (TaskItem) -> Unit, onOpen: (TaskItem) -> Unit) {
    val done = t.doneMs != 0L
    val cat = Category.of(t.category)
    val credited = TaskRewards.counts(TaskRewards.Task(t.estimateMin, t.createdMs, t.doneMs))
    Row(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (done) Green else Color.Transparent)
                .border(2.dp, if (done) Green else MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { onToggle(t) },
            contentAlignment = Alignment.Center,
        ) { if (done) Icon(Icons.Default.Check, "Done", Modifier.size(14.dp), tint = Color.White) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                t.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val sub = buildString {
                append("${cat.emoji} ${cat.label} · ⏱ ${hm(t.estimateMin.toLong())}")
                if (t.repeatWeekly) append(" · 🔁 weekly")
                if (done) append(if (credited) " · 🎁 counted" else " · not counted (finished too soon)")
                else if (t.dueEpochDay < today) append(" · due ${dayLabel(t.dueEpochDay, today)}")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = if (!done && t.dueEpochDay < today) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(existing: TaskItem?, onDismiss: () -> Unit, onSave: (TaskItem) -> Unit, onDelete: (TaskItem) -> Unit) {
    val today = LocalDate.now().toEpochDay()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var category by remember { mutableStateOf(Category.of(existing?.category ?: Category.HOMEWORK.name)) }
    var due by remember { mutableLongStateOf(existing?.dueEpochDay ?: today) }
    var estimate by remember { mutableIntStateOf(existing?.estimateMin ?: 60) }
    var picking by remember { mutableStateOf(false) }
    var weekly by remember { mutableStateOf(existing?.repeatWeekly ?: false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing == null) "New task" else "Edit task", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Physics worksheet") }, singleLine = true)

            Text("Type", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries) { c -> FilterChip(category == c, { category = c }, label = { Text("${c.emoji} ${c.label}") }) }
            }

            Text("Deadline", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val quick = listOf("Today" to today, "Tomorrow" to today + 1, "In 3 days" to today + 3, "Next week" to today + 7)
                items(quick) { (label, day) -> FilterChip(due == day, { due = day }, label = { Text(label) }) }
                item {
                    val custom = quick.none { it.second == due }
                    FilterChip(custom, { picking = true }, label = { Text(if (custom) "📅 ${dayLabel(due, today)}" else "📅 Pick date") })
                }
            }

            ValueSlider("Time needed", estimate, 15..240, 15, "min") { estimate = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 120).forEach { m -> FilterChip(estimate == m, { estimate = m }, label = { Text(hm(m.toLong())) }) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat every week", fontWeight = FontWeight.SemiBold)
                    Text("When you finish it, it comes back due next ${LocalDate.ofEpochDay(due).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(weekly, { weekly = it })
            }

            if (existing == null) Text(
                "Tip: add tasks before you start them. A task only counts toward rewards once it has been on your list for as long as its estimate.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Full-width primary action; Delete sits beside it as a quiet outlined button when editing.
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val shape = RoundedCornerShape(16.dp)
                if (existing != null) OutlinedButton(
                    onClick = { onDelete(existing); onDismiss() }, Modifier.height(52.dp), shape = shape,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                ) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Danger); Text("  Delete", color = Danger) }
                Button(
                    onClick = {
                        onSave(
                            existing?.copy(title = title.trim(), category = category.name, dueEpochDay = due, estimateMin = estimate, repeatWeekly = weekly)
                                ?: TaskItem(title = title.trim(), category = category.name, dueEpochDay = due, estimateMin = estimate,
                                    createdMs = System.currentTimeMillis(), repeatWeekly = weekly),
                        )
                        onDismiss()
                    },
                    Modifier.weight(1f).height(52.dp), enabled = title.isNotBlank(), shape = shape,
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    Text(if (existing == null) "  Add task" else "  Save changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(due).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RedeemSheet(apps: List<TrackedApp>, onDismiss: () -> Unit, onPick: (TrackedApp) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Unlock an app for 10 minutes", style = MaterialTheme.typography.titleLarge)
            Text("Uses 2 hours of finished tasks. All limits are paused for that app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            if (apps.isEmpty()) Text("No limited apps yet.", Modifier.padding(vertical = 16.dp))
            apps.forEach { a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(a) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.packageName, a.label, 44.dp)
                    Text(a.label, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                    Text("10 min", color = Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
