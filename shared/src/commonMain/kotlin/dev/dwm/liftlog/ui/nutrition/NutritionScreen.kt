package dev.dwm.liftlog.ui.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.ui.Palette
import dev.dwm.liftlog.ui.components.CalorieRing
import dev.dwm.liftlog.ui.components.MacroBar
import dev.dwm.liftlog.data.db.DailyMacro
import dev.dwm.liftlog.data.db.GroceryItem
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
import dev.dwm.liftlog.domain.kgToLbDisplay
import dev.dwm.liftlog.domain.lbToKg
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
    takePhoto: (suspend () -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    val today = remember { todayEpochDay() }
    var day by remember { mutableStateOf(today) }
    val logs by remember(day) { db.foodLogDao().forDay(day) }.collectAsStateList()
    var foods by remember { mutableStateOf<Map<String, Food>>(emptyMap()) }
    var tdee by remember { mutableStateOf<TdeeResult?>(null) }
    var todayWeight by remember { mutableStateOf<Double?>(null) }
    var addingTo by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(logs) {
        foods = logs.map { it.foodId }.distinct()
            .mapNotNull { db.foodDao().byId(it) }.associateBy { it.id }
    }
    var week by remember { mutableStateOf<List<DailyMacro>>(emptyList()) }
    var targetKcal by remember { mutableStateOf(2000.0) }
    var proteinPct by remember { mutableStateOf(30.0) }
    var fatPct by remember { mutableStateOf(30.0) }
    var showMicros by remember { mutableStateOf(false) }
    var showGroceries by remember { mutableStateOf(false) }
    LaunchedEffect(logs, refresh) {
        val goal = db.settingDao().get("goalKgPerWeek")?.toDoubleOrNull() ?: 0.0
        val weights = db.weightDao().all().map { DayWeight(it.epochDay, it.kg) }
        val intakes = db.foodLogDao().dailyKcals(today - 35).map { DayIntake(it.epochDay, it.kcal) }
        tdee = computeTdee(weights, intakes, goal)
        todayWeight = db.weightDao().forDay(today)?.kg
        week = db.foodLogDao().dailyMacros(today - 6)
        targetKcal = tdee?.targetKcal
            ?: db.settingDao().get("targetKcal")?.toDoubleOrNull() ?: 2000.0
        proteinPct = db.settingDao().get("proteinPct")?.toDoubleOrNull() ?: 30.0
        fatPct = db.settingDao().get("fatPct")?.toDoubleOrNull() ?: 30.0
    }

    if (showMicros) MicrosDialog(logs.mapNotNull { l -> foods[l.foodId]?.let { f -> l.grams to f } }) { showMicros = false }
    if (showGroceries) GroceriesDialog(db, day) { showGroceries = false }

    addingTo?.let { meal ->
        LogFoodsDialog(
            db, off, scanBarcode, takePhoto,
            onDismiss = { addingTo = null },
            onAdd = { food, grams ->
                scope.launch {
                    db.foodDao().upsert(food)
                    db.foodLogDao().insert(FoodLog(epochDay = day, foodId = food.id, grams = grams, meal = meal))
                }
                addingTo = null
            },
            onLogParsed = { parsed ->
                scope.launch {
                    parsed.forEach { p ->
                        val food = p.toFood()
                        db.foodDao().upsert(food)
                        db.foodLogDao().insert(FoodLog(epochDay = day, foodId = food.id, grams = p.grams, meal = meal))
                    }
                }
                addingTo = null
            },
        )
    }

    val totals = logs.mapNotNull { l -> foods[l.foodId]?.let { f -> l.grams to f } }
    val kcal = totals.sumOf { (g, f) -> g * f.kcal / 100 }
    val protein = totals.sumOf { (g, f) -> g * f.protein / 100 }
    val carbs = totals.sumOf { (g, f) -> g * f.carbs / 100 }
    val fat = totals.sumOf { (g, f) -> g * f.fat / 100 }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { day-- }) { Text("◀") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (day) {
                            today -> "Today"
                            today - 1 -> "Yesterday"
                            today + 1 -> "Tomorrow"
                            else -> kotlinx.datetime.LocalDate.fromEpochDays(day.toInt()).toString()
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (day != today) {
                        Text(
                            if (day > today) "planning" else "history",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { day++ }) { Text("▶") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showMicros = true }, modifier = Modifier.weight(1f)) { Text("Micros") }
                OutlinedButton(onClick = { showGroceries = true }, modifier = Modifier.weight(1f)) { Text("Groceries") }
                if (day != today) OutlinedButton(onClick = { day = today }, modifier = Modifier.weight(1f)) { Text("Today") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Calories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Remaining = Target − Food",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CalorieRing(eaten = kcal, target = targetKcal, modifier = Modifier.size(120.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val carbsPct = (100.0 - proteinPct - fatPct).coerceAtLeast(0.0)
                            MacroBar("Protein", protein, targetKcal * proteinPct / 100 / 4, Palette.Protein)
                            MacroBar("Carbs", carbs, targetKcal * carbsPct / 100 / 4, Palette.Carbs)
                            MacroBar("Fat", fat, targetKcal * fatPct / 100 / 9, Palette.Fat)
                        }
                    }
                    tdee?.let { t ->
                        Text(
                            "Expenditure ~${t.tdeeKcal.toInt()} kcal · target ${t.targetKcal.toInt()} · " +
                                "trend ${t.trendWeightKg.kgToLbDisplay().clean()} lb (${if (t.weeklyDeltaKg >= 0) "+" else ""}${t.weeklyDeltaKg.kgToLbDisplay().clean()} lb/wk)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } ?: Text(
                        "TDEE: log weight + food for 7+ days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WeightRow(todayWeight?.kgToLbDisplay()) { lb ->
                        val kg = lb.lbToKg()
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
        item { WeeklyMacroCard(week, today, targetKcal) }
        meals.forEach { meal ->
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(meal, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { addingTo = meal }) {
                        Text("Log Foods", color = Palette.Trend, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(logs.filter { it.meal == meal }, key = { it.id }) { log ->
                val food = foods[log.foodId]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FoodCircle(food?.name ?: "?")
                    Column(Modifier.weight(1f)) {
                        Text(food?.name ?: "…", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            food?.let { macroLine(it, log.grams) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${log.grams.clean()} grams",
                            style = MaterialTheme.typography.labelSmall,
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

// MacroFactor-style summary: "204 Cal · 33P · 7F · 25C" for the given portion
private fun macroLine(food: Food, grams: Double): String {
    val k = (food.kcal * grams / 100).toInt()
    val p = (food.protein * grams / 100).toInt()
    val f = (food.fat * grams / 100).toInt()
    val c = (food.carbs * grams / 100).toInt()
    return "$k Cal · ${p}P · ${f}F · ${c}C"
}

private val circleColors = listOf(
    Palette.Protein, Palette.Carbs, Palette.Fat, Palette.Calories, Palette.Trend, Palette.Success,
)

@Composable
private fun FoodCircle(name: String) {
    val color = circleColors[(name.hashCode().mod(circleColors.size))]
    Box(
        Modifier.size(38.dp).background(color.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MicrosDialog(entries: List<Pair<Double, Food>>, onDismiss: () -> Unit) {
    // sum micros across the day's logs; microsJson values are per 100g
    val totals = remember(entries) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val acc = mutableMapOf<String, Double>()
        for ((grams, food) in entries) {
            val obj = food.microsJson?.let {
                runCatching { json.parseToJsonElement(it) as? kotlinx.serialization.json.JsonObject }.getOrNull()
            } ?: continue
            for ((k, v) in obj) {
                val amount = (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: continue
                acc[k] = (acc[k] ?: 0.0) + amount * grams / 100.0
            }
        }
        acc.toList().sortedBy { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Micronutrients — today") },
        text = {
            if (totals.isEmpty()) {
                Text("No micronutrient data. Foods from Open Food Facts carry fiber, sugars, sodium, vitamins…")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(totals, key = { it.first }) { (name, amount) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name.replace('-', ' ').replaceFirstChar { it.uppercase() })
                            Text(
                                if (amount < 1.0) "${(amount * 1000).toInt()} mg" else "${amount.clean()} g",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun GroceriesDialog(db: AppDatabase, day: Long, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items by remember { db.groceryDao().items() }.collectAsStateList()
    var newItem by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { scope.launch { db.groceryDao().clearChecked() } }) { Text("Clear checked") }
        },
        title = { Text("Groceries") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        label = { Text("Add item") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        scope.launch { db.groceryDao().upsert(GroceryItem(name = newItem.trim())) }
                        newItem = ""
                    }, enabled = newItem.isNotBlank()) { Text("Add") }
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        // aggregate everything planned for the next 7 days into the list
                        val planned = db.foodLogDao().forRangeOnce(day + 1, day + 7)
                        val byFood = planned.groupBy { it.foodId }
                        for ((foodId, logs) in byFood) {
                            val food = db.foodDao().byId(foodId) ?: continue
                            db.groceryDao().upsert(
                                GroceryItem(name = food.name, qty = "${logs.sumOf { it.grams }.clean()} g")
                            )
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Generate from next 7 days' plan") }
                if (items.isEmpty()) {
                    Text(
                        "Empty. Plan meals on future days (▶ arrow), then generate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(items, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { c ->
                                    scope.launch { db.groceryDao().upsert(item.copy(checked = c, updatedAt = nowMillis())) }
                                },
                            )
                            Text(
                                item.name + if (item.qty.isNotBlank()) " (${item.qty})" else "",
                                Modifier.weight(1f),
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(onClick = { scope.launch { db.groceryDao().delete(item.id) } }) {
                                Icon(Icons.Default.Delete, "delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
    )
}
@Composable
private fun WeeklyMacroCard(week: List<DailyMacro>, today: Long, targetKcal: Double) {
    val byDay = week.associateBy { it.epochDay }
    val days = (today - 6..today).toList()
    val maxKcal = maxOf(targetKcal, week.maxOfOrNull { it.kcal } ?: 0.0)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calorie Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Last 7 days · protein / fat / carbs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val m = byDay[day]
                    Column(Modifier.weight(1f).height(140.dp), verticalArrangement = Arrangement.Bottom) {
                        if (m != null && m.kcal > 0) {
                            val pK = (m.protein * 4).toFloat()
                            val fK = (m.fat * 9).toFloat()
                            val cK = (m.carbs * 4).toFloat()
                            val total = (pK + fK + cK).coerceAtLeast(1f)
                            val frac = (m.kcal / maxKcal).toFloat().coerceIn(0.05f, 1f)
                            StackedBar(
                                Modifier.fillMaxWidth().height(140.dp * frac),
                                pFrac = pK / total, fFrac = fK / total, cFrac = cK / total,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            byDay[day]?.kcal?.toInt()?.toString() ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Calories,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            dayLetter(day),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// stacked bar: protein on top, fat middle, carbs bottom (MacroFactor style)
@Composable
private fun StackedBar(modifier: Modifier, pFrac: Float, fFrac: Float, cFrac: Float) {
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.fillMaxWidth().weight(pFrac.coerceAtLeast(0.01f)).background(Palette.Protein, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
        Box(Modifier.fillMaxWidth().weight(fFrac.coerceAtLeast(0.01f)).background(Palette.Fat))
        Box(Modifier.fillMaxWidth().weight(cFrac.coerceAtLeast(0.01f)).background(Palette.Carbs))
    }
}

private fun dayLetter(epochDay: Long): String =
    when ((epochDay + 3).mod(7L)) { // epoch day 0 = Thursday; +3 aligns Monday=0
        0L -> "M"; 1L -> "T"; 2L -> "W"; 3L -> "T"; 4L -> "F"; 5L -> "S"; else -> "S"
    }

@Composable
private fun WeightRow(current: Double?, onSave: (Double) -> Unit) {
    var text by remember(current) { mutableStateOf(current?.clean() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Weight (lb)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { text.toDoubleOrNull()?.let(onSave) }) { Text("Save") }
    }
}

// MacroFactor-style unified log sheet: Scan / Search / AI / Quick Add tabs
@Composable
private fun LogFoodsDialog(
    db: AppDatabase,
    off: OpenFoodFacts,
    scanBarcode: (suspend () -> String?)?,
    takePhoto: (suspend () -> String?)?,
    onDismiss: () -> Unit,
    onAdd: (Food, Double) -> Unit,
    onLogParsed: (List<ParsedFood>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tabs = buildList {
        if (scanBarcode != null) add("Scan")
        add("Search"); add("AI"); add("Quick Add")
    }
    var tab by remember { mutableStateOf("Search") }
    var grams by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Log Foods") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEach { t ->
                        FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) })
                    }
                }
                when (tab) {
                    "Scan" -> ScanTab(db, off, scanBarcode!!, grams, { grams = it }, onAdd)
                    "Search" -> SearchTab(db, off, grams, { grams = it }, onAdd)
                    "AI" -> AiTab(db, takePhoto, onLogParsed)
                    "Quick Add" -> QuickAddTab(onAdd)
                }
            }
        },
    )
}

@Composable
private fun GramsField(grams: String, onGrams: (String) -> Unit) {
    OutlinedTextField(
        value = grams,
        onValueChange = onGrams,
        label = { Text("Grams") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FoodResultRow(food: Food, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FoodCircle(food.name)
        Column(Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                (food.brand.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") + macroLine(food, 100.0) + " /100g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.size(32.dp)
                .background(Palette.Trend.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) { Text("+", color = Palette.Trend, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SearchTab(
    db: AppDatabase,
    off: OpenFoodFacts,
    grams: String,
    onGrams: (String) -> Unit,
    onAdd: (Food, Double) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Food>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { results = db.foodDao().recentFoods() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search for a food") },
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
        GramsField(grams, onGrams)
        if (searching) CircularProgressIndicator()
        LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(results, key = { it.id }) { food ->
                FoodResultRow(food) { onAdd(food, grams.toDoubleOrNull() ?: 100.0) }
            }
        }
    }
}

@Composable
private fun ScanTab(
    db: AppDatabase,
    off: OpenFoodFacts,
    scanBarcode: suspend () -> String?,
    grams: String,
    onGrams: (String) -> Unit,
    onAdd: (Food, Double) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Food?>(null) }
    var missedBarcode by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newKcal by remember { mutableStateOf("") }
    var newP by remember { mutableStateOf("") }
    var newC by remember { mutableStateOf("") }
    var newF by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            scope.launch {
                searching = true
                val code = scanBarcode()
                if (code != null) {
                    val food = db.foodDao().byBarcode(code) ?: off.byBarcode(code)
                    if (food != null) { found = food; missedBarcode = null } else missedBarcode = code
                }
                searching = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Scan Barcode") }
        GramsField(grams, onGrams)
        if (searching) CircularProgressIndicator()
        found?.let { food -> FoodResultRow(food) { onAdd(food, grams.toDoubleOrNull() ?: 100.0) } }
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
                    found = food
                    missedBarcode = null
                    // contribute upstream to Open Food Facts if creds configured
                    val user = db.settingDao().get("offUser").orEmpty()
                    val pass = db.settingDao().get("offPassword").orEmpty()
                    if (user.isNotBlank() && pass.isNotBlank()) off.submitProduct(food, user, pass)
                }
            }, enabled = newName.isNotBlank() && newKcal.isNotBlank()) { Text("Create food") }
        }
    }
}

@Composable
private fun AiTab(
    db: AppDatabase,
    takePhoto: (suspend () -> String?)?,
    onLog: (List<ParsedFood>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<ParsedFood>>(emptyList()) }

    suspend fun runParse(imageB64: String?) {
        busy = true; error = ""
        val endpoint = db.settingDao().get("aiEndpoint") ?: ""
        val model = db.settingDao().get("aiModel") ?: ""
        if (endpoint.isBlank() || model.isBlank()) {
            error = "Set AI endpoint + model in More tab"
        } else {
            val ai = AiClient(httpClient(), endpoint, model, db.settingDao().get("aiApiKey"))
            parsed = runCatching { ai.parseFoods(text, imageB64) }
                .getOrElse { error = it.message ?: "failed"; emptyList() }
            if (parsed.isEmpty() && error.isBlank()) error = "Nothing parsed"
        }
        busy = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Describe your meal (e.g. 2 eggs and toast)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch { runParse(null) }
            }, enabled = text.isNotBlank() && !busy) { Text("Parse") }
            if (takePhoto != null) {
                Button(onClick = {
                    scope.launch {
                        val photo = takePhoto()
                        if (photo != null) runParse(photo) else error = "No photo taken"
                    }
                }, enabled = !busy) { Text("Snap Photo") }
            }
        }
        if (busy) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        parsed.forEach {
            Text(
                "${it.name}: ${it.grams.clean()}g, ${it.kcal.toInt()} kcal (P${it.protein.toInt()} C${it.carbs.toInt()} F${it.fat.toInt()})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (parsed.isNotEmpty()) {
            Button(onClick = { onLog(parsed) }, modifier = Modifier.fillMaxWidth()) {
                Text("Log ${parsed.size} item(s)")
            }
        }
    }
}

@Composable
private fun QuickAddTab(onAdd: (Food, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Log calories/macros directly — no database lookup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Restaurant meal)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(kcal, { kcal = it }, label = { Text("Calories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(p, { p = it }, label = { Text("P (g)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(f, { f = it }, label = { Text("F (g)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(c, { c = it }, label = { Text("C (g)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            // stored as a 100g custom food logged at 100g, so per-100g == the entered totals
            onAdd(
                Food(
                    name = name.ifBlank { "Quick Add" },
                    kcal = kcal.toDoubleOrNull() ?: 0.0,
                    protein = p.toDoubleOrNull() ?: 0.0,
                    carbs = c.toDoubleOrNull() ?: 0.0,
                    fat = f.toDoubleOrNull() ?: 0.0,
                    custom = true,
                ),
                100.0,
            )
        }, enabled = kcal.toDoubleOrNull() != null, modifier = Modifier.fillMaxWidth()) { Text("Log") }
    }
}
