package dev.dwm.liftlog.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.applyProgression
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Exercise
import dev.dwm.liftlog.data.db.Workout
import dev.dwm.liftlog.data.db.WorkoutSet
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.e1rm
import dev.dwm.liftlog.domain.platesPerSide
import dev.dwm.liftlog.ui.Palette
import dev.dwm.liftlog.ui.collectAsStateList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun now(): Long = nowMillis()

fun formatDuration(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}:${(s % 60).toString().padStart(2, '0')}"
}

data class PrRecord(val exerciseName: String, val weightKg: Double, val reps: Int)

@Composable
fun WorkoutTab(db: AppDatabase, modifier: Modifier = Modifier, refreshKey: Int = 0) {
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) { activeWorkout = db.workoutDao().activeWorkout() }

    val workout = activeWorkout
    if (workout == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    scope.launch {
                        val w = Workout(name = "Workout", startedAt = now())
                        db.workoutDao().insertWorkout(w)
                        activeWorkout = w
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Success, contentColor = Color.White),
            ) { Text("Start Empty Workout") }
        }
    } else {
        ActiveWorkoutScreen(db, workout, modifier, onFinished = { activeWorkout = null })
    }
}

@Composable
fun ActiveWorkoutScreen(
    db: AppDatabase,
    workout: Workout,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sets by remember { db.workoutDao().setsForWorkout(workout.id) }
        .collectAsStateList()
    var exercises by remember { mutableStateOf<Map<String, Exercise>>(emptyMap()) }
    var previous by remember { mutableStateOf<Map<String, List<WorkoutSet>>>(emptyMap()) }
    var bestBefore by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var showPicker by remember { mutableStateOf(false) }
    var restEndsAt by remember { mutableStateOf<Long?>(null) }
    var summary by remember { mutableStateOf<WorkoutSummary?>(null) }

    LaunchedEffect(sets) {
        val ids = sets.map { it.exerciseId }.distinct()
        exercises = ids.mapNotNull { db.exerciseDao().byId(it) }.associateBy { it.id }
        previous = ids.associateWith { db.workoutDao().previousSets(it, workout.id) }
        bestBefore = ids.associateWith { db.workoutDao().bestE1rmBefore(it, workout.startedAt) ?: 0.0 }
    }

    summary?.let { s ->
        WorkoutCompleteDialog(s) {
            summary = null
            onFinished()
        }
    }

    if (showPicker) {
        ExercisePickerDialog(db, onDismiss = { showPicker = false }) { exercise ->
            scope.launch {
                db.workoutDao().insertSet(
                    WorkoutSet(workoutId = workout.id, exerciseId = exercise.id, setIndex = 0, weightKg = 0.0, reps = 0)
                )
            }
            showPicker = false
        }
    }

    Column(modifier.fillMaxSize()) {
        WorkoutHeader(
            workout = workout,
            onFinish = {
                scope.launch {
                    val end = now()
                    db.workoutDao().updateWorkout(workout.copy(finishedAt = end, updatedAt = end))
                    applyProgression(db, workout)
                    val done = sets.filter { it.completed }
                    val prs = done
                        .filter { it.weightKg > 0 && e1rm(it.weightKg, it.reps) > (bestBefore[it.exerciseId] ?: 0.0) }
                        .groupBy { it.exerciseId }
                        .mapNotNull { (id, exSets) ->
                            val best = exSets.maxByOrNull { e1rm(it.weightKg, it.reps) } ?: return@mapNotNull null
                            PrRecord(exercises[id]?.name ?: "?", best.weightKg, best.reps)
                        }
                    summary = WorkoutSummary(
                        durationMillis = end - workout.startedAt,
                        volumeKg = done.sumOf { it.weightKg * it.reps },
                        setCount = done.size,
                        prs = prs,
                    )
                }
            },
            onDiscard = {
                scope.launch {
                    db.workoutDao().deleteWorkout(workout.id)
                    onFinished()
                }
            },
        )
        restEndsAt?.let { endsAt -> RestTimerBar(endsAt) { restEndsAt = null } }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val grouped = sets.groupBy { it.exerciseId }
            items(grouped.keys.toList(), key = { it }) { exerciseId ->
                ExerciseCard(
                    exercise = exercises[exerciseId],
                    sets = grouped[exerciseId].orEmpty(),
                    previous = previous[exerciseId].orEmpty(),
                    bestE1rm = bestBefore[exerciseId] ?: 0.0,
                    onUpdate = { scope.launch { db.workoutDao().updateSet(it.copy(updatedAt = now())) } },
                    onComplete = { set ->
                        scope.launch { db.workoutDao().updateSet(set.copy(completed = true, updatedAt = now())) }
                        restEndsAt = now() + 90_000
                    },
                    onDelete = { scope.launch { db.workoutDao().deleteSet(it.id) } },
                    onAddSet = { last ->
                        scope.launch {
                            db.workoutDao().insertSet(
                                WorkoutSet(
                                    workoutId = workout.id,
                                    exerciseId = exerciseId,
                                    setIndex = last.setIndex + 1,
                                    weightKg = last.weightKg,
                                    reps = last.reps,
                                )
                            )
                        }
                    },
                )
            }
            item {
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("Add Exercises")
                }
            }
        }
    }
}

