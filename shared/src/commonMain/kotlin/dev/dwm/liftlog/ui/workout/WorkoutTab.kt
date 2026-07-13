package dev.dwm.liftlog.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Exercise
import dev.dwm.liftlog.data.db.Workout
import dev.dwm.liftlog.data.db.WorkoutSet
import dev.dwm.liftlog.data.applyProgression
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.platesPerSide
import dev.dwm.liftlog.ui.collectAsStateList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun now(): Long = nowMillis()

@Composable
fun WorkoutTab(db: AppDatabase, modifier: Modifier = Modifier, refreshKey: Int = 0) {
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) { activeWorkout = db.workoutDao().activeWorkout() }

    val workout = activeWorkout
    if (workout == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = {
                scope.launch {
                    val w = Workout(name = "Workout", startedAt = now())
                    db.workoutDao().insertWorkout(w)
                    activeWorkout = w
                }
            }) { Text("Start Workout") }
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
    var showPicker by remember { mutableStateOf(false) }
    var restEndsAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(sets) {
        val ids = sets.map { it.exerciseId }.distinct()
        val ex = ids.mapNotNull { db.exerciseDao().byId(it) }.associateBy { it.id }
        exercises = ex
        previous = ids.associateWith { db.workoutDao().previousSets(it, workout.id) }
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
        restEndsAt?.let { endsAt ->
            RestTimerBar(endsAt) { restEndsAt = null }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val grouped = sets.groupBy { it.exerciseId }
            items(grouped.keys.toList(), key = { it }) { exerciseId ->
                ExerciseCard(
                    exercise = exercises[exerciseId],
                    sets = grouped[exerciseId].orEmpty(),
                    previous = previous[exerciseId].orEmpty(),
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
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Text("Add Exercise")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                scope.launch {
                    db.workoutDao().deleteWorkout(workout.id)
                    onFinished()
                }
            }) { Text("Discard") }
            Button(
                onClick = {
                    scope.launch {
                        db.workoutDao().updateWorkout(workout.copy(finishedAt = now(), updatedAt = now()))
                        applyProgression(db, workout)
                        onFinished()
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Finish Workout") }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise?,
    sets: List<WorkoutSet>,
    previous: List<WorkoutSet>,
    onUpdate: (WorkoutSet) -> Unit,
    onComplete: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onAddSet: (WorkoutSet) -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise?.name ?: "…", style = MaterialTheme.typography.titleMedium)
            sets.forEachIndexed { i, set ->
                SetRow(
                    index = i + 1,
                    set = set,
                    previousHint = previous.getOrNull(i)?.let { "${it.weightKg.clean()}kg × ${it.reps}" },
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
                TextButton(onClick = { onAddSet(last) }) { Text("+ Add set") }
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSet,
    previousHint: String?,
    onUpdate: (WorkoutSet) -> Unit,
    onComplete: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$index", Modifier.padding(end = 2.dp))
        NumField(
            value = if (set.weightKg == 0.0) "" else set.weightKg.clean(),
            label = "kg",
            placeholder = previousHint,
            modifier = Modifier.weight(1.2f),
        ) { onUpdate(set.copy(weightKg = it.toDoubleOrNull() ?: 0.0)) }
        NumField(
            value = if (set.reps == 0) "" else "${set.reps}",
            label = "reps",
            modifier = Modifier.weight(1f),
        ) { onUpdate(set.copy(reps = it.toIntOrNull() ?: 0)) }
        NumField(
            value = set.rpe?.clean() ?: "",
            label = "RPE",
            modifier = Modifier.weight(0.9f),
        ) { onUpdate(set.copy(rpe = it.toDoubleOrNull())) }
        IconButton(onClick = { onComplete(set) }, enabled = !set.completed) {
            Icon(
                Icons.Default.Check, "complete set",
                tint = if (set.completed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(set) }) {
            Icon(Icons.Default.Delete, "delete set", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        singleLine = true,
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
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Rest: ${(remaining / 1000).coerceAtLeast(0)}s", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { (remaining / 90_000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun Double.clean(): String = if (this % 1.0 == 0.0) "${toLong()}" else "$this"
