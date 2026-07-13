package dev.dwm.liftlog.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.dwm.liftlog.data.OpenFoodFacts
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Food
import dev.dwm.liftlog.data.db.FoodLog
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.DayIntake
import dev.dwm.liftlog.domain.DayWeight
import dev.dwm.liftlog.domain.TdeeResult
import dev.dwm.liftlog.domain.computeTdee
import dev.dwm.liftlog.ui.collectAsStateList
import dev.dwm.liftlog.ui.workout.clean
import dev.dwm.liftlog.data.db.WeightEntry
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

val meals = listOf("Breakfast", "Lunch", "Dinner", "Snack")

fun todayEpochDay(): Long = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays().toLong()

@Composable
fun NutritionScreen(
    db: AppDatabase,
    off: OpenFoodFacts,
    modifier: Modifier = Modifier,
    scanBarcode: (suspend () -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    val today = remember { todayEpochDay() }
    val logs by remember { db.foodLogDao().forDay(today) }.collectAsStateList()
    var foods by remember { mutableStateOf<Map<String, Food>>(emptyMap()) }
    var tdee by remember { mutableStateOf<TdeeResult?>(null) }
    var todayWeight by remember { mutableStateOf<Double?>(null) }
    var addingTo by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(logs) {
        foods = logs.map { it.foodId }.distinct()
            .mapNotNull { db.foodDao().byId(it) }.associateBy { it.id }
    }
    LaunchedEffect(logs, refresh) {
        val goal = db.settingDao().get("goalKgPerWeek")?.toDoubleOrNull() ?: 0.0
        val weights = db.weightDao().all().map { DayWeight(it.epochDay, it.kg) }
        val intakes = db.foodLogDao().dailyKcals(today - 35).map { DayIntake(it.epochDay, it.kcal) }
        tdee = computeTdee(weights, intakes, goal)
        todayWeight = db.weightDao().forDay(today)?.kg
    }

    addingTo?.let { meal ->
        AddFoodDialog(db, off, scanBarcode, onDismiss = { addingTo = null }) { food, grams ->
            scope.launch {
                db.foodDao().upsert(food)
                db.foodLogDao().insert(FoodLog(epochDay = today, foodId = food.id, grams = grams, meal = meal))
            }
            addingTo = null
        }
    }

    val totals = logs.mapNotNull { l -> foods[l.foodId]?.let { f -> l.grams to f } }
    val kcal = totals.sumOf { (g, f) -> g * f.kcal / 100 }
    val protein = totals.sumOf { (g, f) -> g * f.protein / 100 }
    val carbs = totals.sumOf { (g, f) -> g * f.carbs / 100 }
    val fat = totals.sumOf { (g, f) -> g * f.fat / 100 }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    Text("${kcal.toInt()} kcal   P ${protein.toInt()}g   C ${carbs.toInt()}g   F ${fat.toInt()}g")
                    tdee?.let { t ->
                        Text(
                            "TDEE ~${t.tdeeKcal.toInt()} kcal · target ${t.targetKcal.toInt()} · " +
                                "trend ${t.trendWeightKg.clean()}kg (${if (t.weeklyDeltaKg >= 0) "+" else ""}${t.weeklyDeltaKg.clean()}kg/wk)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } ?: Text(
                        "TDEE: log weight + food for 7+ days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WeightRow(todayWeight) { kg ->
                        scope.launch {
                            db.weightDao().forDay(today)?.let {
                                db.weightDao().upsert(it.copy(kg = kg, updatedAt = nowMillis()))
                            } ?: db.weightDao().upsert(WeightEntry(epochDay = today, kg = kg))
                            refresh++
                        }
                    }
                }
            }
        }
        meals.forEach { meal ->
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(meal, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { addingTo = meal }) { Text("+ Add") }
                }
            }
            items(logs.filter { it.meal == meal }, key = { it.id }) { log ->
                val food = foods[log.foodId]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(food?.name ?: "…")
                        Text(
                            "${log.grams.clean()}g · ${((food?.kcal ?: 0.0) * log.grams / 100).toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { scope.launch { db.foodLogDao().delete(log.id) } }) {
                        Icon(Icons.Default.Delete, "delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightRow(current: Double?, onSave: (Double) -> Unit) {
    var text by remember(current) { mutableStateOf(current?.clean() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Weight kg") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { text.toDoubleOrNull()?.let(onSave) }) { Text("Save") }
    }
}

@Composable
private fun AddFoodDialog(
    db: AppDatabase,
    off: OpenFoodFacts,
    scanBarcode: (suspend () -> String?)?,
    onDismiss: () -> Unit,
    onAdd: (Food, Double) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Food>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Food?>(null) }
    var grams by remember { mutableStateOf("100") }

    LaunchedEffect(Unit) { results = db.foodDao().recentFoods() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val food = selected
            TextButton(
                onClick = { food?.let { onAdd(it, grams.toDoubleOrNull() ?: 100.0) } },
                enabled = food != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add Food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        scope.launch {
                            searching = true
                            val local = db.foodDao().search(query)
                            results = local + off.search(query).filter { remote ->
                                local.none { it.barcode != null && it.barcode == remote.barcode }
                            }
                            searching = false
                        }
                    }) { Text("Go") }
                }
                if (scanBarcode != null) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            searching = true
                            val code = scanBarcode()
                            if (code != null) {
                                val food = db.foodDao().byBarcode(code) ?: off.byBarcode(code)
                                if (food != null) {
                                    results = listOf(food)
                                    selected = food
                                }
                            }
                            searching = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Scan Barcode") }
                }
                if (searching) CircularProgressIndicator()
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(results, key = { it.id }) { food ->
                        Column(
                            Modifier.fillMaxWidth().clickable { selected = food }.padding(vertical = 8.dp)
                        ) {
                            Text(
                                food.name,
                                color = if (food.id == selected?.id) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${food.brand} · ${food.kcal.toInt()} kcal/100g · P${food.protein.toInt()} C${food.carbs.toInt()} F${food.fat.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Grams") },
                    singleLine = true,
                )
            }
        },
    )
}
