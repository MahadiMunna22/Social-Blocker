package ch.puc.blocker.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.puc.blocker.data.TrackedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- App icons ----------

private val iconCache = LruCache<String, ImageBitmap>(300)

private fun loadIcon(ctx: Context, pkg: String): ImageBitmap? {
    val pm = ctx.packageManager
    val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        ?: pm.getLaunchIntentForPackage(pkg)?.let { runCatching { pm.getActivityIcon(it) }.getOrNull() }
    return drawable?.let { runCatching { it.toBitmap(144, 144).asImageBitmap() }.getOrNull() }
}

/** App icon loaded off the main thread and cached; falls back to a coloured letter. */
@Composable
fun AppIcon(packageName: String, label: String = packageName, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { loadIcon(ctx, packageName)?.also { iconCache.put(packageName, it) } }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, null, Modifier.size(size))
    } else {
        val hue = (packageName.hashCode() and 0xFFFF) % 360
        Box(Modifier.size(size).background(Color.hsl(hue.toFloat(), 0.45f, 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
        }
    }
}

// ---------- Timer editing ----------

@Composable
internal fun ValueSlider(label: String, value: Int, range: IntRange, step: Int, unit: String, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, Modifier.weight(1f))
            Text("$value $unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = enabled && value > range.first) {
                Text("−", fontSize = 20.sp)
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange((Math.round(it / step) * step).coerceIn(range)) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / step - 1,
                enabled = enabled,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = enabled && value < range.last) {
                Text("+", fontSize = 20.sp)
            }
        }
    }
}

/** Presets plus fine-grained controls for one app's three timers. */
@Composable
private fun LimitsEditor(limits: Limits, appName: String, enabled: Boolean = true, onChange: (Limits) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Limits.presets.forEach { (name, preset) ->
            FilterChip(limits == preset, { onChange(preset) }, label = { Text(name) }, enabled = enabled)
        }
    }
    Spacer(Modifier.height(8.dp))
    ValueSlider("⏱ Session timer", limits.session, 1..60, 1, "min", enabled) { onChange(limits.copy(session = it)) }
    ValueSlider("☕ Break after session", limits.cooldown, 5..120, 5, "min", enabled) { onChange(limits.copy(cooldown = it)) }
    ValueSlider("📅 Daily limit", limits.cap, 5..240, 5, "min", enabled) { onChange(limits.copy(cap = it)) }
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "A timer counts down while you use $appName. After ${limits.session} min it takes a ${limits.cooldown} min break, " +
                "and after ${limits.cap} min in total it's blocked until tomorrow.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------- Apps tab ----------

@Composable
internal fun AddMoreCard(first: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AccentSoft.copy(alpha = if (isSystemInDarkTheme()) 0.12f else 1f), RoundedCornerShape(24.dp))
            .clickable(onClick = onAdd).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (first) "Add your first app" else "Add more apps", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("Select apps to help you stay focused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(56.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, "Add apps", tint = Color.White)
        }
    }
}

@Composable
private fun RuleTag(text: String) = Text(
    text,
    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    style = MaterialTheme.typography.labelSmall,
)

/** Everything about one app: limit on/off, timers, remove. Loosening anything needs the unlock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(app: TrackedApp, vm: SettingsViewModel, onDismiss: () -> Unit) {
    val now by vm.now.collectAsStateWithLifecycle()
    val unlocked = vm.isUnlocked(now)
    var limits by remember { mutableStateOf(app.limits()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val unlock = { onDismiss(); vm.openUnlock() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                AppIcon(app.packageName, app.label, 56.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(if (app.enabled) "Limits on" else "Not limited", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Turning on is instant; turning off asks for the unlock.
                Switch(app.enabled, { on -> if (on || unlocked) vm.setAppEnabled(app, on) else unlock() })
            }
            if (!unlocked) LockedHint(unlock)
            Spacer(Modifier.height(8.dp))
            LimitsEditor(limits, app.label, enabled = unlocked) { limits = it }
            if (unlocked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirmRemove = true }) { Text("Remove app", color = Danger) }
                Spacer(Modifier.weight(1f))
                PillButton("Save", enabled = limits != app.limits()) {
                    vm.updateApp(app, app.copy(sessionLimitMin = limits.session, cooldownMin = limits.cooldown, dailyCapMin = limits.cap))
                    onDismiss()
                }
            }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        icon = { AppIcon(app.packageName, app.label) },
        title = { Text("Stop limiting ${app.label}?") },
        text = { Text("Its timers will no longer apply.") },
        confirmButton = { TextButton(onClick = { vm.removeApp(app); confirmRemove = false; onDismiss() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}

// ---------- Add-apps picker (2 steps: choose, then set timers) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerSheet(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val all by produceState<List<Candidate>?>(null) { value = withContext(Dispatchers.IO) { vm.candidates() } }
    val picked = remember { mutableStateListOf<Candidate>() }
    var step by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var limits by remember { mutableStateOf(Limits.presets[1].second) }
    val toggle = { c: Candidate -> if (c in picked) picked.remove(c) else picked.add(c); Unit }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AnimatedContent(step, label = "pickerStep") { s ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (s == 0) {
                    Text("Choose apps to limit", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    )
                    val list = all
                    if (list == null) {
                        Text("Loading apps…", Modifier.padding(24.dp))
                    } else {
                        val shown = list.filter { query.isBlank() || it.label.contains(query, true) }
                        val suggested = shown.filter { it.suggested }
                        LazyVerticalGrid(
                            GridCells.Adaptive(80.dp), Modifier.fillMaxWidth().height(440.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            if (suggested.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Popular social apps") }
                                items(suggested, key = { "s" + it.packageName }) { AppTile(it, it in picked, toggle) }
                                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("All apps") }
                            }
                            items(shown.filterNot { it.suggested }, key = { it.packageName }) { AppTile(it, it in picked, toggle) }
                        }
                    }
                    Button(
                        onClick = { step = 1 }, enabled = picked.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
                    ) { Text(if (picked.isEmpty()) "Tap apps to select them" else "Next: set timers (${picked.size})") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Text("Set timers", style = MaterialTheme.typography.titleLarge)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        items(picked, key = { it.packageName }) { AppIcon(it.packageName, it.label, 44.dp) }
                    }
                    Text(
                        "These timers apply to all ${picked.size} app(s). You can fine-tune each one later.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LimitsEditor(limits, if (picked.size == 1) picked[0].label else "these apps") { limits = it }
                    Button(
                        onClick = { vm.addApps(picked.toList(), limits); onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
                    ) { Text("Add ${picked.size} app(s)") }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun AppTile(c: Candidate, selected: Boolean, toggle: (Candidate) -> Unit) {
    Column(
        Modifier.padding(4.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { toggle(c) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(c.packageName, c.label, 52.dp)
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        Text(
            c.label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
