package ch.puc.blocker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import ch.puc.blocker.data.filterAppSet
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
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
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Settings
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import android.app.Activity
import android.net.VpnService
import ch.puc.blocker.receiver.Protection
import ch.puc.blocker.service.WebFilterService
import ch.puc.blocker.service.GuardService
import ch.puc.blocker.domain.Quotes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** [icon] when selected, [outline] otherwise. */
private enum class Tab(val label: String, val icon: ImageVector, val outline: ImageVector) {
    TODAY("Today", Icons.Filled.Home, Icons.Outlined.Home), TASKS("Tasks", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    FOCUS("Focus", Icons.Filled.Lock, Icons.Outlined.Lock), REPORTS("Reports", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MainScaffold(vm: SettingsViewModel, openSetup: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    LaunchedEffect(vm.requestedTab) { vm.requestedTab?.let { tab = Tab.valueOf(it); vm.requestedTab = null } } // from a notification
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
    ) { pad ->
        // The bar floats over the content and blurs what scrolls under it; each screen leaves room at the bottom.
        val haze = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                // Your photo, or the built-in dusk / morning backdrop.
                val backdrop = vm.background ?: ImageBitmap.imageResource(
                    if (isDarkLook) ch.puc.blocker.R.drawable.graphite_backdrop else ch.puc.blocker.R.drawable.graphite_backdrop_light,
                )
                BackgroundPhoto(backdrop, wash = if (vm.background == null) 0.25f else if (isDarkLook) 0.6f else 0.7f, tinted = vm.background == null)
                Box(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
                    when (tab) {
                        Tab.TODAY -> TodayTab(vm, openSetup)
                        Tab.TASKS -> TasksTab(vm)
                        Tab.FOCUS -> FocusTab(vm)
                        Tab.REPORTS -> ReportsTab(vm)
                        Tab.SETTINGS -> SettingsTab(vm, openSetup)
                    }
                }
            }
            // Blurs whatever scrolls out below the bar, fading in towards the bottom edge.
            val page = MaterialTheme.colorScheme.background
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .hazeBlur(HazeInput.Sources(haze), HazeBlurStyle {
                        blurRadius(20.dp)
                        backgroundColor(page)
                        colorEffects(listOf(HazeColorEffect.tint(page.copy(alpha = 0.35f))))
                        progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
                    }),
            )
            BottomBar(tab, haze, Modifier.align(Alignment.BottomCenter)) { tab = it }
        }
    }
}

/** The chosen photo behind every screen, blurred and washed in the page colour so cards and text stay readable. */
@Composable
private fun BackgroundPhoto(photo: ImageBitmap, wash: Float, tinted: Boolean) = Box(Modifier.fillMaxSize()) {
    // The built-in backdrop takes the accent colour, keeping its light and shade; your own photo stays as it is.
    Image(photo, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop,
        colorFilter = if (tinted) accentTint(Accent, if (isDarkLook) 0.6f else 0.15f) else null)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = wash)))
}

/** Greyscale times a mix of white and [accent]: [strength] 0 is plain grey, 1 the full accent hue at the same brightness. */
private fun accentTint(accent: Color, strength: Float): ColorFilter {
    val lum = (0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue).coerceAtLeast(0.05f)
    val f = listOf(accent.red, accent.green, accent.blue).map { (1 - strength + strength * it / lum).coerceAtMost(3f) }
    return ColorFilter.colorMatrix(ColorMatrix(FloatArray(20).also { m ->
        f.forEachIndexed { row, k -> m[row * 5] = 0.299f * k; m[row * 5 + 1] = 0.587f * k; m[row * 5 + 2] = 0.114f * k }
        m[18] = 1f // alpha unchanged
    }))
}

