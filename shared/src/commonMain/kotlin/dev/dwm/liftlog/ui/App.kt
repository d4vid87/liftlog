package dev.dwm.liftlog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.dwm.liftlog.data.OpenFoodFacts
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.httpClient
import dev.dwm.liftlog.data.seed.seedExercisesIfEmpty
import dev.dwm.liftlog.ui.dashboard.DashboardScreen
import dev.dwm.liftlog.ui.history.HistoryScreen
import dev.dwm.liftlog.ui.nutrition.NutritionScreen
import dev.dwm.liftlog.ui.settings.SettingsScreen
import dev.dwm.liftlog.ui.workout.WorkoutTab

enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    Workout("Workout", Icons.Default.FitnessCenter),
    Nutrition("Food", Icons.Default.Restaurant),
    History("History", Icons.Default.History),
    Settings("More", Icons.Default.Settings),
}

@Composable
fun App(
    db: AppDatabase,
    scanBarcode: (suspend () -> String?)? = null,
    saveExport: (suspend (String) -> String)? = null,
    takePhoto: (suspend () -> String?)? = null,
) {
    val off = remember { OpenFoodFacts(httpClient()) }
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var workoutRefresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { seedExercisesIfEmpty(db) }

    LiftLogTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                if (t == Tab.Workout) workoutRefresh++
                                tab = t
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                Tab.Dashboard -> DashboardScreen(db, modifier) {
                    if (it == Tab.Workout) workoutRefresh++
                    tab = it
                }
                Tab.Workout -> WorkoutTab(db, modifier, refreshKey = workoutRefresh)
                Tab.Nutrition -> NutritionScreen(db, off, modifier, scanBarcode, takePhoto)
                Tab.History -> HistoryScreen(db, modifier)
                Tab.Settings -> SettingsScreen(db, modifier, saveExport)
            }
        }
    }
}
