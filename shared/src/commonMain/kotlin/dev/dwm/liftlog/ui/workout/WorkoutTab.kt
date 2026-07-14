package dev.dwm.liftlog.ui.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.AiClient
import dev.dwm.liftlog.data.applyProgression
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Exercise
import dev.dwm.liftlog.data.db.Program
import dev.dwm.liftlog.data.db.ProgramDay
import dev.dwm.liftlog.data.db.Routine
import dev.dwm.liftlog.data.db.RoutineExercise
import dev.dwm.liftlog.data.db.Workout
import dev.dwm.liftlog.data.db.WorkoutSet
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.data.httpClient
import dev.dwm.liftlog.data.installTemplate
import dev.dwm.liftlog.data.startProgramWorkout
import dev.dwm.liftlog.data.startRoutineWorkout
import dev.dwm.liftlog.data.templates
import dev.dwm.liftlog.domain.e1rm
import dev.dwm.liftlog.domain.kgToLb
import dev.dwm.liftlog.domain.kgToLbDisplay
import dev.dwm.liftlog.domain.lbToKg
import dev.dwm.liftlog.domain.platesPerSide
import dev.dwm.liftlog.ui.Haptic
import dev.dwm.liftlog.ui.Palette
import dev.dwm.liftlog.ui.collectAsStateList
import dev.dwm.liftlog.ui.haptic
import dev.dwm.liftlog.ui.playBeep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun now(): Long = nowMillis()

fun formatDuration(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}:${(s % 60).toString().padStart(2, '0')}"
}

data class PrRecord(val exerciseName: String, val weightKg: Double, val reps: Int)

enum class SetField { WEIGHT, REPS, RPE }

private fun fieldText(s: WorkoutSet, f: SetField): String = when (f) {
    SetField.WEIGHT -> if (s.weightKg == 0.0) "" else s.weightKg.kgToLbDisplay().clean()
    SetField.REPS -> if (s.reps == 0) "" else "${s.reps}"
    SetField.RPE -> s.rpe?.clean() ?: ""
}

private fun applyText(s: WorkoutSet, f: SetField, t: String): WorkoutSet = when (f) {
    SetField.WEIGHT -> s.copy(weightKg = t.toDoubleOrNull()?.lbToKg() ?: 0.0)
    SetField.REPS -> s.copy(reps = t.toIntOrNull() ?: 0)
    SetField.RPE -> s.copy(rpe = t.toDoubleOrNull())
}

@Composable
fun WorkoutTab(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    voiceInput: (suspend () -> String?)? = null,
) {
    var activeWorkout by remember { mutableStateOf<Workout?>(null) }

    LaunchedEffect(refreshKey) { activeWorkout = db.workoutDao().activeWorkout() }

    val workout = activeWorkout
    if (workout == null) {
        StartScreen(db, modifier) { activeWorkout = it }
    } else {
        ActiveWorkoutScreen(db, workout, modifier, voiceInput, onFinished = { activeWorkout = null })
    }
}

// ---------- Boostcamp-style start screen: program hero / routine day cards ----------

@Composable
private fun StartScreen(db: AppDatabase, modifier: Modifier, onStarted: (Workout) -> Unit) {
    val scope = rememberCoroutineScope()
    val routines by remember { db.routineDao().routines() }.collectAsStateList()
    var editorFor by remember { mutableStateOf<Routine?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    if (showEditor) {
        RoutineEditorDialog(db, editorFor) { showEditor = false; editorFor = null }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Train", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    scope.launch {
                        val w = Workout(name = "Workout", startedAt = now())
                        db.workoutDao().insertWorkout(w)
                        onStarted(w)
                    }
                }) { Text("Quick Start", color = Palette.Boost, fontWeight = FontWeight.Bold) }
            }
        }
        item { ProgramsSection(db, onStarted) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("My Routines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { editorFor = null; showEditor = true }) {
                    Text("+ New", color = Palette.Boost, fontWeight = FontWeight.Bold)
                }
            }
            if (routines.isEmpty()) {
                Text(
                    "Build your plan: create a routine with your exercises and set counts. " +
                        "Starting a routine pre-fills your previous weights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(routines, key = { it.id }) { routine ->
            RoutineCard(
                db, routine,
                index = routines.indexOf(routine) + 1,
                onStart = { scope.launch { onStarted(startRoutineWorkout(db, routine)) } },
                onEdit = { editorFor = routine; showEditor = true },
                onDelete = { scope.launch { db.routineDao().delete(routine.id) } },
            )
        }
    }
}

