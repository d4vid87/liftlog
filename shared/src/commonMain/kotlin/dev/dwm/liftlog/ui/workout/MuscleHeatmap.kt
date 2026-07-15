package dev.dwm.liftlog.ui.workout

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.Muscle
import dev.dwm.liftlog.domain.MuscleLoad
import dev.dwm.liftlog.domain.fatigueMap
import dev.dwm.liftlog.domain.musclesFor
import dev.dwm.liftlog.domain.readiness
import androidx.compose.foundation.clickable
import kotlinx.coroutines.flow.first

private val RestedColor = Color(0xFF3C6B4F)   // recovered = calm green
private val MidColor = Color(0xFFFFB74D)      // working = amber
private val HotColor = Color(0xFFFF5252)      // fried = red
private val BodyColor = Color(0xFF23262B)

private fun heatColor(f: Float): Color =
    if (f < 0.5f) lerp(RestedColor, MidColor, f * 2f)
    else lerp(MidColor, HotColor, (f - 0.5f) * 2f)

/** Last time each muscle was trained (finished workouts, last 7 days), keyed to workout.startedAt. */
suspend fun lastTrainedByMuscle(db: AppDatabase): Map<Muscle, Long> {
    val now = nowMillis()
    val since = now - 7 * 24 * 3600_000L
    val out = mutableMapOf<Muscle, Long>()
    val exCache = mutableMapOf<String, List<Muscle>>()
    for (w in db.workoutDao().history().first()) {
        if (w.startedAt < since) break // history is startedAt DESC
        for (set in db.workoutDao().setsForWorkoutOnce(w.id)) {
            if (!set.completed) continue
            val muscles = exCache.getOrPut(set.exerciseId) {
                db.exerciseDao().byId(set.exerciseId)?.let { musclesFor(it.muscles, it.category) } ?: emptyList()
            }
            for (m in muscles) out[m] = maxOf(out[m] ?: 0L, w.startedAt)
        }
    }
    return out
}

