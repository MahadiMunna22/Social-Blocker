package ch.puc.blocker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.puc.blocker.data.ChallengeType
import ch.puc.blocker.data.GlobalSettings
import ch.puc.blocker.domain.Decision
import ch.puc.blocker.domain.Reason
import ch.puc.blocker.domain.WaitState
import ch.puc.blocker.service.BlockerService
import java.text.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date

// ---------- helpers ----------

internal fun time(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
private fun dateTime(ms: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
internal fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
internal fun hm(minutes: Long) = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

// ---------- scaffold ----------

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Home), TASKS("Tasks", Icons.Default.CheckCircle),
    REPORTS("Reports", Icons.Default.DateRange), SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun MainScaffold(vm: SettingsViewModel, openSetup: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var confirmExit by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    // Back: other tabs return to Today; on Today, ask before closing.
    BackHandler { if (tab != Tab.TODAY) tab = Tab.TODAY else confirmExit = true }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Close Social Blocker?") },
        text = { Text("Your limits keep working in the background.") },
        confirmButton = { TextButton(onClick = { activity?.finish() }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { UnlockStrip(vm) },
        bottomBar = { BottomBar(tab) { tab = it } },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.TODAY -> TodayTab(vm, openSetup)
                Tab.TASKS -> TasksTab(vm)
                Tab.REPORTS -> ReportsTab(vm)
                Tab.SETTINGS -> SettingsTab(vm, openSetup)
            }
        }
    }
}

/** Floating white bar, as in the reference design. */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 6.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { t ->
                val on = t == selected
                val color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(t) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, null, tint = color)
                    Text(t.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Only visible while editing is unlocked or a wait is running, so the lock never clutters the UI. */
@Composable
private fun UnlockStrip(vm: SettingsViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val unlocked = vm.isUnlocked(now)
    val wait = if (now > 0) vm.waitState() else WaitState.None
    if (!unlocked && wait == WaitState.None) return
    Row(
        Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
            .background((if (unlocked) Green else Accent).copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable { if (unlocked) vm.lock() else vm.openUnlock() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                unlocked -> "🔓 Editing unlocked · ${countdown(vm.unlockedUntilMs - now)}"
                wait is WaitState.Waiting -> "⏳ Unlock in ${countdown(wait.remainingMs)}"
                else -> "✓ Wait finished. Tap to unlock"
            },
            Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
        )
        Text(if (unlocked) "Lock" else "View", fontWeight = FontWeight.Bold, color = if (unlocked) Green else Accent)
    }
}

internal fun LazyListScope.header(title: String, subtitle: String? = null) = item {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun Screen(title: String? = null, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (title != null) header(title, subtitle)
        content()
    }
}

@Composable
internal fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
internal fun IconBadge(icon: ImageVector, tint: Color, size: Int = 36) = Box(
    Modifier.size(size.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center,
) { Icon(icon, null, Modifier.size((size * 0.5f).dp), tint = tint) }

// ---------- Today: status + your apps, all in one place ----------

@Composable
private fun TodayTab(vm: SettingsViewModel, openSetup: () -> Unit) {
    val ctx = LocalContext.current
    val now by vm.now.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val morningUntil by vm.morningUntilMs.collectAsStateWithLifecycle()
    val reward by vm.rewards.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val permsOk = now > 0 && Perms.requiredGranted(ctx)
    val hour = LocalTime.now().hour

    Screen {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (hour) { in 5..11 -> "Good morning,"; in 12..17 -> "Good afternoon,"; else -> "Good evening," },
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Stay focused 👏", style = MaterialTheme.typography.headlineLarge)
                }
                AppLogo(52.dp)
            }
        }
        if (!permsOk || !BlockerService.running) item {
            Notice(Icons.Default.Warning, Danger,
                if (!permsOk) "Finish setup" else "Blocker is stopped",
                if (!permsOk) "Permissions are missing, so nothing is blocked." else "Tap to start it again.",
            ) { if (!permsOk) openSetup() else BlockerService.start(ctx) }
        }
        if (settings?.blockingEnabled == false) item {
            Notice(Icons.Default.PlayArrow, Amber, "Blocking is paused", "Tap to turn it back on") { vm.toggleBlocking() }
        }
        item { HeroCard(rows.filter { it.app.enabled }, morningUntil, now) }
        if (rows.isNotEmpty()) {
            item { SectionTitle("Your apps") }
            item {
                WhiteCard {
                    Column {
                        rows.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            AppRowItem(row, now, reward.active[row.app.packageName]) { editing = row.app.packageName }
                        }
                    }
                }
            }
        }
        item { AddMoreCard(first = rows.isEmpty()) { picking = true } }
    }
    if (picking) AppPickerSheet(vm) { picking = false }
    editing?.let { pkg -> rows.find { it.app.packageName == pkg }?.let { AppSheet(it.app, vm) { editing = null } } }
}

