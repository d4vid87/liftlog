package dev.dwm.liftlog.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp { name, weight, reps -> sendSet(name, weight, reps) } }
    }

    private suspend fun sendSet(name: String, weight: Double, reps: Int): Boolean = runCatching {
        val nodes = Wearable.getNodeClient(this).connectedNodes.await()
        val payload = "$name|$weight|$reps".encodeToByteArray()
        nodes.forEach { node ->
            Wearable.getMessageClient(this).sendMessage(node.id, "/liftlog/log-set", payload).await()
        }
        nodes.isNotEmpty()
    }.getOrDefault(false)
}

private val quickExercises = listOf("Squat", "Bench Press", "Deadlift", "Overhead Press", "Bent Over Row")

@Composable
fun WearApp(sendSet: suspend (String, Double, Int) -> Boolean) {
    var exerciseIndex by remember { mutableIntStateOf(0) }
    var weight by remember { mutableDoubleStateOf(60.0) }
    var reps by remember { mutableIntStateOf(5) }
    var restEndsAt by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(restEndsAt) {
        while (restEndsAt > System.currentTimeMillis()) {
            delay(250)
            nowTick = System.currentTimeMillis()
        }
    }

    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val resting = restEndsAt > nowTick
            if (resting) {
                Text("Rest ${(restEndsAt - nowTick) / 1000}s", style = MaterialTheme.typography.title2)
            }
            Text(
                quickExercises[exerciseIndex],
                style = MaterialTheme.typography.title3,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { exerciseIndex = (exerciseIndex + 1) % quickExercises.size }) { Text("↻") }
                Button(onClick = { weight = (weight - 2.5).coerceAtLeast(0.0) }) { Text("−") }
                Text("${if (weight % 1.0 == 0.0) weight.toInt() else weight}kg")
                Button(onClick = { weight += 2.5 }) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { reps = (reps - 1).coerceAtLeast(1) }) { Text("−") }
                Text("$reps reps")
                Button(onClick = { reps += 1 }) { Text("+") }
            }
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = sendSet(quickExercises[exerciseIndex], weight, reps)
                        status = if (ok) "Logged ✓" else "No phone"
                        if (ok) restEndsAt = System.currentTimeMillis() + 90_000
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f),
            ) { Text("Log Set") }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.caption1)
        }
    }
}