/** Fitbod-style recovery heat map: front + back body, muscles tinted by 96h fatigue. */
@Composable
fun RecoveryCard(db: AppDatabase, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    var fatigue by remember { mutableStateOf<Map<Muscle, Double>>(emptyMap()) }
    var readyCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val now = nowMillis()
        val since = now - 96 * 3600_000L
        val loads = mutableListOf<MuscleLoad>()
        val exCache = mutableMapOf<String, List<Muscle>>()
        for (set in db.syncDao().setsSince(since)) {
            if (!set.completed || set.deletedAt != null) continue
            val muscles = exCache.getOrPut(set.exerciseId) {
                db.exerciseDao().byId(set.exerciseId)?.let { musclesFor(it.muscles, it.category) } ?: emptyList()
            }
            if (muscles.isEmpty()) continue
            // bodyweight sets: count reps as volume so abs/cardio still register
            val volume = if (set.weightKg > 0) set.weightKg * set.reps else set.reps.toDouble()
            loads.add(MuscleLoad(muscles, volume, (now - set.updatedAt) / 3600_000.0))
        }
        fatigue = fatigueMap(loads)
        readyCount = readiness(lastTrainedByMuscle(db), now).count { it.hoursLeft == 0 }
    }

    val cardMod = if (onOpen != null) modifier.fillMaxWidth().clickable { onOpen() } else modifier.fillMaxWidth()
    Card(cardMod) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muscle Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            readyCount?.let {
                Text(
                    "$it of ${Muscle.entries.size} muscles ready" + if (onOpen != null) " · tap for details" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                if (fatigue.isEmpty()) "Train to see fatigue from the last 4 days."
                else "Green = recovered · amber = working · red = fried (last 4 days).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyCanvas(fatigue, front = true, Modifier.weight(1f))
                BodyCanvas(fatigue, front = false, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Front", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Back", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- anatomical figure: hand-authored SVG paths in a 100×220 viewport ---
// left-side muscle paths are mirrored at draw time; symmetric shapes drawn once
private object BodyPaths {
    const val VW = 100f
    const val VH = 220f

    val head = "M50,2 C56,2 59,6.5 59,13 C59,19.5 56,24 50,24 C44,24 41,19.5 41,13 C41,6.5 44,2 50,2 Z"

    // full silhouette: neck, delt caps, arms slightly abducted, v-taper torso, legs to ankles
    val body =
        "M45,24 L55,24 L56.5,29 " +
        "C66,30 73,32 76.5,37 C79.5,42 80.5,49 82,57 L85,76 L87,96 L82.5,97 L79.5,79 L77,61 L74.5,51 " +
        "L73.5,61 L74.5,80 L75.5,96 C75.5,103 73.5,109 71,113 L69,131 L68,151 L69,166 L68,186 L69,204 L61.5,204 L61,186 L60,166 L59,149 L57,129 L54,113 " +
        "L50,111 L46,113 L43,129 L41,149 L40,166 L39,186 L38.5,204 L31,204 L32,186 L31,166 L32,151 L31,131 L29,113 " +
        "C26.5,109 24.5,103 24.5,96 L25.5,80 L26.5,61 L25.5,51 L23,61 L20.5,79 L17.5,97 L13,96 L15,76 L18,57 " +
        "C19.5,49 20.5,42 23.5,37 C27,32 34,30 43.5,29 Z"

    // ---- front regions (left-side ones mirrored for right) ----
    val trapsFront = "M43,26 L57,26 L61,31 L39,31 Z"
    val deltL = "M23.5,31.5 C27.5,29.5 32.5,29.5 35,31.5 C36,34.5 35.5,38.5 33,40.5 C29,40.5 25,38.5 24,35.5 Z"
    val pecL = "M36.5,32.5 C42,31.5 47.5,32.5 49.2,34.5 L49.2,46 C45,49.5 38.5,48.5 36,44.5 C35,40.5 35,35.5 36.5,32.5 Z"
    val bicepL = "M22,42 C25,41 28.5,42 29.5,44 L28.5,58 C26.5,60.5 23,60.5 21.5,58 L21,46 Z"
    val abs = "M42.5,50 L57.5,50 L56.5,79 C53.5,83 46.5,83 43.5,79 Z"
    val obliqueL = "M36.5,50 L41,51 L42,77 L37.5,73 Z"
    val quadL = "M34,110 C39,108 44.5,110 46,114 L45,139 C42,143 36.5,143 34.8,139 Z"
    val calfFrontL = "M32.5,150 C36,148 40,149 41,152 L40,173 C38,176 34.5,176 33.5,173 Z"

    // ---- back regions ----
    val trapsBack = "M50,26 L62.5,32 L50,53 L37.5,32 Z"
    val latL = "M35.5,36 L47.5,44 L47.5,66 L38,72.5 C36,60 35,46 35.5,36 Z"
    val gluteL = "M37.5,84 C43,82 48.5,84 49.3,88 L48.3,101 C44,105 38.5,104 36.8,99.5 Z"
    val hamL = "M34.5,110 C39.5,108.5 44.5,110 45.8,114 L44.8,139 C41.8,143 36.5,143 34.8,139 Z"
    val calfBackL = "M32.5,149 C36.5,146.5 40.5,148 41.3,151.5 L40.3,174 C38,177 34.3,177 33.3,174 Z"
}

@Composable
internal fun BodyCanvas(fatigue: Map<Muscle, Double>, front: Boolean, modifier: Modifier) {
    val pulse = rememberInfiniteTransition()
    val throb by pulse.animateFloat(
        0.72f, 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    // parse once per composition, reuse across frames
    val paths = remember {
        fun p(d: String): Path = PathParser().parsePathString(d).toPath()
        object {
            val head = p(BodyPaths.head)
            val body = p(BodyPaths.body)
            val front = listOf(
                Muscle.TRAPS to listOf(p(BodyPaths.trapsFront)),
                Muscle.SHOULDERS to listOf(p(BodyPaths.deltL), p(BodyPaths.deltL).mirrored()),
                Muscle.CHEST to listOf(p(BodyPaths.pecL), p(BodyPaths.pecL).mirrored()),
                Muscle.BICEPS to listOf(p(BodyPaths.bicepL), p(BodyPaths.bicepL).mirrored()),
                Muscle.ABS to listOf(p(BodyPaths.abs)),
                Muscle.OBLIQUES to listOf(p(BodyPaths.obliqueL), p(BodyPaths.obliqueL).mirrored()),
                Muscle.QUADS to listOf(p(BodyPaths.quadL), p(BodyPaths.quadL).mirrored()),
                Muscle.CALVES to listOf(p(BodyPaths.calfFrontL), p(BodyPaths.calfFrontL).mirrored()),
            )
            val back = listOf(
                Muscle.TRAPS to listOf(p(BodyPaths.trapsBack)),
                Muscle.SHOULDERS to listOf(p(BodyPaths.deltL), p(BodyPaths.deltL).mirrored()),
                Muscle.LATS to listOf(p(BodyPaths.latL), p(BodyPaths.latL).mirrored()),
                Muscle.TRICEPS to listOf(p(BodyPaths.bicepL), p(BodyPaths.bicepL).mirrored()),
                Muscle.GLUTES to listOf(p(BodyPaths.gluteL), p(BodyPaths.gluteL).mirrored()),
                Muscle.HAMSTRINGS to listOf(p(BodyPaths.hamL), p(BodyPaths.hamL).mirrored()),
                Muscle.CALVES to listOf(p(BodyPaths.calfBackL), p(BodyPaths.calfBackL).mirrored()),
            )
        }
    }
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Canvas(modifier.height(260.dp)) {
        // fit viewport preserving aspect, centered
        val s = minOf(size.width / BodyPaths.VW, size.height / BodyPaths.VH)
        val dx = (size.width - BodyPaths.VW * s) / 2f
        val dy = (size.height - BodyPaths.VH * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            // body fill with a subtle vertical gradient for depth
            val bodyBrush = Brush.verticalGradient(
                listOf(BodyColor.copy(alpha = 0.95f), BodyColor.copy(alpha = 0.75f)),
                startY = 0f, endY = BodyPaths.VH,
            )
            drawPath(paths.head, bodyBrush)
            drawPath(paths.body, bodyBrush)
            drawPath(paths.head, outlineColor, style = Stroke(width = 0.8f))
            drawPath(paths.body, outlineColor, style = Stroke(width = 0.8f))

            for ((m, regions) in if (front) paths.front else paths.back) {
                val v = (fatigue[m] ?: 0.0).toFloat()
                val c = heatColor(v).let { if (v > 0.55f) it.copy(alpha = throb) else it }
                for (r in regions) {
                    drawPath(r, c)
                    drawPath(r, outlineColor.copy(alpha = 0.25f), style = Stroke(width = 0.5f))
                }
            }
        }
    }
}

/** Mirror a left-side region across the figure's vertical center line (x = 50). */
private fun Path.mirrored(): Path {
    val m = androidx.compose.ui.graphics.Matrix()
    m.translate(BodyPaths.VW, 0f, 0f)
    m.scale(-1f, 1f, 1f)
    val copy = Path().apply { addPath(this@mirrored) }
    copy.transform(m)
    return copy
}