@Composable
private fun Notice(icon: ImageVector, tint: Color, title: String, body: String, onClick: () -> Unit) {
    WhiteCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun HeroCard(rows: List<AppRow>, morningUntil: Long?, now: Long) {
    val usedMin = rows.sumOf { it.usedMs } / 60_000
    val capMin = rows.sumOf { it.app.dailyCapMin }.toLong()
    val frac = when {
        morningUntil != null -> 1f - ((morningUntil - now) / 60_000f / 30f).coerceIn(0f, 1f)
        capMin == 0L -> 0f
        else -> (usedMin.toFloat() / capMin).coerceIn(0f, 1f)
    }
    Box(Modifier.fillMaxWidth().background(heroBrush(), RoundedCornerShape(28.dp)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (morningUntil != null) {
                    Text("☀ Morning lockout", fontWeight = FontWeight.Medium)
                    Text(countdown(morningUntil - now), style = MaterialTheme.typography.displayMedium)
                    Text("Apps unlock at ${time(morningUntil)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Screen time today", fontWeight = FontWeight.Medium)
                    Text(hm(usedMin), style = MaterialTheme.typography.displayMedium)
                    Text(if (capMin > 0) "of ${hm(capMin)} limit" else "Add an app to start", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.size(120.dp).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f), CircleShape).padding(8.dp)) {
                GradientRing(frac, 104.dp, 11.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                        Text(if (morningUntil != null) "done" else "used", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun statusText(d: Decision, now: Long): Pair<String, Color>? = when (d) {
    Decision.Allow -> null
    is Decision.Block -> when (d.reason) {
        Reason.MORNING -> "Morning lockout · ${countdown(d.untilMs - now)}" to Amber
        Reason.COOLDOWN -> "On a break · ${countdown(d.untilMs - now)}" to Danger
        Reason.DAILY_CAP -> "Blocked for today" to Danger
    }
}

@Composable
private fun AppRowItem(row: AppRow, now: Long, rewardUntil: Long?, onClick: () -> Unit) {
    val a = row.app
    val used = row.usedMs / 60_000
    val status = rewardUntil?.let { "🎁 Reward unlock · ${countdown(it - now)}" to Accent } ?: statusText(row.decision, now)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.packageName, a.label, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(a.label, fontWeight = FontWeight.SemiBold)
            Text(
                when { !a.enabled -> "Not limited"; status != null -> status.first; else -> "${(a.dailyCapMin - used).coerceAtLeast(0)} min left today" },
                color = when { !a.enabled -> MaterialTheme.colorScheme.onSurfaceVariant; status != null -> status.second; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                style = MaterialTheme.typography.bodySmall,
            )
            if (a.enabled) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmoothBar(used.toFloat() / a.dailyCapMin, Modifier.weight(1f), alert = if (status != null && rewardUntil == null) Danger else null)
                Text("$used/${a.dailyCapMin}m", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Reports ----------

@Composable
private fun ReportsTab(vm: SettingsViewModel) {
    val week by vm.week.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }

    Screen("Reports", "Your week at a glance") {
        item {
            WhiteCard {
                Column(Modifier.padding(20.dp)) {
                    val avg = if (week.isEmpty()) 0 else week.sumOf { it.minutes } / week.size
                    Text("Daily average", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(hm(avg), style = MaterialTheme.typography.headlineMedium)
                    // Tap a bar to see that day's apps; today is selected at first.
                    var selected by rememberSaveable { mutableStateOf(6) }
                    WeekChart(week, selected) { selected = it }
                    week.getOrNull(selected)?.let { DayBreakdown(it, isToday = selected == week.lastIndex) }
                }
            }
        }
        item {
            val passed = results.count { it.passed }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Quizzes passed", "$passed/${results.size}", Modifier.weight(1f))
                StatCard("Rule changes", "${changes.size}", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Rule changes") }
        item {
            WhiteCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (changes.isEmpty()) EmptyLine("No changes yet. Nice discipline!")
                    (if (showAll) changes else changes.take(5)).forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        LogRow(c.what, "${c.oldValue} → ${c.newValue}", dateTime(c.timestampMs))
                    }
                    if (changes.size > 5) TextButton(onClick = { showAll = !showAll }, Modifier.padding(horizontal = 8.dp)) {
                        Text(if (showAll) "Show less" else "Show all ${changes.size}")
                    }
                }
            }
        }
        item { SectionTitle("Quiz attempts") }
        item {
            WhiteCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (results.isEmpty()) EmptyLine("No attempts yet.")
                    results.take(10).forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${r.type.name.lowercase().replaceFirstChar(Char::uppercase)} · ${r.score}/${r.total}", fontWeight = FontWeight.SemiBold)
                                Text(dateTime(r.timestampMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(if (r.passed) "Passed" else "Failed", if (r.passed) Green else Danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) =
    Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun LogRow(title: String, detail: String, time: String) = Row(Modifier.padding(16.dp)) {
    Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) = WhiteCard(modifier) {
    Column(Modifier.padding(20.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayBreakdown(day: DayTotal, isToday: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (isToday) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale)),
            Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
        )
        Text(hm(day.minutes), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    if (day.apps.isEmpty()) {
        Text("No tracked app use.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val max = (day.apps.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    day.apps.forEach { a ->
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.packageName, a.label, size = 28.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(a.label, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(a.minutes.toFloat() / max).height(6.dp)
                    .background(Accent.copy(alpha = 0.6f), RoundedCornerShape(3.dp)))
            }
            Text(hm(a.minutes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeekChart(week: List<DayTotal>, selected: Int, onSelect: (Int) -> Unit) {
    val max = (week.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
        week.forEachIndexed { i, d ->
            val barHeight by animateDpAsState((110 * d.minutes / max).toInt().dp, label = "bar")
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("${d.minutes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.padding(horizontal = 7.dp, vertical = 4.dp).fillMaxWidth().height(barHeight.coerceAtLeast(4.dp))
                        .background(if (i == selected) Accent else Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                )
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun SettingsTab(vm: SettingsViewModel, openSetup: () -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val saved by vm.settings.collectAsStateWithLifecycle()
    val unlocked = vm.isUnlocked(now)
    val s0 = saved ?: GlobalSettings()
    // Local draft so sliders move smoothly; saved with the button.
    var draft by remember(s0) { mutableStateOf(s0) }
    val commit = { new: GlobalSettings -> draft = new; vm.updateSettings(new) }

    Screen("Settings", "Rules that apply to every app") {
        if (!unlocked) item { LockedHint(vm::openUnlock) }
        item {
            WhiteCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Blocking", fontWeight = FontWeight.SemiBold)
                            Text(if (draft.blockingEnabled) "Limits are enforced" else "Paused: nothing is blocked",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(draft.blockingEnabled, { vm.toggleBlocking() })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ValueSlider("☀ Morning lockout", draft.morningLockoutMin, 0..120, 5, "min", unlocked) { draft = draft.copy(morningLockoutMin = it) }
                    ValueSlider("🌅 Morning starts at", draft.morningStartHour, 3..11, 1, "am", unlocked) { draft = draft.copy(morningStartHour = it) }
                    ValueSlider("↩ Grace for quick app switches", draft.sessionGraceSec, 0..300, 15, "s", unlocked) { draft = draft.copy(sessionGraceSec = it) }
                    if (draft.morningLockoutMin != s0.morningLockoutMin || draft.morningStartHour != s0.morningStartHour ||
                        draft.sessionGraceSec != s0.sessionGraceSec) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { draft = s0 }) { Text("Reset") }
                            PillButton("Save") { commit(draft) }
                        }
                    }
                }
            }
        }
        item {
            WhiteCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Unlock quiz", fontWeight = FontWeight.SemiBold)
                    Text("Get ${vm.passThreshold} of ${vm.questionCount} right, or wait ${vm.waitMinutes} min instead.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ChallengeType.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = draft.challengeType == t,
                                onClick = { commit(draft.copy(challengeType = t)) },
                                shape = SegmentedButtonDefaults.itemShape(i, ChallengeType.entries.size),
                                enabled = unlocked,
                            ) { Text(if (t == ChallengeType.VOCAB) "🇩🇪 German A2–B1" else "🧮 Math") }
                        }
                    }
                    if (draft.challengeType == ChallengeType.MATH) {
                        Text("Difficulty", Modifier.padding(top = 12.dp, bottom = 4.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = draft.mathDifficulty == i + 1,
                                    onClick = { commit(draft.copy(mathDifficulty = i + 1)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                    enabled = unlocked,
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
        item {
            WhiteCard {
                Column {
                    NavRow("Permissions & setup", "Check what the blocker needs", openSetup)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    NavRow("Preview block screen", "Shows it for 4 seconds", vm::previewOverlay)
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One-line hint shown above locked controls. */
@Composable
internal fun LockedHint(onUnlock: () -> Unit) = Row(
    Modifier.fillMaxWidth().background(AccentSoft.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("🔒 Unlock to change these", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2A1A66))
    TextButton(onClick = onUnlock) { Text("Unlock", fontWeight = FontWeight.Bold, color = Accent) }
}
