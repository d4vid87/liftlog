package dev.dwm.liftlog.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.E1rmPoint
import dev.dwm.liftlog.data.db.Exercise
import dev.dwm.liftlog.data.db.WorkoutSet
import dev.dwm.liftlog.ui.collectAsStateList
import dev.dwm.liftlog.ui.workout.clean
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryScreen(db: AppDatabase, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Workouts") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Progress") })
        }
        when (tab) {
            0 -> WorkoutList(db)
            1 -> ProgressCharts(db)
        }
    }
}

@Composable
private fun WorkoutList(db: AppDatabase) {
    val workouts by remember { db.workoutDao().history() }.collectAsStateList()
    LazyColumn(
        Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(workouts, key = { it.id }) { workout ->
            var expanded by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Column(Modifier.padding(12.dp)) {
                    Text(formatDate(workout.startedAt), style = MaterialTheme.typography.titleMedium)
                    workout.finishedAt?.let {
                        Text(
                            "${(it - workout.startedAt) / 60_000} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (expanded) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        WorkoutDetail(db, workout.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetail(db: AppDatabase, workoutId: Long) {
    val sets by remember { db.workoutDao().setsForWorkout(workoutId) }.collectAsStateList()
    var names by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(sets) {
        names = sets.map { it.exerciseId }.distinct()
            .associateWith { db.exerciseDao().byId(it)?.name ?: "?" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sets.groupBy { it.exerciseId }.forEach { (exerciseId, exSets) ->
            Text(names[exerciseId] ?: "…", style = MaterialTheme.typography.titleSmall)
            exSets.forEach { s: WorkoutSet ->
                Text(
                    "  ${s.weightKg.clean()}kg × ${s.reps}" + (s.rpe?.let { " @${it.clean()}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProgressCharts(db: AppDatabase) {
    val exercises by remember { db.workoutDao().loggedExercises() }.collectAsStateList()
    var selected by remember { mutableStateOf<Exercise?>(null) }
    var points by remember { mutableStateOf<List<E1rmPoint>>(emptyList()) }

    LaunchedEffect(selected) {
        points = selected?.let { db.workoutDao().e1rmHistory(it.id) } ?: emptyList()
    }

    LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        selected?.let { exercise ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${exercise.name} — est. 1RM", style = MaterialTheme.typography.titleMedium)
                        if (points.size < 2) {
                            Text("Need 2+ logged workouts for a chart.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LineChart(points, Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDate(points.first().time), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "best ${points.maxOf { it.e1rm }.clean()}kg",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(formatDate(points.last().time), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        items(exercises, key = { it.id }) { exercise ->
            Text(
                exercise.name,
                Modifier.fillMaxWidth().clickable { selected = exercise }.padding(vertical = 10.dp),
                color = if (exercise.id == selected?.id) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LineChart(points: List<E1rmPoint>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val minT = points.first().time
        val maxT = points.last().time
        val minV = points.minOf { it.e1rm }
        val maxV = points.maxOf { it.e1rm }
        val spanT = (maxT - minT).coerceAtLeast(1)
        val spanV = (maxV - minV).coerceAtLeast(1.0)
        val offsets = points.map {
            Offset(
                x = (it.time - minT) / spanT.toFloat() * size.width,
                y = size.height - ((it.e1rm - minV) / spanV).toFloat() * size.height * 0.9f - size.height * 0.05f,
            )
        }
        offsets.zipWithNext { a, b ->
            drawLine(color, a, b, strokeWidth = 4f, cap = StrokeCap.Round)
        }
        offsets.forEach { drawCircle(color, radius = 7f, center = it) }
        drawCircle(Color.White, radius = 3f, center = offsets.last())
    }
}

private fun formatDate(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.date} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}
