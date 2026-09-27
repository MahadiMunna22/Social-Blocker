package ch.puc.blocker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.puc.blocker.domain.ChallengeOutcome

private val Right = Color(0xFF2E7D32)
private val Wrong = Color(0xFFC62828)

@Composable
fun ChallengeScreen(vm: SettingsViewModel) {
    val q = vm.quiz ?: return
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeUnlock) { Icon(Icons.Default.Close, "Close") }
            Text("Unlock challenge", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        val outcome = q.outcome
        if (outcome != null) ResultView(vm, outcome) else QuestionView(vm, q)
    }
}

@Composable
private fun QuestionView(vm: SettingsViewModel, q: QuizState) {
    val progress by animateFloatAsState((q.index + if (q.answers[q.index] != null) 1 else 0) / q.questions.size.toFloat(), label = "progress")
    Text("Question ${q.index + 1} of ${q.questions.size} · get ${vm.passThreshold} right to unlock", style = MaterialTheme.typography.bodySmall)
    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(vertical = 12.dp))

    AnimatedContent(
        targetState = q.index,
        transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
        label = "question",
    ) { index ->
        val question = q.questions[index]
        val chosen = q.answers[index]
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    question.label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Accent, fontWeight = FontWeight.Bold) }
                    Text(
                        question.prompt, Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            question.options.forEach { opt ->
                // Once answered, reveal the right option in green and a wrong pick in red.
                val color = when {
                    chosen == null -> null
                    opt == question.answer -> Right
                    opt == chosen -> Wrong
                    else -> null
                }
                OutlinedCard(
                    onClick = { vm.answer(opt) },
                    enabled = chosen == null,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(2.dp, color ?: MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = color?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surface,
                        disabledContainerColor = color?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(opt, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (color == Right) Icon(Icons.Default.Check, null, tint = Right)
                        if (color == Wrong) Icon(Icons.Default.Close, null, tint = Wrong)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::next, enabled = chosen != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (index == q.questions.lastIndex) "See result" else "Next")
            }
        }
    }
}

@Composable
private fun ResultView(vm: SettingsViewModel, outcome: ChallengeOutcome) {
    val now by vm.now.collectAsStateWithLifecycle()
    val wait = if (now > 0) vm.cooldownRemainingMs() / 1000 else 0 // re-read every tick
    val frac by animateFloatAsState(outcome.score / outcome.total.toFloat(), label = "score")
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { frac }, Modifier.size(160.dp), strokeWidth = 12.dp,
                color = if (outcome.passed) Right else Wrong,
            )
            Text("${outcome.score}/${outcome.total}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        Text(if (outcome.passed) "Unlocked!" else "Not quite", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (outcome.passed) "You can change your rules for the next 5 minutes."
            else "You need ${vm.passThreshold} correct answers. Take a moment. Maybe you don't need that change after all.",
            textAlign = TextAlign.Center,
        )
        if (outcome.corrections.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Correct answers", fontWeight = FontWeight.Bold)
                    outcome.corrections.forEach {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            if (it.prompt != it.solution && it.id?.startsWith("g:") == true)
                                Text(it.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("✓ ${it.solution}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("These will come back in a later quiz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (outcome.passed) {
            Button(onClick = vm::closeUnlock, Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
        } else {
            Button(onClick = vm::startChallenge, enabled = wait == 0L, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (wait > 0) "Try again in ${wait}s" else "Try again with new questions")
            }
            OutlinedButton(onClick = vm::startWait, Modifier.fillMaxWidth()) { Text("Wait ${vm.waitMinutes} minutes instead") }
            TextButton(onClick = vm::closeUnlock, Modifier.fillMaxWidth()) { Text("Not now") }
        }
    }
}