@Composable
private fun RoutineCard(
    db: AppDatabase,
    routine: Routine,
    index: Int,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var preview by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(0) }
    LaunchedEffect(routine.id, routine.updatedAt) {
        val names = db.routineDao().exercisesFor(routine.id)
            .mapNotNull { db.exerciseDao().byId(it.exerciseId)?.name }
        count = names.size
        preview = names.joinToString(" · ")
    }
    // Boostcamp day card: numbered coral badge, name + exercise preview, Start pill
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(Palette.Boost.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$index", color = Palette.Boost, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (preview.isBlank()) "Tap to add exercises" else "$count exercises · $preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "delete routine", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.background(Palette.Boost, RoundedCornerShape(20.dp))
                    .clickable(onClick = onStart)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Start", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

internal data class EditorRow(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val restSeconds: Int? = null,
    val linked: Boolean = false, // superset with previous row
)

private val restChoices = listOf(null, 60, 90, 120, 180)

/** Consecutive linked rows share a superset group id (chain-start index); solo rows get null. */
internal fun supersetGroups(rows: List<EditorRow>): List<Int?> {
    val out = MutableList<Int?>(rows.size) { null }
    var start = 0
    for (i in rows.indices) {
        if (i > 0 && rows[i].linked) {
            out[start] = start
            out[i] = start
        } else {
            start = i
        }
    }
    return out
}

@Composable
private fun RoutineEditorDialog(db: AppDatabase, existing: Routine?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var rows by remember { mutableStateOf<List<EditorRow>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.id) {
        if (existing != null) {
            val res = db.routineDao().exercisesFor(existing.id)
            rows = res.mapIndexedNotNull { i, re ->
                db.exerciseDao().byId(re.exerciseId)?.let {
                    EditorRow(
                        re.exerciseId, it.name, re.sets, re.restSeconds,
                        linked = re.supersetGroup != null && re.supersetGroup == res.getOrNull(i - 1)?.supersetGroup,
                    )
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(db, onDismiss = { showPicker = false }) { exercise ->
            rows = rows + EditorRow(exercise.id, exercise.name, 3)
            showPicker = false
        }
    }

    dev.dwm.liftlog.ui.components.FullScreenDialog(
        title = if (existing == null) "New Routine" else "Edit Routine",
        onDismiss = onClose,
        actionLabel = "Save",
        actionEnabled = name.isNotBlank() && rows.isNotEmpty(),
        onAction = {
            scope.launch {
                val routine = existing?.copy(name = name.trim(), updatedAt = nowMillis())
                    ?: Routine(name = name.trim())
                db.routineDao().upsert(routine)
                // replace exercise list wholesale — simplest correct sync story
                db.routineDao().deleteExercisesFor(routine.id)
                val groups = supersetGroups(rows)
                rows.forEachIndexed { i, r ->
                    db.routineDao().upsertExercise(
                        RoutineExercise(
                            routineId = routine.id, exerciseId = r.exerciseId, position = i,
                            sets = r.sets, restSeconds = r.restSeconds, supersetGroup = groups[i],
                        )
                    )
                }
                onClose()
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine name (e.g. Push Day)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            rows.forEachIndexed { i, row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (row.linked) {
                                Text("⛓ ", color = Palette.Volt)
                            }
                            Text(row.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                if (i > 0) rows = rows.toMutableList().also {
                                    val t = it[i - 1]; it[i - 1] = it[i]; it[i] = t
                                }
                            }, enabled = i > 0, modifier = Modifier.size(32.dp)) { Text("↑") }
                            IconButton(onClick = {
                                if (i < rows.size - 1) rows = rows.toMutableList().also {
                                    val t = it[i + 1]; it[i + 1] = it[i]; it[i] = t
                                }
                            }, enabled = i < rows.size - 1, modifier = Modifier.size(32.dp)) { Text("↓") }
                            IconButton(onClick = { rows = rows.filterIndexed { j, _ -> j != i } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                if (row.sets > 1) rows = rows.toMutableList().also { it[i] = row.copy(sets = row.sets - 1) }
                            }) { Text("−") }
                            Text("${row.sets} sets")
                            TextButton(onClick = {
                                rows = rows.toMutableList().also { it[i] = row.copy(sets = row.sets + 1) }
                            }) { Text("+") }
                            TextButton(onClick = {
                                val next = restChoices[(restChoices.indexOf(row.restSeconds) + 1) % restChoices.size]
                                rows = rows.toMutableList().also { it[i] = row.copy(restSeconds = next) }
                            }) { Text("Rest: ${row.restSeconds?.let { s -> "${s}s" } ?: "default"}") }
                            if (i > 0) {
                                TextButton(onClick = {
                                    rows = rows.toMutableList().also { it[i] = row.copy(linked = !row.linked) }
                                }) {
                                    Text(
                                        if (row.linked) "Unlink" else "Superset ↑",
                                        color = if (row.linked) MaterialTheme.colorScheme.onSurfaceVariant else Palette.Volt,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("Add Exercise")
            }
        }
    }
}

// ---------- Programs (auto-progression) ----------

@Composable
private fun ProgramsSection(db: AppDatabase, onStarted: (Workout) -> Unit) {
    val scope = rememberCoroutineScope()
    val programs by remember { db.programDao().programs() }.collectAsStateList()
    var showTemplates by remember { mutableStateOf(false) }

    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            confirmButton = { TextButton(onClick = { showTemplates = false }) { Text("Cancel") } },
            title = { Text("Choose Program") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    templates.forEach { template ->
                        TextButton(onClick = {
                            scope.launch { installTemplate(db, template) }
                            showTemplates = false
                        }, modifier = Modifier.fillMaxWidth()) { Text(template.name) }
                    }
                }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Programs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showTemplates = true }) { Text("+ Add Program") }
        }
        programs.forEach { program -> ProgramCard(db, program, onStarted) }
        AiSuggestCard(db)
    }
}

@Composable
private fun ProgramCard(db: AppDatabase, program: Program, onStarted: (Workout) -> Unit) {
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf<List<ProgramDay>>(emptyList()) }
    LaunchedEffect(program.id, program.currentDayIndex) { days = db.programDao().daysFor(program.id) }
    val n = days.size.coerceAtLeast(1)
    val dayNum = (program.currentDayIndex % n) + 1
    val today = days.getOrNull(program.currentDayIndex % n)

    // Boostcamp hero: coral gradient card with cycle progress + big start pill
    Box(
        Modifier.fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
                ),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(program.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Next: ${today?.name ?: "…"} · Day $dayNum of ${days.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                IconButton(onClick = { scope.launch { db.programDao().deleteProgram(program.id) } }) {
                    Icon(Icons.Default.Delete, "delete program", tint = Color.White.copy(alpha = 0.7f))
                }
            }
            LinearProgressIndicator(
                progress = { dayNum / n.toFloat() },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Box(
                Modifier.fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .clickable { scope.launch { startProgramWorkout(db, program)?.let(onStarted) } }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Start ${today?.name ?: "Workout"}", color = Color(0xFF006B36), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AiSuggestCard(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }

    suggestion?.let {
        AlertDialog(
            onDismissRequest = { suggestion = null },
            confirmButton = { TextButton(onClick = { suggestion = null }) { Text("Close") } },
            title = { Text("AI Workout Suggestion") },
            text = { Text(it) },
        )
    }

    OutlinedButton(onClick = {
        scope.launch {
            busy = true
            suggestion = dev.dwm.liftlog.data.aiClient(db).fold(
                onSuccess = { client ->
                    runCatching { client.suggestWorkout(recentTrainingSummary(db)) }
                        .getOrElse { "AI request failed: ${it.message}" }
                },
                onFailure = { it.message ?: "AI not configured" },
            )
            busy = false
        }
    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Thinking…" else "AI Suggest Workout")
    }
}

private suspend fun recentTrainingSummary(db: AppDatabase): String {
    val workouts = db.workoutDao().history().first().take(5)
    if (workouts.isEmpty()) return "No training history yet — suggest a beginner full-body workout."
    val lines = mutableListOf<String>()
    for (w in workouts) {
        val sets = db.workoutDao().setsForWorkoutOnce(w.id).filter { it.completed }
        val parts = mutableListOf<String>()
        for ((exerciseId, exSets) in sets.groupBy { it.exerciseId }) {
            val name = db.exerciseDao().byId(exerciseId)?.name ?: "?"
            val best = exSets.maxByOrNull { e1rm(it.weightKg, it.reps) }
            parts.add("$name ${exSets.size}x, best ${best?.weightKg?.kgToLbDisplay()?.clean()} lb x ${best?.reps}")
        }
        lines.add("${w.name}: ${parts.joinToString("; ")}")
    }
    return lines.joinToString("\n")
}

// ---------- Active workout (Strong-style, lbs) ----------

@Composable
fun ActiveWorkoutScreen(
    db: AppDatabase,
    workout: Workout,
    modifier: Modifier = Modifier,
    voiceInput: (suspend () -> String?)? = null,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sets by remember { db.workoutDao().setsForWorkout(workout.id) }
        .collectAsStateList()
    var exercises by remember { mutableStateOf<Map<String, Exercise>>(emptyMap()) }
    var previous by remember { mutableStateOf<Map<String, List<WorkoutSet>>>(emptyMap()) }
    var bestBefore by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var showPicker by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<WorkoutSummary?>(null) }
    var editing by remember { mutableStateOf<Pair<String, SetField>?>(null) }
    var fresh by remember { mutableStateOf(true) }
    var historyFor by remember { mutableStateOf<Exercise?>(null) }

    // workout prefs + per-exercise routine metadata (rest, superset)
    var defaultRest by remember { mutableStateOf(90) }
    var autoStartRest by remember { mutableStateOf(true) }
    var showRpe by remember { mutableStateOf(false) }
    var routineMeta by remember { mutableStateOf<Map<String, RoutineExercise>>(emptyMap()) }
    LaunchedEffect(workout.id) {
        defaultRest = db.settingDao().get("restSeconds")?.toIntOrNull() ?: 90
        autoStartRest = db.settingDao().get("autoStartRest") != "false"
        showRpe = db.settingDao().get("showRpe") == "true"
        // ponytail: routine resolved by name match — workouts don't store routineId
        db.routineDao().byName(workout.name)?.let { r ->
            routineMeta = db.routineDao().exercisesFor(r.id).associateBy { it.exerciseId }
        }
    }

    fun completeSet(set: WorkoutSet) {
        scope.launch { db.workoutDao().updateSet(set.copy(completed = true, updatedAt = now())) }
        playBeep()
        haptic(Haptic.Click)
        if (!autoStartRest) return
        val meta = routineMeta[set.exerciseId]
        // superset: rest only after the last exercise of the group
        if (meta?.supersetGroup != null) {
            val group = routineMeta.values.filter { it.supersetGroup == meta.supersetGroup }
            if (meta.position != group.maxOf { it.position }) return
        }
        RestTimer.start(meta?.restSeconds ?: defaultRest)
    }

    LaunchedEffect(sets) {
        val ids = sets.map { it.exerciseId }.distinct()
        exercises = ids.mapNotNull { db.exerciseDao().byId(it) }.associateBy { it.id }
        previous = ids.associateWith { db.workoutDao().previousSets(it, workout.id) }
        bestBefore = ids.associateWith { db.workoutDao().bestE1rmBefore(it, workout.startedAt) ?: 0.0 }
    }

    historyFor?.let { ex -> ExerciseHistoryDialog(db, ex) { historyFor = null } }

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
                    RestTimer.clear()
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
                    // session muscle map for the celebration heatmap
                    val loads = mutableListOf<dev.dwm.liftlog.domain.MuscleLoad>()
                    for (s in done) {
                        val m = exercises[s.exerciseId]?.let {
                            dev.dwm.liftlog.domain.musclesFor(it.muscles, it.category)
                        }.orEmpty()
                        if (m.isEmpty()) continue
                        val vol = if (s.weightKg > 0) s.weightKg * s.reps else s.reps.toDouble()
                        loads.add(dev.dwm.liftlog.domain.MuscleLoad(m, vol, 0.0))
                    }
                    summary = WorkoutSummary(
                        durationMillis = end - workout.startedAt,
                        volumeKg = done.sumOf { it.weightKg * it.reps },
                        setCount = done.size,
                        prs = prs,
                        muscles = dev.dwm.liftlog.domain.fatigueMap(loads),
                    )
                }
            },
            onDiscard = {
                scope.launch {
                    RestTimer.clear()
                    db.workoutDao().deleteWorkout(workout.id)
                    onFinished()
                }
            },
        )
        RestRing()
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
                    editing = editing,
                    showRpe = showRpe,
                    supersetGroup = routineMeta[exerciseId]?.supersetGroup,
                    onNameClick = { exercises[exerciseId]?.let { historyFor = it } },
                    onEdit = { setId, field ->
                        editing = setId to field
                        fresh = true
                    },
                    onComplete = { set -> completeSet(set) },
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
        editing?.let { (setId, field) ->
            val set = sets.find { it.id == setId }
            if (set == null) {
                editing = null
            } else {
                NumericKeypad(
                    onKey = { c ->
                        val cur = if (fresh) "" else fieldText(set, field)
                        if (c == '.' && (field == SetField.REPS || '.' in cur)) return@NumericKeypad
                        scope.launch { db.workoutDao().updateSet(applyText(set, field, cur + c).copy(updatedAt = now())) }
                        fresh = false
                    },
                    onBackspace = {
                        val cur = fieldText(set, field)
                        scope.launch { db.workoutDao().updateSet(applyText(set, field, cur.dropLast(1)).copy(updatedAt = now())) }
                        fresh = false
                    },
                    onNext = {
                        editing = when (field) {
                            SetField.WEIGHT -> setId to SetField.REPS
                            SetField.REPS -> if (showRpe) setId to SetField.RPE else null
                            SetField.RPE -> null
                        }
                        fresh = true
                    },
                    onDone = { editing = null },
                    onVoice = voiceInput?.let { vi ->
                        {
                            scope.launch {
                                val heard = vi() ?: return@launch
                                // "225 for 8" / "225 by 8" / "225 x 8"
                                val m = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:for|by|x|times)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                                    .find(heard)
                                if (m != null) {
                                    val (w, r) = m.destructured
                                    db.workoutDao().updateSet(
                                        set.copy(
                                            weightKg = w.toDouble().lbToKg(),
                                            reps = r.toInt(),
                                            updatedAt = now(),
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onCompleteSet = {
                        completeSet(set)
                        // jump to the next uncompleted set's weight, or hide keypad
                        val next = sets.firstOrNull { !it.completed && it.id != set.id }
                        editing = next?.let { it.id to SetField.WEIGHT }
                        fresh = true
                    },
                )
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
        Box(
            Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF4CAF50), Color(0xFF2FB86A), Color(0xFF1E9E86))
                    ),
                    RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onFinish)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) { Text("Finish", fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

data class WorkoutSummary(
    val durationMillis: Long,
    val volumeKg: Double,
    val setCount: Int,
    val prs: List<PrRecord>,
    val muscles: Map<dev.dwm.liftlog.domain.Muscle, Double> = emptyMap(),
)

/** Full-screen celebration: count-up stats, confetti on PR, session muscle heatmap. */
@Composable
private fun WorkoutCompleteDialog(s: WorkoutSummary, onDone: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "WORKOUT\nCOMPLETE",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Success,
                        textAlign = TextAlign.Center,
                    )
                    var volTarget by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) { volTarget = s.volumeKg.kgToLb().toInt() }
                    val volume by androidx.compose.animation.core.animateIntAsState(volTarget, animationSpec = tween(1200))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatBlock(formatDuration(s.durationMillis), "duration")
                        StatBlock("$volume lb", "volume")
                        StatBlock("${s.setCount}", "sets")
                    }
                    if (s.prs.isNotEmpty()) {
                        val pulse = rememberInfiniteTransition()
                        val scale by pulse.animateFloat(
                            1f, 1.35f,
                            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.EmojiEvents, null, tint = Palette.Pr,
                                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                            )
                            Text("${s.prs.size} PR${if (s.prs.size > 1) "s" else ""}!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        }
                        s.prs.forEach {
                            Text(
                                "${it.exerciseName} — ${it.weightKg.kgToLbDisplay().clean()} lb × ${it.reps} (new e1RM record)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Pr,
                            )
                        }
                    }
                    if (s.muscles.isNotEmpty()) {
                        Text("Muscles hit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BodyCanvas(s.muscles, front = true, Modifier.weight(1f))
                            BodyCanvas(s.muscles, front = false, Modifier.weight(1f))
                        }
                    }
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Success, contentColor = Color.Black),
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                }
                if (s.prs.isNotEmpty()) Confetti(Modifier.fillMaxSize())
            }
        }
    }
}

/** One-shot particle burst from top center. */
@Composable
private fun Confetti(modifier: Modifier) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(2200)) }
    val progress = anim.value
    val particles = remember {
        val colors = listOf(Palette.Success, Palette.Volt, Palette.Pr, Palette.Protein, Palette.Trend)
        List(40) {
            Triple(
                kotlin.random.Random.nextFloat() * 2f - 1f,           // x spread
                0.5f + kotlin.random.Random.nextFloat(),              // speed
                colors[it % colors.size],
            )
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        particles.forEach { (dx, speed, color) ->
            val t = (progress * speed).coerceAtMost(1f)
            val x = size.width / 2 + dx * size.width / 2 * t
            val y = size.height * t * t // gravity-ish
            drawCircle(color.copy(alpha = (1f - t).coerceIn(0f, 1f)), radius = 8f, center = Offset(x, y))
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise?,
    sets: List<WorkoutSet>,
    previous: List<WorkoutSet>,
    bestE1rm: Double,
    editing: Pair<String, SetField>?,
    showRpe: Boolean,
    supersetGroup: Int?,
    onNameClick: () -> Unit,
    onEdit: (String, SetField) -> Unit,
    onComplete: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onAddSet: (WorkoutSet) -> Unit,
) {
    // finished exercises collapse to a slim green row; tap to reopen
    val allDone = sets.isNotEmpty() && sets.all { it.completed }
    var reopened by remember(allDone) { mutableStateOf(false) }
    if (allDone && !reopened) {
        Card(Modifier.fillMaxWidth().animateContentSize().clickable { reopened = true }) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Palette.Success.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Check, null, tint = Palette.Success)
                Text(
                    exercise?.name ?: "…",
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = Palette.Success,
                )
                Text(
                    "${sets.size} sets done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Card(Modifier.animateContentSize()) {
        Row {
            if (supersetGroup != null) {
                Box(Modifier.width(4.dp).heightIn(min = 48.dp).fillMaxHeight().background(Palette.Volt))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        exercise?.name ?: "…",
                        Modifier.clickable(onClick = onNameClick),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    if (supersetGroup != null) {
                        Text("SUPERSET", style = MaterialTheme.typography.labelSmall, color = Palette.Volt, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Set", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Previous", Modifier.weight(1.1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Text("lbs", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Text("Reps", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (showRpe) Text("RPE", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Box(Modifier.size(36.dp))
                    Box(Modifier.size(36.dp))
                }
                sets.forEachIndexed { i, set ->
                    SetRow(
                        index = i + 1,
                        set = set,
                        previous = previous.getOrNull(i),
                        isPr = set.completed && set.weightKg > 0 && e1rm(set.weightKg, set.reps) > bestE1rm,
                        activeField = editing?.takeIf { it.first == set.id }?.second,
                        showRpe = showRpe,
                        onEdit = { field -> onEdit(set.id, field) },
                        onComplete = onComplete,
                        onDelete = onDelete,
                    )
                }
                sets.lastOrNull()?.let { last ->
                    val plates = platesPerSide(
                        last.weightKg.kgToLb(),
                        barKg = 45.0,
                        available = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
                    )
                    if (!plates.isNullOrEmpty()) {
                        PlateBar(plates)
                    }
                    OutlinedButton(onClick = { onAddSet(last) }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Add Set")
                    }
                }
            }
        }
    }
}

// bar + colored plates per side, gym color code
private val plateColors = mapOf(
    45.0 to Color(0xFF4D9FFF), 35.0 to Color(0xFFFFC94D), 25.0 to Color(0xFF66BB6A),
    10.0 to Color(0xFFE8EAED), 5.0 to Color(0xFFEF5350), 2.5 to Color(0xFF9AA0A6),
)

@Composable
private fun PlateBar(plates: List<Double>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.Canvas(Modifier.height(44.dp).width(120.dp)) {
            val midY = size.height / 2
            drawLine(Color(0xFF6B7280), Offset(0f, midY), Offset(size.width, midY), strokeWidth = 6f)
            var x = 26f
            plates.forEach { p ->
                // bigger plate = taller rect
                val h = (18 + (p / 45.0) * 22).toFloat() * 2
                drawRoundRect(
                    plateColors[p] ?: Color.Gray,
                    topLeft = Offset(x, midY - h / 2),
                    size = androidx.compose.ui.geometry.Size(12f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                )
                x += 16f
            }
        }
        Text(
            "${plates.joinToString(" + ") { it.clean() }} lb / side",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSet,
    previous: WorkoutSet?,
    isPr: Boolean,
    activeField: SetField?,
    showRpe: Boolean,
    onEdit: (SetField) -> Unit,
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
            previous?.let { "${it.weightKg.kgToLbDisplay().clean()}×${it.reps}" } ?: "—",
            Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        ValueCell(
            text = fieldText(set, SetField.WEIGHT),
            placeholder = previous?.weightKg?.kgToLbDisplay()?.clean(),
            active = activeField == SetField.WEIGHT,
            modifier = Modifier.weight(1f),
        ) { onEdit(SetField.WEIGHT) }
        ValueCell(
            text = fieldText(set, SetField.REPS),
            placeholder = previous?.reps?.toString(),
            active = activeField == SetField.REPS,
            modifier = Modifier.weight(1f),
        ) { onEdit(SetField.REPS) }
        if (showRpe) {
            ValueCell(
                text = fieldText(set, SetField.RPE),
                placeholder = null,
                active = activeField == SetField.RPE,
                modifier = Modifier.weight(0.8f),
            ) { onEdit(SetField.RPE) }
        }
        // spring pop when the set completes
        val checkScale by androidx.compose.animation.core.animateFloatAsState(
            if (set.completed) 1f else 0.9f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            ),
        )
        IconButton(
            onClick = { onComplete(set) },
            enabled = !set.completed,
            modifier = Modifier.size(36.dp)
                .graphicsLayer(scaleX = checkScale, scaleY = checkScale)
                .background(
                    if (set.completed) Palette.Success else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.Default.Check, "complete set",
                tint = if (set.completed) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(set) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, "delete set", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ValueCell(
    text: String,
    placeholder: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .then(
                if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium)
        else Text(
            placeholder ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumericKeypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onVoice: (() -> Unit)?,
    onCompleteSet: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // strict 4-column grid: digits left, actions right
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            "123".forEach { c -> KeypadKey("$c", Modifier.weight(1f)) { onKey(c) } }
            KeypadKey("⌫", Modifier.weight(1f)) { onBackspace() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            "456".forEach { c -> KeypadKey("$c", Modifier.weight(1f)) { onKey(c) } }
            KeypadKey("Next", Modifier.weight(1f), accent = true, onClick = onNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            "789".forEach { c -> KeypadKey("$c", Modifier.weight(1f)) { onKey(c) } }
            KeypadKey("Hide", Modifier.weight(1f), accent = true, onClick = onDone)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeypadKey(".", Modifier.weight(1f)) { onKey('.') }
            KeypadKey("0", Modifier.weight(1f)) { onKey('0') }
            if (onVoice != null) {
                KeypadKey("🎤", Modifier.weight(1f), accent = true, onClick = onVoice)
            }
            Box(
                Modifier.weight(if (onVoice != null) 1f else 2f).height(52.dp)
                    .background(Palette.Success, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCompleteSet),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Check, null, tint = Color.Black)
                    if (onVoice == null) Text("Complete Set", color = Color.Black, fontWeight = FontWeight.Bold)
                    else Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp)
            .background(
                if (accent) Palette.Volt.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable {
                haptic(Haptic.Tick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Big rest countdown ring on the workout screen; display-only (App's engine owns side effects). */
@Composable
private fun RestRing() {
    val endsAt = RestTimer.endsAt ?: return
    var tick by remember { mutableStateOf(now()) }
    LaunchedEffect(endsAt) {
        while (true) {
            tick = now()
            delay(200)
        }
    }
    if (RestTimer.over) {
        val flash = rememberInfiniteTransition()
        val alpha by flash.animateFloat(
            0.55f, 1f,
            animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        )
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .background(Palette.Success.copy(alpha = alpha), RoundedCornerShape(12.dp))
                .clickable { RestTimer.clear() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("REST OVER — GO!  (tap)", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val remaining = (endsAt - tick).coerceAtLeast(0)
    val secs = remaining / 1000
    val frac = (remaining / RestTimer.durationMs.toFloat()).coerceIn(0f, 1f)
    val urgent = secs <= 10
    val ringColor = if (urgent) Palette.Protein else Palette.Volt
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 14f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val inset = 8f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
                val tl = Offset(inset, inset)
                drawArc(Color(0xFF1C2536), -90f, 360f, false, tl, arcSize, style = stroke)
                drawArc(ringColor, -90f, 360f * frac, false, tl, arcSize, style = stroke)
            }
            Text(
                "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ringColor,
            )
        }
        Column {
            Text("REST", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { RestTimer.add(-15) }) { Text("−15") }
                TextButton(onClick = { RestTimer.add(15) }) { Text("+15") }
                TextButton(onClick = { RestTimer.clear() }) { Text("Skip") }
            }
        }
    }
}

/** Last sessions for an exercise: sets grouped by day, best e1RM. */
@Composable
private fun ExerciseHistoryDialog(db: AppDatabase, exercise: Exercise, onClose: () -> Unit) {
    var recent by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    var best by remember { mutableStateOf(0.0) }
    LaunchedEffect(exercise.id) {
        recent = db.workoutDao().recentSetsFor(exercise.id)
        best = db.workoutDao().bestE1rmBefore(exercise.id, now()) ?: 0.0
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text(exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (best > 0) {
                    Text(
                        "Best e1RM: ${best.kgToLbDisplay().clean()} lb",
                        fontWeight = FontWeight.Bold,
                        color = Palette.Pr,
                    )
                }
                if (recent.isEmpty()) {
                    Text("No completed sets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(recent, key = { it.id }) { s ->
                            Text(
                                "${s.weightKg.kgToLbDisplay().clean()} lb × ${s.reps}" +
                                    (s.rpe?.let { " @ RPE ${it.clean()}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
    )
}

fun Double.clean(): String = if (this % 1.0 == 0.0) "${toLong()}" else "$this"
