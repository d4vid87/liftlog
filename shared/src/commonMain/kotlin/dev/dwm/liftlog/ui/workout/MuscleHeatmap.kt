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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

// --- anatomical figure: real muscle paths from BodyArt.kt (react-native-body-highlighter, MIT) ---

/** SVG slug -> muscle group. Slugs absent here (forearms, shins, hands, head…) render neutral. */
internal val slugMuscle: Map<String, Muscle> = mapOf(
    "chest" to Muscle.CHEST, "abs" to Muscle.ABS, "obliques" to Muscle.OBLIQUES,
    "biceps" to Muscle.BICEPS, "triceps" to Muscle.TRICEPS, "deltoids" to Muscle.SHOULDERS,
    "trapezius" to Muscle.TRAPS, "quadriceps" to Muscle.QUADS, "hamstring" to Muscle.HAMSTRINGS,
    "gluteal" to Muscle.GLUTES, "calves" to Muscle.CALVES, "upper-back" to Muscle.LATS,
)

private fun buildArt(art: List<Pair<String, List<String>>>): List<Pair<Muscle?, List<Path>>> =
    art.map { (slug, ds) -> slugMuscle[slug] to ds.map { PathParser().parsePathString(it).toPath() } }

// parse SVG strings to Paths once per process; drawn in source order to preserve overlaps
private val frontArt by lazy { buildArt(BodyArt.front) }
private val backArt by lazy { buildArt(BodyArt.back) }

@Composable
internal fun BodyCanvas(fatigue: Map<Muscle, Double>, front: Boolean, modifier: Modifier) {
    val pulse = rememberInfiniteTransition()
    val throb by pulse.animateFloat(
        0.72f, 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    Canvas(modifier.height(260.dp)) {
        // fit viewport preserving aspect, centered; back paths live at x+BACK_DX, so shift left
        val s = minOf(size.width / BodyArt.VW, size.height / BodyArt.VH)
        val dx = (size.width - BodyArt.VW * s) / 2f - (if (front) 0f else BodyArt.BACK_DX * s)
        val dy = (size.height - BodyArt.VH * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            for ((m, paths) in if (front) frontArt else backArt) {
                val c = if (m == null) BodyColor else {
                    val v = (fatigue[m] ?: 0.0).toFloat()
                    heatColor(v).let { if (v > 0.55f) it.copy(alpha = throb) else it }
                }
                for (p in paths) drawPath(p, c)
            }
        }
    }
}
