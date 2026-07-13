package dev.dwm.liftlog.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.dwm.liftlog.data.db.Program
import dev.dwm.liftlog.data.db.ProgramDay
import dev.dwm.liftlog.data.installTemplate
import dev.dwm.liftlog.data.startProgramWorkout
import dev.dwm.liftlog.data.templates
import dev.dwm.liftlog.ui.collectAsStateList
import kotlinx.coroutines.launch

@Composable
fun ProgramsScreen(db: AppDatabase, modifier: Modifier = Modifier, onWorkoutStarted: () -> Unit) {
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

    LazyColumn(
        modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(programs, key = { it.id }) { program ->
            ProgramCard(db, program, onWorkoutStarted)
        }
        item {
            OutlinedButton(onClick = { showTemplates = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Add Program")
            }
        }
    }
}

@Composable
private fun ProgramCard(db: AppDatabase, program: Program, onWorkoutStarted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf<List<ProgramDay>>(emptyList()) }
    LaunchedEffect(program.id, program.currentDayIndex) { days = db.programDao().daysFor(program.id) }
    val today = days.getOrNull(program.currentDayIndex % days.size.coerceAtLeast(1))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(program.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Next: ${today?.name ?: "…"} (day ${(program.currentDayIndex % days.size.coerceAtLeast(1)) + 1}/${days.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    scope.launch {
                        if (startProgramWorkout(db, program) != null) onWorkoutStarted()
                    }
                }) { Text("Start ${today?.name ?: "workout"}") }
                TextButton(onClick = { scope.launch { db.programDao().deleteProgram(program.id) } }) {
                    Text("Delete")
                }
            }
        }
    }
}
