package ch.puc.blocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.puc.blocker.data.ChallengeType
import ch.puc.blocker.domain.WaitState

/** Routes between the unlock chooser, the quiz and the waiting screen. */
@Composable
fun UnlockFlow(vm: SettingsViewModel) {
    BackHandler(onBack = vm::closeUnlock)
    AnimatedContent(vm.unlockStep, label = "unlock") { step ->
        when (step) {
            UnlockStep.CHOOSE -> UnlockChooser(vm)
            UnlockStep.QUIZ -> ChallengeScreen(vm)
            UnlockStep.WAIT -> WaitScreen(vm)
            null -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun UnlockHeader(title: String, onClose: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun UnlockChooser(vm: SettingsViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cooldown = if (now > 0) vm.cooldownRemainingMs() / 1000 else 0
    val vocab = settings?.challengeType != ChallengeType.MATH

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UnlockHeader("Unlock settings", vm::closeUnlock)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(16.dp))
            Text("Take a breath first", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Changing your limits takes a little effort on purpose. That pause is what keeps them working. Pick one:",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
        }

        UnlockOption(
            emoji = if (vocab) "🇩🇪" else "🧮",
            title = if (vocab) "German quiz (A2–B1)" else "Math quiz",
            body = "${vm.questionCount} questions, get ${vm.passThreshold} right. Takes 2–3 minutes.",
            tag = "Fastest",
            enabled = cooldown == 0L,
            disabledNote = "Available again in ${cooldown}s",
            onClick = vm::startChallenge,
        )
        UnlockOption(
            emoji = "⏳",
            title = "Wait ${vm.waitMinutes} minutes",
            body = "No quiz. Start the timer and leave the app if you like. We'll send a notification when it's ready.",
            tag = "No quiz",
            enabled = true,
            onClick = vm::startWait,
        )
        Text(
            "Once unlocked, you can make changes for 5 minutes or until you leave the app.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UnlockOption(
    emoji: String, title: String, body: String, tag: String, enabled: Boolean,
    disabledNote: String = "", onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 28.sp) }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        tag,
                        Modifier.padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(if (enabled) body else disabledNote, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private val reflections = listOf(
    "Why do you want to change this limit right now?",
    "Will you be glad you changed it tomorrow morning?",
    "Is there something else you could do for the next 10 minutes?",
    "What were you about to open, and do you really need it?",
)

@Composable
private fun WaitScreen(vm: SettingsViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val state = if (now > 0) vm.waitState() else WaitState.None
    val totalMs = vm.waitMinutes * 60_000f
    val progress by animateFloatAsState(
        when (state) { is WaitState.Waiting -> 1f - state.remainingMs / totalMs; WaitState.Ready -> 1f; WaitState.None -> 0f },
        label = "wait",
    )
    val reflection = remember { reflections.random() }
    // The wait was cancelled elsewhere or expired unclaimed: go back to the options.
    LaunchedEffect(state == WaitState.None) { if (state == WaitState.None) vm.openUnlock() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth()) { UnlockHeader("Waiting to unlock", vm::closeUnlock) }
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress }, Modifier.size(240.dp), strokeWidth = 14.dp, strokeCap = StrokeCap.Round,
                color = if (state == WaitState.Ready) Green else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    is WaitState.Waiting -> {
                        Text(countdown(state.remainingMs), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Text("remaining", style = MaterialTheme.typography.bodySmall)
                    }
                    WaitState.Ready -> {
                        Text("✓", fontSize = 56.sp, color = Green, fontWeight = FontWeight.Bold)
                        Text("Ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    WaitState.None -> {}
                }
            }
        }

        if (state == WaitState.Ready) {
            Text("You waited it out. Unlock now to make your changes.", textAlign = TextAlign.Center)
            Button(onClick = vm::claimWait, Modifier.fillMaxWidth().height(56.dp)) { Text("Unlock now", fontSize = 16.sp) }
            Text("This stays ready for 15 minutes.", style = MaterialTheme.typography.bodySmall)
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("While you wait…", fontWeight = FontWeight.Bold)
                    Text(reflection, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text(
                "You can leave the app. The timer keeps running and you'll get a notification when it's done.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
            val cooldown = vm.cooldownRemainingMs()
            OutlinedButton(onClick = vm::startChallenge, enabled = cooldown == 0L, modifier = Modifier.fillMaxWidth()) {
                Text(if (cooldown == 0L) "Solve the quiz instead" else "Quiz available in ${cooldown / 1000}s")
            }
            TextButton(onClick = vm::cancelWait) { Text("Cancel waiting") }
        }
    }
}
