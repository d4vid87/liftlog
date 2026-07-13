package dev.dwm.liftlog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.seed.seedExercisesIfEmpty
import dev.dwm.liftlog.ui.history.HistoryScreen
import dev.dwm.liftlog.ui.workout.WorkoutTab

enum class Tab { Workout, History }

@Composable
fun App(db: AppDatabase) {
    var tab by remember { mutableStateOf(Tab.Workout) }

    LaunchedEffect(Unit) { seedExercisesIfEmpty(db) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Workout,
                        onClick = { tab = Tab.Workout },
                        icon = { Icon(Icons.Default.FitnessCenter, null) },
                        label = { Text("Workout") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.History,
                        onClick = { tab = Tab.History },
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text("History") },
                    )
                }
            }
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                Tab.Workout -> WorkoutTab(db, modifier)
                Tab.History -> HistoryScreen(db, modifier)
            }
        }
    }
}
