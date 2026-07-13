package dev.dwm.liftlog.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.WeightEntry
import dev.dwm.liftlog.domain.DayIntake
import dev.dwm.liftlog.domain.DayWeight
import dev.dwm.liftlog.domain.TdeeResult
import dev.dwm.liftlog.domain.computeTdee
import dev.dwm.liftlog.domain.kgToLbDisplay
import dev.dwm.liftlog.ui.Palette
import dev.dwm.liftlog.ui.Tab
import dev.dwm.liftlog.ui.components.CalorieRing
import dev.dwm.liftlog.ui.components.MacroBar
import dev.dwm.liftlog.ui.nutrition.todayEpochDay
import dev.dwm.liftlog.ui.workout.clean
import dev.dwm.liftlog.ui.collectAsStateList

/** MFP-style dashboard: calories ring, macros, weight trend, quick "+" actions. */
@Composable
fun DashboardScreen(db: AppDatabase, modifier: Modifier = Modifier, onGoTo: (Tab) -> Unit) {
    val today = remember { todayEpochDay() }
    val logs by remember { db.foodLogDao().forDay(today) }.collectAsStateList()
    var kcal by remember { mutableStateOf(0.0) }
    var protein by remember { mutableStateOf(0.0) }
    var carbs by remember { mutableStateOf(0.0) }
    var fat by remember { mutableStateOf(0.0) }
    var tdee by remember { mutableStateOf<TdeeResult?>(null) }
    var targetKcal by remember { mutableStateOf(2000.0) }
    var proteinPct by remember { mutableStateOf(30.0) }
    var fatPct by remember { mutableStateOf(30.0) }
    var weights by remember { mutableStateOf<List<WeightEntry>>(emptyList()) }
    var fabOpen by remember { mutableStateOf(false) }

    LaunchedEffect(logs) {
        var k = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
        for (log in logs) {
            val food = db.foodDao().byId(log.foodId) ?: continue
            k += log.grams * food.kcal / 100
            p += log.grams * food.protein / 100
            c += log.grams * food.carbs / 100
            f += log.grams * food.fat / 100
        }
        kcal = k; protein = p; carbs = c; fat = f

        val goal = db.settingDao().get("goalKgPerWeek")?.toDoubleOrNull() ?: 0.0
        val allWeights = db.weightDao().all()
        weights = allWeights.filter { it.epochDay >= today - 90 }
        val intakes = db.foodLogDao().dailyKcals(today - 35).map { DayIntake(it.epochDay, it.kcal) }
        tdee = computeTdee(allWeights.map { DayWeight(it.epochDay, it.kg) }, intakes, goal)
        targetKcal = tdee?.targetKcal ?: db.settingDao().get("targetKcal")?.toDoubleOrNull() ?: 2000.0
        proteinPct = db.settingDao().get("proteinPct")?.toDoubleOrNull() ?: 30.0
        fatPct = db.settingDao().get("fatPct")?.toDoubleOrNull() ?: 30.0
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Today", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Calories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Remaining = Target − Food",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CalorieRing(eaten = kcal, target = targetKcal, modifier = Modifier.size(130.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LabeledValue("Target", "${targetKcal.toInt()}")
                            LabeledValue("Food", "${kcal.toInt()}")
                            tdee?.let { LabeledValue("Expenditure", "~${it.tdeeKcal.toInt()}") }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Macros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val carbsPct = (100.0 - proteinPct - fatPct).coerceAtLeast(0.0)
                    MacroBar("Protein", protein, targetKcal * proteinPct / 100 / 4, Palette.Protein)
                    MacroBar("Carbs", carbs, targetKcal * carbsPct / 100 / 4, Palette.Carbs)
                    MacroBar("Fat", fat, targetKcal * fatPct / 100 / 9, Palette.Fat)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Weight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        weights.lastOrNull()?.let {
                            Text(
                                "${it.kg.kgToLbDisplay().clean()} lb",
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Calories,
                            )
                        }
                    }
                    Text(
                        "Last 90 days" + (tdee?.let {
                            " · trend ${it.trendWeightKg.kgToLbDisplay().clean()} lb"
                        } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (weights.size >= 2) {
                        WeightChart(weights, Modifier.fillMaxWidth().height(120.dp))
                    } else {
                        Text(
                            "Log weight on the Food tab to see your trend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { fabOpen = true }, containerColor = Palette.Calories) {
                Icon(Icons.Default.Add, "quick actions")
            }
            DropdownMenu(expanded = fabOpen, onDismissRequest = { fabOpen = false }) {
                DropdownMenuItem(text = { Text("Log Food") }, onClick = { fabOpen = false; onGoTo(Tab.Nutrition) })
                DropdownMenuItem(text = { Text("Snap Food Photo (AI)") }, onClick = { fabOpen = false; onGoTo(Tab.Nutrition) })
                DropdownMenuItem(text = { Text("Start Workout") }, onClick = { fabOpen = false; onGoTo(Tab.Workout) })
                DropdownMenuItem(text = { Text("Log Weight") }, onClick = { fabOpen = false; onGoTo(Tab.Nutrition) })
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeightChart(entries: List<WeightEntry>, modifier: Modifier) {
    val color = Palette.Calories
    Canvas(modifier) {
        val minD = entries.first().epochDay
        val maxD = entries.last().epochDay
        val minV = entries.minOf { it.kg }
        val maxV = entries.maxOf { it.kg }
        val spanD = (maxD - minD).coerceAtLeast(1)
        val spanV = (maxV - minV).coerceAtLeast(0.5)
        val offsets = entries.map {
            Offset(
                x = (it.epochDay - minD) / spanD.toFloat() * size.width,
                y = size.height - ((it.kg - minV) / spanV).toFloat() * size.height * 0.85f - size.height * 0.075f,
            )
        }
        offsets.zipWithNext { a, b -> drawLine(color, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
        offsets.forEach { drawCircle(color, radius = 5f, center = it) }
    }
}
