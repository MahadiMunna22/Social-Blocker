package ch.puc.blocker.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private class Step(val title: String, val why: String, val granted: Boolean, val required: Boolean, val action: () -> Unit)

@Composable
fun SetupScreen(permTick: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var notifTick by remember { mutableStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifTick++ }

    val steps = remember(permTick, notifTick) {
        listOf(
            Step("Usage access", "Lets the app see which app is on screen. This is how it measures your time.",
                Perms.usageAccess(ctx), true) { Perms.openUsageAccess(ctx) },
            Step("Display over other apps", "Needed to cover a blocked app with the break screen.",
                Perms.overlay(ctx), true) { Perms.openOverlay(ctx) },
            Step("Notifications", "Shows a quiet status notification with your remaining time.",
                Perms.notifications(ctx), false) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            Step("Unrestricted battery", "Stops Android from killing the blocker in the background.",
                Perms.batteryExempt(ctx), false) { Perms.openBattery(ctx) },
        )
    }
    val done = steps.count { it.granted }
    val current = steps.indexOfFirst { !it.granted }
    val requiredDone = steps.filter { it.required }.all { it.granted }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLogo(64.dp)
        Text("Let's set things up", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("$done of ${steps.size} done", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { done / steps.size.toFloat() }, Modifier.fillMaxWidth())

        steps.forEachIndexed { i, step -> StepCard(i + 1, step, highlighted = i == current) }

        RestrictedHelp(ctx)

        Button(onClick = onDone, enabled = requiredDone, modifier = Modifier.fillMaxWidth()) {
            Text(if (requiredDone) "Continue" else "Grant the required steps to continue")
        }
        if (!requiredDone) TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip for now (blocking won't work)")
        }
    }
}

@Composable
private fun StepCard(number: Int, step: Step, highlighted: Boolean) {
    val colors = if (highlighted) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().animateContentSize(), colors = colors) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(
                    if (step.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (step.granted) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                else Text("$number", fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(step.title + if (step.required) "" else "  (recommended)", fontWeight = FontWeight.Bold)
                Text(step.why, style = MaterialTheme.typography.bodySmall)
            }
            if (!step.granted) {
                if (highlighted) Button(onClick = step.action) { Text("Grant") }
                else OutlinedButton(onClick = step.action) { Text("Grant") }
            }
        }
    }
}

@Composable
private fun RestrictedHelp(ctx: Context) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null)
                Text("\"App was denied access\" or toggle greyed out?", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
                TextButton(onClick = { open = !open }) {
                    Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                }
            }
            if (open) {
                Text(
                    "Android blocks these permissions for apps installed from an APK file. To unlock them:\n\n" +
                        "1. Tap Grant above and try the toggle once. You'll see \"App was denied access\" or \"Restricted setting\". Close it.\n" +
                        "2. Open App info (button below).\n" +
                        "3. Tap ⋮ in the top-right corner, then \"Allow restricted settings\", and confirm with your PIN.\n" +
                        "4. Come back here and tap Grant again.\n\n" +
                        "Xiaomi/HyperOS: also enable \"Display pop-up windows while running in the background\" in App info → Other permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = { Perms.openAppInfo(ctx) }, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Open App info")
                }
                Text("Still stuck? With USB debugging on, run these from a computer:", style = MaterialTheme.typography.bodySmall)
                val cmds = Perms.adbCommands(ctx)
                Text(
                    cmds,
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", cmds))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy commands") }
            }
        }
    }
}
