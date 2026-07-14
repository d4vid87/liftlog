package dev.dwm.liftlog.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Exercise
import dev.dwm.liftlog.ui.collectAsStateList
import dev.dwm.liftlog.ui.components.FullScreenDialog

@Composable
fun ExercisePickerDialog(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by remember(query) { db.exerciseDao().search(query) }.collectAsStateList()

    FullScreenDialog("Add Exercise", onDismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.id }) { exercise ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(exercise) }.padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        dev.dwm.liftlog.ui.components.ExerciseImage(exercise.name, Modifier.size(48.dp))
                        Column {
                            Text(exercise.name)
                            Text(
                                listOf(exercise.category, exercise.muscles)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