/** Floating frosted bar: blurs the screen under it; the selected tab turns filled and accent-coloured. */
@Composable
private fun BottomBar(selected: Tab, haze: HazeState, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDarkLook) 0.7f else 0.72f)
    val rim = Color.White.copy(alpha = if (isDarkLook) 0.12f else 0.8f)
    val page = MaterialTheme.colorScheme.background
    Row(
        // 12 dp from the sides, a little more from the bottom so it clears the gesture handle.
        modifier.padding(start = 12.dp, end = 12.dp, bottom = 22.dp)
            .fillMaxWidth()
            .clip(shape)
            .hazeBlur(HazeInput.Sources(haze), HazeBlurStyle {
                blurRadius(24.dp)
                backgroundColor(page)
                colorEffects(listOf(HazeColorEffect.tint(tint)))
            })
            .border(1.dp, rim, shape)
            .padding(vertical = 8.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val color by animateColorAsState(if (on) Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), label = "tab")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .clickable(interactionSource = null, indication = null) { onSelect(t) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (on) t.icon else t.outline, t.label, Modifier.size(26.dp), tint = color)
                Text(t.label, Modifier.padding(top = 2.dp), color = color, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
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
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun Screen(title: String? = null, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        // Bottom: clears the floating tab bar and the system navigation bar.
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
    val streak by vm.streak.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val permsOk = now > 0 && Perms.requiredGranted(ctx)
    val hour = LocalTime.now().hour

    Screen {
        item {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (hour) { in 5..11 -> "Good morning,"; in 12..17 -> "Good afternoon,"; else -> "Good evening," },
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Stay focused", style = MaterialTheme.typography.headlineMedium)
                }
                AppLogo(40.dp)
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
        item { HeroCard(rows.filter { it.app.enabled && !it.app.banned }, morningUntil, now) }
        if (streak > 0) item {
            Text("🔥 $streak-day streak under your limits", Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.titleMedium, color = Amber)
        }
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
    Box(Modifier.fillMaxWidth().background(heroBrush(), RoundedCornerShape(24.dp)).padding(18.dp)) {
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
            Box(Modifier.size(100.dp).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f), CircleShape).padding(7.dp)) {
                ProgressRing(frac, 86.dp, 10.dp) {
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
        Reason.BANNED -> "🚫 Can't be installed" to Danger
        Reason.OPENS -> "No opens left today" to Danger
    }
}

@Composable
private fun AppRowItem(row: AppRow, now: Long, rewardUntil: Long?, onClick: () -> Unit) {
    val a = row.app
    val used = row.usedMs / 60_000
    val status = rewardUntil?.let { "🎁 Reward unlock · ${countdown(it - now)}" to Accent } ?: statusText(row.decision, now)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.packageName, a.label, 38.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(a.label, fontWeight = FontWeight.SemiBold)
            Text(
                when { !a.enabled -> "Not limited"; status != null -> status.first; else -> "${(a.dailyCapMin - used).coerceAtLeast(0)} min left today" },
                color = when { !a.enabled -> MaterialTheme.colorScheme.onSurfaceVariant; status != null -> status.second; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                style = MaterialTheme.typography.bodySmall,
            )
            if (a.enabled && !a.banned) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun ToggleRow(title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit, alert: Boolean = false, onClick: (() -> Unit)? = null) =
    Row(
        Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = if (alert) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
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
    Row(Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
        week.forEachIndexed { i, d ->
            val barHeight by animateDpAsState((88 * d.minutes / max).toInt().dp, label = "bar")
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

    Screen("Settings") {
        if (!unlocked) item { LockedHint(vm::openUnlock) }
        item { Group("Appearance") { AppearanceSection(vm, s0) } }
        item {
            Group("Limits", "Loosening a limit needs the unlock.") {
                ToggleRow("Blocking", if (draft.blockingEnabled) "Limits are enforced" else "Paused: nothing is blocked",
                    draft.blockingEnabled, onChange = { vm.toggleBlocking() })
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ValueSlider("Morning lockout", draft.morningLockoutMin, 0..120, 5, "min", unlocked) { draft = draft.copy(morningLockoutMin = it) }
                    ValueSlider("Morning starts at", draft.morningStartHour, 3..11, 1, "am", unlocked) { draft = draft.copy(morningStartHour = it) }
                    ValueSlider("Grace for quick app switches", draft.sessionGraceSec, 0..300, 15, "s", unlocked) { draft = draft.copy(sessionGraceSec = it) }
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
            Group("Unlock quiz", "Get ${vm.passThreshold} of ${vm.questionCount} right, or wait ${vm.waitMinutes} min instead. An easier quiz needs the unlock.") {
                Column(Modifier.padding(16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ChallengeType.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = s0.challengeType == t,
                                onClick = { vm.setQuiz(t, s0.mathDifficulty) },
                                shape = SegmentedButtonDefaults.itemShape(i, ChallengeType.entries.size),
                            ) { Text(if (t == ChallengeType.VOCAB) "German A2–B1" else "Math") }
                        }
                    }
                    if (s0.challengeType == ChallengeType.MATH) {
                        Text("Difficulty", Modifier.padding(top = 12.dp, bottom = 4.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = s0.mathDifficulty == i + 1,
                                    onClick = { vm.setQuiz(ChallengeType.MATH, i + 1) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
        item { Group("Setup") { NavRow("Permissions & setup", "What the blocker needs", openSetup) } }
    }
}

/** Focus tools, block screen and protection: everything that keeps you from slipping past the limits. */
@Composable
private fun FocusTab(vm: SettingsViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val saved by vm.settings.collectAsStateWithLifecycle()
    val s = saved ?: GlobalSettings()
    val unlocked = vm.isUnlocked(now)
    Screen("Focus") {
        item {
            Group("Focus tools", "Turning these off needs the unlock.") {
                ToggleRow("Pause before opening", "5 s breather before each new session", s.pauseBeforeOpen, vm::setPauseBeforeOpen)
                RowDivider()
                ToggleRow("Block Shorts & Reels", "YouTube Shorts, Instagram Reels", s.blockShortForm, vm::setBlockShortForm)
            }
        }
        item {
            Group("Block screen") {
                QuotesSection(vm, s)
                RowDivider()
                NavRow("Preview", "See the block screen", vm::previewOverlay)
            }
        }
        item { Group("Protection") { ProtectionSection(vm, s.webFilter, s.filterAppSet, s.blockAds, unlocked, now) } }
    }
}

/** iOS-style grouped section: small grey header, one card of rows, optional note under it. */
@Composable
private fun Group(title: String, footer: String? = null, content: @Composable () -> Unit) = Column {
    Text(title.uppercase(), Modifier.padding(start = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
    WhiteCard { Column { content() } }
    footer?.let {
        Text(it, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(vm: SettingsViewModel, s: GlobalSettings) = Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
    Text("Colour", fontWeight = FontWeight.SemiBold)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppTheme.entries.forEach { t ->
            val on = s.theme == t.ordinal
            val c = if (isDarkLook) t.dark else t.light
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c)
                    .border(3.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { vm.setTheme(t) }.semantics { contentDescription = t.label },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = Color.White) }
        }
    }
    Text("Mode", Modifier.padding(top = 16.dp, bottom = 6.dp), fontWeight = FontWeight.SemiBold)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        AppearanceMode.entries.forEachIndexed { i, m ->
            SegmentedButton(s.appearance == i, { vm.setAppearance(m) }, SegmentedButtonDefaults.itemShape(i, AppearanceMode.entries.size)) { Text(m.label) }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setBackground) }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Background photo", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (vm.background != null) TextButton(onClick = { vm.setBackground(null) }) { Text("Remove") }
        TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (vm.background == null) "Choose" else "Change", color = Accent)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuotesSection(vm: SettingsViewModel, s: GlobalSettings) = Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
    Text("Quotes", fontWeight = FontWeight.SemiBold)
    val ticked = Quotes.sources(s.quoteMask, s.quoteSource)
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Quotes.Source.entries.forEach { q ->
            FilterChip(q in ticked, { vm.toggleQuoteSource(q) }, label = { Text(q.label) },
                leadingIcon = if (q in ticked) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null)
        }
    }
}

/**
 * Adult content filter and tamper guard: the two things that stop the blocker from being sidestepped.
 * Their state lives in system settings, so [now] (ticking each second) makes them re-read it.
 */
@Composable
private fun ProtectionSection(vm: SettingsViewModel, filterOn: Boolean, filterApps: Set<String>, blockAds: Boolean, unlocked: Boolean, now: Long) {
    val ctx = LocalContext.current
    // Android asks once for VPN consent; the setting is saved only if it's given.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.setWebFilter(true)
    }
    val enableFilter = { VpnService.prepare(ctx)?.let(consent::launch) ?: vm.setWebFilter(true); Unit }
    val privateDns = WebFilterService.privateDnsServer(ctx)
    val broken = filterOn && privateDns == null && !WebFilterService.running
    ToggleRow(
        "Adult content filter",
        when {
            privateDns != null -> "Paused: Private DNS is on ($privateDns)"
            !filterOn -> "Blocks adult websites. Optional, turn off anytime"
            WebFilterService.running -> "On"
            else -> "Not connected. Tap to reconnect"
        },
        filterOn, { on -> if (on) enableFilter() else vm.setWebFilter(false) },
        alert = broken, onClick = if (broken) enableFilter else null,
    )
    if (filterOn) {
        var picking by remember { mutableStateOf(false) }
        val labels = remember(filterApps) { vm.launchableApps().filter { it.first in filterApps }.map { it.second } }
        RowDivider()
        ToggleRow("Also block ads", "Ads and trackers in apps and websites. Not YouTube or Instagram ads", blockAds, onChange = vm::setBlockAds)
        RowDivider()
        NavRow("Filtered apps", when {
            filterApps.isEmpty() -> "All apps"
            labels.size <= 2 -> labels.joinToString()
            else -> "${labels.take(2).joinToString()} +${labels.size - 2}"
        }) { picking = true }
        if (picking) FilterAppsSheet(vm, filterApps) { picking = false }
    }
    RowDivider()
    val protectedNow = Protection.active(ctx)
    val guardOn = GuardService.enabled(ctx)
    val paused = now < GuardService.pausedUntil
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Tamper guard", fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    protectedNow -> "On (device owner)"
                    !guardOn -> "Off: the app can be force-stopped"
                    paused -> "Paused for a few minutes"
                    else -> "On: blocks force-stop and uninstall"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!guardOn && !protectedNow) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!guardOn && !protectedNow) TextButton(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Turn on") }
    }
    if (unlocked && guardOn && !paused) TextButton(onClick = vm::pauseGuard, Modifier.padding(start = 8.dp)) { Text("Pause 5 min", color = Danger) }
    if (unlocked && protectedNow) TextButton(onClick = vm::removeProtection, Modifier.padding(start = 8.dp)) { Text("Turn off device owner", color = Danger) }
}


@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Picks the apps routed through the adult content filter; everything else connects as usual. Rows match Today's app list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterAppsSheet(vm: SettingsViewModel, current: Set<String>, onDismiss: () -> Unit) {
    val all by produceState<List<Pair<String, String>>?>(null) { value = withContext(Dispatchers.IO) { vm.launchableApps() } }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val limited = rows.map { it.app.packageName }.toSet()
    var picked by remember { mutableStateOf(current) }
    val toggle = { pkg: String -> picked = if (pkg in picked) picked - pkg else picked + pkg }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Filtered apps", style = MaterialTheme.typography.titleLarge)
            Text("Only the apps you tick go through the filter; the others connect as usual. None ticked: every app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val apps = all
        if (apps == null) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(
            Modifier.weight(1f, fill = false), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Your limited apps first, then everything else, each in one card like on Today.
            apps.partition { it.first in limited }.toList().zip(listOf("Your apps", "Other apps")).forEach { (list, title) ->
                if (list.isNotEmpty()) item(key = title) {
                    Column {
                        Text(title.uppercase(), Modifier.padding(start = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                        WhiteCard {
                            Column {
                                list.forEachIndexed { i, (pkg, label) ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(
                                        Modifier.fillMaxWidth().clickable { toggle(pkg) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppIcon(pkg, label, 38.dp)
                                        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Checkbox(pkg in picked, { toggle(pkg) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ picked = emptySet() }, Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("All apps") }
            Button({ vm.setFilterApps(picked); onDismiss() }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (picked.isEmpty()) "Save · all apps" else "Save · ${picked.size} app${if (picked.size == 1) "" else "s"}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One-line hint shown above locked controls. */
@Composable
internal fun LockedHint(onUnlock: () -> Unit) = Row(
    Modifier.fillMaxWidth().background(AccentSoft.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("🔒 Unlock to change these", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    TextButton(onClick = onUnlock) { Text("Unlock", fontWeight = FontWeight.Bold, color = Accent) }
}