@Composable
private fun WorkoutHeader(workout: Workout, onFinish: () -> Unit, onDiscard: () -> Unit) {
    var elapsed by remember { mutableStateOf(now() - workout.startedAt) }
    LaunchedEffect(workout.id) {
        while (true) {
            elapsed = now() - workout.startedAt
            delay(1000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Timer, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatDuration(elapsed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Success, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Finish", fontWeight = FontWeight.Bold) }
    }
}

data class WorkoutSummary(
    val durationMillis: Long,
    val volumeKg: Double,
    val setCount: Int,
    val prs: List<PrRecord>,
)

@Composable
private fun WorkoutCompleteDialog(s: WorkoutSummary, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Success, contentColor = Color.White),
            ) { Text("Done") }
        },
        title = { Text("Workout Complete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock(formatDuration(s.durationMillis), "duration")
                    StatBlock("${s.volumeKg.toInt()} kg", "volume")
                    StatBlock("${s.setCount}", "sets")
                }
                if (s.prs.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, tint = Palette.Pr)
                        Text("${s.prs.size} PR${if (s.prs.size > 1) "s" else ""}!", fontWeight = FontWeight.Bold)
                    }
                    s.prs.forEach {
                        Text(
                            "${it.exerciseName} — ${it.weightKg.clean()}kg × ${it.reps} (new e1RM record)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise?,
    sets: List<WorkoutSet>,
    previous: List<WorkoutSet>,
    bestE1rm: Double,
    onUpdate: (WorkoutSet) -> Unit,
    onComplete: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onAddSet: (WorkoutSet) -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                exercise?.name ?: "…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Set", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Previous", Modifier.weight(1.1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text("kg", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text("Reps", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text("RPE", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Box(Modifier.size(36.dp))
                Box(Modifier.size(36.dp))
            }
            sets.forEachIndexed { i, set ->
                SetRow(
                    index = i + 1,
                    set = set,
                    previous = previous.getOrNull(i),
                    isPr = set.completed && set.weightKg > 0 && e1rm(set.weightKg, set.reps) > bestE1rm,
                    onUpdate = onUpdate,
                    onComplete = onComplete,
                    onDelete = onDelete,
                )
            }
            sets.lastOrNull()?.let { last ->
                val plates = platesPerSide(last.weightKg)
                if (!plates.isNullOrEmpty()) {
                    Text(
                        "Per side: ${plates.joinToString(" + ") { it.clean() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { onAddSet(last) }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Add Set")
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSet,
    previous: WorkoutSet?,
    isPr: Boolean,
    onUpdate: (WorkoutSet) -> Unit,
    onComplete: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.weight(0.5f).size(28.dp)
                .background(
                    if (set.completed) Palette.Success.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isPr) Icon(Icons.Default.EmojiEvents, "PR", Modifier.size(16.dp), tint = Palette.Pr)
            else Text("$index", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            previous?.let { "${it.weightKg.clean()}×${it.reps}" } ?: "—",
            Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        NumField(
            value = if (set.weightKg == 0.0) "" else set.weightKg.clean(),
            placeholder = previous?.weightKg?.clean(),
            modifier = Modifier.weight(1f),
        ) { onUpdate(set.copy(weightKg = it.toDoubleOrNull() ?: 0.0)) }
        NumField(
            value = if (set.reps == 0) "" else "${set.reps}",
            placeholder = previous?.reps?.toString(),
            modifier = Modifier.weight(1f),
        ) { onUpdate(set.copy(reps = it.toIntOrNull() ?: 0)) }
        NumField(
            value = set.rpe?.clean() ?: "",
            modifier = Modifier.weight(0.8f),
        ) { onUpdate(set.copy(rpe = it.toDoubleOrNull())) }
        IconButton(
            onClick = { onComplete(set) },
            enabled = !set.completed,
            modifier = Modifier.size(36.dp)
                .background(
                    if (set.completed) Palette.Success else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.Default.Check, "complete set",
                tint = if (set.completed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(set) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, "delete set", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumField(
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        modifier = modifier,
    )
}

@Composable
private fun RestTimerBar(endsAt: Long, onDone: () -> Unit) {
    var remaining by remember(endsAt) { mutableStateOf(endsAt - now()) }
    LaunchedEffect(endsAt) {
        while (remaining > 0) {
            delay(250)
            remaining = endsAt - now()
        }
        onDone()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Rest ${(remaining / 1000).coerceAtLeast(0)}s", style = MaterialTheme.typography.titleSmall, color = Palette.Calories)
            TextButton(onClick = onDone) { Text("Skip") }
        }
        LinearProgressIndicator(
            progress = { (remaining / 90_000f).coerceIn(0f, 1f) },
            color = Palette.Calories,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun Double.clean(): String = if (this % 1.0 == 0.0) "${toLong()}" else "$this"
