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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.Muscle
import dev.dwm.liftlog.domain.MuscleLoad
import dev.dwm.liftlog.domain.fatigueMap
import dev.dwm.liftlog.domain.musclesFor

private val RestedColor = Color(0xFF3C6B4F)   // recovered = calm green
private val MidColor = Color(0xFFFFB74D)      // working = amber
private val HotColor = Color(0xFFFF5252)      // fried = red
private val BodyColor = Color(0xFF23262B)

private fun heatColor(f: Float): Color =
    if (f < 0.5f) lerp(RestedColor, MidColor, f * 2f)
    else lerp(MidColor, HotColor, (f - 0.5f) * 2f)

/** Fitbod-style recovery heat map: front + back body, muscles tinted by 96h fatigue. */
@Composable
fun RecoveryCard(db: AppDatabase, modifier: Modifier = Modifier) {
    var fatigue by remember { mutableStateOf<Map<Muscle, Double>>(emptyMap()) }

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
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muscle Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

@Composable
internal fun BodyCanvas(fatigue: Map<Muscle, Double>, front: Boolean, modifier: Modifier) {
    // hot muscles pulse; the whole figure sits on a smooth capsule-limbed silhouette
    val pulse = rememberInfiniteTransition()
    val throb by pulse.animateFloat(
        0.72f, 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    Canvas(modifier.height(260.dp)) {
        fun f(m: Muscle) = (fatigue[m] ?: 0.0).toFloat()
        fun tint(m: Muscle): Color {
            val v = f(m)
            val c = heatColor(v)
            return if (v > 0.55f) c.copy(alpha = throb) else c
        }

        // --- silhouette: head, neck, tapered torso, capsule limbs ---
        drawCircle(BodyColor, radius = 0.048f * size.height, center = pt(0.5f, 0.055f))
        limb(0.5f, 0.095f, 0.5f, 0.135f, 0.055f, BodyColor)            // neck
        torso(BodyColor)
        limb(0.305f, 0.165f, 0.245f, 0.33f, 0.062f, BodyColor)         // upper arms
        limb(0.695f, 0.165f, 0.755f, 0.33f, 0.062f, BodyColor)
        limb(0.245f, 0.33f, 0.215f, 0.47f, 0.05f, BodyColor)           // forearms
        limb(0.755f, 0.33f, 0.785f, 0.47f, 0.05f, BodyColor)
        limb(0.435f, 0.52f, 0.415f, 0.73f, 0.085f, BodyColor)          // thighs
        limb(0.565f, 0.52f, 0.585f, 0.73f, 0.085f, BodyColor)
        limb(0.415f, 0.73f, 0.41f, 0.94f, 0.06f, BodyColor)            // lower legs
        limb(0.585f, 0.73f, 0.59f, 0.94f, 0.06f, BodyColor)

        // --- muscles: glow halo + shape ---
        fun muscle(m: Muscle, x: Float, y: Float, w: Float, h: Float) {
            val v = f(m)
            val c = tint(m)
            if (v > 0.15f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(heatColor(v).copy(alpha = 0.45f * v * (if (v > 0.55f) throb else 1f)), Color.Transparent),
                        center = pt(x + w / 2, y + h / 2),
                        radius = (w + h) / 2 * size.height,
                    ),
                    radius = (w + h) / 2 * size.height,
                    center = pt(x + w / 2, y + h / 2),
                )
            }
            drawOval(c, topLeft = pt(x, y), size = Size(w * size.width, h * size.height))
        }

        if (front) {
            muscle(Muscle.SHOULDERS, 0.27f, 0.14f, 0.09f, 0.06f); muscle(Muscle.SHOULDERS, 0.64f, 0.14f, 0.09f, 0.06f)
            muscle(Muscle.CHEST, 0.375f, 0.165f, 0.115f, 0.085f); muscle(Muscle.CHEST, 0.51f, 0.165f, 0.115f, 0.085f)
            muscle(Muscle.BICEPS, 0.245f, 0.20f, 0.075f, 0.11f); muscle(Muscle.BICEPS, 0.68f, 0.20f, 0.075f, 0.11f)
            muscle(Muscle.ABS, 0.435f, 0.265f, 0.13f, 0.16f)
            muscle(Muscle.OBLIQUES, 0.385f, 0.265f, 0.045f, 0.12f); muscle(Muscle.OBLIQUES, 0.57f, 0.265f, 0.045f, 0.12f)
            muscle(Muscle.QUADS, 0.385f, 0.50f, 0.095f, 0.20f); muscle(Muscle.QUADS, 0.52f, 0.50f, 0.095f, 0.20f)
            muscle(Muscle.CALVES, 0.375f, 0.75f, 0.075f, 0.13f); muscle(Muscle.CALVES, 0.55f, 0.75f, 0.075f, 0.13f)
        } else {
            muscle(Muscle.TRAPS, 0.40f, 0.125f, 0.20f, 0.065f)
            muscle(Muscle.SHOULDERS, 0.27f, 0.14f, 0.09f, 0.06f); muscle(Muscle.SHOULDERS, 0.64f, 0.14f, 0.09f, 0.06f)
            muscle(Muscle.LATS, 0.375f, 0.20f, 0.11f, 0.16f); muscle(Muscle.LATS, 0.515f, 0.20f, 0.11f, 0.16f)
            muscle(Muscle.TRICEPS, 0.245f, 0.20f, 0.075f, 0.11f); muscle(Muscle.TRICEPS, 0.68f, 0.20f, 0.075f, 0.11f)
            muscle(Muscle.GLUTES, 0.40f, 0.435f, 0.095f, 0.085f); muscle(Muscle.GLUTES, 0.505f, 0.435f, 0.095f, 0.085f)
            muscle(Muscle.HAMSTRINGS, 0.385f, 0.545f, 0.095f, 0.17f); muscle(Muscle.HAMSTRINGS, 0.52f, 0.545f, 0.095f, 0.17f)
            muscle(Muscle.CALVES, 0.375f, 0.75f, 0.075f, 0.13f); muscle(Muscle.CALVES, 0.55f, 0.75f, 0.075f, 0.13f)
        }
    }
}

