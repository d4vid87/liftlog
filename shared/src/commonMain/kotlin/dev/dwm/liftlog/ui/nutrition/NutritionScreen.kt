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
import dev.dwm.liftlog.data.AiClient
import dev.dwm.liftlog.data.OpenFoodFacts
import dev.dwm.liftlog.data.ParsedFood
import dev.dwm.liftlog.data.httpClient
import dev.dwm.liftlog.data.toFood
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
    var aiFor by remember { mutableStateOf<String?>(null) }
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
    aiFor?.let { meal ->
        AiFoodDialog(db, onDismiss = { aiFor = null }) { parsed ->
            scope.launch {
                parsed.forEach { p ->
                    val food = p.toFood()
                    db.foodDao().upsert(food)
                    db.foodLogDao().insert(FoodLog(epochDay = today, foodId = food.id, grams = p.grams, meal = meal))
                }
            }
            aiFor = null
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
                    Row {
                        TextButton(onClick = { aiFor = meal }) { Text("AI") }
                        TextButton(onClick = { addingTo = meal }) { Text("+ Add") }
                    }
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
private fun AiFoodDialog(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onLog: (List<ParsedFood>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<ParsedFood>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onLog(parsed) }, enabled = parsed.isNotEmpty()) {
                Text("Log ${parsed.size} item(s)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("AI Food Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Describe your meal (e.g. 2 eggs and toast)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    scope.launch {
                        busy = true; error = ""
                        val endpoint = db.settingDao().get("aiEndpoint") ?: ""
                        val model = db.settingDao().get("aiModel") ?: ""
                        if (endpoint.isBlank() || model.isBlank()) {
                            error = "Set AI endpoint + model in More tab"
                        } else {
                            val ai = AiClient(httpClient(), endpoint, model, db.settingDao().get("aiApiKey"))
                            parsed = runCatching { ai.parseFoods(text) }.getOrElse { error = it.message ?: "failed"; emptyList() }
                            if (parsed.isEmpty() && error.isBlank()) error = "Nothing parsed"
                        }
                        busy = false
                    }
                }, enabled = text.isNotBlank() && !busy) { Text("Parse") }
                if (busy) CircularProgressIndicator()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                parsed.forEach {
                    Text(
                        "${it.name}: ${it.grams.clean()}g, ${it.kcal.toInt()} kcal (P${it.protein.toInt()} C${it.carbs.toInt()} F${it.fat.toInt()})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
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
    var missedBarcode by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newKcal by remember { mutableStateOf("") }
    var newP by remember { mutableStateOf("") }
    var newC by remember { mutableStateOf("") }
    var newF by remember { mutableStateOf("") }

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
                                    missedBarcode = null
                                } else {
                                    missedBarcode = code
                                }
                            }
                            searching = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Scan Barcode") }
                }
                if (searching) CircularProgressIndicator()
                missedBarcode?.let { code ->
                    Text("Barcode $code not found — create it:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(newKcal, { newKcal = it }, label = { Text("kcal/100g") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(newP, { newP = it }, label = { Text("P") }, singleLine = true, modifier = Modifier.weight(0.6f))
                        OutlinedTextField(newC, { newC = it }, label = { Text("C") }, singleLine = true, modifier = Modifier.weight(0.6f))
                        OutlinedTextField(newF, { newF = it }, label = { Text("F") }, singleLine = true, modifier = Modifier.weight(0.6f))
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val food = Food(
                                barcode = code,
                                name = newName.trim(),
                                kcal = newKcal.toDoubleOrNull() ?: 0.0,
                                protein = newP.toDoubleOrNull() ?: 0.0,
                                carbs = newC.toDoubleOrNull() ?: 0.0,
                                fat = newF.toDoubleOrNull() ?: 0.0,
                                custom = true,
                            )
                            db.foodDao().upsert(food)
                            results = listOf(food)
                            selected = food
                            missedBarcode = null
                            // contribute upstream to Open Food Facts if creds configured
                            val user = db.settingDao().get("offUser").orEmpty()
                            val pass = db.settingDao().get("offPassword").orEmpty()
                            if (user.isNotBlank() && pass.isNotBlank()) off.submitProduct(food, user, pass)
                        }
                    }, enabled = newName.isNotBlank() && newKcal.isNotBlank()) { Text("Create food") }
                }
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
