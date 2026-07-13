package dev.dwm.liftlog.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun ExercisePickerDialog(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by remember(query) { db.exerciseDao().search(query) }.collectAsStateList()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add Exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(results, key = { it.id }) { exercise ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(exercise) }.padding(vertical = 10.dp)
                        ) {
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
        },
    )
}