private fun DrawScope.pt(x: Float, y: Float) = Offset(x * size.width, y * size.height)

/** Capsule limb: thick round-capped line in unit coords; width relative to canvas width. */
private fun DrawScope.limb(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Color) {
    drawLine(color, pt(x1, y1), pt(x2, y2), strokeWidth = w * size.width, cap = StrokeCap.Round)
}

/** Tapered torso: shoulders wide, waist narrow, hips flare. */
private fun DrawScope.torso(color: Color) {
    val path = Path().apply {
        moveTo(pt(0.32f, 0.135f).x, pt(0.32f, 0.135f).y)
        lineTo(pt(0.68f, 0.135f).x, pt(0.68f, 0.135f).y)
        cubicTo(
            pt(0.66f, 0.30f).x, pt(0.66f, 0.30f).y,
            pt(0.63f, 0.38f).x, pt(0.63f, 0.38f).y,
            pt(0.62f, 0.46f).x, pt(0.62f, 0.46f).y,
        )
        cubicTo(
            pt(0.60f, 0.53f).x, pt(0.60f, 0.53f).y,
            pt(0.40f, 0.53f).x, pt(0.40f, 0.53f).y,
            pt(0.38f, 0.46f).x, pt(0.38f, 0.46f).y,
        )
        cubicTo(
            pt(0.37f, 0.38f).x, pt(0.37f, 0.38f).y,
            pt(0.34f, 0.30f).x, pt(0.34f, 0.30f).y,
            pt(0.32f, 0.135f).x, pt(0.32f, 0.135f).y,
        )
        close()
    }
    drawPath(path, color)
}
