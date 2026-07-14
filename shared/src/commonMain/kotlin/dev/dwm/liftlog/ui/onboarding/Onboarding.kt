package dev.dwm.liftlog.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.aiClient
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Setting
import dev.dwm.liftlog.data.db.WeightEntry
import dev.dwm.liftlog.domain.lbToKg
import dev.dwm.liftlog.ui.Palette
import dev.dwm.liftlog.ui.components.FullScreenDialog
import dev.dwm.liftlog.ui.nutrition.todayEpochDay
import kotlinx.coroutines.launch

/** First-launch wizard: welcome → targets → connect AI → done. Every step skippable. */
@Composable
fun OnboardingWizard(db: AppDatabase, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    fun finish() {
        scope.launch { db.settingDao().put(Setting("onboarded", "1")) }
        onDone()
    }
    FullScreenDialog(
        title = "",
        onDismiss = { finish() },
        actionLabel = "Skip all",
        onAction = { finish() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(24.dp))
            when (step) {
                0 -> {
                    Text("OVERLOAD", style = MaterialTheme.typography.displayMedium, color = Palette.Success)
                    Text(
                        "Lift heavier. Eat smarter. One app, zero cloud accounts.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
                }
                1 -> TargetsStep(db) { step = 2 }
                2 -> {
                    Text("Connect AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    AiSetupSheet(db)
                    Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                }
                3 -> {
                    Text("You're set 🏋️", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Snap your first meal with 📷 on the Food tab, or hit START on the Workout tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Let's go") }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(8.dp).background(
                            if (i == step) Palette.Success else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetsStep(db: AppDatabase, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var weight by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("0") }
    var kcal by remember { mutableStateOf("2000") }
    Text("Your targets", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    OutlinedTextField(weight, { weight = it }, label = { Text("Bodyweight (lb)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(goal, { goal = it }, label = { Text("Goal lb/week (− cut, + bulk)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(kcal, { kcal = it }, label = { Text("Starting calorie target") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(
        "Overload learns your real expenditure from weigh-ins + food logs — after ~7 days the target adjusts itself.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = {
        scope.launch {
            weight.toDoubleOrNull()?.let { db.weightDao().upsert(WeightEntry(epochDay = todayEpochDay(), kg = it.lbToKg())) }
            goal.toDoubleOrNull()?.let { db.settingDao().put(Setting("goalKgPerWeek", "${it.lbToKg()}")) }
            kcal.toDoubleOrNull()?.let { db.settingDao().put(Setting("targetKcal", "$it")) }
        }
        onNext()
    }, modifier = Modifier.fillMaxWidth()) { Text("Save & continue") }
}

/** Guided AI-key setup with live test; reused by wizard, Food-tab card, and camera-without-key. */
@Composable
fun AiSetupSheet(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { key = db.settingDao().get("aiApiKey").orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Photo calorie counting needs one free API key:", fontWeight = FontWeight.Bold)
        Text("1.  Open  openrouter.ai/keys  in any browser", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("2.  Sign up (free) and tap “Create Key”", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("3.  Copy the key and paste it below", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            key, { key = it },
            label = { Text("OpenRouter API key (sk-or-…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    busy = true; status = ""; ok = false
                    db.settingDao().put(Setting("aiApiKey", key.trim()))
                    val result = aiClient(db).mapCatching { it.chat("Reply with exactly: OK") }
                    result.fold(
                        onSuccess = { ok = true; status = "✓ Connected — photo logging ready" },
                        onFailure = { status = it.message ?: "Connection failed" },
                    )
                    busy = false
                }
            }, enabled = key.isNotBlank() && !busy) { Text("Save & Test") }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp))
        }
        if (status.isNotBlank()) {
            Text(status, color = if (ok) Palette.Success else MaterialTheme.colorScheme.error)
        }
    }
}

/** Food-tab entry point: the setup sheet in a dismissable full-screen dialog. */
@Composable
fun AiSetupDialog(db: AppDatabase, onDismiss: () -> Unit) {
    FullScreenDialog(title = "Connect AI", onDismiss = onDismiss, actionLabel = "Done", onAction = onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            AiSetupSheet(db)
        }
    }
}
